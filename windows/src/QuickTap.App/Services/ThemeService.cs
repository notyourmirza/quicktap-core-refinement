using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using QuickTap.Core.Abstractions;
using QuickTap.Core.Theming;
using Windows.UI;

namespace QuickTap.App.Services;

/// <summary>
/// Applies the preset published by the Super Admin plus the local light/dark
/// preference. Colours flow into the brand brushes used across every page.
/// </summary>
public sealed class ThemeService
{
    private readonly IRemoteConfigService _config;
    private readonly ISettingsStore _settings;

    public ThemeService(IRemoteConfigService config, ISettingsStore settings)
    {
        _config = config;
        _settings = settings;
        _config.Changed += (_, _) => Apply();
    }

    /// <summary>system | light | dark</summary>
    public string Mode
    {
        get => _settings.GetString("theme_mode", "system") ?? "system";
        set { _settings.SetString("theme_mode", value); Apply(); }
    }

    public ThemePreset Current => ThemePresets.ByKey(_config.ThemeKey);

    public void Apply()
    {
        var window = App.Shell;
        if (window?.Content is not FrameworkElement root) return;

        root.DispatcherQueue.TryEnqueue(() =>
        {
            root.RequestedTheme = Mode switch
            {
                "light" => ElementTheme.Light,
                "dark" => ElementTheme.Dark,
                _ => ElementTheme.Default
            };

            var preset = Current;
            var resources = Application.Current.Resources;
            resources["BrandPrimaryColor"] = Parse(preset.Primary);
            resources["BrandSecondaryColor"] = Parse(preset.Secondary);
            resources["BrandPrimaryBrush"] = new SolidColorBrush(Parse(preset.Primary));
            resources["BrandSecondaryBrush"] = new SolidColorBrush(Parse(preset.Secondary));
            resources["CardCornerRadius"] = new CornerRadius(preset.CornerRadius);
        });
    }

    public static Color Parse(string hex)
    {
        hex = hex.TrimStart('#');
        if (hex.Length == 6) hex = "FF" + hex;
        if (hex.Length != 8 || !uint.TryParse(hex, System.Globalization.NumberStyles.HexNumber, null, out var v))
            return Colors.SeaGreen;
        return Color.FromArgb((byte)(v >> 24), (byte)(v >> 16), (byte)(v >> 8), (byte)v);
    }
}
