package net.mistsmp.kits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class MistKitsPlugin extends JavaPlugin implements Listener, CommandExecutor {

    // Added explicit  and  parameters here
    private final Map adminMovingKits = new HashMap<>();
    private final Set registeredKits = new LinkedHashSet<>(Arrays.asList("Default", "Archer", "Warrior"));

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        for (String cmd : List.of("kits", "kitadmin", "givetoken", "kitrename")) {
            if (getCommand(cmd) != null) {
                getCommand(cmd).setExecutor(this);
            }
        }
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can execute kit commands.");
            return true;
        }

        switch (cmd.getName().toLowerCase()) {
            case "kits" -> openKitSelector(player);
            case "kitadmin" -> handleKitAdmin(player);
            case "givetoken" -> handleGiveToken(player, args);
            case "kitrename" -> handleKitRename(player, args);
        }

        return true;
    }

    private void handleKitAdmin(Player player) {
        if (!player.isOp()) {
            player.sendMessage(color("&cYou do not have permission to use this command."));
            return;
        }
        openKitAdmin(player);
    }

    private void handleGiveToken(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(color("&cYou do not have permission to use this command."));
            return;
        }

        if (args.length < 1) {
            player.sendMessage(color("&6&lAvailable Tokens: &eletokens &7(Kill Tokens), &dcompressedtoken &7(Compressed Token)"));
            player.sendMessage(color("&7Usage: /givetoken [player] [amount]"));
            return;
        }

        Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : player;
        if (target == null) {
            player.sendMessage(color("&cPlayer not found!"));
            return;
        }

        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {}
        }

        String type = args[0].toLowerCase();
        ItemStack token;

        if (type.equals("letokens")) {
            token = createItem(Material.NETHER_STAR, amount, "&e&lKill Token", List.of("&7Used to purchase kits."));
            player.sendMessage(color("&aGiven " + amount + "x &eKill Tokens &ato &e" + target.getName() + "."));
        } else if (type.equals("compressedtoken")) {
            token = createItem(Material.BONE_BLOCK, amount, "&d&lCompressed Kill Token",
                    List.of("&7Worth &e64 Kill Tokens &7when used for kits.", "&c&oCurse of Vanishing"));
            token.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
            player.sendMessage(color("&aGiven " + amount + "x &dCompressed Kill Token &ato &e" + target.getName() + "."));
        } else {
            player.sendMessage(color("&cUnknown token type. Use 'letokens' or 'compressedtoken'."));
            return;
        }

        target.getInventory().addItem(token);
    }

    private void handleKitRename(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage(color("&cYou do not have permission to use this command."));
            return;
        }

        if (args.length < 2) {
            player.sendMessage(color("&cUsage: /kitrename  "));
            return;
        }

        String oldName = args[0];
        String newName = args[1];

        String matchingKit = registeredKits.stream()
                .filter(k -> k.equalsIgnoreCase(oldName))
                .findFirst()
                .orElse(null);

        if (matchingKit == null) {
            player.sendMessage(color("&cNo kit named '" + oldName + "' exists!"));
            return;
        }

        if (registeredKits.stream().anyMatch(k -> k.equalsIgnoreCase(newName))) {
            player.sendMessage(color("&cA kit named '" + newName + "' already exists!"));
            return;
        }

        registeredKits.remove(matchingKit);
        registeredKits.add(newName);
        player.sendMessage(color("&aSuccessfully renamed kit '&e" + matchingKit + "&a' to '&e" + newName + "&a'."));
    }

    private void openKitSelector(Player player) {
        KitSelectorHolder holder = new KitSelectorHolder();
        Inventory gui = Bukkit.createInventory(holder, 54, color("&6Kit Selector"));
        holder.setInventory(gui);

        int slot = 0;
        for (String kitName : registeredKits) {
            ItemStack item = createItem(Material.CHEST, 1, "&6&l" + kitName, List.of("&7Click to open kit layout or claim!"));
            gui.setItem(slot++, item);
        }

        player.openInventory(gui);
    }

    private void openKitAdmin(Player player) {
        KitAdminHolder holder = new KitAdminHolder();
        Inventory gui = Bukkit.createInventory(holder, 54, color("&4Kit Admin Manager"));
        holder.setInventory(gui);

        int slot = 0;
        for (String kitName : registeredKits) {
            ItemStack item = createItem(Material.CHEST, 1, "&c&l" + kitName,
                    List.of("&7Current Slot: &e" + slot, "&eClick slot to assign kit position!"));
            gui.setItem(slot++, item);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = e.getInventory().getHolder();

        if (holder instanceof KitSelectorHolder) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

            String kitName = LegacyComponentSerializer.legacyAmpersand().serialize(clicked.getItemMeta().displayName());
            player.sendMessage(color("&aSelected kit: " + kitName));
            player.closeInventory();

        } else if (holder instanceof KitAdminHolder) {
            e.setCancelled(true);
            ItemStack clicked = e.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR || !clicked.hasItemMeta()) return;

            String kitName = LegacyComponentSerializer.legacyAmpersand().serialize(clicked.getItemMeta().displayName());
            adminMovingKits.put(player.getUniqueId(), kitName);
            player.sendMessage(color("&aSelected " + kitName + "&a. Click an empty slot to place it."));
        }
    }

    @EventHandler
    public void onPlayerDeath(EntityDeathEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = victim.getKiller();

        if (attacker != null && !victim.equals(attacker)) {
            ItemStack token = createItem(Material.NETHER_STAR, 1, "&e&lKill Token", List.of("&7Used to purchase kits."));

            if (attacker.getInventory().firstEmpty() != -1) {
                attacker.getInventory().addItem(token);
                attacker.sendMessage(color("&a&l+1 Kill Token &7for killing " + victim.getName() + "!"));
            } else {
                attacker.getWorld().dropItem(attacker.getLocation(), token);
                attacker.sendMessage(color("&a&l+1 Kill Token &7dropped at your feet (Inventory Full)!"));
            }
        }
    }

    // Added explicit  parameter to the lore List here
    private ItemStack createItem(Material material, int amount, String name, List lore) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(color(name));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore.stream().map(this::color).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static class KitSelectorHolder implements InventoryHolder {
        private Inventory inv;
        public void setInventory(Inventory inv) { this.inv = inv; }
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private static class KitAdminHolder implements InventoryHolder {
        private Inventory inv;
        public void setInventory(Inventory inv) { this.inv = inv; }
        @Override public @NotNull Inventory getInventory() { return inv; }
    }
}
