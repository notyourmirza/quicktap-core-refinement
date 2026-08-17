namespace QuickTap.Core.Theming;

/// <summary>
/// Exact port of com.quicktap.pos.theme.ThemePresets — same keys, same colours,
/// so a theme published by the Super Admin renders identically on Android and
/// Windows.
/// </summary>
public sealed record ThemePreset(
    string Key,
    string Name,
    string Primary,
    string Secondary,
    string Background,
    string Surface,
    string SurfaceMuted,
    string Outline,
    string TextPrimary,
    string TextMuted,
    string HeroStart,
    string HeroEnd,
    int CornerRadius,
    int Elevation,
    int StrokeWidth,
    bool Dark,
    string[] Chart);

public static class ThemePresets
{
    public static readonly ThemePreset Luxe = new(
        "quicktap_luxe", "QuickTap Luxe", "#0E9F6E", "#34D399",
        "#F6F8FA", "#FFFFFF", "#F1F4F8", "#E6EAF1",
        "#0A0F1C", "#6C7689", "#0A0F1C", "#16233A",
        22, 0, 1, false,
        ["#0E9F6E", "#34D399", "#2E90FA", "#F5A524", "#7A5AF8"]);

    public static IReadOnlyList<ThemePreset> All { get; } =
    [
        Luxe,
        new("material_you", "Material You", "#6750A4", "#7D5260",
            "#FEF7FF", "#FFFFFF", "#F3EDF7", "#E7E0EC",
            "#1D1B20", "#625B71", "#6750A4", "#7F67BE", 24, 0, 0, false,
            ["#6750A4", "#7D5260", "#B58392", "#8E9AAF", "#4A4458"]),

        new("minimal_luxury", "Minimal Luxury", "#B08D3F", "#D9BE73",
            "#FBFAF7", "#FFFFFF", "#F5F2EA", "#E8E2D4",
            "#1A1814", "#8A8172", "#1A1814", "#3A342A", 18, 1, 1, false,
            ["#B08D3F", "#D9BE73", "#8A8172", "#1A1814", "#E8E2D4"]),

        new("glassmorphism", "Glassmorphism", "#5B8DEF", "#8E7CF5",
            "#EEF3FF", "#FFFFFF", "#E7EEFC", "#D6E1F7",
            "#16233A", "#5A6B87", "#5B8DEF", "#8E7CF5", 28, 0, 1, false,
            ["#5B8DEF", "#8E7CF5", "#4ECDC4", "#F49AC2", "#9BB8F0"]),

        new("neo_banking", "Neo Banking", "#1B4DFF", "#3E7BFA",
            "#F5F7FC", "#FFFFFF", "#EDF1FA", "#DFE5F2",
            "#0A1330", "#5D6B8A", "#0A1330", "#1B4DFF", 20, 2, 0, false,
            ["#1B4DFF", "#3E7BFA", "#00C48C", "#FFB020", "#7A8CFF"]),

        new("dark_pro", "Dark Pro", "#00E5A0", "#38BDF8",
            "#000000", "#0B0F12", "#12181C", "#1E262B",
            "#F2F6F8", "#8A9AA4", "#00E5A0", "#38BDF8", 18, 0, 1, true,
            ["#00E5A0", "#38BDF8", "#A855F7", "#FACC15", "#FB7185"]),

        new("modern_retail", "Modern Retail", "#FF5A1F", "#FFB020",
            "#FFFFFF", "#FFFFFF", "#F6F7F9", "#E6E8EC",
            "#131720", "#6B7280", "#FF5A1F", "#FF8A3D", 14, 0, 1, false,
            ["#FF5A1F", "#FFB020", "#12B76A", "#2E90FA", "#7A5AF8"]),

        new("elegant_business", "Elegant Business", "#12386B", "#2C6EB5",
            "#F7F9FC", "#FFFFFF", "#EEF2F8", "#DDE4EE",
            "#0C1A2B", "#5A6B80", "#0C1A2B", "#12386B", 16, 1, 1, false,
            ["#12386B", "#2C6EB5", "#5FA8D3", "#93B7BE", "#C0A062"]),

        new("soft_pastel", "Soft Pastel", "#8E7CF5", "#7FD1C1",
            "#FBF7FF", "#FFFFFF", "#F4EFFB", "#EADFF5",
            "#3A3350", "#7C7391", "#B8A9F7", "#9FE0D3", 26, 0, 0, false,
            ["#B8A9F7", "#9FE0D3", "#FFC7D6", "#FFE0A3", "#A9C9F7"]),

        new("black_gold", "Premium Black & Gold", "#D4AF37", "#F0DB8C",
            "#0A0A0A", "#121212", "#181818", "#2A2419",
            "#F7F3E8", "#9C9483", "#D4AF37", "#8C6F1F", 16, 0, 1, true,
            ["#D4AF37", "#F0DB8C", "#8C6F1F", "#EFE7D2", "#5A4A1E"]),

        new("futuristic_ai", "Futuristic AI", "#7C3AED", "#22D3EE",
            "#070A18", "#0E1328", "#151B33", "#242C4A",
            "#EAF0FF", "#8C97BD", "#7C3AED", "#22D3EE", 22, 0, 1, true,
            ["#7C3AED", "#22D3EE", "#F472B6", "#34D399", "#FBBF24"]),
    ];

    public static ThemePreset ByKey(string? key) =>
        All.FirstOrDefault(p => string.Equals(p.Key, key, StringComparison.OrdinalIgnoreCase)) ?? Luxe;
}
