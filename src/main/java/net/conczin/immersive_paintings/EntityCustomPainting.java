package net.conczin.immersive_paintings;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import javax.annotation.Nullable;

public class EntityCustomPainting extends EntityHanging implements IEntityAdditionalSpawnData {
    private int blocksWide = 1;
    private int blocksTall = 1;
    private byte[] png = new byte[0];

    public EntityCustomPainting(World world) {
        super(world);
    }

    public EntityCustomPainting(World world, BlockPos position, EnumFacing facing) {
        super(world, position);
        updateFacingWithBoundingBox(facing);
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        // Open the editor when clicking a placed painting, even with an empty hand.
        if (world.isRemote) {
            ImmersivePaintings.PROXY.openEntityEditor(this);
        }
        return true;
    }

    /**
     * Called on the server after permission and image validation.
     * Rejects dimensions that would no longer fit on the supporting wall.
     */
    public boolean applyPainting(int width, int height, byte[] image) {
        int oldWidth = blocksWide;
        int oldHeight = blocksTall;
        byte[] oldPng = png;
        blocksWide = clamp(width);
        blocksTall = clamp(height);
        png = image.clone();
        if (facingDirection != null) updateFacingWithBoundingBox(facingDirection);
        if (!onValidSurface()) {
            blocksWide = oldWidth;
            blocksTall = oldHeight;
            png = oldPng;
            if (facingDirection != null) updateFacingWithBoundingBox(facingDirection);
            return false;
        }
        return true;
    }

    /** Client-side state update after a server broadcast. */
    public void receivePainting(int width, int height, byte[] image) {
        blocksWide = clamp(width);
        blocksTall = clamp(height);
        png = image.clone();
        if (facingDirection != null) updateFacingWithBoundingBox(facingDirection);
    }

    public int getBlocksWide() { return blocksWide; }
    public int getBlocksTall() { return blocksTall; }
    public byte[] getPng() { return png; }

    public void loadFromItem(ItemStack stack) {
        if (!stack.hasTagCompound()) return;
        NBTTagCompound tag = stack.getTagCompound();
        blocksWide = clamp(tag.getInteger("PaintingWidth"));
        blocksTall = clamp(tag.getInteger("PaintingHeight"));
        byte[] bytes = tag.getByteArray("PaintingPng");
        if (ImageOps.isValid(bytes)) png = bytes.clone();
        if (facingDirection != null) updateFacingWithBoundingBox(facingDirection);
    }

    private static int clamp(int v) { return Math.max(1, Math.min(4, v)); }

    @Override
    public int getWidthPixels() { return 16 * blocksWide; }

    @Override
    public int getHeightPixels() { return 16 * blocksTall; }

    @Override
    public void onBroken(@Nullable Entity brokenEntity) {
        if (!world.getGameRules().getBoolean("doEntityDrops")) return;
        playSound(SoundEvents.ENTITY_PAINTING_BREAK, 1.0F, 1.0F);
        if (brokenEntity instanceof EntityPlayer &&
                ((EntityPlayer) brokenEntity).capabilities.isCreativeMode) return;
        ItemStack item = new ItemStack(ImmersivePaintings.PAINTING_ITEM);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("PaintingWidth", blocksWide);
        tag.setInteger("PaintingHeight", blocksTall);
        tag.setByteArray("PaintingPng", png);
        item.setTagCompound(tag);
        entityDropItem(item, 0.0F);
    }

    @Override
    public void playPlaceSound() {
        playSound(SoundEvents.ENTITY_PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setInteger("PaintingWidth", blocksWide);
        tag.setInteger("PaintingHeight", blocksTall);
        tag.setByteArray("PaintingPng", png);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        blocksWide = clamp(tag.getInteger("PaintingWidth"));
        blocksTall = clamp(tag.getInteger("PaintingHeight"));
        byte[] bytes = tag.getByteArray("PaintingPng");
        png = bytes.length <= ImageOps.MAX_BYTES ? bytes : new byte[0];
        super.readEntityFromNBT(tag);
        if (facingDirection != null) updateFacingWithBoundingBox(facingDirection);
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        BlockPos pos = getHangingPosition();
        buf.writeInt(pos.getX());
        buf.writeInt(pos.getY());
        buf.writeInt(pos.getZ());
        buf.writeByte(facingDirection == null ? EnumFacing.NORTH.getIndex() : facingDirection.getIndex());
        buf.writeByte(blocksWide);
        buf.writeByte(blocksTall);
        buf.writeInt(png.length);
        buf.writeBytes(png);
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        hangingPosition = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());
        EnumFacing facing = EnumFacing.getFront(buf.readUnsignedByte());
        blocksWide = clamp(buf.readUnsignedByte());
        blocksTall = clamp(buf.readUnsignedByte());
        int length = buf.readInt();
        if (length < 0 || length > ImageOps.MAX_BYTES || length > buf.readableBytes()) {
            throw new IllegalArgumentException("Invalid painting payload");
        }
        png = new byte[length];
        buf.readBytes(png);
        updateFacingWithBoundingBox(facing.getAxis().isHorizontal() ? facing : EnumFacing.NORTH);
    }
}
