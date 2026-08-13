package org.mingzu.puppetLite;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
public class ConfigGUI implements Listener {
    private static final String TITLE = "§8Puppet §8| §3Settings";
    private static final Material BORDER_MAIN = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material BORDER_ACCENT = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
    private final Puppet plugin;
    public ConfigGUI(Puppet plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        FileConfiguration cfg = plugin.getConfig();
        ItemStack mainPane = border(BORDER_MAIN);
        ItemStack accentPane = border(BORDER_ACCENT);
        for (int i = 0; i < 54; i++) inv.setItem(i, mainPane);
        for (int i = 18; i <= 26; i++) inv.setItem(i, accentPane);
        inv.setItem(4, label(Material.COMPARATOR, "§3Session & Sync Settings", "§7Adjust numeric limits and intervals"));
        inv.setItem(22, label(Material.REDSTONE_TORCH, "§3Behaviour Toggles", "§7Enable or disable features"));
        inv.setItem(10, numberItem(Material.ENDER_PEARL, "Max Concurrent Sessions", "Global limit of active sessions.\n0 = Unlimited.", cfg.getInt("max-concurrent-sessions"), 1, 5));
        inv.setItem(11, numberItem(Material.CHEST, "Inventory Sync Interval", "Ticks between each inventory sync.", cfg.getInt("inventory-sync-interval"), 1, 5));
        inv.setItem(12, numberItem(Material.POTION, "Effect Sync Interval", "Ticks between each potion effect sync.", cfg.getInt("effect-sync-interval"), 1, 5));
        inv.setItem(13, numberItem(Material.CLOCK, "Consent Timeout", "Seconds victim has to accept a request.", cfg.getInt("consent-timeout"), 1, 5));
        inv.setItem(14, numberItem(Material.EXPERIENCE_BOTTLE, "Max Session Duration", "Max session length in seconds.\n0 = Infinite.", cfg.getInt("max-session-duration"), 10, 60));
        inv.setItem(15, mainPane);
        inv.setItem(16, mainPane);
        inv.setItem(28, toggleItem("Require Victim Consent", "Victim must type /puppet accept\nbefore the session can begin.", cfg.getBoolean("require-victim-consent")));
        inv.setItem(29, toggleItem("Notify Victim on Start", "Send the victim a warning message\nwhen a session starts.", cfg.getBoolean("notify-victim-on-start")));
        inv.setItem(30, toggleItem("Log Sessions to Console", "Print session start/end events\nto the server console.", cfg.getBoolean("log-sessions-to-console")));
        inv.setItem(31, toggleItem("Prevent Victim Damage", "Cancel all incoming damage\nto the victim while puppeted.", cfg.getBoolean("prevent-victim-damage")));
        inv.setItem(32, toggleItem("Block Victim Chat", "Prevent the victim from sending\ntheir own chat messages.", cfg.getBoolean("block-victim-chat")));
        inv.setItem(49, reloadButton());
        player.openInventory(inv);
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == BORDER_MAIN || item.getType() == BORDER_ACCENT || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        String rawName = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
        FileConfiguration cfg = plugin.getConfig();
        String path = configPath(rawName);
        if (rawName.equals("Reload Config")) {
            plugin.reloadConfig();
            player.sendMessage("§8[§3Puppet§8] §aConfiguration reloaded successfully.");
            open(player);
            return;
        }
        if (path == null) return;
        ClickType click = event.getClick();
        if (item.getType() == Material.LIME_DYE || item.getType() == Material.GRAY_DYE) {
            cfg.set(path, !cfg.getBoolean(path));
            plugin.saveConfig();
            open(player);
            return;
        }
        int small = getSmallStep(rawName);
        int large = getLargeStep(rawName);
        int val = cfg.getInt(path);
        if (click == ClickType.LEFT) val += small;
        else if (click == ClickType.RIGHT) val -= small;
        else if (click == ClickType.SHIFT_LEFT) val += large;
        else if (click == ClickType.SHIFT_RIGHT) val -= large;
        if (val < 0) val = 0;
        cfg.set(path, val);
        plugin.saveConfig();
        open(player);
    }
    private int getSmallStep(String name) {
        return name.equals("Max Session Duration") ? 10 : 1;
    }
    private int getLargeStep(String name) {
        return name.equals("Max Session Duration") ? 60 : 5;
    }
    private String configPath(String name) {
        return switch (name) {
            case "Max Concurrent Sessions"  -> "max-concurrent-sessions";
            case "Inventory Sync Interval"  -> "inventory-sync-interval";
            case "Effect Sync Interval"     -> "effect-sync-interval";
            case "Consent Timeout"          -> "consent-timeout";
            case "Max Session Duration"     -> "max-session-duration";
            case "Require Victim Consent"   -> "require-victim-consent";
            case "Notify Victim on Start"   -> "notify-victim-on-start";
            case "Log Sessions to Console"  -> "log-sessions-to-console";
            case "Prevent Victim Damage"    -> "prevent-victim-damage";
            case "Block Victim Chat"        -> "block-victim-chat";
            default -> null;
        };
    }
    private ItemStack border(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        if (m != null) { m.setDisplayName("§r"); item.setItemMeta(m); }
        return item;
    }
    private ItemStack label(Material mat, String title, String sub) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName(title);
            m.setLore(List.of(sub));
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(m);
        }
        return item;
    }
    private ItemStack numberItem(Material mat, String name, String desc, int current, int small, int large) {
        ItemStack item = new ItemStack(mat);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName("§b" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§8§m-------------------------");
            for (String line : desc.split("\n")) lore.add("§7" + line);
            lore.add("");
            lore.add("§fCurrent Value: §3" + current);
            lore.add("");
            lore.add("§8▶ §aLeft-Click §7(+" + small + ") §8| §cRight-Click §7(-" + small + ")");
            lore.add("§8▶ §aShift-Left §7(+" + large + ") §8| §cShift-Right §7(-" + large + ")");
            lore.add("§8§m-------------------------");
            m.setLore(lore);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            item.setItemMeta(m);
        }
        return item;
    }
    private ItemStack toggleItem(String name, String desc, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§8§m-------------------------");
            for (String line : desc.split("\n")) lore.add("§7" + line);
            lore.add("");
            lore.add("§fStatus: " + (enabled ? "§aEnabled" : "§cDisabled"));
            lore.add("");
            lore.add(enabled ? "§8▶ §cClick to disable" : "§8▶ §aClick to enable");
            lore.add("§8§m-------------------------");
            m.setLore(lore);
            if (enabled) {
                m.addEnchant(Enchantment.UNBREAKING, 1, true);
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(m);
        }
        return item;
    }
    private ItemStack reloadButton() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = item.getItemMeta();
        if (m != null) {
            m.setDisplayName("§fReload Config");
            m.setLore(List.of(
                    "§8§m-------------------------",
                    "§7Reload settings directly",
                    "§7from the config.yml file.",
                    "",
                    "§8▶ §bClick to reload",
                    "§8§m-------------------------"
            ));
            m.addEnchant(Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(m);
        }
        return item;
    }
}
