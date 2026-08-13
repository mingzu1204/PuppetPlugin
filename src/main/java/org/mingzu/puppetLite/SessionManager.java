package org.mingzu.puppetLite;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Particle;
import java.util.*;
public class SessionManager {
    private final Puppet plugin;
    private final Map<UUID, PuppetSession> byController = new HashMap<>();
    private final Map<UUID, PuppetSession> byVictim     = new HashMap<>();
    private final Map<UUID, UUID>        pendingRequests = new HashMap<>();
    private final Map<UUID, BukkitTask>  consentTimers   = new HashMap<>();
    private final Set<UUID> animating = new HashSet<>();
    public SessionManager(Puppet plugin) {
        this.plugin = plugin;
    }
    private void sendError(Player player, String message) {
        player.sendMessage(message);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }
    public boolean startSession(Player controller, Player victim) {
        if (!validateStart(controller, victim)) return false;
        if (plugin.getConfig().getBoolean("require-victim-consent", false)) {
            requestConsent(controller, victim);
            return false; 
        }
        return startWithAnimation(controller, victim);
    }
    private boolean validateStart(Player controller, Player victim) {
        if (isController(controller.getUniqueId())) {
            sendError(controller, "§cYou already control someone. Use /puppet stop first.");
            return false;
        }
        if (isVictim(controller.getUniqueId())) {
            sendError(controller, "§cYou are currently being controlled by someone.");
            return false;
        }
        if (!controller.getWorld().equals(victim.getWorld()) || controller.getLocation().distanceSquared(victim.getLocation()) > 10000) {
            sendError(controller, "§cTarget is too far! You can only puppet players within 100 blocks.");
            return false;
        }
        if (isController(victim.getUniqueId())) {
            sendError(controller, "§cThat player is currently controlling someone else.");
            return false;
        }
        if (isVictim(victim.getUniqueId())) {
            sendError(controller, "§cThat player is already being controlled.");
            return false;
        }
        if (controller.equals(victim)) {
            sendError(controller, "§cYou cannot puppet yourself.");
            return false;
        }
        List<String> blacklist = plugin.getConfig().getStringList("blacklisted-worlds");
        if (blacklist.contains(victim.getWorld().getName())) {
            sendError(controller, "§cPuppet sessions are not allowed in that world.");
            return false;
        }
        if (blacklist.contains(controller.getWorld().getName())) {
            sendError(controller, "§cPuppet sessions are not allowed in your current world.");
            return false;
        }
        int maxSessions = plugin.getConfig().getInt("max-concurrent-sessions", 10);
        if (maxSessions > 0 && byController.size() >= maxSessions) {
            sendError(controller, "§cThe server has reached the maximum number of concurrent puppet sessions (" + maxSessions + ").");
            return false;
        }
        return true;
    }
    private boolean startWithAnimation(Player controller, Player victim) {
        UUID ctrlId = controller.getUniqueId();
        if (animating.contains(ctrlId)) return false;
        animating.add(ctrlId);
        new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!controller.isOnline() || !victim.isOnline() || controller.isDead() || victim.isDead()) {
                    animating.remove(ctrlId);
                    this.cancel();
                    return;
                }
                if (ticks >= 20) { 
                    animating.remove(ctrlId);
                    doStartSession(controller, victim);
                    this.cancel();
                    return;
                }
                controller.getWorld().spawnParticle(Particle.PORTAL, controller.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.5);
                victim.getWorld().spawnParticle(Particle.PORTAL, victim.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.5);
                controller.getWorld().spawnParticle(Particle.WITCH, controller.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1);
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
        return true;
    }
    private boolean doStartSession(Player controller, Player victim) {
        PuppetSession session = new PuppetSession(controller, victim);
        byController.put(controller.getUniqueId(), session);
        byVictim.put(victim.getUniqueId(), session);
        controller.playSound(controller.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);
        victim.playSound(victim.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 1.0f);
        NMSUtils.applySession(plugin, session, controller, victim);
        logInfo("[PuppetLite] Session started: " + controller.getName() + " → " + victim.getName());
        controller.sendMessage("§8[§b§lPUPPET§8] §aControl established over §b" + victim.getName() + "§a. Type §f/puppet stop §ato release.");
        if (plugin.getConfig().getBoolean("notify-victim-on-start", true)) {
            victim.sendMessage("§8[§c§lPUPPET§8] §cYou are now being puppeted by §e" + controller.getName() + "§c.");
        }
        return true;
    }
    private void requestConsent(Player controller, Player victim) {
        UUID victimId = victim.getUniqueId();
        if (pendingRequests.containsKey(victimId)) {
            sendError(controller, "That player already has a pending consent request.");
            return;
        }
        pendingRequests.put(victimId, controller.getUniqueId());
        int timeout = plugin.getConfig().getInt("consent-timeout", 15);
        BukkitTask timer = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.remove(victimId) != null) {
                consentTimers.remove(victimId);
                sendError(controller, "Consent request to §e" + victim.getName() + " §ctimed out.");
                if (victim.isOnline()) victim.sendMessage("§8[§e§lPUPPET§8] §ePuppet request from §f" + controller.getName() + " §eexpired.");
            }
        }, timeout * 20L);
        consentTimers.put(victimId, timer);
        victim.sendMessage("§8[§b§lPUPPET§8] §b" + controller.getName()
                + " §7wants to puppet control you! Type §f/puppet accept §7or §f/puppet deny §7within §e"
                + timeout + "s§7.");
        controller.sendMessage("§8[§b§lPUPPET§8] §7Consent request sent to §b" + victim.getName() + "§7. Awaiting response...");
    }
    public void acceptConsent(Player victim) {
        UUID victimId = victim.getUniqueId();
        UUID controllerId = pendingRequests.remove(victimId);
        if (controllerId == null) {
            sendError(victim, "You have no pending puppet request.");
            return;
        }
        cancelTimer(victimId);
        Player controller = Bukkit.getPlayer(controllerId);
        if (controller == null || !controller.isOnline()) {
            sendError(victim, "The player who requested control is no longer online.");
            return;
        }
        victim.sendMessage("§8[§b§lPUPPET§8] §aYou accepted the puppet request.");
        startWithAnimation(controller, victim);
    }
    public void denyConsent(Player victim) {
        UUID victimId = victim.getUniqueId();
        UUID controllerId = pendingRequests.remove(victimId);
        if (controllerId == null) {
            sendError(victim, "You have no pending puppet request.");
            return;
        }
        cancelTimer(victimId);
        Player controller = Bukkit.getPlayer(controllerId);
        victim.sendMessage("§8[§b§lPUPPET§8] §cYou denied the puppet request.");
        if (controller != null && controller.isOnline()) {
            sendError(controller, "§e" + victim.getName() + " §cdenied your puppet request.");
        }
    }
    public void cancelPendingConsent(UUID uuid) {
        if (pendingRequests.remove(uuid) != null) {
            cancelTimer(uuid);
        }
        pendingRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(uuid)) {
                cancelTimer(entry.getKey());
                return true;
            }
            return false;
        });
    }
    private void cancelTimer(UUID victimId) {
        BukkitTask task = consentTimers.remove(victimId);
        if (task != null) task.cancel();
    }
    public void endSessionByController(UUID controllerUUID, String reason) {
        PuppetSession session = byController.get(controllerUUID);
        if (session == null || !session.isActive()) return;
        teardown(session, reason);
    }
    public void endSessionByVictim(UUID victimUUID, String reason) {
        PuppetSession session = byVictim.get(victimUUID);
        if (session == null || !session.isActive()) return;
        teardown(session, reason);
    }
    private void teardown(PuppetSession session, String reason) {
        if (!session.isActive()) return;
        session.setActive(false);
        Player controller = Bukkit.getPlayer(session.getControllerUUID());
        Player victim     = Bukkit.getPlayer(session.getVictimUUID());
        if (controller != null && controller.isOnline()) {
            controller.playSound(controller.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        }
        if (victim != null && victim.isOnline()) {
            victim.playSound(victim.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        }
        NMSUtils.removeSession(plugin, session, controller, victim, reason);
        byController.remove(session.getControllerUUID());
        byVictim.remove(session.getVictimUUID());
        logInfo("[PuppetLite] Session ended"
                + (reason.isEmpty() ? "." : " (" + reason + ")"));
    }
    public void terminateAll() {
        for (PuppetSession session : new ArrayList<>(byController.values())) {
            if (session.isActive()) teardown(session, "Server shutting down.");
        }
        byController.clear();
        byVictim.clear();
        consentTimers.values().forEach(BukkitTask::cancel);
        consentTimers.clear();
        pendingRequests.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PacketInterceptor.eject(player);
            player.setGravity(true);
        }
    }
    public void tickAll() {
        int maxDuration = plugin.getConfig().getInt("max-session-duration", 0);
        for (PuppetSession session : new ArrayList<>(byController.values())) {
            if (!session.isActive()) continue;
            Player controller = Bukkit.getPlayer(session.getControllerUUID());
            Player victim     = Bukkit.getPlayer(session.getVictimUUID());
            if (controller == null || !controller.isOnline()) {
                teardown(session, "Controller disconnected.");
                continue;
            }
            if (victim == null || !victim.isOnline()) {
                teardown(session, "Victim disconnected.");
                continue;
            }
            if (maxDuration > 0) {
                long elapsedSeconds = (System.currentTimeMillis() - session.getStartTime()) / 1000;
                if (elapsedSeconds >= maxDuration) {
                    teardown(session, "Session time limit reached (" + maxDuration + "s).");
                    continue;
                }
            }
            NMSUtils.syncSession(session, controller, victim);
        }
    }
    public PuppetSession getByController(UUID uuid) { return byController.get(uuid); }
    public PuppetSession getByVictim(UUID uuid)     { return byVictim.get(uuid); }
    public boolean isController(UUID uuid) {
        PuppetSession s = byController.get(uuid);
        return s != null && s.isActive();
    }
    public boolean isVictim(UUID uuid) {
        PuppetSession s = byVictim.get(uuid);
        return s != null && s.isActive();
    }
    public Collection<PuppetSession> getAllSessions() {
        return Collections.unmodifiableCollection(byController.values());
    }
    public boolean hasPendingConsentAsVictim(UUID uuid) {
        return pendingRequests.containsKey(uuid);
    }
    private void logInfo(String msg) {
        if (plugin.getConfig().getBoolean("log-sessions-to-console", true)) {
            plugin.getLogger().info(msg);
        }
    }
}
