package de.zwegen.zpaint.motif;

final class CutoutUnion {
    private CutoutUnion() {}

    // Both cutouts come from the same original. Keep the stronger alpha, not a
    // source-over blend: tapping the same soft edge twice must not make it opaque.
    static void include(byte[] union, byte[] matte) {
        for (int i = 0; i < union.length; i++) {
            if ((matte[i] & 255) > (union[i] & 255)) union[i] = matte[i];
        }
    }
}
