package net.conczin.immersive_paintings;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(value = Side.CLIENT, modid = ImmersivePaintings.MODID)
public final class ClientEvents {
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
            ImmersivePaintings.PAINTING_ITEM, 0,
            new ModelResourceLocation(ImmersivePaintings.MODID + ":immersive_painting", "inventory"));
    }
}
