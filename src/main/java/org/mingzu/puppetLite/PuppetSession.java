package org.mingzu.puppetLite;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Team;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
public class PuppetSession {
    private final UUID controllerUUID;
    private final UUID victimUUID;
    private final Location controllerOriginalLocation;
    private final ItemStack[] controllerSavedInventory;
    private final ItemStack[] controllerSavedArmor;
    private final ItemStack controllerSavedOffhand;
    private final double controllerSavedHealth;
    private final double controllerSavedMaxHealth;
    private final int controllerSavedFood;
    private final float controllerSavedSaturation;
    private final float controllerSavedExhaustion;
    private final int controllerSavedExpLevel;
    private final float controllerSavedExp;
    private final Collection<PotionEffect> controllerSavedEffects;
    private final GameMode controllerSavedGameMode;
    private final int controllerSavedFireTicks;
    private final int controllerSavedAir;
    private final int controllerSavedHeldItemSlot;
    private final float controllerSavedFallDistance;
    private final String controllerSavedTeamName;
    private boolean active;
    private boolean teleportingVictim;
    private long startTime;
    private int tickCounter;
    private double lastSyncX, lastSyncY, lastSyncZ;
    private float lastSyncYaw, lastSyncPitch;
    public PuppetSession(Player controller, Player victim) {
        this.controllerUUID = controller.getUniqueId();
        this.victimUUID     = victim.getUniqueId();
        this.controllerOriginalLocation = controller.getLocation().clone();
        this.controllerSavedInventory   = cloneArray(controller.getInventory().getContents());
        this.controllerSavedArmor       = cloneArray(controller.getInventory().getArmorContents());
        this.controllerSavedOffhand     = controller.getInventory().getItemInOffHand().clone();
        this.controllerSavedHealth      = controller.getHealth();
        this.controllerSavedMaxHealth   = controller.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        this.controllerSavedFood        = controller.getFoodLevel();
        this.controllerSavedSaturation  = controller.getSaturation();
        this.controllerSavedExhaustion  = controller.getExhaustion();
        this.controllerSavedExpLevel    = controller.getLevel();
        this.controllerSavedExp         = controller.getExp();
        this.controllerSavedEffects     = new ArrayList<>(controller.getActivePotionEffects());
        this.controllerSavedGameMode    = controller.getGameMode();
        this.controllerSavedFireTicks   = controller.getFireTicks();
        this.controllerSavedAir         = controller.getRemainingAir();
        this.controllerSavedHeldItemSlot = controller.getInventory().getHeldItemSlot();
        this.controllerSavedFallDistance = controller.getFallDistance();
        Team t = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard().getPlayerTeam(controller);
        this.controllerSavedTeamName = (t != null) ? t.getName() : null;
        this.active           = true;
        this.teleportingVictim = false;
        this.startTime        = System.currentTimeMillis();
        this.tickCounter      = 0;
        Location loc    = victim.getLocation();
        this.lastSyncX  = loc.getX();
        this.lastSyncY  = loc.getY();
        this.lastSyncZ  = loc.getZ();
        this.lastSyncYaw   = loc.getYaw();
        this.lastSyncPitch = loc.getPitch();
    }
    private static ItemStack[] cloneArray(ItemStack[] src) {
        ItemStack[] copy = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) copy[i] = (src[i] != null) ? src[i].clone() : null;
        return copy;
    }
    public UUID getControllerUUID()              { return controllerUUID; }
    public UUID getVictimUUID()                  { return victimUUID; }
    public Location getControllerOriginalLocation() { return controllerOriginalLocation.clone(); }
    public ItemStack[] getControllerSavedInventory() { return controllerSavedInventory; }
    public ItemStack[] getControllerSavedArmor()     { return controllerSavedArmor; }
    public ItemStack getControllerSavedOffhand()     { return controllerSavedOffhand; }
    public double getControllerSavedHealth()         { return controllerSavedHealth; }
    public double getControllerSavedMaxHealth()      { return controllerSavedMaxHealth; }
    public int getControllerSavedFood()              { return controllerSavedFood; }
    public float getControllerSavedSaturation()      { return controllerSavedSaturation; }
    public float getControllerSavedExhaustion()      { return controllerSavedExhaustion; }
    public int getControllerSavedExpLevel()          { return controllerSavedExpLevel; }
    public float getControllerSavedExp()             { return controllerSavedExp; }
    public Collection<PotionEffect> getControllerSavedEffects() { return controllerSavedEffects; }
    public GameMode getControllerSavedGameMode()     { return controllerSavedGameMode; }
    public int getControllerSavedFireTicks()         { return controllerSavedFireTicks; }
    public int getControllerSavedAir()               { return controllerSavedAir; }
    public int getControllerSavedHeldItemSlot()      { return controllerSavedHeldItemSlot; }
    public float getControllerSavedFallDistance()    { return controllerSavedFallDistance; }
    public String getControllerSavedTeamName()       { return controllerSavedTeamName; }
    public boolean isActive()                        { return active; }
    public void setActive(boolean active)            { this.active = active; }
    public boolean isTeleportingVictim()             { return teleportingVictim; }
    public void setTeleportingVictim(boolean v)      { this.teleportingVictim = v; }
    public long getStartTime()                       { return startTime; }
    public int getAndIncrementTick()                 { return tickCounter++; }
    public double getLastSyncX()  { return lastSyncX; }
    public double getLastSyncY()  { return lastSyncY; }
    public double getLastSyncZ()  { return lastSyncZ; }
    public float getLastSyncYaw()   { return lastSyncYaw; }
    public float getLastSyncPitch() { return lastSyncPitch; }
    public void setLastSync(double x, double y, double z, float yaw, float pitch) {
        this.lastSyncX = x; this.lastSyncY = y; this.lastSyncZ = z;
        this.lastSyncYaw = yaw; this.lastSyncPitch = pitch;
    }
}
