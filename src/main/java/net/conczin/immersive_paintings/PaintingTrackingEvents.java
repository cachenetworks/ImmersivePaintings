package net.conczin.immersive_paintings;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** The spawn packet intentionally excludes large image data; send after tracking begins. */
@Mod.EventBusSubscriber(modid = ImmersivePaintings.MODID)
public final class PaintingTrackingEvents {
    private PaintingTrackingEvents() {}

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP &&
            event.getTarget() instanceof EntityCustomPainting) {
            MessagePaintingSync.sendToPlayer(
                (EntityCustomPainting) event.getTarget(),
                (EntityPlayerMP) event.getEntityPlayer());
        }
    }
}
