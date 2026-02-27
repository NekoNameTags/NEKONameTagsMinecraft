package uk.co.nekosunevr.nekonametags.core;

public final class TagEffects {
    private TagEffects() {
    }

    public static int rainbowRgb(long nowMs, int phaseOffset) {
        float hue = ((nowMs % 4000L) / 4000.0f + ((phaseOffset % 1000) / 1000.0f)) % 1.0f;
        return hsbToRgb(hue, 0.9f, 1.0f);
    }

    public static String animatedWindow(String text, long nowMs) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int len = text.length();
        int window = Math.min(6, len);
        int start = (int) ((nowMs / 120L) % len);
        int end = Math.min(len, start + window);
        if (start == 0 && end == len) {
            return text;
        }
        if (end > start) {
            return text.substring(start, end);
        }
        return text;
    }

    private static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0;
        int g = 0;
        int b = 0;
        if (saturation == 0) {
            r = g = b = Math.round(brightness * 255f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            switch ((int) h) {
                case 0:
                    r = Math.round(brightness * 255f);
                    g = Math.round(t * 255f);
                    b = Math.round(p * 255f);
                    break;
                case 1:
                    r = Math.round(q * 255f);
                    g = Math.round(brightness * 255f);
                    b = Math.round(p * 255f);
                    break;
                case 2:
                    r = Math.round(p * 255f);
                    g = Math.round(brightness * 255f);
                    b = Math.round(t * 255f);
                    break;
                case 3:
                    r = Math.round(p * 255f);
                    g = Math.round(q * 255f);
                    b = Math.round(brightness * 255f);
                    break;
                case 4:
                    r = Math.round(t * 255f);
                    g = Math.round(p * 255f);
                    b = Math.round(brightness * 255f);
                    break;
                default:
                    r = Math.round(brightness * 255f);
                    g = Math.round(p * 255f);
                    b = Math.round(q * 255f);
                    break;
            }
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
