package net.conczin.immersive_paintings;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

public final class ImageOps {
    public static final int MAX_BYTES = 384 * 1024;
    public static final int MAX_DIMENSION = 512;
    private ImageOps() {}

    /** Check image headers and dimensions before decoding any remote image. */
    public static boolean isValid(byte[] bytes) {
        if (bytes == null || bytes.length < 32 || bytes.length > MAX_BYTES) return false;
        final byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        for (int i = 0; i < sig.length; i++) {
            if (bytes[i] != sig[i]) return false;
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return false;
            Iterator<ImageReader> it = ImageIO.getImageReaders(input);
            if (!it.hasNext()) return false;
            ImageReader reader = it.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                return width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Rescale and encode a local PNG/JPEG for safe network transmission. */
    public static byte[] readLocal(File file) throws IOException {
        if (!file.isFile() || file.length() > 16L * 1024 * 1024) {
            throw new IOException("File missing or larger than 16 MiB");
        }
        BufferedImage source = ImageIO.read(file);
        if (source == null) throw new IOException("Unsupported image; use PNG or JPEG");
        int w = source.getWidth(), h = source.getHeight();
        if (w <= 0 || h <= 0) throw new IOException("Empty image");
        double scale = Math.min(1.0, Math.min((double) MAX_DIMENSION / w,
                                             (double) MAX_DIMENSION / h));
        w = Math.max(1, (int) Math.round(w * scale));
        h = Math.max(1, (int) Math.round(h * scale));
        for (int attempt = 0; attempt < 12; attempt++) {
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                   RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(source, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            if (!ImageIO.write(out, "png", buffer)) throw new IOException("PNG encoder unavailable");
            byte[] bytes = buffer.toByteArray();
            if (isValid(bytes)) return bytes;
            w = Math.max(1, (int) (w * 0.75));
            h = Math.max(1, (int) (h * 0.75));
        }
        throw new IOException("Cannot compress image below the upload limit");
    }
}
