package de.zwegen.zpaint.motif;

import java.nio.FloatBuffer;

final class ConfidenceMask {
    private ConfidenceMask() {}

    static void readAlphas(FloatBuffer confidence, byte[] pixels) {
        for (int x = 0; x < pixels.length; x++) {
            float value = confidence.get();
            int alpha = Math.round(255f * Math.min(1f, Math.max(0f, value)));
            pixels[x] = (byte) alpha;
        }
    }
}
