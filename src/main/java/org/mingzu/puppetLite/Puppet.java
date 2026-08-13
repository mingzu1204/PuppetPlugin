package org.mingzu.puppetLite;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
public final class Puppet extends JavaPlugin {
    private static Puppet instance;
    private SessionManager sessionManager;
    private BukkitTask syncTask;
    private ConfigGUI configGUI;
    private SelectorGUI selectorGUI;
    private boolean dependencyDisabled = false;
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        if (getServer().getPluginManager().getPlugin("ProtocolLib") == null || !getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            dependencyDisabled = true;
        }
        sessionManager = new SessionManager(this);
        configGUI = new ConfigGUI(this);
        selectorGUI = new SelectorGUI(this);
        PuppetCommand cmd = new PuppetCommand(this, sessionManager);
        getCommand("puppet").setExecutor(cmd);
        getCommand("puppet").setTabCompleter(cmd);
        getServer().getPluginManager().registerEvents(new PuppetListener(this, sessionManager), this);
        if (!dependencyDisabled) {
            syncTask = getServer().getScheduler().runTaskTimer(this, () -> sessionManager.tickAll(), 0L, 1L);
        }
        printStartupBanner();
        if (dependencyDisabled) {
            notifyOnlineOps();
        }
    }
    @Override
    public void onDisable() {
        if (syncTask != null) syncTask.cancel();
        if (sessionManager != null) sessionManager.terminateAll();
        instance = null;
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&cPuppet&8] &cPlugin has been disabled."));
    }
    private void printStartupBanner() {
        String status = dependencyDisabled
                ? "&c&lDISABLED &f(&cMissing dependency: ProtocolLib!&f)"
                : "&a&lENABLED &f(&aSuccessfully loaded!&f)";
        String[] banner = {
                "&8&m==============================================",
                "&b&l   PUPPET",
                "",
                "&f   &lVersion: &e1.0",
                "&f   &lAuthor: &aMingZu",
                "&f   &lStatus: " + status,
                "&8&m=============================================="
        };
        for (String line : banner) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
        if (dependencyDisabled) {
            Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&cPuppet&8] &c[WARNING] ProtocolLib dependency is missing! Puppet plugin features are disabled until ProtocolLib is installed and enabled."));
        }
    }
    public void notifyOnlineOps() {
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp()) {
                sendDependencyWarning(player);
            }
        }
    }
    public void sendDependencyWarning(org.bukkit.command.CommandSender sender) {
        if (sender instanceof org.bukkit.entity.Player player && player.isOp()) {
            net.kyori.adventure.text.Component message = net.kyori.adventure.text.Component.text("[Puppet] ", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY)
                .append(net.kyori.adventure.text.Component.text("[WARNING] ", net.kyori.adventure.text.format.NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(net.kyori.adventure.text.Component.text("Puppet plugin is currently unusable because required dependency ", net.kyori.adventure.text.format.NamedTextColor.RED))
                .append(net.kyori.adventure.text.Component.text("ProtocolLib", net.kyori.adventure.text.format.NamedTextColor.YELLOW, net.kyori.adventure.text.format.TextDecoration.BOLD))
                .append(net.kyori.adventure.text.Component.text(" is missing! ", net.kyori.adventure.text.format.NamedTextColor.RED))
                .append(net.kyori.adventure.text.Component.text("[Click here to download ProtocolLib]", net.kyori.adventure.text.format.NamedTextColor.GREEN, net.kyori.adventure.text.format.TextDecoration.UNDERLINED)
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://github.com/dmulloy2/ProtocolLib/releases/tag/dev-build"))
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to open ProtocolLib dev-build download page", net.kyori.adventure.text.format.NamedTextColor.GRAY))));
            player.sendMessage(message);
        } else {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    "&8[&cPuppet&8] &c[WARNING] Puppet plugin is currently unusable because required dependency &eProtocolLib &cis missing! Download link: &ahttps://github.com/dmulloy2/ProtocolLib/releases/tag/dev-build"));
        }
    }
    public boolean isDependencyDisabled() {
        return dependencyDisabled;
    }
    public static Puppet getInstance() { return instance; }
    public SessionManager getSessionManager() { return sessionManager; }
    public ConfigGUI getConfigGUI() { return configGUI; }
    public SelectorGUI getSelectorGUI() { return selectorGUI; }
}
