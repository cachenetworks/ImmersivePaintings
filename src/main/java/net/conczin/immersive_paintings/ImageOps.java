package net.conczin.immersive_paintings;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.net.ssl.HttpsURLConnection;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Locale;

public final class ImageOps {
    public static final int MAX_BYTES = 384 * 1024;
    public static final int MAX_DIMENSION = 512;
    private static final int MAX_DOWNLOAD_BYTES = 8 * 1024 * 1024;
    private static final long MAX_SOURCE_PIXELS = 16L * 1024L * 1024L;
    private ImageOps() {}

    /** Validate the PNG saved in item and entity NBT without fully decoding it. */
    public static boolean isValid(byte[] bytes) {
        if (bytes == null || bytes.length < 32 || bytes.length > MAX_BYTES) return false;
        final byte[] sig = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        for (int i = 0; i < sig.length; i++) {
            if (bytes[i] != sig[i]) return false;
        }
        try (ImageInputStream input =
                 ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return false;
            Iterator<ImageReader> it = ImageIO.getImageReaders(input);
            if (!it.hasNext()) return false;
            ImageReader reader = it.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                return width > 0 && height > 0 &&
                       width <= MAX_DIMENSION && height <= MAX_DIMENSION &&
                       "png".equalsIgnoreCase(reader.getFormatName());
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Read local PNG/JPG input, limiting its size and decoded dimensions. */
    public static byte[] readLocal(File file) throws IOException {
        if (!file.isFile() || file.length() > 16L * 1024 * 1024) {
            throw new IOException("File missing or larger than 16 MiB.");
        }
        try (InputStream input = Files.newInputStream(file.toPath())) {
            return transcode(input);
        }
    }

    /** Download a public HTTPS PNG/JPG on the CLIENT only, with strict limits. */
    public static byte[] readRemote(String address) throws IOException {
        URL url = new URL(address);
        for (int hop = 0; hop <= 3; hop++) {
            if (!"https".equalsIgnoreCase(url.getProtocol()) ||
                url.getHost() == null || url.getHost().isEmpty() ||
                url.getUserInfo() != null) {
                throw new IOException("Enter a public https:// image URL (HTTP is not supported).");
            }

            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "ImmersivePaintings/1.12.2");
            connection.setRequestProperty("Accept", "image/png,image/jpeg,*/*;q=0.5");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 ||
                    status == 307 || status == 308) {
                    if (hop == 3) throw new IOException("Too many image redirects.");
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isEmpty()) {
                        throw new IOException("Image redirect is missing a destination.");
                    }
                    url = new URL(url, location);
                    continue;
                }
                if (status != 200) {
                    throw new IOException("Image server returned HTTP " + status + ".");
                }
                if (connection.getContentLengthLong() > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("Remote image exceeds the 8 MiB download limit.");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = connection.getInputStream()) {
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = stream.read(chunk)) != -1) {
                        if (bytes.size() + n > MAX_DOWNLOAD_BYTES) {
                            throw new IOException("Remote image exceeds the 8 MiB download limit.");
                        }
                        bytes.write(chunk, 0, n);
                    }
                }
                return transcode(new ByteArrayInputStream(bytes.toByteArray()));
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many image redirects.");
    }

    /** Decode only PNG/JPEG with bounded dimensions; compress to an upload-safe PNG. */
    private static byte[] transcode(InputStream stream) throws IOException {
        BufferedImage source;
        try (ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            if (input == null) throw new IOException("Could not read image data.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Not an image. Use a direct PNG or JPG URL.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!"png".equals(format) && !"jpeg".equals(format) && !"jpg".equals(format)) {
                    throw new IOException("Only PNG and JPG images are supported.");
                }
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 ||
                    (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new IOException("Image dimensions are too large (16 megapixel limit).");
                }
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
        }

        if (source == null) throw new IOException("Could not decode PNG or JPEG.");
        int w = source.getWidth(), h = source.getHeight();
        double scale = Math.min(1.0,
            Math.min((double) MAX_DIMENSION / w, (double) MAX_DIMENSION / h));
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
            if (!ImageIO.write(out, "png", buffer)) throw new IOException("PNG encoder unavailable.");
            byte[] bytes = buffer.toByteArray();
            if (isValid(bytes)) return bytes;
            w = Math.max(1, (int) (w * 0.75));
            h = Math.max(1, (int) (h * 0.75));
        }
        throw new IOException("Cannot compress image below the upload limit.");
    }
}
