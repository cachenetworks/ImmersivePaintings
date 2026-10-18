package net.conczin.immersive_paintings;

import net.minecraft.util.EnumHand;

public class CommonProxy {
    public void preInit() { }
    public void openEditor(EnumHand hand) { }
    public void openEntityEditor(EntityCustomPainting entity) { }
    public void receivePaintingSync(int entityId, int width, int height, byte[] image) { }
    public void receivePaintingChunk(int entityId, int transferId, int width, int height,
                                    int count, int totalLength, int index, byte[] bytes) { }
}
