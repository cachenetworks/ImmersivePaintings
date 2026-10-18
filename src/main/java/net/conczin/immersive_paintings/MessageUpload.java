package net.conczin.immersive_paintings;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forge 1.12.2 custom payloads may not exceed 32767 bytes.
 * Every painting upload is split into 12 KiB messages and assembled server-side.
 */
public class MessageUpload implements IMessage {
    public static final int CHUNK_SIZE = 12 * 1024;
    public static final int MAX_CHUNKS = (ImageOps.MAX_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE;
    private static final AtomicInteger NEXT_TRANSFER = new AtomicInteger();
    private static final Map<EntityPlayerMP, Inbound> TRANSFERS =
        Collections.synchronizedMap(new WeakHashMap<EntityPlayerMP, Inbound>());

    private EnumHand hand = EnumHand.MAIN_HAND;
    private int entityId = -1;
    private int width;
    private int height;
    private int transferId;
    private int imageLength;
    private int part;
    private int count;
    private byte[] bytes = new byte[0];

    public MessageUpload() {}

    private MessageUpload(EnumHand hand, int entityId, int width, int height,
                          int transferId, int imageLength, int part,
                          int count, byte[] bytes) {
        this.hand = hand;
        this.entityId = entityId;
        this.width = width;
        this.height = height;
        this.transferId = transferId;
        this.imageLength = imageLength;
        this.part = part;
        this.count = count;
        this.bytes = bytes;
    }

    /** Called on the client main thread after the local image was encoded. */
    public static void sendImage(EnumHand hand, int entityId, int width, int height, byte[] image) {
        if (!ImageOps.isValid(image)) {
            throw new IllegalArgumentException("Image cannot be uploaded");
        }
        int count = (image.length + CHUNK_SIZE - 1) / CHUNK_SIZE;
        int transferId = NEXT_TRANSFER.incrementAndGet();
        for (int part = 0; part < count; part++) {
            int start = part * CHUNK_SIZE;
            int length = Math.min(CHUNK_SIZE, image.length - start);
            byte[] chunk = new byte[length];
            System.arraycopy(image, start, chunk, 0, length);
            ImmersivePaintings.NETWORK.sendToServer(new MessageUpload(
                hand, entityId, width, height, transferId, image.length, part, count, chunk));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(hand == EnumHand.MAIN_HAND ? 0 : 1);
        buf.writeInt(entityId);
        buf.writeByte(width);
        buf.writeByte(height);
        buf.writeInt(transferId);
        buf.writeInt(imageLength);
        buf.writeShort(part);
        buf.writeShort(count);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hand = buf.readUnsignedByte() == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        entityId = buf.readInt();
        width = buf.readUnsignedByte();
        height = buf.readUnsignedByte();
        transferId = buf.readInt();
        imageLength = buf.readInt();
        part = buf.readUnsignedShort();
        count = buf.readUnsignedShort();
        int chunkSize = buf.readUnsignedShort();
        if (imageLength < 1 || imageLength > ImageOps.MAX_BYTES ||
            count < 1 || count > MAX_CHUNKS ||
            count != (imageLength + CHUNK_SIZE - 1) / CHUNK_SIZE ||
            part >= count || chunkSize < 1 || chunkSize > CHUNK_SIZE ||
            chunkSize > buf.readableBytes() ||
            chunkSize != Math.min(CHUNK_SIZE, imageLength - part * CHUNK_SIZE)) {
            throw new IllegalArgumentException("Malformed segmented painting upload");
        }
        bytes = new byte[chunkSize];
        buf.readBytes(bytes);
    }

    private static final class Inbound {
        final int id, imageLength, count, width, height, entityId;
        final EnumHand hand;
        final byte[] data;
        int next;
        final long started = System.currentTimeMillis();

        Inbound(MessageUpload msg) {
            id = msg.transferId;
            imageLength = msg.imageLength;
            count = msg.count;
            width = msg.width;
            height = msg.height;
            entityId = msg.entityId;
            hand = msg.hand;
            data = new byte[imageLength];
        }

        boolean matches(MessageUpload msg) {
            return id == msg.transferId && imageLength == msg.imageLength &&
                count == msg.count && width == msg.width && height == msg.height &&
                entityId == msg.entityId && hand == msg.hand &&
                System.currentTimeMillis() - started < 30_000L;
        }
    }

    public static final class Handler implements IMessageHandler<MessageUpload, IMessage> {
        @Override
        public IMessage onMessage(final MessageUpload message, final MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(() -> handle(sender, message));
            return null;
        }

        private void handle(EntityPlayerMP sender, MessageUpload msg) {
            if (msg.width < 1 || msg.width > 4 || msg.height < 1 || msg.height > 4) return;
            Inbound assembling = TRANSFERS.get(sender);
            if (msg.part == 0) {
                assembling = new Inbound(msg);
                TRANSFERS.put(sender, assembling);
            }
            if (assembling == null || !assembling.matches(msg) ||
                assembling.next != msg.part) {
                TRANSFERS.remove(sender);
                return;
            }

            System.arraycopy(msg.bytes, 0, assembling.data,
                             msg.part * CHUNK_SIZE, msg.bytes.length);
            assembling.next++;
            if (assembling.next != assembling.count) return;
            TRANSFERS.remove(sender);
            if (!ImageOps.isValid(assembling.data)) {
                sender.sendMessage(new TextComponentString("Invalid uploaded painting image."));
                return;
            }
            apply(sender, assembling);
        }

        private void apply(EntityPlayerMP sender, Inbound incoming) {
            if (incoming.entityId >= 0) {
                Entity entity = sender.world.getEntityByID(incoming.entityId);
                if (!(entity instanceof EntityCustomPainting) ||
                    sender.getDistanceSq(entity) > 64.0D) {
                    sender.sendMessage(new TextComponentString("Move closer to the painting."));
                    return;
                }
                EntityCustomPainting painting = (EntityCustomPainting) entity;
                if (!sender.world.isBlockModifiable(sender, painting.getHangingPosition()) ||
                    !sender.canPlayerEdit(painting.getHangingPosition(), EnumFacing.UP, ItemStack.EMPTY)) {
                    sender.sendMessage(new TextComponentString("You cannot edit this painting here."));
                    return;
                }
                if (!painting.applyPainting(incoming.width, incoming.height, incoming.data)) {
                    sender.sendMessage(new TextComponentString(
                        "Not enough wall space for those dimensions."));
                    return;
                }
                sender.world.getChunkFromBlockCoords(painting.getHangingPosition()).markDirty();
                MessagePaintingSync.sendToTracking(painting);
                sender.sendMessage(new TextComponentString("Painting updated on wall."));
                return;
            }

            ItemStack stack = sender.getHeldItem(incoming.hand);
            if (stack.isEmpty() || stack.getItem() != ImmersivePaintings.PAINTING_ITEM) {
                sender.sendMessage(new TextComponentString(
                    "Hold an Immersive Painting item to edit it."));
                return;
            }
            NBTTagCompound tag = stack.hasTagCompound()
                ? stack.getTagCompound() : new NBTTagCompound();
            tag.setInteger("PaintingWidth", incoming.width);
            tag.setInteger("PaintingHeight", incoming.height);
            tag.setByteArray("PaintingPng", incoming.data);
            stack.setTagCompound(tag);
            sender.inventory.markDirty();
            sender.inventoryContainer.detectAndSendChanges();
            sender.sendMessage(new TextComponentString(
                "Painting image applied (" + incoming.width + "x" +
                incoming.height + " blocks)."));
        }
    }
}
