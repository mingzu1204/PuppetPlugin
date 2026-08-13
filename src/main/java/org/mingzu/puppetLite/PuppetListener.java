package org.mingzu.puppetLite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.entity.EntityMountEvent;
import java.util.UUID;
public class PuppetListener implements Listener {
    private final Puppet plugin;
    private final SessionManager sessions;
    public PuppetListener(Puppet plugin, SessionManager sessions) {
        this.plugin   = plugin;
        this.sessions = sessions;
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.isDependencyDisabled() && player.isOp()) {
            plugin.sendDependencyWarning(player);
        }
        PacketInterceptor.eject(player);
        if (!sessions.isVictim(player.getUniqueId())) {
            player.setGravity(true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.cancelPendingConsent(uuid);
        if (sessions.isController(uuid)) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    sessions.endSessionByController(uuid, "Controller kicked."));
        }
        if (sessions.isVictim(uuid)) {
            PuppetSession session = sessions.getByVictim(uuid);
            if (session != null) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        sessions.endSessionByController(session.getControllerUUID(), "Victim kicked."));
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.cancelPendingConsent(uuid);
        if (sessions.isController(uuid)) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                    sessions.endSessionByController(uuid, "Controller disconnected."), 1L);
        }
        if (sessions.isVictim(uuid)) {
            PuppetSession session = sessions.getByVictim(uuid);
            if (session != null) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                        sessions.endSessionByController(session.getControllerUUID(), "Victim disconnected."), 1L);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!sessions.isVictim(uuid)) return;
        PuppetSession session = sessions.getByVictim(uuid);
        if (session == null || session.isTeleportingVictim()) return;
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!sessions.isVictim(uuid)) return;
        PuppetSession session = sessions.getByVictim(uuid);
        if (session == null || session.isTeleportingVictim()) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        boolean isVictim = sessions.isVictim(uuid);
        boolean isController = sessions.isController(uuid);
        if (!isVictim && !isController) return;
        double damageToRealHealth = Math.max(0.0, event.getFinalDamage() - player.getAbsorptionAmount());
        boolean isFatal = damageToRealHealth >= player.getHealth();
        if (isFatal) {
            event.setCancelled(true);
            double safeHealth = Math.max(1.0, player.getHealth());
            player.setHealth(safeHealth);
            if (isVictim) {
                PuppetSession session = sessions.getByVictim(uuid);
                if (session != null) {
                    final UUID controllerUUID = session.getControllerUUID();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && player.getHealth() <= 0) {
                            player.setHealth(1.0);
                        }
                        sessions.endSessionByController(controllerUUID, "Victim took fatal damage.");
                    });
                }
            } else {
                PuppetSession session = sessions.getByController(uuid);
                if (session != null) {
                    final UUID controllerUUID = session.getControllerUUID();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline() && player.getHealth() <= 0) {
                            player.setHealth(1.0);
                        }
                        sessions.endSessionByController(controllerUUID, "Puppet took fatal damage.");
                    });
                }
            }
        } else if (isVictim && plugin.getConfig().getBoolean("prevent-victim-damage", true)) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        if (sessions.isController(uuid)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    try { player.spigot().respawn(); } catch (Exception ignored) {}
                }
                sessions.endSessionByController(uuid, "Controller died.");
            });
            return;
        }
        if (sessions.isVictim(uuid)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            PuppetSession session = sessions.getByVictim(uuid);
            if (session != null) {
                final UUID controllerUUID = session.getControllerUUID();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        try { player.spigot().respawn(); } catch (Exception ignored) {}
                    }
                    sessions.endSessionByController(controllerUUID, "Victim died.");
                });
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (sessions.isController(uuid)) {
            sessions.endSessionByController(uuid, "Controller respawned.");
            return;
        }
        if (sessions.isVictim(uuid)) {
            PuppetSession session = sessions.getByVictim(uuid);
            if (session != null) sessions.endSessionByController(session.getControllerUUID(), "Victim respawned.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (sessions.isVictim(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (sessions.isVictim(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (sessions.isVictim(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (sessions.isVictim(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!sessions.isController(uuid)) return;
        PuppetSession session = sessions.getByController(uuid);
        if (session == null || !session.isActive()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!session.isActive()) return;
            Player controller = Bukkit.getPlayer(session.getControllerUUID());
            Player victim     = Bukkit.getPlayer(session.getVictimUUID());
            if (controller == null || victim == null) return;
            if (!controller.isOnline() || !victim.isOnline()) return;
            NMSUtils.teleportVictimToWorld(plugin, session, victim, controller);
        }, 10L);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGamemodeChange(PlayerGameModeChangeEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        PuppetSession session = sessions.getByController(player.getUniqueId());
        if (session != null) {
            Player victim = Bukkit.getPlayer(session.getVictimUUID());
            if (victim != null && victim.isOnline()) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> victim.chat(event.getMessage()));
            }
            return;
        }
        if (sessions.isVictim(player.getUniqueId())) {
            if (plugin.getConfig().getBoolean("block-victim-chat", false)) {
                event.setCancelled(true);
                player.sendMessage("§cYou cannot chat while being puppeted.");
            }
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAnimation(PlayerAnimationEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (sessions.isController(uuid)) {
            PuppetSession session = sessions.getByController(uuid);
            if (session != null) {
                Player victim = Bukkit.getPlayer(session.getVictimUUID());
                if (victim != null && victim.isOnline()) {
                    NMSUtils.broadcastArmSwing(victim, false);
                }
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && sessions.isVictim(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player && sessions.isVictim(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (sessions.isVictim(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (sessions.isVictim(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage("§cYou cannot execute commands while being puppeted.");
        }
    }
}
