using QuickTap.Core.Entities;

namespace QuickTap.Core.Abstractions;

public interface IRepository<T> where T : SyncEntity
{
    Task<IReadOnlyList<T>> ListAsync(CancellationToken ct = default);
    Task<T?> GetAsync(long id, CancellationToken ct = default);
    Task<T?> GetByUuidAsync(string uuid, CancellationToken ct = default);
    Task<T> UpsertAsync(T entity, CancellationToken ct = default);
    /// <summary>Soft delete so the removal replicates to Android devices.</summary>
    Task SoftDeleteAsync(long id, CancellationToken ct = default);
    Task<IReadOnlyList<T>> DirtyAsync(CancellationToken ct = default);
}

public interface IProductRepository : IRepository<Product>
{
    Task<IReadOnlyList<Product>> SearchAsync(string term, long? categoryId, CancellationToken ct = default);
    Task<Product?> ByBarcodeAsync(string barcode, CancellationToken ct = default);
    Task AdjustStockAsync(long productId, int delta, CancellationToken ct = default);
}

public interface IBillRepository : IRepository<Bill>
{
    Task<Bill> SaveBillAsync(Bill bill, IEnumerable<BillItem> items, CancellationToken ct = default);
    Task<IReadOnlyList<Bill>> BetweenAsync(DateTimeOffset from, DateTimeOffset to, CancellationToken ct = default);
    Task<IReadOnlyList<BillItem>> ItemsOfAsync(long billId, CancellationToken ct = default);
    Task<string> NextInvoiceNoAsync(CancellationToken ct = default);
}

public record ApiResult(bool NetworkOk, bool Success, int StatusCode, string RawBody, string? Message)
{
    public static ApiResult Offline() => new(false, false, 0, string.Empty, "No connection");
}

public interface IApiClient
{
    bool IsOnline { get; }
    Task<ApiResult> GetAsync(string path, IDictionary<string, string>? query = null, bool authed = true, CancellationToken ct = default);
    Task<ApiResult> PostAsync(string path, object? body = null, bool authed = true, CancellationToken ct = default);
    Task<ApiResult> DeleteAsync(string path, bool authed = true, CancellationToken ct = default);
}

public interface ISessionService
{
    bool IsAuthenticated { get; }
    string? AccessToken { get; }
    string DeviceId { get; }
    Task<bool> LoginAsync(string username, string password, CancellationToken ct = default);
    Task<bool> RefreshAsync(CancellationToken ct = default);
    Task LogoutAsync();
}

/// <summary>Local key/value preferences — the AppPrefs equivalent.</summary>
public interface ISettingsStore
{
    string? GetString(string key, string? fallback = null);
    void SetString(string key, string? value);
    bool GetBool(string key, bool fallback = false);
    void SetBool(string key, bool value);
    long GetLong(string key, long fallback = 0);
    void SetLong(string key, long value);
}

public enum SyncOutcome { Skipped, Success, PartialConflict, Failed }

public record SyncReport(SyncOutcome Outcome, int Pushed, int Pulled, int Conflicts, string? Message);

public interface ISyncService
{
    bool AutoSyncEnabled { get; set; }
    DateTimeOffset? LastSyncAt { get; }
    event EventHandler<SyncReport>? Completed;
    Task<SyncReport> SyncNowAsync(CancellationToken ct = default);
}

public interface IBackupService
{
    /// <summary>Weekly cadence: writes a new archive and deletes the previous one.</summary>
    Task<string> RunWeeklyBackupAsync(CancellationToken ct = default);
    Task RestoreAsync(string archivePath, CancellationToken ct = default);
}

public interface IReceiptPrinter
{
    Task<IReadOnlyList<string>> ListPrintersAsync();
    Task PrintAsync(string rendered, string? printerName = null, CancellationToken ct = default);
}

public interface IBarcodeScanner
{
    /// <summary>Raised for both USB HID barcode guns and camera/QR decoding.</summary>
    event EventHandler<string>? CodeScanned;
    void Start();
    void Stop();
}

public interface IRemoteConfigService
{
    string? ThemeKey { get; }
    string? ReceiptTemplateKey { get; }
    IReadOnlyDictionary<string, string> Settings { get; }
    IReadOnlyDictionary<string, bool> Features { get; }
    string? WhatsAppNumber { get; }
    event EventHandler? Changed;
    Task RefreshAsync(CancellationToken ct = default);
    bool IsEnabled(string moduleKey);
}

public interface IAnalyticsService
{
    Task<IReadOnlyDictionary<string, double>> DaySummaryAsync(DateTimeOffset day, CancellationToken ct = default);
    Task<string> GenerateAiReportAsync(DateTimeOffset from, DateTimeOffset to, CancellationToken ct = default);
}
