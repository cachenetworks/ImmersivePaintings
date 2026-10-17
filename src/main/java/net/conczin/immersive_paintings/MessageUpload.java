package net.conczin.immersive_paintings;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class MessageUpload implements IMessage {
    private EnumHand hand = EnumHand.MAIN_HAND;
    private int entityId = -1;
    private int width;
    private int height;
    private byte[] image = new byte[0];

    public MessageUpload() {}

    public MessageUpload(EnumHand hand, int entityId, int width, int height, byte[] image) {
        this.hand = hand;
        this.entityId = entityId;
        this.width = width;
        this.height = height;
        this.image = image;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(hand == EnumHand.MAIN_HAND ? 0 : 1);
        buf.writeInt(entityId);
        buf.writeByte(width);
        buf.writeByte(height);
        buf.writeInt(image.length);
        buf.writeBytes(image);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int which = buf.readUnsignedByte();
        hand = which == 1 ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        entityId = buf.readInt();
        width = buf.readUnsignedByte();
        height = buf.readUnsignedByte();
        int size = buf.readInt();
        if (size < 0 || size > ImageOps.MAX_BYTES || size > buf.readableBytes()) {
            throw new IllegalArgumentException("Oversized or malformed image upload");
        }
        image = new byte[size];
        buf.readBytes(image);
    }

    public static final class Handler implements IMessageHandler<MessageUpload, IMessage> {
        @Override
        public IMessage onMessage(final MessageUpload message, final MessageContext ctx) {
            final EntityPlayerMP sender = ctx.getServerHandler().player;
            sender.getServerWorld().addScheduledTask(() -> {
                if (message.width < 1 || message.width > 4 ||
                    message.height < 1 || message.height > 4 ||
                    !ImageOps.isValid(message.image)) {
                    sender.sendMessage(new TextComponentString("Invalid painting image or dimensions."));
                    return;
                }

                if (message.entityId >= 0) {
                    Entity entity = sender.world.getEntityByID(message.entityId);
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
                    if (!painting.applyPainting(message.width, message.height, message.image)) {
                        sender.sendMessage(new TextComponentString("Not enough wall space for those dimensions."));
                        return;
                    }
                    sender.world.getChunkFromBlockCoords(painting.getHangingPosition()).markDirty();
                    ImmersivePaintings.NETWORK.sendToAllTracking(new MessagePaintingSync(painting), painting);
                    sender.sendMessage(new TextComponentString("Painting updated on wall."));
                    return;
                }

                ItemStack stack = sender.getHeldItem(message.hand);
                if (stack.isEmpty() || stack.getItem() != ImmersivePaintings.PAINTING_ITEM) {
                    sender.sendMessage(new TextComponentString("Hold an Immersive Painting item to edit it."));
                    return;
                }
                NBTTagCompound tag = stack.hasTagCompound()
                    ? stack.getTagCompound() : new NBTTagCompound();
                tag.setInteger("PaintingWidth", message.width);
                tag.setInteger("PaintingHeight", message.height);
                tag.setByteArray("PaintingPng", message.image);
                stack.setTagCompound(tag);
                sender.inventory.markDirty();
                sender.inventoryContainer.detectAndSendChanges();
                sender.sendMessage(new TextComponentString(
                    "Painting image applied (" + message.width + "x" + message.height + " blocks)."));
            });
            return null;
        }
    }
}
