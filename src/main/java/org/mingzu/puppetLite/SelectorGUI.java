package org.mingzu.puppetLite;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
public class SelectorGUI implements Listener {
    private static final String TITLE_PREFIX = "§8Puppet §8| §3Nearby: ";
    private final Puppet plugin;
    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    public SelectorGUI(Puppet plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    public void open(Player player, int page) {
        List<Player> nearby = player.getWorld().getPlayers().stream()
                .filter(p -> !p.equals(player))
                .filter(p -> p.getLocation().distanceSquared(player.getLocation()) <= 10000)
                .sorted(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(player.getLocation())))
                .toList();
        int totalPages = Math.max(1, (int) Math.ceil((double) nearby.size() / SLOTS.length));
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_PREFIX + "Page " + (page + 1));
        ItemStack border = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta();
        if (bm != null) { bm.setDisplayName("§r"); border.setItemMeta(bm); }
        for (int i = 0; i < 54; i++) inv.setItem(i, border);
        int startIndex = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length; i++) {
            int listIndex = startIndex + i;
            if (listIndex >= nearby.size()) {
                inv.setItem(SLOTS[i], new ItemStack(Material.AIR));
                continue;
            }
            inv.setItem(SLOTS[i], createPlayerHead(player, nearby.get(listIndex)));
        }
        if (page > 0) inv.setItem(48, createNavButton("§fPrevious Page", Material.ARROW));
        inv.setItem(49, createNavButton("§cClose GUI", Material.BARRIER));
        if (page < totalPages - 1) inv.setItem(50, createNavButton("§fNext Page", Material.ARROW));
        player.openInventory(inv);
    }
    private ItemStack createPlayerHead(Player viewer, Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;
        meta.setOwningPlayer(target);
        meta.setDisplayName("§3" + target.getName());
        double dist = viewer.getLocation().distance(target.getLocation());
        double hp = target.getHealth();
        boolean isVictim = plugin.getSessionManager().isVictim(target.getUniqueId());
        String status = "§aFree";
        if (isVictim) {
            PuppetSession session = plugin.getSessionManager().getByVictim(target.getUniqueId());
            if (session != null) {
                Player ctrl = Bukkit.getPlayer(session.getControllerUUID());
                status = "§cControlled by " + (ctrl != null ? ctrl.getName() : "Unknown");
            }
        }
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Distance: §f" + String.format("%.1f", dist) + "m");
        lore.add("§7Health: §c" + String.format("%.1f", hp) + " ❤");
        lore.add("");
        lore.add("§7Status: " + status);
        lore.add("");
        if (!isVictim) {
            lore.add("§8▶ §bClick to puppet");
        } else {
            lore.add("§8▶ §cCannot puppet (Busy)");
        }
        meta.setLore(lore);
        head.setItemMeta(meta);
        return head;
    }
    private ItemStack createNavButton(String name, Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
        String title = event.getView().getTitle();
        int page = Integer.parseInt(title.substring(title.lastIndexOf(" ") + 1)) - 1;
        if (item.getType() == Material.ARROW) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                if (meta.getDisplayName().contains("Previous")) open(player, page - 1);
                else if (meta.getDisplayName().contains("Next")) open(player, page + 1);
            }
            return;
        }
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        if (item.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }
        if (item.getType() == Material.PLAYER_HEAD) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String targetName = org.bukkit.ChatColor.stripColor(meta.getDisplayName());
                Player target = Bukkit.getPlayer(targetName);
                player.closeInventory();
                if (target == null || !target.isOnline()) {
                    player.sendMessage("§cPlayer is no longer online.");
                    return;
                }
                plugin.getSessionManager().startSession(player, target);
            }
        }
    }
}
