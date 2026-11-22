package de.zwegen.zpaint.motif;

import java.nio.FloatBuffer;

final class MagicTouchInput {
    static final int EDGE = 512;
    static final int CHANNELS = 4;

    private MagicTouchInput() {}

    static int tapIndex(float x, float y) {
        if (!(x >= 0 && x <= 1 && y >= 0 && y <= 1)) {
            throw new IllegalArgumentException("Expected a normalized point in [0, 1]");
        }
        // Match round((edge - 1) * coordinate) in the model smoke test.
        int column = (int) Math.rint((EDGE - 1) * (double) x);
        int row = (int) Math.rint((EDGE - 1) * (double) y);
        return row * EDGE + column;
    }

    static void encode(int[] pixels, int tap, FloatBuffer input) {
        input.clear();
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            input.put(((color >>> 16) & 255) / 255f);
            input.put(((color >>> 8) & 255) / 255f);
            input.put((color & 255) / 255f);
            input.put(i == tap ? 1f : 0f);
        }
        input.flip();
    }
}
