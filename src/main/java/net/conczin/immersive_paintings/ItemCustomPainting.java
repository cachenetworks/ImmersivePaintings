package net.conczin.immersive_paintings;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ItemCustomPainting extends Item {
    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (world.isRemote) {
            ImmersivePaintings.PROXY.openEditor(hand);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    @Override
    public EnumActionResult onItemUse(EntityPlayer player, World world, BlockPos pos,
                                      EnumHand hand, EnumFacing facing, float hitX,
                                      float hitY, float hitZ) {
        if (!facing.getAxis().isHorizontal()) return EnumActionResult.FAIL;
        ItemStack stack = player.getHeldItem(hand);
        BlockPos position = pos.offset(facing);
        if (!player.canPlayerEdit(position, facing, stack)) return EnumActionResult.FAIL;
        EntityCustomPainting painting = new EntityCustomPainting(world, position, facing);
        painting.loadFromItem(stack);
        if (!painting.onValidSurface()) return EnumActionResult.FAIL;
        if (!world.isRemote) {
            world.spawnEntity(painting);
            painting.playPlaceSound();
            if (!player.capabilities.isCreativeMode) stack.shrink(1);
        }
        return EnumActionResult.SUCCESS;
    }
}
