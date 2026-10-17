package net.conczin.immersive_paintings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

public class RenderCustomPainting extends Render<EntityCustomPainting> {
    private final Map<EntityCustomPainting, CachedTexture> textures = new WeakHashMap<>();

    private static final class CachedTexture {
        final byte[] imageData;
        final ResourceLocation texture;
        CachedTexture(byte[] imageData, ResourceLocation texture) {
            this.imageData = imageData;
            this.texture = texture;
        }
    }

    protected RenderCustomPainting(RenderManager manager) {
        super(manager);
        shadowSize = 0.0F;
    }

    @Nullable
    @Override
    protected ResourceLocation getEntityTexture(EntityCustomPainting entity) {
        CachedTexture previous = textures.get(entity);
        byte[] imageData = entity.getPng();
        if (previous != null && previous.imageData == imageData) {
            return previous.texture;
        }
        if (previous != null) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(previous.texture);
            textures.remove(entity);
        }
        if (!ImageOps.isValid(imageData)) return TextureMap.LOCATION_BLOCKS_TEXTURE;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image == null) return TextureMap.LOCATION_BLOCKS_TEXTURE;
            ResourceLocation current = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation("immersive_paintings", new DynamicTexture(image));
            textures.put(entity, new CachedTexture(imageData, current));
            return current;
        } catch (IOException e) {
            return TextureMap.LOCATION_BLOCKS_TEXTURE;
        }
    }

    @Override
    public void doRender(EntityCustomPainting entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        float w = entity.getBlocksWide();
        float h = entity.getBlocksTall();
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(180.0F - entity.rotationYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.disableCull();
        GlStateManager.disableLighting();

        // Flat brown border behind the painting surface.
        // Painting front faces local -Z in the hanging entity's coordinate system.
        GlStateManager.disableTexture2D();
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        float left = -w / 2.0F - 0.035F, right = w / 2.0F + 0.035F;
        float top = h / 2.0F + 0.035F, bottom = -h / 2.0F - 0.035F;
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        b.pos(left, bottom, -0.01).color(82, 51, 28, 255).endVertex();
        b.pos(right, bottom, -0.01).color(82, 51, 28, 255).endVertex();
        b.pos(right, top, -0.01).color(82, 51, 28, 255).endVertex();
        b.pos(left, top, -0.01).color(82, 51, 28, 255).endVertex();
        t.draw();

        // Fallback matte for an unconfigured item.
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        b.pos(-w/2, -h/2, -0.02).color(216, 209, 181, 255).endVertex();
        b.pos(w/2, -h/2, -0.02).color(216, 209, 181, 255).endVertex();
        b.pos(w/2, h/2, -0.02).color(216, 209, 181, 255).endVertex();
        b.pos(-w/2, h/2, -0.02).color(216, 209, 181, 255).endVertex();
        t.draw();

        // Header and dimensions are checked when the texture is first registered;
        // avoid repeatedly constructing an ImageReader on every render frame.
        if (entity.getPng().length > 0) {
            ResourceLocation image = getEntityTexture(entity);
            GlStateManager.enableTexture2D();
            bindTexture(image);
            GlStateManager.color(1F, 1F, 1F, 1F);
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            b.pos(-w/2, -h/2, -0.03).tex(0, 1).endVertex();
            b.pos(w/2, -h/2, -0.03).tex(1, 1).endVertex();
            b.pos(w/2, h/2, -0.03).tex(1, 0).endVertex();
            b.pos(-w/2, h/2, -0.03).tex(0, 0).endVertex();
            t.draw();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }
}
