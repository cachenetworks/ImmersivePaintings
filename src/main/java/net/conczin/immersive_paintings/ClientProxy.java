package net.conczin.immersive_paintings;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumHand;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(
            EntityCustomPainting.class, RenderCustomPainting::new);
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
}
