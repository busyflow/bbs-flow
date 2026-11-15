package ans.pack;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Decodes uncompressed and RLE-compressed TGA images (8-bit grayscale, 16-bit,
 * 24-bit BGR, and 32-bit BGRA) into ARGB {@link BufferedImage} instances and
 * normalizes Bedrock-specific alpha conventions when converting to Java 1.20.1 PNGs.
 */
public final class TgaDecoder {

    private TgaDecoder() {
    }

    public enum AlphaMode {
        /** Preserve exact decoded alpha for all pixels. */
        PRESERVE,
        /** Force every pixel to opaque alpha 255 (e.g., grass_block_side, grass_block_snow). */
        FORCE_OPAQUE,
        /** Keep only overlay pixels (alpha &gt; 128) as alpha 255; zero everything else. */
        EXTRACT_OVERLAY_ONLY,
        /** Treat near-zero Bedrock cutout alpha (&lt;= 4) as fully transparent (alpha 0). */
        CUTOUT_ALPHA_FLOOR,
        /** For armor layers like leather_1.tga: preserve cutout mask cleanly. */
        PRESERVE_CLEAN_ZERO
    }

    public static BufferedImage decode(byte[] data) throws IOException {
        if (data == null || data.length < 8) {
            throw new IOException("Invalid TGA header: length=" + (data == null ? 0 : data.length));
        }
        // Some Bedrock .tga files (e.g., 1x1 blank entity masks) actually contain PNG-encoded bytes.
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            BufferedImage png = ImageIO.read(new ByteArrayInputStream(data));
            if (png == null) {
                throw new IOException("Failed to decode PNG-disguised .tga image");
            }
            return png;
        }
        if (data.length < 18) {
            throw new IOException("Invalid TGA header: length=" + data.length);
        }

        int idLength = data[0] & 0xFF;
        int colorMapType = data[1] & 0xFF;
        int imageType = data[2] & 0xFF;
        int colorMapLength = u16(data, 5);
        int colorMapDepth = data[7] & 0xFF;
        int width = u16(data, 12);
        int height = u16(data, 14);
        int bpp = data[16] & 0xFF;
        int descriptor = data[17] & 0xFF;

        if (width <= 0 || height <= 0 || width > 16384 || height > 16384) {
            throw new IOException("Invalid TGA dimensions: " + width + "x" + height);
        }

        int offset = 18 + idLength;
        byte[] colorMap = null;
        int cmBytesPerEntry = 0;
        if (colorMapType == 1 && colorMapLength > 0) {
            cmBytesPerEntry = (colorMapDepth + 7) / 8;
            int cmTotal = colorMapLength * cmBytesPerEntry;
            colorMap = new byte[cmTotal];
            if (offset + cmTotal > data.length) {
                throw new IOException("Truncated TGA colormap");
            }
            System.arraycopy(data, offset, colorMap, 0, cmTotal);
            offset += cmTotal;
        }

        int bytesPerPixel = (bpp + 7) / 8;
        int pixelCount = width * height;
        byte[] rawPixels = new byte[pixelCount * bytesPerPixel];

        boolean rle = (imageType == 9 || imageType == 10 || imageType == 11);
        int baseType = rle ? (imageType - 8) : imageType;
        if (baseType != 1 && baseType != 2 && baseType != 3) {
            throw new IOException("Unsupported TGA imageType: " + imageType);
        }

        if (!rle) {
            int needed = pixelCount * bytesPerPixel;
            if (offset + needed > data.length) {
                throw new IOException("Truncated uncompressed TGA pixel data");
            }
            System.arraycopy(data, offset, rawPixels, 0, needed);
        } else {
            int p = 0;
            int pos = offset;
            while (p < pixelCount && pos < data.length) {
                int header = data[pos++] & 0xFF;
                int count = (header & 0x7F) + 1;
                if ((header & 0x80) != 0) {
                    if (pos + bytesPerPixel > data.length) {
                        throw new IOException("Truncated RLE packet in TGA");
                    }
                    for (int i = 0; i < count && p < pixelCount; i++) {
                        System.arraycopy(data, pos, rawPixels, p * bytesPerPixel, bytesPerPixel);
                        p++;
                    }
                    pos += bytesPerPixel;
                } else {
                    for (int i = 0; i < count && p < pixelCount; i++) {
                        if (pos + bytesPerPixel > data.length) {
                            throw new IOException("Truncated raw packet in RLE TGA");
                        }
                        System.arraycopy(data, pos, rawPixels, p * bytesPerPixel, bytesPerPixel);
                        pos += bytesPerPixel;
                        p++;
                    }
                }
            }
        }

        boolean topToBottom = (descriptor & 0x20) != 0;
        boolean rightToLeft = (descriptor & 0x10) != 0;

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < height; row++) {
            int destY = topToBottom ? row : (height - 1 - row);
            for (int col = 0; col < width; col++) {
                int destX = rightToLeft ? (width - 1 - col) : col;
                int idx = (row * width + col) * bytesPerPixel;
                int argb = readPixelArgb(rawPixels, idx, baseType, bpp, colorMap, cmBytesPerEntry, colorMapDepth);
                img.setRGB(destX, destY, argb);
            }
        }
        return img;
    }

    private static int readPixelArgb(byte[] buf, int idx, int baseType, int bpp,
                                     byte[] colorMap, int cmBytes, int cmDepth) {
        if (baseType == 3) {
            if (bpp == 8) {
                int v = buf[idx] & 0xFF;
                return (0xFF << 24) | (v << 16) | (v << 8) | v;
            } else {
                int v = buf[idx] & 0xFF;
                int a = buf[idx + 1] & 0xFF;
                return (a << 24) | (v << 16) | (v << 8) | v;
            }
        }
        if (baseType == 1 && colorMap != null) {
            int ci = bpp == 8 ? (buf[idx] & 0xFF) : u16(buf, idx);
            int cIdx = ci * cmBytes;
            if (cIdx + cmBytes <= colorMap.length) {
                return readRgbBytes(colorMap, cIdx, cmDepth);
            }
            return 0;
        }
        return readRgbBytes(buf, idx, bpp);
    }

    private static int readRgbBytes(byte[] buf, int idx, int bpp) {
        if (bpp == 32) {
            int b = buf[idx] & 0xFF;
            int g = buf[idx + 1] & 0xFF;
            int r = buf[idx + 2] & 0xFF;
            int a = buf[idx + 3] & 0xFF;
            return (a << 24) | (r << 16) | (g << 8) | b;
        } else if (bpp == 24) {
            int b = buf[idx] & 0xFF;
            int g = buf[idx + 1] & 0xFF;
            int r = buf[idx + 2] & 0xFF;
            return (0xFF << 24) | (r << 16) | (g << 8) | b;
        } else if (bpp == 16 || bpp == 15) {
            int v = u16(buf, idx);
            int r = ((v >> 10) & 0x1F) * 255 / 31;
            int g = ((v >> 5) & 0x1F) * 255 / 31;
            int b = (v & 0x1F) * 255 / 31;
            int a = bpp == 16 ? (((v & 0x8000) != 0) ? 255 : 0) : 255;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
        int v = buf[idx] & 0xFF;
        return (0xFF << 24) | (v << 16) | (v << 8) | v;
    }

    public static BufferedImage applyAlphaMode(BufferedImage src, AlphaMode mode) {
        if (mode == AlphaMode.PRESERVE) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int rgb = argb & 0x00FFFFFF;
                int newArgb = switch (mode) {
                    case PRESERVE -> argb;
                    case FORCE_OPAQUE -> (0xFF << 24) | rgb;
                    case EXTRACT_OVERLAY_ONLY -> (a > 128) ? ((0xFF << 24) | rgb) : 0x00000000;
                    case CUTOUT_ALPHA_FLOOR -> (a <= 4) ? 0x00000000 : argb;
                    case PRESERVE_CLEAN_ZERO -> (a == 0) ? 0x00000000 : argb;
                };
                out.setRGB(x, y, newArgb);
            }
        }
        return out;
    }

    public static byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "PNG", out)) {
            throw new IOException("No PNG ImageWriter available");
        }
        return out.toByteArray();
    }

    public static BufferedImage decodePngOrTga(byte[] data, String filename) throws IOException {
        if (filename != null && filename.toLowerCase().endsWith(".tga")) {
            return decode(data);
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) {
            throw new IOException("Failed to decode image: " + filename);
        }
        return img;
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }
}
