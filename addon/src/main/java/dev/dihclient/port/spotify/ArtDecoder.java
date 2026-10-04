package dev.dihclient.port.spotify;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Ported from an open-source client (GPL-3.0).
 * Decodes the cover bytes Windows hands over (PNG/JPEG/BMP...) with stb_image, which the game ships. Runs on the poll thread.
 */
final class ArtDecoder {
    private static final int MAX_SOURCE_SIDE = 4096;

    private ArtDecoder() {
    }

    /** Null when the bytes are not an image we want (too large, corrupt). */
    static AlbumArt decode(byte[] encoded, int maxSide) {
        if (encoded == null || encoded.length == 0 || maxSide <= 0) {
            return null;
        }
        ByteBuffer file = MemoryUtil.memAlloc(encoded.length);
        ByteBuffer pixels = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            file.put(encoded).flip();
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer channels = stack.mallocInt(1);
            if (!STBImage.stbi_info_from_memory(file, w, h, channels)) {
                return null;
            }
            if (w.get(0) <= 0 || h.get(0) <= 0 || w.get(0) > MAX_SOURCE_SIDE || h.get(0) > MAX_SOURCE_SIDE) {
                return null;
            }
            pixels = STBImage.stbi_load_from_memory(file, w, h, channels, 4);
            return pixels == null ? null : AlbumArt.crop(pixels, w.get(0), h.get(0), maxSide);
        } finally {
            if (pixels != null) {
                STBImage.stbi_image_free(pixels);
            }
            MemoryUtil.memFree(file);
        }
    }
}
