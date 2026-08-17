using System.Text.Json;
using QuickTap.Core.Abstractions;

namespace QuickTap.Infrastructure.Services;

/// <summary>
/// Mirrors RemoteTheme/ConfigController on Android: theme, receipt template,
/// white-label branding, feature flags, WhatsApp number and payment gateways
/// all come from the same Super Admin panel, and are cached so the desktop app
/// still boots correctly offline.
/// </summary>
public sealed class RemoteConfigService : IRemoteConfigService
{
    private readonly IApiClient _api;
    private readonly ISettingsStore _settings;
    private Dictionary<string, string> _values = new(StringComparer.OrdinalIgnoreCase);
    private Dictionary<string, bool> _features = new(StringComparer.OrdinalIgnoreCase);

    public RemoteConfigService(IApiClient api, ISettingsStore settings)
    {
        _api = api;
        _settings = settings;
        LoadCache();
    }

    public string? ThemeKey => Get("theme_key") ?? "quicktap_luxe";
    public string? ReceiptTemplateKey => Get("receipt_template") ?? "classic";
    public string? WhatsAppNumber => Get("whatsapp_number") ?? Get("support_whatsapp");
    public IReadOnlyDictionary<string, string> Settings => _values;
    public IReadOnlyDictionary<string, bool> Features => _features;

    public event EventHandler? Changed;

    public bool IsEnabled(string moduleKey) =>
        !_features.TryGetValue(moduleKey, out var on) || on; // unknown modules default to on

    public async Task RefreshAsync(CancellationToken ct = default)
    {
        var res = await _api.GetAsync("v1/config", authed: true, ct: ct);
        if (!res.Success) return;

        try
        {
            var root = JsonDocument.Parse(res.RawBody).RootElement;
            if (root.TryGetProperty("data", out var d)) root = d;

            var values = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            var features = new Dictionary<string, bool>(StringComparer.OrdinalIgnoreCase);

            Flatten(root, "settings", values);
            Flatten(root, "theme", values);
            Flatten(root, "branding", values);

            if (root.TryGetProperty("features", out var f) && f.ValueKind == JsonValueKind.Object)
            {
                foreach (var p in f.EnumerateObject())
                    features[p.Name] = p.Value.ValueKind switch
                    {
                        JsonValueKind.True => true,
                        JsonValueKind.False => false,
                        _ => p.Value.ToString() is "1" or "true"
                    };
            }

            _values = values;
            _features = features;
            _settings.SetString("config_cache", JsonSerializer.Serialize(new { values, features }));
            Changed?.Invoke(this, EventArgs.Empty);
        }
        catch { /* keep the cached config */ }
    }

    private static void Flatten(JsonElement root, string node, IDictionary<string, string> into)
    {
        if (!root.TryGetProperty(node, out var obj) || obj.ValueKind != JsonValueKind.Object) return;
        foreach (var p in obj.EnumerateObject())
            if (p.Value.ValueKind is not (JsonValueKind.Object or JsonValueKind.Array))
                into[p.Name] = p.Value.ToString();
    }

    private string? Get(string key) => _values.TryGetValue(key, out var v) && !string.IsNullOrWhiteSpace(v) ? v : null;

    private void LoadCache()
    {
        var raw = _settings.GetString("config_cache");
        if (string.IsNullOrEmpty(raw)) return;
        try
        {
            var root = JsonDocument.Parse(raw).RootElement;
            if (root.TryGetProperty("values", out var v))
                _values = JsonSerializer.Deserialize<Dictionary<string, string>>(v.GetRawText()) ?? _values;
            if (root.TryGetProperty("features", out var f))
                _features = JsonSerializer.Deserialize<Dictionary<string, bool>>(f.GetRawText()) ?? _features;
        }
        catch { /* ignore corrupt cache */ }
    }
}
