package org.mingzu.puppetLite;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public class PuppetCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = "§8[§b§lPUPPET§8] §7";
    private static final String ERR_PREFIX = "§8[§c§lPUPPET§8] §c";
    private final Puppet plugin;
    private final SessionManager sessions;
    public PuppetCommand(Puppet plugin, SessionManager sessions) {
        this.plugin   = plugin;
        this.sessions = sessions;
    }
    private void sendError(CommandSender sender, String message) {
        sender.sendMessage(ERR_PREFIX + message);
        if (sender instanceof Player player) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (plugin.isDependencyDisabled()) {
            plugin.sendDependencyWarning(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sendError(sender, "Only players can use puppet commands.");
            return true;
        }
        if (!player.hasPermission("puppet.use")) {
            sendError(player, "You do not have permission to use Puppet.");
            return true;
        }
        if (args.length == 0) {
            plugin.getSelectorGUI().open(player, 0);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "help"                   -> sendHelp(player);
            case "stop", "end", "release" -> handleStop(player);
            case "status"                 -> handleStatus(player);
            case "list"                   -> handleList(player);
            case "force"                  -> handleForce(player, args);
            case "fix"                    -> handleFix(player, args);
            case "config", "settings"     -> handleConfig(player);
            case "inspect"                -> handleInspect(player, args);
            case "accept"                 -> sessions.acceptConsent(player);
            case "deny"                   -> sessions.denyConsent(player);
            case "reload"                 -> handleReload(player);
            default                       -> handleStart(player, args[0]);
        }
        return true;
    }
    private void handleConfig(Player player) {
        if (!player.hasPermission("puppet.admin")) {
            sendError(player, "You do not have permission.");
            return;
        }
        plugin.getConfigGUI().open(player);
    }
    private void handleFix(Player player, String[] args) {
        if (!player.hasPermission("puppet.admin")) {
            sendError(player, "You do not have permission.");
            return;
        }
        Player target = args.length >= 2 ? Bukkit.getPlayer(args[1]) : player;
        if (target == null) {
            sendError(player, "Player not found.");
            return;
        }
        if (sessions.isController(target.getUniqueId())) sessions.endSessionByController(target.getUniqueId(), "Force-fixed");
        if (sessions.isVictim(target.getUniqueId())) sessions.endSessionByVictim(target.getUniqueId(), "Force-fixed");
        NMSUtils.forceRefreshVisibility(plugin, target);
        player.sendMessage(PREFIX + "Visibility and state refreshed for §b" + target.getName() + "§7.");
    }
    private void handleReload(Player player) {
        if (!player.hasPermission("puppet.admin")) {
            sendError(player, "You do not have permission.");
            return;
        }
        plugin.reloadConfig();
        player.sendMessage(PREFIX + "Configuration reloaded successfully.");
    }
    private void handleStart(Player controller, String targetName) {
        Player victim = Bukkit.getPlayer(targetName);
        if (victim == null) {
            sendError(controller, "Player not found: §e" + targetName);
            return;
        }
        sessions.startSession(controller, victim);
    }
    private void handleStop(Player controller) {
        if (!sessions.isController(controller.getUniqueId())) {
            sendError(controller, "You do not have an active puppet session.");
            return;
        }
        sessions.endSessionByController(controller.getUniqueId(), "");
    }
    private void handleStatus(Player player) {
        UUID uuid = player.getUniqueId();
        if (sessions.isController(uuid)) {
            PuppetSession session = sessions.getByController(uuid);
            Player victim = Bukkit.getPlayer(session.getVictimUUID());
            long elapsed = (System.currentTimeMillis() - session.getStartTime()) / 1000;
            player.sendMessage(PREFIX + "Controlling: §b" + (victim != null ? victim.getName() : "Unknown") + " §8(§7" + formatDuration(elapsed) + "§8)");
            return;
        }
        if (sessions.isVictim(uuid)) {
            PuppetSession session = sessions.getByVictim(uuid);
            Player ctrl = Bukkit.getPlayer(session.getControllerUUID());
            long elapsed = (System.currentTimeMillis() - session.getStartTime()) / 1000;
            player.sendMessage(PREFIX + "Controlled by: §c" + (ctrl != null ? ctrl.getName() : "Unknown") + " §8(§7" + formatDuration(elapsed) + "§8)");
            return;
        }
        player.sendMessage(PREFIX + "No active puppet session.");
    }
    private void handleList(Player player) {
        if (!player.hasPermission("puppet.admin")) return;
        var allSessions = sessions.getAllSessions();
        if (allSessions.isEmpty()) {
            player.sendMessage(PREFIX + "No active puppet sessions.");
            return;
        }
        player.sendMessage(PREFIX + "Active sessions §8(§b" + allSessions.size() + "§8)§7:");
        for (PuppetSession session : allSessions) {
            Player ctrl = Bukkit.getPlayer(session.getControllerUUID());
            Player vict = Bukkit.getPlayer(session.getVictimUUID());
            String ctrlName = ctrl != null ? ctrl.getName() : "Unknown";
            String victName = vict != null ? vict.getName() : "Unknown";
            player.sendMessage("  §b▸ §b" + ctrlName + " §8→ §3" + victName);
        }
    }
    private void handleForce(Player player, String[] args) {
        if (!player.hasPermission("puppet.admin")) return;
        if (args.length < 2) { sendError(player, "Usage: /puppet force <player>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sendError(player, "Player not found."); return; }
        if (sessions.isController(target.getUniqueId())) {
            sessions.endSessionByController(target.getUniqueId(), "Force-ended");
            player.sendMessage(PREFIX + "Session force-ended for §b" + target.getName() + "§7.");
        } else if (sessions.isVictim(target.getUniqueId())) {
            sessions.endSessionByVictim(target.getUniqueId(), "Force-ended");
            player.sendMessage(PREFIX + "Session force-ended for §b" + target.getName() + "§7.");
        } else {
            sendError(player, "§e" + target.getName() + " §cis not in an active session.");
        }
    }
    private void handleInspect(Player player, String[] args) {
        if (!player.hasPermission("puppet.admin")) return;
        if (args.length < 2) { sendError(player, "Usage: /puppet inspect <player>"); return; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sendError(player, "Player not found."); return; }
        UUID uuid = target.getUniqueId();
        if (sessions.isController(uuid)) {
            PuppetSession session = sessions.getByController(uuid);
            Player victim = Bukkit.getPlayer(session.getVictimUUID());
            player.sendMessage(PREFIX + "§b" + target.getName() + " §7is currently controlling §b" + (victim != null ? victim.getName() : "Unknown"));
        } else if (sessions.isVictim(uuid)) {
            PuppetSession session = sessions.getByVictim(uuid);
            Player ctrl = Bukkit.getPlayer(session.getControllerUUID());
            player.sendMessage(PREFIX + "§b" + target.getName() + " §7is currently controlled by §c" + (ctrl != null ? ctrl.getName() : "Unknown"));
        } else {
            player.sendMessage(PREFIX + "§b" + target.getName() + " §7is not in any active session.");
        }
    }
    private void sendHelp(Player player) {
        player.sendMessage("§8§m--------------------------------------------------");
        player.sendMessage("  §b§lPUPPET §8┊ §7Control Directory §8(v1.0)");
        player.sendMessage("§8§m--------------------------------------------------");
        player.sendMessage("  §b▸ §f/puppet §8- §7Open player selector GUI");
        player.sendMessage("  §b▸ §f/puppet §3<player> §8- §7Control a target player");
        player.sendMessage("  §b▸ §f/puppet §3stop §8- §7Release current session");
        player.sendMessage("  §b▸ §f/puppet §3status §8- §7Check active session info");
        player.sendMessage("  §b▸ §f/puppet §3accept §8/ §3deny §8- §7Respond to control request");
        if (player.hasPermission("puppet.admin")) {
            player.sendMessage("");
            player.sendMessage("  §c§lADMINISTRATION");
            player.sendMessage("  §c▸ §f/puppet §esettings §8- §7Open plugin configuration GUI");
            player.sendMessage("  §c▸ §f/puppet §elist §8- §7List all active puppet sessions");
            player.sendMessage("  §c▸ §f/puppet §eforce §7<player> §8- §7Force-terminate a session");
            player.sendMessage("  §c▸ §f/puppet §einspect §7<player> §8- §7Inspect target player state");
            player.sendMessage("  §c▸ §f/puppet §efix §7[player] §8- §7Fix player desync / visibility");
            player.sendMessage("  §c▸ §f/puppet §ereload §8- §7Reload plugin configuration");
        }
        player.sendMessage("§8§m--------------------------------------------------");
    }
    private String formatDuration(long seconds) {
        long m = seconds / 60, s = seconds % 60;
        return m > 0 ? m + "m " + s + "s" : s + "s";
    }
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        if (!player.hasPermission("puppet.use")) return List.of();
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("help", "stop", "status", "accept", "deny"));
            if (player.hasPermission("puppet.admin")) options.addAll(List.of("settings", "list", "force", "inspect", "reload", "fix"));
            for (Player p : Bukkit.getOnlinePlayers()) if (!p.equals(player)) options.add(p.getName());
            options.stream().filter(o -> o.toLowerCase().startsWith(args[0].toLowerCase())).forEach(completions::add);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (player.hasPermission("puppet.admin") && (sub.equals("force") || sub.equals("inspect") || sub.equals("fix"))) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }
        return completions;
    }
}
