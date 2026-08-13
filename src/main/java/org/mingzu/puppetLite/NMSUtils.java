package org.mingzu.puppetLite;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.*;
import com.comphenix.protocol.wrappers.EnumWrappers.ItemSlot;
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.*;
public final class NMSUtils {
    private static final ProtocolManager PROTOCOL_MANAGER = ProtocolLibrary.getProtocolManager();
    public static final String PREFIX = "§8[§bPUPPET§8] §3";
    private NMSUtils() {}
    public static void applySession(Puppet plugin, PuppetSession session, Player controller, Player victim) {
        controller.hidePlayer(plugin, victim);
        copyVictimStateToController(controller, victim);
        controller.teleport(victim.getLocation());
        hideVictimFromAll(plugin, victim);
        disguiseControllerAsVictim(controller, victim);
        applySelfSkin(controller, victim);
        applyTeamDisguise(controller, victim);
        victim.setGravity(false);
        PacketInterceptor.inject(plugin, victim);
        victim.hidePlayer(plugin, controller);
        try {
            PacketContainer destroyController = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            destroyController.getIntLists().write(0, Collections.singletonList(controller.getEntityId()));
            PROTOCOL_MANAGER.sendServerPacket(victim, destroyController);
        } catch (Exception ignored) {}
    }
    public static void removeSession(Puppet plugin, PuppetSession session, Player controller, Player victim, String reason) {
        try {
            if (victim != null && victim.isOnline()) {
                PacketInterceptor.eject(victim);
                victim.setGravity(true);
                if (controller != null && controller.isOnline()) {
                    victim.showPlayer(plugin, controller);
                }
            }
            if (controller != null && controller.isOnline() && victim != null && victim.isOnline()) {
                applyControllerStateToVictim(controller, victim);
                victim.teleport(controller.getLocation(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
            }
            if (controller != null && controller.isOnline()) {
                restoreControllerState(session, controller);
                undisguiseController(plugin, controller, victim);
                restoreSelfSkin(controller);
                if (victim != null && victim.isOnline()) restoreTeam(session, controller, victim);
                if (victim != null && victim.isOnline()) controller.showPlayer(plugin, victim);
                String controllerMsg = PREFIX + "Session ended.";
                if (reason != null && !reason.isEmpty()) {
                    controllerMsg += " §8(§7" + reason + "§8)";
                }
                controller.sendMessage(controllerMsg);
            }
            if (victim != null && victim.isOnline()) {
                showVictimToAll(plugin, victim, controller);
                String victimMsg = PREFIX + "Control released.";
                if (reason != null && !reason.isEmpty()) {
                    victimMsg += " §8(§7" + reason + "§8)";
                }
                victim.sendMessage(victimMsg);
            }
        } catch (Throwable t) {
            plugin.getLogger().severe("Error during removeSession: " + t.getMessage());
            t.printStackTrace();
        }
    }
    public static void syncSession(PuppetSession session, Player controller, Player victim) {
        int tick = session.getAndIncrementTick();
        if (tick % 40 == 0) {
            victim.playSound(victim.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.0f);
        }
        if (controller.getWorld().equals(victim.getWorld())) {
            session.setTeleportingVictim(true);
            syncServerPosition(victim, controller.getLocation());
            session.setTeleportingVictim(false);
        }
        victim.setFireTicks(controller.getFireTicks());
        victim.setRemainingAir(controller.getRemainingAir());
        if (victim.getGameMode() != controller.getGameMode()) {
            victim.setGameMode(controller.getGameMode());
            updateGameModeForVictim(victim, controller.getGameMode());
        }
        if (victim.isSneaking() != controller.isSneaking())   victim.setSneaking(controller.isSneaking());
        if (victim.isSprinting() != controller.isSprinting()) victim.setSprinting(controller.isSprinting());
        syncPosesAndMetadata(controller, victim);
        if (Math.abs(controller.getHealth() - victim.getHealth()) > 0.05) {
            double max = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
            victim.setHealth(Math.max(0.0, Math.min(controller.getHealth(), max)));
        }
        if (controller.getFoodLevel() != victim.getFoodLevel()) victim.setFoodLevel(controller.getFoodLevel());
        if (Math.abs(controller.getSaturation() - victim.getSaturation()) > 0.05f) victim.setSaturation(controller.getSaturation());
        if (controller.getInventory().getHeldItemSlot() != victim.getInventory().getHeldItemSlot()) {
            int targetSlot = controller.getInventory().getHeldItemSlot();
            victim.getInventory().setHeldItemSlot(targetSlot);
            PacketContainer heldSlotPacket = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.HELD_ITEM_SLOT);
            heldSlotPacket.getIntegers().write(0, targetSlot);
            try { PROTOCOL_MANAGER.sendServerPacket(victim, heldSlotPacket); } catch (Exception ignored) {}
        }
        Puppet pl = Puppet.getInstance();
        int effectInterval    = pl.getConfig().getInt("effect-sync-interval", 20);
        int inventoryInterval = pl.getConfig().getInt("inventory-sync-interval", 10);
        if (tick % effectInterval    == 0) syncEffects(controller, victim);
        if (tick % inventoryInterval == 0) syncInventoryToVictim(controller, victim);
    }
    private static void applySelfSkin(Player controller, Player victim) {
        if (controller == null || !controller.isOnline() || victim == null) return;
        try {
            WrappedGameProfile victimProfile = WrappedGameProfile.fromPlayer(victim);
            WrappedGameProfile fakeProfile = new WrappedGameProfile(controller.getUniqueId(), controller.getName());
            copyGameProfileProperties(victimProfile, fakeProfile);
            List<PlayerInfoData> data = Collections.singletonList(new PlayerInfoData(
                    fakeProfile, controller.getPing(), NativeGameMode.fromBukkit(controller.getGameMode()), WrappedChatComponent.fromText(controller.getName())
            ));
            PacketContainer add = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.PLAYER_INFO);
            add.getPlayerInfoActions().write(0, EnumSet.of(PlayerInfoAction.ADD_PLAYER));
            add.getPlayerInfoDataLists().write(0, data);
            PROTOCOL_MANAGER.sendServerPacket(controller, add);
        } catch (Exception e) {
            Puppet.getInstance().getLogger().warning("Error in applySelfSkin: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private static void restoreSelfSkin(Player controller) {
        if (controller == null || !controller.isOnline()) return;
        try {
            WrappedGameProfile realProfile = safeProfile(controller);
            List<PlayerInfoData> data = Collections.singletonList(new PlayerInfoData(
                    realProfile, controller.getPing(), NativeGameMode.fromBukkit(controller.getGameMode()), WrappedChatComponent.fromText(controller.getName())
            ));
            PacketContainer add = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.PLAYER_INFO);
            add.getPlayerInfoActions().write(0, EnumSet.of(PlayerInfoAction.ADD_PLAYER));
            add.getPlayerInfoDataLists().write(0, data);
            PROTOCOL_MANAGER.sendServerPacket(controller, add);
        } catch (Exception e) {
            Puppet.getInstance().getLogger().warning("Error in restoreSelfSkin: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public static void teleportVictimToWorld(Puppet plugin, PuppetSession session, Player victim, Player controller) {
        session.setTeleportingVictim(true);
        victim.teleport(controller.getLocation());
        victim.setGravity(false);
        session.setTeleportingVictim(false);
        PacketInterceptor.inject(plugin, victim);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!session.isActive() || !victim.isOnline() || !controller.isOnline()) return;
            victim.hidePlayer(plugin, controller);
        }, 5L);
    }
    private static void syncServerPosition(Player victim, Location target) {
        victim.setVelocity(new Vector(0, 0, 0));
        ServerPlayer nmsVictim = ((CraftPlayer) victim).getHandle();
        nmsVictim.connection.teleport(target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch(), org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
    private static void updateGameModeForVictim(Player victim, @NotNull GameMode newMode) {
        PacketContainer packet = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.PLAYER_INFO);
        packet.getPlayerInfoActions().write(0, EnumSet.of(PlayerInfoAction.UPDATE_GAME_MODE));
        packet.getPlayerInfoDataLists().write(0, Collections.singletonList(
                new PlayerInfoData(
                        safeProfile(victim),
                        victim.getPing(),
                        NativeGameMode.fromBukkit(newMode),
                        WrappedChatComponent.fromText(victim.getPlayerListName() != null ? victim.getPlayerListName() : victim.getName())
                )
        ));
        try { PROTOCOL_MANAGER.sendServerPacket(victim, packet); } catch (Exception ignored) {}
    }
    private static void copyVictimStateToController(Player controller, Player victim) {
        for (PotionEffect e : new ArrayList<>(controller.getActivePotionEffects())) controller.removePotionEffect(e.getType());
        controller.getInventory().setContents(cloneItems(victim.getInventory().getContents()));
        controller.getInventory().setArmorContents(cloneItems(victim.getInventory().getArmorContents()));
        controller.getInventory().setItemInOffHand(victim.getInventory().getItemInOffHand().clone());
        controller.getInventory().setHeldItemSlot(victim.getInventory().getHeldItemSlot());
        double victimMaxHp = victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        controller.getAttribute(Attribute.MAX_HEALTH).setBaseValue(victimMaxHp);
        controller.setHealth(Math.min(victim.getHealth(), victimMaxHp));
        controller.setFoodLevel(victim.getFoodLevel());
        controller.setSaturation(victim.getSaturation());
        controller.setExhaustion(victim.getExhaustion());
        controller.setLevel(victim.getLevel());
        controller.setExp(victim.getExp());
        for (PotionEffect effect : victim.getActivePotionEffects()) {
            controller.addPotionEffect(new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
        controller.setFireTicks(victim.getFireTicks());
        controller.setRemainingAir(victim.getRemainingAir());
        controller.setFallDistance(0f);
        controller.setGameMode(victim.getGameMode());
        controller.updateInventory();
    }
    static void applyControllerStateToVictim(Player controller, Player victim) {
        for (PotionEffect e : new ArrayList<>(victim.getActivePotionEffects())) victim.removePotionEffect(e.getType());
        victim.getInventory().setContents(cloneItems(controller.getInventory().getContents()));
        victim.getInventory().setArmorContents(cloneItems(controller.getInventory().getArmorContents()));
        victim.getInventory().setItemInOffHand(controller.getInventory().getItemInOffHand().clone());
        victim.getInventory().setHeldItemSlot(controller.getInventory().getHeldItemSlot());
        double ctrlMaxHp = controller.getAttribute(Attribute.MAX_HEALTH).getValue();
        victim.getAttribute(Attribute.MAX_HEALTH).setBaseValue(ctrlMaxHp);
        victim.setHealth(Math.max(1.0, Math.min(controller.getHealth(), ctrlMaxHp)));
        victim.setFoodLevel(controller.getFoodLevel());
        victim.setSaturation(controller.getSaturation());
        victim.setExhaustion(controller.getExhaustion());
        victim.setLevel(controller.getLevel());
        victim.setExp(controller.getExp());
        for (PotionEffect effect : controller.getActivePotionEffects()) {
            victim.addPotionEffect(new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
        victim.setFireTicks(controller.getFireTicks());
        victim.setRemainingAir(controller.getRemainingAir());
        victim.setFallDistance(controller.getFallDistance());
        victim.setGameMode(controller.getGameMode());
        victim.updateInventory();
    }
    private static void restoreControllerState(PuppetSession session, Player controller) {
        for (PotionEffect e : new ArrayList<>(controller.getActivePotionEffects())) controller.removePotionEffect(e.getType());
        controller.getInventory().setContents(cloneItems(session.getControllerSavedInventory()));
        controller.getInventory().setArmorContents(cloneItems(session.getControllerSavedArmor()));
        controller.getInventory().setItemInOffHand(session.getControllerSavedOffhand().clone());
        controller.getInventory().setHeldItemSlot(session.getControllerSavedHeldItemSlot());
        controller.getAttribute(Attribute.MAX_HEALTH).setBaseValue(session.getControllerSavedMaxHealth());
        controller.setHealth(Math.min(session.getControllerSavedHealth(), session.getControllerSavedMaxHealth()));
        controller.setFoodLevel(session.getControllerSavedFood());
        controller.setSaturation(session.getControllerSavedSaturation());
        controller.setExhaustion(session.getControllerSavedExhaustion());
        controller.setLevel(session.getControllerSavedExpLevel());
        controller.setExp(session.getControllerSavedExp());
        for (PotionEffect effect : session.getControllerSavedEffects()) {
            controller.addPotionEffect(new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        }
        controller.setFireTicks(session.getControllerSavedFireTicks());
        controller.setRemainingAir(session.getControllerSavedAir());
        controller.setFallDistance(session.getControllerSavedFallDistance());
        controller.setGameMode(session.getControllerSavedGameMode());
        controller.teleport(session.getControllerOriginalLocation());
        controller.updateInventory();
    }
    private static void syncInventoryToVictim(Player controller, Player victim) {
        victim.getInventory().setContents(cloneItems(controller.getInventory().getContents()));
        victim.getInventory().setArmorContents(cloneItems(controller.getInventory().getArmorContents()));
        victim.getInventory().setItemInOffHand(controller.getInventory().getItemInOffHand().clone());
    }
    private static void syncEffects(Player controller, Player victim) {
        Set<PotionEffectType> srcTypes = new HashSet<>();
        for (PotionEffect e : controller.getActivePotionEffects()) srcTypes.add(e.getType());
        for (PotionEffect e : new ArrayList<>(victim.getActivePotionEffects())) {
            if (!srcTypes.contains(e.getType())) victim.removePotionEffect(e.getType());
        }
        for (PotionEffect src : controller.getActivePotionEffects()) {
            PotionEffect dst = victim.getPotionEffect(src.getType());
            if (dst == null || dst.getAmplifier() != src.getAmplifier() || Math.abs(dst.getDuration() - src.getDuration()) > 40) {
                victim.addPotionEffect(new PotionEffect(src.getType(), src.getDuration(), src.getAmplifier(), src.isAmbient(), src.hasParticles(), src.hasIcon()), true);
            }
        }
    }
    public static void hideVictimFromAll(Puppet plugin, Player victim) {
        if (victim == null || !victim.isOnline()) return;
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(victim)) continue;
            try {
                observer.hidePlayer(plugin, victim);
            } catch (Exception e) {
                plugin.getLogger().warning("Error in hideVictimFromAll: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    public static void showVictimToAll(Puppet plugin, Player victim, Player except) {
        if (victim == null || !victim.isOnline()) return;
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(victim) || observer.equals(except)) continue;
            try {
                observer.hidePlayer(plugin, victim);
                observer.showPlayer(plugin, victim);
            } catch (Exception e) {
                plugin.getLogger().warning("Error in showVictimToAll: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    public static void disguiseControllerAsVictim(Player controller, Player victim) {
        if (controller == null || !controller.isOnline() || victim == null) return;
        try {
            WrappedGameProfile victimProfile = WrappedGameProfile.fromPlayer(victim);
            WrappedGameProfile fakeProfile   = new WrappedGameProfile(controller.getUniqueId(), victim.getName());
            copyGameProfileProperties(victimProfile, fakeProfile);
            List<PlayerInfoData> fakeInfoData = buildFakePlayerInfoData(controller, fakeProfile);
            int  controllerId   = controller.getEntityId();
            UUID controllerUuid = controller.getUniqueId();
            PacketContainer metaPacket = createEntityMetadata(controllerId, getSkinFlags(victim));
            for (Player observer : Bukkit.getOnlinePlayers()) {
                if (observer.equals(controller) || observer.equals(victim)) continue;
                try {
                    PacketContainer removeInfo = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                    removeInfo.getUUIDLists().write(0, Collections.singletonList(controllerUuid));
                    PacketContainer destroyEntity = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                    destroyEntity.getIntLists().write(0, Collections.singletonList(controllerId));
                    PROTOCOL_MANAGER.sendServerPacket(observer, removeInfo);
                    PROTOCOL_MANAGER.sendServerPacket(observer, destroyEntity);
                } catch (Exception e) {
                    Puppet.getInstance().getLogger().warning("Error hiding controller for disguise: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            Bukkit.getScheduler().runTaskLater(Puppet.getInstance(), () -> {
                for (Player observer : Bukkit.getOnlinePlayers()) {
                    if (observer.equals(controller) || observer.equals(victim)) continue;
                    try {
                        PacketContainer add = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.PLAYER_INFO);
                        add.getPlayerInfoActions().write(0, EnumSet.of(PlayerInfoAction.ADD_PLAYER));
                        add.getPlayerInfoDataLists().write(0, fakeInfoData);
                        PacketContainer spawn = createNamedEntitySpawn(controller);
                        PacketContainer equip = createEntityEquipment(controller);
                        PROTOCOL_MANAGER.sendServerPacket(observer, add);
                        PROTOCOL_MANAGER.sendServerPacket(observer, spawn);
                        PROTOCOL_MANAGER.sendServerPacket(observer, equip);
                        PROTOCOL_MANAGER.sendServerPacket(observer, metaPacket);
                    } catch (Exception e) {
                        Puppet.getInstance().getLogger().warning("Error showing controller for disguise: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }, 1L);
        } catch (Exception e) {
            Puppet.getInstance().getLogger().warning("Error in disguiseControllerAsVictim: " + e.getMessage());
            e.printStackTrace();
        }
    }
    public static void undisguiseController(Puppet plugin, Player controller, Player victim) {
        if (controller == null || !controller.isOnline()) return;
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(controller)) continue;
            try {
                observer.hidePlayer(plugin, controller);
                observer.showPlayer(plugin, controller);
            } catch (Exception e) {
                plugin.getLogger().warning("Error in undisguiseController: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    private static List<PlayerInfoData> buildPlayerInfoData(Player player) {
        return Collections.singletonList(new PlayerInfoData(
                safeProfile(player),
                player.getPing(),
                NativeGameMode.fromBukkit(player.getGameMode()),
                WrappedChatComponent.fromText(player.getPlayerListName() != null ? player.getPlayerListName() : player.getName())
        ));
    }
    private static List<PlayerInfoData> buildFakePlayerInfoData(Player controller, WrappedGameProfile fakeProfile) {
        return Collections.singletonList(new PlayerInfoData(
                fakeProfile,
                controller.getPing(),
                NativeGameMode.fromBukkit(controller.getGameMode()),
                WrappedChatComponent.fromText(controller.getPlayerListName() != null ? controller.getPlayerListName() : controller.getName())
        ));
    }
    private static PacketContainer createNamedEntitySpawn(Player player) {
        PacketContainer spawn = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
        Location loc = player.getLocation();
        spawn.getIntegers().write(0, player.getEntityId());
        spawn.getUUIDs().write(0, player.getUniqueId());
        spawn.getEntityTypeModifier().write(0, org.bukkit.entity.EntityType.PLAYER);
        spawn.getDoubles().write(0, loc.getX());
        spawn.getDoubles().write(1, loc.getY());
        spawn.getDoubles().write(2, loc.getZ());
        spawn.getBytes().write(0, (byte) (loc.getPitch() * 256.0F / 360.0F));
        spawn.getBytes().write(1, (byte) (loc.getYaw() * 256.0F / 360.0F));
        spawn.getBytes().write(2, (byte) (loc.getYaw() * 256.0F / 360.0F)); 
        return spawn;
    }
    private static PacketContainer createEntityEquipment(Player player) {
        PacketContainer equip = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        equip.getIntegers().write(0, player.getEntityId());
        List<com.comphenix.protocol.wrappers.Pair<ItemSlot, ItemStack>> slots = new ArrayList<>();
        slots.add(new com.comphenix.protocol.wrappers.Pair<>(ItemSlot.MAINHAND, player.getInventory().getItemInMainHand()));
        slots.add(new com.comphenix.protocol.wrappers.Pair<>(ItemSlot.OFFHAND,  player.getInventory().getItemInOffHand()));
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length >= 4) {
            slots.add(new com.comphenix.protocol.wrappers.Pair<>(ItemSlot.FEET,  armor[0]));
            slots.add(new com.comphenix.protocol.wrappers.Pair<>(ItemSlot.LEGS,  armor[1]));
            slots.add(new com.comphenix.protocol.wrappers.Pair<>(ItemSlot.CHEST, armor[2]));
            slots.add(new com.comphenix.protocol.wrappers.Pair<>(ItemSlot.HEAD,  armor[3]));
        }
        slots.removeIf(p -> p.getSecond() == null);
        equip.getSlotStackPairLists().write(0, slots);
        return equip;
    }
    private static PacketContainer createEntityMetadata(int entityId, byte skinFlags) {
        PacketContainer meta = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.ENTITY_METADATA);
        meta.getIntegers().write(0, entityId);
        List<WrappedDataValue> values = new ArrayList<>();
        values.add(new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Byte.class), skinFlags));
        meta.getDataValueCollectionModifier().write(0, values);
        return meta;
    }
    private static byte getSkinFlags(Player player) {
        try {
            ServerPlayer nms = ((CraftPlayer) player).getHandle();
            return nms.getEntityData().get(net.minecraft.world.entity.player.Player.DATA_PLAYER_MODE_CUSTOMISATION);
        } catch (Exception e) {
            return (byte) 0x7F;
        }
    }
    private static void applyTeamDisguise(Player controller, Player victim) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team victimTeam = board.getPlayerTeam(victim);
        if (victimTeam == null) return;
        Team controllerCurrentTeam = board.getPlayerTeam(controller);
        if (controllerCurrentTeam != null && !controllerCurrentTeam.equals(victimTeam))
            controllerCurrentTeam.removePlayer(controller);
        victimTeam.addPlayer(controller);
    }
    private static void restoreTeam(PuppetSession session, Player controller, Player victim) {
        if (victim == null) return;
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team victimTeam = board.getPlayerTeam(victim);
        if (victimTeam != null && victimTeam.hasPlayer(controller)) victimTeam.removePlayer(controller);
        String savedTeamName = session.getControllerSavedTeamName();
        if (savedTeamName != null) {
            Team savedTeam = board.getTeam(savedTeamName);
            if (savedTeam != null) savedTeam.addPlayer(controller);
        }
    }
    public static void broadcastArmSwing(Player victim, boolean offhand) {
        PacketContainer anim = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.ANIMATION);
        anim.getIntegers().write(0, victim.getEntityId());
        anim.getIntegers().write(1, offhand ? 3 : 0);
        PROTOCOL_MANAGER.broadcastServerPacket(anim);
    }
    public static void syncPosesAndMetadata(Player controller, Player victim) {
        if (controller == null || victim == null) return;
        if (!controller.isOnline() || !victim.isOnline()) return;
        try {
            WrappedDataWatcher watcher = WrappedDataWatcher.getEntityWatcher(controller);
            if (watcher == null) return;
            List<WrappedDataValue> values = new ArrayList<>();
            if (watcher.hasIndex(0)) {
                Object val = watcher.getObject(0);
                if (val != null) {
                    try { values.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), val)); } catch (Exception ignored) {}
                }
            }
            if (watcher.hasIndex(6)) {
                Object val = watcher.getObject(6);
                if (val != null) {
                    try { values.add(new WrappedDataValue(6, WrappedDataWatcher.Registry.get(net.minecraft.world.entity.Pose.class), val)); } catch (Exception ignored) {}
                }
            }
            if (watcher.hasIndex(8)) {
                Object val = watcher.getObject(8);
                if (val != null) {
                    try { values.add(new WrappedDataValue(8, WrappedDataWatcher.Registry.get(Byte.class), val)); } catch (Exception ignored) {}
                }
            }
            if (values.isEmpty()) return;
            PacketContainer packet = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            packet.getIntegers().write(0, victim.getEntityId());
            packet.getDataValueCollectionModifier().write(0, values);
            PROTOCOL_MANAGER.broadcastServerPacket(packet);
        } catch (Exception ignored) {}
    }
    public static void forceRefreshVisibility(Puppet plugin, Player target) {
        target.setGravity(true);
        target.setInvisible(false);
        PacketInterceptor.eject(target);
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (!observer.equals(target)) {
                observer.showPlayer(plugin, target);
            }
        }
        List<PlayerInfoData> realInfo = buildPlayerInfoData(target);
        PacketContainer metaPacket = createEntityMetadata(target.getEntityId(), getSkinFlags(target));
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(target)) continue;
            try {
                PacketContainer remove = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.PLAYER_INFO_REMOVE);
                remove.getUUIDLists().write(0, Collections.singletonList(target.getUniqueId()));
                PacketContainer destroy = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
                destroy.getIntLists().write(0, Collections.singletonList(target.getEntityId()));
                PROTOCOL_MANAGER.sendServerPacket(observer, remove);
                PROTOCOL_MANAGER.sendServerPacket(observer, destroy);
                if (observer.getWorld().equals(target.getWorld())) {
                    PacketContainer add = PROTOCOL_MANAGER.createPacket(PacketType.Play.Server.PLAYER_INFO);
                    add.getPlayerInfoActions().write(0, EnumSet.of(PlayerInfoAction.ADD_PLAYER));
                    add.getPlayerInfoDataLists().write(0, realInfo);
                    PacketContainer spawn = createNamedEntitySpawn(target);
                    PacketContainer equip = createEntityEquipment(target);
                    PROTOCOL_MANAGER.sendServerPacket(observer, add);
                    PROTOCOL_MANAGER.sendServerPacket(observer, spawn);
                    PROTOCOL_MANAGER.sendServerPacket(observer, equip);
                    PROTOCOL_MANAGER.sendServerPacket(observer, metaPacket);
                }
            } catch (Exception ignored) {}
        }
    }
    private static ItemStack[] cloneItems(ItemStack[] src) {
        ItemStack[] copy = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) copy[i] = (src[i] != null) ? src[i].clone() : null;
        return copy;
    }
    private static WrappedGameProfile safeProfile(Player player) {
        WrappedGameProfile profile = new WrappedGameProfile(player.getUniqueId(), player.getName());
        try {
            copyGameProfileProperties(WrappedGameProfile.fromPlayer(player), profile);
        } catch (Throwable ignored) {
        }
        return profile;
    }
    private static void copyGameProfileProperties(WrappedGameProfile src, WrappedGameProfile dest) {
        if (src == null || dest == null) return;
        try {
            for (Map.Entry<String, Collection<WrappedSignedProperty>> entry : src.getProperties().asMap().entrySet()) {
                for (WrappedSignedProperty prop : entry.getValue()) {
                    dest.getProperties().put(entry.getKey(), prop);
                }
            }
            return;
        } catch (Throwable ignored) {
        }
        try {
            Object srcHandle = src.getHandle();
            Object destHandle = dest.getHandle();
            if (srcHandle instanceof GameProfile srcGp && destHandle instanceof GameProfile destGp) {
                Object srcProps = getPropertyMap(srcGp);
                Object destProps = getPropertyMap(destGp);
                if (srcProps != null && destProps != null) {
                    Method putAll = destProps.getClass().getMethod("putAll", com.google.common.collect.Multimap.class);
                    putAll.invoke(destProps, srcProps);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
    private static Object getPropertyMap(GameProfile profile) throws Exception {
        for (String name : new String[]{"getProperties", "properties"}) {
            try {
                Method m = GameProfile.class.getMethod(name);
                return m.invoke(profile);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("No PropertyMap accessor found on GameProfile (tried getProperties/properties)");
    }
}
