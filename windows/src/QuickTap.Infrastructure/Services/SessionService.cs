using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using QuickTap.Core.Abstractions;
using QuickTap.Infrastructure.Api;

namespace QuickTap.Infrastructure.Services;

/// <summary>
/// Credentials are DPAPI-encrypted per Windows user; nothing sensitive is kept
/// in plain text on disk. Login never blocks on theme/config work — that runs
/// in the background exactly like the fixed Android SessionManager.
/// </summary>
public sealed class SessionService : ISessionService, ITokenProvider
{
    private readonly IApiClient _api;
    private readonly IRemoteConfigService _config;
    private readonly string _statePath;
    private string? _refreshToken;

    public SessionService(IApiClient api, IRemoteConfigService config, AppPaths paths)
    {
        _api = api;
        _config = config;
        _statePath = Path.Combine(paths.DataDirectory, "session.bin");
        DeviceId = LoadOrCreateDeviceId(paths);
        Load();
    }

    public string DeviceId { get; }
    public string? AccessToken { get; private set; }
    public bool IsAuthenticated => !string.IsNullOrEmpty(AccessToken);

    public async Task<bool> LoginAsync(string username, string password, CancellationToken ct = default)
    {
        var res = await _api.PostAsync("v1/auth/login",
            new { username, password, device_id = DeviceId, platform = "windows" }, authed: false, ct);
        if (!res.Success) return false;

        if (!TryReadTokens(res.RawBody)) return false;
        Save();

        // Fire-and-forget: theme/config refresh must never delay the shell.
        _ = Task.Run(() => _config.RefreshAsync(CancellationToken.None), CancellationToken.None);
        return true;
    }

    public async Task<bool> RefreshAsync(CancellationToken ct = default)
    {
        if (string.IsNullOrEmpty(_refreshToken)) return false;
        var res = await _api.PostAsync("v1/auth/refresh",
            new { refresh_token = _refreshToken, device_id = DeviceId }, authed: false, ct);
        if (!res.Success || !TryReadTokens(res.RawBody)) return false;
        Save();
        return true;
    }

    public Task LogoutAsync()
    {
        AccessToken = null;
        _refreshToken = null;
        if (File.Exists(_statePath)) File.Delete(_statePath);
        return Task.CompletedTask;
    }

    private bool TryReadTokens(string body)
    {
        try
        {
            var root = JsonDocument.Parse(body).RootElement;
            if (root.TryGetProperty("data", out var data)) root = data;
            AccessToken = root.TryGetProperty("access_token", out var a) ? a.GetString() : AccessToken;
            _refreshToken = root.TryGetProperty("refresh_token", out var r) ? r.GetString() : _refreshToken;
            return !string.IsNullOrEmpty(AccessToken);
        }
        catch { return false; }
    }

    private void Save()
    {
        var json = JsonSerializer.Serialize(new { AccessToken, Refresh = _refreshToken });
        File.WriteAllBytes(_statePath, Protect(json));
    }

    private void Load()
    {
        if (!File.Exists(_statePath)) return;
        try
        {
            var json = Unprotect(File.ReadAllBytes(_statePath));
            var root = JsonDocument.Parse(json).RootElement;
            AccessToken = root.GetProperty("AccessToken").GetString();
            _refreshToken = root.GetProperty("Refresh").GetString();
        }
        catch { /* corrupted state simply means "sign in again" */ }
    }

    private static byte[] Protect(string value) =>
        OperatingSystem.IsWindows()
            ? ProtectedData.Protect(Encoding.UTF8.GetBytes(value), null, DataProtectionScope.CurrentUser)
            : Encoding.UTF8.GetBytes(value);

    private static string Unprotect(byte[] raw) =>
        OperatingSystem.IsWindows()
            ? Encoding.UTF8.GetString(ProtectedData.Unprotect(raw, null, DataProtectionScope.CurrentUser))
            : Encoding.UTF8.GetString(raw);

    private static string LoadOrCreateDeviceId(AppPaths paths)
    {
        var file = Path.Combine(paths.DataDirectory, "device.id");
        if (File.Exists(file)) return File.ReadAllText(file).Trim();
        var id = "win-" + Guid.NewGuid().ToString("N");
        File.WriteAllText(file, id);
        return id;
    }
}

public sealed class AppPaths
{
    public AppPaths()
    {
        DataDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "QuickTapPOS");
        BackupDirectory = Path.Combine(DataDirectory, "backups");
        Directory.CreateDirectory(DataDirectory);
        Directory.CreateDirectory(BackupDirectory);
    }

    public string DataDirectory { get; }
    public string BackupDirectory { get; }
    public string DatabasePath => Path.Combine(DataDirectory, "quicktap.db");
}
