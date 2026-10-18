package net.conczin.immersive_paintings;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.concurrent.atomic.AtomicInteger;

/** Segmented server-to-client image updates, also sent when an entity starts tracking. */
public class MessagePaintingSync implements IMessage {
    private static final AtomicInteger NEXT_TRANSFER = new AtomicInteger();
    private int entityId;
    private int transferId;
    private int width;
    private int height;
    private int count;
    private int imageLength;
    private int part;
    private byte[] bytes = new byte[0];

    public MessagePaintingSync() {}

    private MessagePaintingSync(EntityCustomPainting painting, int transferId, int imageLength,
                                int part, int count, byte[] bytes) {
        entityId = painting.getEntityId();
        width = painting.getBlocksWide();
        height = painting.getBlocksTall();
        this.transferId = transferId;
        this.imageLength = imageLength;
        this.part = part;
        this.count = count;
        this.bytes = bytes;
    }

    /** Send the image to each player tracking the edited painting. */
    public static void sendToTracking(EntityCustomPainting painting) {
        send(painting, null);
    }

    /** Called for each player who starts tracking a painting entity. */
    public static void sendToPlayer(EntityCustomPainting painting, EntityPlayerMP player) {
        send(painting, player);
    }

    private static void send(EntityCustomPainting painting, EntityPlayerMP player) {
        byte[] image = painting.getPng();
        if (image.length == 0 || !ImageOps.isValid(image)) return;
        int count = (image.length + MessageUpload.CHUNK_SIZE - 1) / MessageUpload.CHUNK_SIZE;
        int transfer = NEXT_TRANSFER.incrementAndGet();
        for (int part = 0; part < count; part++) {
            int start = part * MessageUpload.CHUNK_SIZE;
            int length = Math.min(MessageUpload.CHUNK_SIZE, image.length - start);
            byte[] chunk = new byte[length];
            System.arraycopy(image, start, chunk, 0, length);
            MessagePaintingSync message = new MessagePaintingSync(
                painting, transfer, image.length, part, count, chunk);
            if (player != null) {
                ImmersivePaintings.NETWORK.sendTo(message, player);
            } else {
                ImmersivePaintings.NETWORK.sendToAllTracking(message, painting);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(transferId);
        buf.writeByte(width);
        buf.writeByte(height);
        buf.writeShort(part);
        buf.writeShort(count);
        buf.writeInt(imageLength);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        transferId = buf.readInt();
        width = buf.readUnsignedByte();
        height = buf.readUnsignedByte();
        part = buf.readUnsignedShort();
        count = buf.readUnsignedShort();
        imageLength = buf.readInt();
        int chunkSize = buf.readUnsignedShort();
        if (width < 1 || width > 4 || height < 1 || height > 4 ||
            imageLength < 1 || imageLength > ImageOps.MAX_BYTES ||
            count < 1 || count > MessageUpload.MAX_CHUNKS ||
            count != (imageLength + MessageUpload.CHUNK_SIZE - 1) / MessageUpload.CHUNK_SIZE ||
            part >= count || chunkSize < 1 || chunkSize > MessageUpload.CHUNK_SIZE ||
            chunkSize > buf.readableBytes() ||
            chunkSize != Math.min(MessageUpload.CHUNK_SIZE,
                                  imageLength - part * MessageUpload.CHUNK_SIZE)) {
            throw new IllegalArgumentException("Malformed painting image sync");
        }
        bytes = new byte[chunkSize];
        buf.readBytes(bytes);
    }

    public static final class Handler implements IMessageHandler<MessagePaintingSync, IMessage> {
        @Override
        public IMessage onMessage(MessagePaintingSync message, MessageContext context) {
            ImmersivePaintings.PROXY.receivePaintingChunk(
                message.entityId, message.transferId, message.width, message.height,
                message.count, message.imageLength, message.part, message.bytes);
            return null;
        }
    }
}
