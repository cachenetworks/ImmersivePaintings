package net.conczin.immersive_paintings;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** Synchronizes changes to already-spawned paintings for all tracking players. */
public class MessagePaintingSync implements IMessage {
    private int entityId;
    private int width;
    private int height;
    private byte[] image = new byte[0];

    public MessagePaintingSync() {}

    public MessagePaintingSync(EntityCustomPainting painting) {
        entityId = painting.getEntityId();
        width = painting.getBlocksWide();
        height = painting.getBlocksTall();
        image = painting.getPng().clone();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeByte(width);
        buf.writeByte(height);
        buf.writeInt(image.length);
        buf.writeBytes(image);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        entityId = buf.readInt();
        width = buf.readUnsignedByte();
        height = buf.readUnsignedByte();
        int length = buf.readInt();
        if (width < 1 || width > 4 || height < 1 || height > 4 ||
            length < 0 || length > ImageOps.MAX_BYTES || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Malformed painting update");
        }
        image = new byte[length];
        buf.readBytes(image);
    }

    public static final class Handler implements IMessageHandler<MessagePaintingSync, IMessage> {
        @Override
        public IMessage onMessage(MessagePaintingSync message, MessageContext context) {
            ImmersivePaintings.PROXY.receivePaintingSync(
                message.entityId, message.width, message.height, message.image);
            return null;
        }
    }
}
