package org.mingzu.puppetLite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.*;
import java.util.function.Consumer;
public class SignGUI implements Listener {
    private static final Map<UUID, Consumer<String>> INPUT_CALLBACKS = new HashMap<>();
    static {
        Bukkit.getPluginManager().registerEvents(new SignGUI(), Puppet.getInstance());
    }
    public static void open(Player player, String defaultText, String title, Consumer<String> callback) {
        UUID uuid = player.getUniqueId();
        INPUT_CALLBACKS.put(uuid, callback);
        Inventory inv = Bukkit.createInventory(null, 9, title);
        ItemStack inputItem = new ItemStack(org.bukkit.Material.NAME_TAG);
        ItemMeta meta = inputItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(defaultText);
            inputItem.setItemMeta(meta);
        }
        inv.setItem(4, inputItem);
        player.openInventory(inv);
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!INPUT_CALLBACKS.containsKey(uuid)) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() != org.bukkit.Material.NAME_TAG) return;
        ItemMeta meta = item.getItemMeta();
        String input = (meta != null && meta.hasDisplayName()) ? meta.getDisplayName() : "";
        Consumer<String> callback = INPUT_CALLBACKS.remove(uuid);
        player.closeInventory();
        if (callback != null) {
            callback.accept(input);
        }
    }
}
