using System.Net.Http.Json;
using System.Net.NetworkInformation;
using System.Text;
using System.Text.Json;
using Microsoft.Extensions.Options;
using QuickTap.Core.Abstractions;

namespace QuickTap.Infrastructure.Api;

public sealed class ApiOptions
{
    /// <summary>Same host the Android build talks to.</summary>
    public string BaseUrl { get; set; } = "https://mediumaquamarine-baboon-263984.hostingersite.com/api/";
    public string AppId { get; set; } = string.Empty;
    public string ApiKey { get; set; } = string.Empty;
    public string ApiSecret { get; set; } = string.Empty;
    public int TimeoutSeconds { get; set; } = 20;
}

/// <summary>
/// Port of com.quicktap.pos.net.ApiClient: app credential headers, device
/// fingerprint, bearer token plus one transparent refresh on 401.
/// </summary>
public sealed class ApiClient : IApiClient
{
    private readonly HttpClient _http;
    private readonly ApiOptions _options;
    private readonly ITokenProvider _tokens;

    public ApiClient(HttpClient http, IOptions<ApiOptions> options, ITokenProvider tokens)
    {
        _options = options.Value;
        _tokens = tokens;
        _http = http;
        _http.BaseAddress = new Uri(_options.BaseUrl);
        _http.Timeout = TimeSpan.FromSeconds(_options.TimeoutSeconds);
    }

    public bool IsOnline
    {
        get
        {
            try { return NetworkInterface.GetIsNetworkAvailable(); }
            catch { return true; } // never block a request because the probe failed
        }
    }

    public Task<ApiResult> GetAsync(string path, IDictionary<string, string>? query = null, bool authed = true, CancellationToken ct = default)
    {
        if (query is { Count: > 0 })
        {
            var qs = string.Join("&", query.Select(kv =>
                $"{Uri.EscapeDataString(kv.Key)}={Uri.EscapeDataString(kv.Value)}"));
            path += (path.Contains('?') ? "&" : "?") + qs;
        }
        return SendAsync(HttpMethod.Get, path, null, authed, true, ct);
    }

    public Task<ApiResult> PostAsync(string path, object? body = null, bool authed = true, CancellationToken ct = default)
        => SendAsync(HttpMethod.Post, path, body, authed, true, ct);

    public Task<ApiResult> DeleteAsync(string path, bool authed = true, CancellationToken ct = default)
        => SendAsync(HttpMethod.Delete, path, null, authed, true, ct);

    private async Task<ApiResult> SendAsync(HttpMethod method, string path, object? body, bool authed, bool allowRefresh, CancellationToken ct)
    {
        if (!IsOnline) return ApiResult.Offline();

        using var req = new HttpRequestMessage(method, path.TrimStart('/'));
        req.Headers.TryAddWithoutValidation("X-App-Id", _options.AppId);
        req.Headers.TryAddWithoutValidation("X-Api-Key", _options.ApiKey);
        req.Headers.TryAddWithoutValidation("X-Api-Secret", _options.ApiSecret);
        req.Headers.TryAddWithoutValidation("X-Device-Id", _tokens.DeviceId);
        req.Headers.TryAddWithoutValidation("X-Platform", "windows");

        if (authed && !string.IsNullOrEmpty(_tokens.AccessToken))
            req.Headers.TryAddWithoutValidation("Authorization", "Bearer " + _tokens.AccessToken);

        if (body is not null)
            req.Content = new StringContent(JsonSerializer.Serialize(body), Encoding.UTF8, "application/json");

        try
        {
            using var res = await _http.SendAsync(req, ct);
            var raw = await res.Content.ReadAsStringAsync(ct);

            if (res.StatusCode == System.Net.HttpStatusCode.Unauthorized && authed && allowRefresh
                && await _tokens.RefreshAsync(ct))
            {
                return await SendAsync(method, path, body, authed, false, ct);
            }

            string? message = null;
            try { message = JsonDocument.Parse(raw).RootElement.TryGetProperty("message", out var m) ? m.GetString() : null; }
            catch { /* non-JSON body */ }

            return new ApiResult(true, res.IsSuccessStatusCode, (int)res.StatusCode, raw, message);
        }
        catch (Exception ex)
        {
            return new ApiResult(false, false, 0, string.Empty, ex.Message);
        }
    }
}

public interface ITokenProvider
{
    string DeviceId { get; }
    string? AccessToken { get; }
    Task<bool> RefreshAsync(CancellationToken ct = default);
}
