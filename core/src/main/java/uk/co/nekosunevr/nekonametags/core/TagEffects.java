package uk.co.nekosunevr.nekonametags.core;

public final class TagEffects {
    private static final long RAINBOW_CYCLE_MS = 2200L;
    private static final long ANIMATION_STEP_MS = 240L;

    private TagEffects() {
    }

    public static int rainbowRgb(long nowMs, int phaseOffset) {
        float hue = ((nowMs % RAINBOW_CYCLE_MS) / (float) RAINBOW_CYCLE_MS + ((phaseOffset % 1000) / 1000.0f)) % 1.0f;
        return hsbToRgb(hue, 0.9f, 1.0f);
    }

    public static String animatedWindow(String text, long nowMs) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int len = text.length();
        if (len == 1) {
            return text;
        }

        int cycle = (len * 2) - 2;
        int step = (int) ((nowMs / ANIMATION_STEP_MS) % cycle);
        int count;
        if (step < len) {
            count = step + 1;
        } else {
            count = len - (step - len + 1);
        }
        if (count < 1) {
            count = 1;
        }
        return text.substring(0, Math.min(count, len));
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
