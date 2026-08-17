using System.Collections.Concurrent;
using System.Text.Json;
using QuickTap.Core.Abstractions;

namespace QuickTap.Infrastructure.Services;

/// <summary>JSON-backed preferences, the Windows twin of AppPrefs.</summary>
public sealed class SettingsStore : ISettingsStore
{
    private readonly string _path;
    private readonly ConcurrentDictionary<string, string> _values;
    private readonly object _writeLock = new();

    public SettingsStore(AppPaths paths)
    {
        _path = Path.Combine(paths.DataDirectory, "settings.json");
        _values = Load(_path);
    }

    public string? GetString(string key, string? fallback = null) =>
        _values.TryGetValue(key, out var v) ? v : fallback;

    public void SetString(string key, string? value)
    {
        if (value is null) _values.TryRemove(key, out _);
        else _values[key] = value;
        Persist();
    }

    public bool GetBool(string key, bool fallback = false) =>
        _values.TryGetValue(key, out var v) ? v is "1" or "true" : fallback;

    public void SetBool(string key, bool value) => SetString(key, value ? "1" : "0");

    public long GetLong(string key, long fallback = 0) =>
        _values.TryGetValue(key, out var v) && long.TryParse(v, out var l) ? l : fallback;

    public void SetLong(string key, long value) => SetString(key, value.ToString());

    private void Persist()
    {
        lock (_writeLock)
        {
            File.WriteAllText(_path, JsonSerializer.Serialize(_values));
        }
    }

    private static ConcurrentDictionary<string, string> Load(string path)
    {
        try
        {
            if (File.Exists(path))
            {
                var map = JsonSerializer.Deserialize<Dictionary<string, string>>(File.ReadAllText(path));
                if (map is not null) return new ConcurrentDictionary<string, string>(map);
            }
        }
        catch { /* start from defaults */ }
        return new ConcurrentDictionary<string, string>();
    }
}
