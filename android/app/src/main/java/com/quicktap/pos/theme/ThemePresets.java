package com.quicktap.pos.theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Ten complete premium design languages.
 *
 * <p>A preset is not just an accent colour: it carries the full surface stack,
 * ink colours, outline, corner radius, elevation, hero gradient and chart
 * palette, so switching a key repaints the entire application without any
 * change to navigation or business logic.
 *
 * <p>Only the Super Admin panel can publish a key (themes.theme_key →
 * /v1/theme); the device caches the last one so the brand survives offline.
 */
public final class ThemePresets {

    public static final class Preset {
        public final String key;
        public final String name;
        public final String primary;
        public final String secondary;
        public final String background;
        public final String surface;
        public final String surfaceMuted;
        public final String outline;
        public final String textPrimary;
        public final String textMuted;
        public final String heroStart;
        public final String heroEnd;
        public final int cornerRadiusDp;
        public final int elevationDp;
        public final int strokeWidthDp;
        public final boolean dark;
        public final String[] chart;

        Preset(String key, String name, String primary, String secondary,
               String background, String surface, String surfaceMuted, String outline,
               String textPrimary, String textMuted, String heroStart, String heroEnd,
               int cornerRadiusDp, int elevationDp, int strokeWidthDp, boolean dark,
               String[] chart) {
            this.key = key;
            this.name = name;
            this.primary = primary;
            this.secondary = secondary;
            this.background = background;
            this.surface = surface;
            this.surfaceMuted = surfaceMuted;
            this.outline = outline;
            this.textPrimary = textPrimary;
            this.textMuted = textMuted;
            this.heroStart = heroStart;
            this.heroEnd = heroEnd;
            this.cornerRadiusDp = cornerRadiusDp;
            this.elevationDp = elevationDp;
            this.strokeWidthDp = strokeWidthDp;
            this.dark = dark;
            this.chart = chart;
        }
    }

    private static final List<Preset> ALL = new ArrayList<>();

    /**
     * The single production design language. The Super Admin no longer switches
     * layout languages — only the brand colours travel from the panel — so the
     * app always renders this one hand-tuned luxury system.
     */
    public static final Preset LUXE = new Preset("quicktap_luxe", "QuickTap Luxe",
            "#F97316", "#EA580C",
            "#FFFFFF", "#FFFFFF", "#F4F6F9", "#DDE2EA",
            "#0B0F14", "#5B6675", "#0B0F14", "#1B222C",
            10, 0, 1, false,
            new String[]{"#F97316", "#EA580C", "#0B0F14", "#2E90FA", "#0E9F6E"});

    static {
        ALL.add(LUXE);

        // 1. Material You — clean, dynamic, minimal.
        ALL.add(new Preset("material_you", "Material You",
                "#6750A4", "#7D5260",
                "#FEF7FF", "#FFFFFF", "#F3EDF7", "#E7E0EC",
                "#1D1B20", "#625B71", "#6750A4", "#7F67BE",
                24, 0, 0, false,
                new String[]{"#6750A4", "#7D5260", "#B58392", "#8E9AAF", "#4A4458"}));

        // 2. Minimal Luxury — elegant white with gold.
        ALL.add(new Preset("minimal_luxury", "Minimal Luxury",
                "#B08D3F", "#D9BE73",
                "#FBFAF7", "#FFFFFF", "#F5F2EA", "#E8E2D4",
                "#1A1814", "#8A8172", "#1A1814", "#3A342A",
                18, 1, 1, false,
                new String[]{"#B08D3F", "#D9BE73", "#8A8172", "#1A1814", "#E8E2D4"}));

        // 3. Glassmorphism — frosted, translucent, soft gradients.
        ALL.add(new Preset("glassmorphism", "Glassmorphism",
                "#5B8DEF", "#8E7CF5",
                "#EEF3FF", "#FFFFFF", "#E7EEFC", "#D6E1F7",
                "#16233A", "#5A6B87", "#5B8DEF", "#8E7CF5",
                28, 0, 1, false,
                new String[]{"#5B8DEF", "#8E7CF5", "#4ECDC4", "#F49AC2", "#9BB8F0"}));

        // 4. Neo Banking — professional finance blue.
        ALL.add(new Preset("neo_banking", "Neo Banking",
                "#1B4DFF", "#3E7BFA",
                "#F5F7FC", "#FFFFFF", "#EDF1FA", "#DFE5F2",
                "#0A1330", "#5D6B8A", "#0A1330", "#1B4DFF",
                20, 2, 0, false,
                new String[]{"#1B4DFF", "#3E7BFA", "#00C48C", "#FFB020", "#7A8CFF"}));

        // 5. Dark Pro — AMOLED black with neon.
        ALL.add(new Preset("dark_pro", "Dark Pro",
                "#00E5A0", "#38BDF8",
                "#000000", "#0B0F12", "#12181C", "#1E262B",
                "#F2F6F8", "#8A9AA4", "#00E5A0", "#38BDF8",
                18, 0, 1, true,
                new String[]{"#00E5A0", "#38BDF8", "#A855F7", "#FACC15", "#FB7185"}));

        // 6. Modern Retail — bright, product focused.
        ALL.add(new Preset("modern_retail", "Modern Retail",
                "#FF5A1F", "#FFB020",
                "#FFFFFF", "#FFFFFF", "#F6F7F9", "#E6E8EC",
                "#131720", "#6B7280", "#FF5A1F", "#FF8A3D",
                14, 0, 1, false,
                new String[]{"#FF5A1F", "#FFB020", "#12B76A", "#2E90FA", "#7A5AF8"}));

        // 7. Elegant Business — corporate premium blue.
        ALL.add(new Preset("elegant_business", "Elegant Business",
                "#12386B", "#2C6EB5",
                "#F7F9FC", "#FFFFFF", "#EEF2F8", "#DDE4EE",
                "#0C1A2B", "#5A6B80", "#0C1A2B", "#12386B",
                16, 1, 1, false,
                new String[]{"#12386B", "#2C6EB5", "#5FA8D3", "#93B7BE", "#C0A062"}));

        // 8. Soft Pastel — light, friendly, soft shadows.
        ALL.add(new Preset("soft_pastel", "Soft Pastel",
                "#8E7CF5", "#7FD1C1",
                "#FBF7FF", "#FFFFFF", "#F4EFFB", "#EADFF5",
                "#3A3350", "#7C7391", "#B8A9F7", "#9FE0D3",
                26, 0, 0, false,
                new String[]{"#B8A9F7", "#9FE0D3", "#FFC7D6", "#FFE0A3", "#A9C9F7"}));

        // 9. Premium Black & Gold — luxury high-end.
        ALL.add(new Preset("black_gold", "Premium Black & Gold",
                "#D4AF37", "#F0DB8C",
                "#0A0A0A", "#121212", "#181818", "#2A2419",
                "#F7F3E8", "#9C9483", "#D4AF37", "#8C6F1F",
                16, 0, 1, true,
                new String[]{"#D4AF37", "#F0DB8C", "#8C6F1F", "#EFE7D2", "#5A4A1E"}));

        // 10. Futuristic AI — neon gradients, analytics first.
        ALL.add(new Preset("futuristic_ai", "Futuristic AI",
                "#7C3AED", "#22D3EE",
                "#070A18", "#0E1328", "#151B33", "#242C4A",
                "#EAF0FF", "#8C97BD", "#7C3AED", "#22D3EE",
                22, 0, 1, true,
                new String[]{"#7C3AED", "#22D3EE", "#F472B6", "#34D399", "#FBBF24"}));
    }

    private ThemePresets() { }

    public static List<Preset> all() { return ALL; }

    /**
     * The design language is locked to the QuickTap Luxe system; the admin panel
     * only publishes brand colours, so every key resolves to the same preset.
     */
    public static Preset byKey(String key) {
        return LUXE;
    }
}
