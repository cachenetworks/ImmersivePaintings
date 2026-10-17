package net.conczin.immersive_paintings;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumHand;
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
}
