package net.conczin.immersive_paintings;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {
    private World activeWorld;
    private final Map<Integer, Inbound> receiving = new HashMap<>();
    private final Map<Integer, Completed> pending = new HashMap<>();

    private static class Inbound {
        final int id, count, length, width, height;
        final byte[] bytes;
        int next;
        final long started = System.currentTimeMillis();
        Inbound(int id, int count, int length, int width, int height) {
            this.id = id; this.count = count; this.length = length;
            this.width = width; this.height = height;
            this.bytes = new byte[length];
        }
    }

    private static class Completed {
        final byte[] data;
        final int width, height;
        final long receivedAt = System.currentTimeMillis();
        Completed(int width, int height, byte[] data) {
            this.width = width; this.height = height; this.data = data;
        }
    }

    private void switchWorldIfNeeded() {
        World world = Minecraft.getMinecraft().world;
        if (activeWorld != world) {
            receiving.clear();
            pending.clear();
            activeWorld = world;
        }
    }

    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(
            EntityCustomPainting.class, RenderCustomPainting::new);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void openEditor(EnumHand hand) {
        Minecraft.getMinecraft().displayGuiScreen(new GuiPaintingEditor(hand));
    }

    @Override
    public void openEntityEditor(EntityCustomPainting entity) {
        Minecraft.getMinecraft().displayGuiScreen(
            new GuiPaintingEditor(entity.getEntityId(), entity.getBlocksWide(), entity.getBlocksTall()));
    }

    @Override
    public void receivePaintingSync(int entityId, int width, int height, byte[] image) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (Minecraft.getMinecraft().world == null) return;
            Entity entity = Minecraft.getMinecraft().world.getEntityByID(entityId);
            if (entity instanceof EntityCustomPainting) {
                ((EntityCustomPainting) entity).receivePainting(width, height, image);
            }
        });
    }

    @Override
    public void receivePaintingChunk(int entityId, int transferId,
                                     int width, int height, int count,
                                     int totalLength, int index, byte[] bytes) {
        // Packet handlers execute on Netty's thread; mutate world and assembly state on the client thread.
        Minecraft.getMinecraft().addScheduledTask(() -> {
            switchWorldIfNeeded();
            if (activeWorld == null) return;
            if (count < 1 || count > MessageUpload.MAX_CHUNKS ||
                totalLength < 1 || totalLength > ImageOps.MAX_BYTES ||
                index < 0 || index >= count || bytes.length < 1 ||
                bytes.length > MessageUpload.CHUNK_SIZE ||
                width < 1 || width > 4 || height < 1 || height > 4) return;

            Inbound incoming = receiving.get(entityId);
            if (index == 0) {
                incoming = new Inbound(transferId, count, totalLength, width, height);
                receiving.put(entityId, incoming);
            }
            if (incoming == null || incoming.id != transferId ||
                incoming.count != count || incoming.length != totalLength ||
                incoming.width != width || incoming.height != height ||
                incoming.next != index ||
                System.currentTimeMillis() - incoming.started > 30_000L) return;

            int offset = index * MessageUpload.CHUNK_SIZE;
            int expected = Math.min(MessageUpload.CHUNK_SIZE, totalLength - offset);
            if (expected <= 0 || expected != bytes.length) {
                receiving.remove(entityId);
                return;
            }
            System.arraycopy(bytes, 0, incoming.bytes, offset, bytes.length);
            incoming.next++;
            if (incoming.next == count) {
                receiving.remove(entityId);
                if (!ImageOps.isValid(incoming.bytes)) return;
                Completed finished = new Completed(width, height, incoming.bytes);
                if (!applyPainting(entityId, finished)) pending.put(entityId, finished);
            }
        });
    }

    private boolean applyPainting(int entityId, Completed completed) {
        if (activeWorld == null) return false;
        Entity entity = activeWorld.getEntityByID(entityId);
        if (!(entity instanceof EntityCustomPainting)) return false;
        ((EntityCustomPainting) entity).receivePainting(
            completed.width, completed.height, completed.data);
        return true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        switchWorldIfNeeded();
        if (activeWorld == null) return;
        long now = System.currentTimeMillis();
        receiving.entrySet().removeIf(e -> now - e.getValue().started > 30_000L);
        pending.entrySet().removeIf(e ->
            now - e.getValue().receivedAt > 15_000L ||
            applyPainting(e.getKey(), e.getValue()));
    }
}
