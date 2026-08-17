package com.quicktap.pos.print;

import android.graphics.Bitmap;
import android.graphics.Color;

/** Raw ESC/POS control byte sequences used by the receipt builder. */
public final class EscPos {

    public static final byte[] INIT = {0x1B, 0x40};
    public static final byte[] ALIGN_LEFT = {0x1B, 0x61, 0x00};
    public static final byte[] ALIGN_CENTER = {0x1B, 0x61, 0x01};
    public static final byte[] BOLD_ON = {0x1B, 0x45, 0x01};
    public static final byte[] BOLD_OFF = {0x1B, 0x45, 0x00};
    public static final byte[] DOUBLE_ON = {0x1D, 0x21, 0x11};
    public static final byte[] DOUBLE_OFF = {0x1D, 0x21, 0x00};
    public static final byte[] DOUBLE_HEIGHT_ON = {0x1D, 0x21, 0x01};
    public static final byte[] FEED_CUT = {0x0A, 0x0A, 0x0A, 0x1D, 0x56, 0x42, 0x00};

    private EscPos() { }

    /**
     * Converts a bitmap into a GS v 0 raster block so the shop logo can be
     * printed on top of the slip. The image is scaled to the printer dot width
     * and thresholded to pure black and white.
     */
    public static byte[] raster(Bitmap source, int dotWidth) {
        if (source == null || dotWidth <= 0) return new byte[0];
        int targetWidth = Math.min(dotWidth, 576);
        targetWidth -= targetWidth % 8;
        if (targetWidth <= 0) return new byte[0];

        int targetHeight = Math.max(1, Math.round(
                source.getHeight() * (targetWidth / (float) source.getWidth())));
        targetHeight = Math.min(targetHeight, 320);
        Bitmap scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true);

        int bytesPerRow = targetWidth / 8;
        byte[] out = new byte[8 + bytesPerRow * targetHeight];
        out[0] = 0x1D; out[1] = 0x76; out[2] = 0x30; out[3] = 0x00;
        out[4] = (byte) (bytesPerRow & 0xFF);
        out[5] = (byte) ((bytesPerRow >> 8) & 0xFF);
        out[6] = (byte) (targetHeight & 0xFF);
        out[7] = (byte) ((targetHeight >> 8) & 0xFF);

        for (int y = 0; y < targetHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                int pixel = scaled.getPixel(x, y);
                int alpha = Color.alpha(pixel);
                int luminance = (int) (0.299 * Color.red(pixel)
                        + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel));
                boolean ink = alpha > 128 && luminance < 160;
                if (ink) {
                    int index = 8 + y * bytesPerRow + (x / 8);
                    out[index] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        if (scaled != source) scaled.recycle();
        return out;
    }
}
