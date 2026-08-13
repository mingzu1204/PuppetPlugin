package org.mingzu.puppetLite;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import java.lang.reflect.Field;
public class PacketInterceptor extends ChannelInboundHandlerAdapter {
    private static final String HANDLER_NAME = "puppet_lite_freeze";
    private static final Field CONNECTION_FIELD;
    static {
        try {
            Field connectionField = null;
            Class<?> currentClass = ServerGamePacketListenerImpl.class;
            while (currentClass != null && currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (field.getType().equals(Connection.class)) {
                        connectionField = field;
                        break;
                    }
                }
                if (connectionField != null) break; 
                currentClass = currentClass.getSuperclass(); 
            }
            if (connectionField == null) throw new NoSuchFieldException("Connection field not found in class hierarchy.");
            connectionField.setAccessible(true);
            CONNECTION_FIELD = connectionField;
        } catch (Exception e) {
            throw new RuntimeException("PacketInterceptor failed to cache connection field: " + e.getMessage(), e);
        }
    }
    private final Puppet plugin;
    private final Player victim;
    private PacketInterceptor(Puppet plugin, Player victim) {
        this.plugin = plugin;
        this.victim = victim;
    }
    public static void inject(Puppet plugin, Player victim) {
        Channel channel = getChannel(victim);
        if (channel == null) return;
        channel.eventLoop().submit(() -> {
            if (channel.pipeline().get(HANDLER_NAME) == null) {
                channel.pipeline().addBefore("packet_handler", HANDLER_NAME, new PacketInterceptor(plugin, victim));
            }
        });
    }
    public static void eject(Player player) {
        Channel channel = getChannel(player);
        if (channel == null) return;
        channel.eventLoop().submit(() -> {
            if (channel.pipeline().get(HANDLER_NAME) != null) channel.pipeline().remove(HANDLER_NAME);
        });
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Packet<?> packet && shouldBlock(packet)) return;
        super.channelRead(ctx, msg);
    }
    private boolean shouldBlock(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket) return true;
        if (packet instanceof ServerboundInteractPacket) return true;
        if (packet instanceof ServerboundSwingPacket) return true;
        if (packet instanceof ServerboundContainerClickPacket) return true;
        if (packet instanceof ServerboundSetCreativeModeSlotPacket) return true;
        if (packet instanceof ServerboundPickItemFromBlockPacket || packet instanceof ServerboundPickItemFromEntityPacket) return true;
        if (packet instanceof ServerboundSetCarriedItemPacket) return true; 
        return false;
    }
    private static Channel getChannel(Player player) {
        try {
            ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
            Connection conn = (Connection) CONNECTION_FIELD.get(nmsPlayer.connection);
            return conn.channel;
        } catch (Exception e) {
            Puppet.getInstance().getLogger().severe("Failed to get netty channel for " + player.getName() + ": " + e.getMessage());
            return null;
        }
    }
}
