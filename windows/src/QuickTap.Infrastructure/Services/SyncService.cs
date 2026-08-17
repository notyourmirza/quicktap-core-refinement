using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using QuickTap.Core.Abstractions;
using QuickTap.Core.Entities;
using QuickTap.Infrastructure.Data;

namespace QuickTap.Infrastructure.Services;

/// <summary>
/// Offline-first sync against the existing PHP endpoints
/// (<c>v1/sync/pull</c> / <c>v1/sync/push</c>).
///
/// Duplicate protection — the bug that plagued the Android build:
///  1. Uuid is the only upsert key and is unique in SQLite.
///  2. The pull cursor uses the <c>server_time</c> the server returns, never
///     the local clock, so clock skew cannot replay old rows.
///  3. Incoming rows are matched by Uuid first, then by natural key
///     (barcode / name) so a row created locally adopts the server identity
///     instead of becoming a second copy.
///  4. Rows are cleared of <c>Dirty</c> only for the uuids the server accepted.
/// </summary>
public sealed class SyncService : ISyncService
{
    private readonly IDbContextFactory<PosDbContext> _factory;
    private readonly IApiClient _api;
    private readonly ISettingsStore _settings;
    private readonly SemaphoreSlim _gate = new(1, 1);

    public SyncService(IDbContextFactory<PosDbContext> factory, IApiClient api, ISettingsStore settings)
    {
        _factory = factory;
        _api = api;
        _settings = settings;
    }

    public bool AutoSyncEnabled
    {
        get => _settings.GetBool("auto_sync", false);   // manual by default, as requested
        set => _settings.SetBool("auto_sync", value);
    }

    public DateTimeOffset? LastSyncAt
    {
        get
        {
            var v = _settings.GetLong("last_sync_at", 0);
            return v > 0 ? DateTimeOffset.FromUnixTimeMilliseconds(v) : null;
        }
    }

    public event EventHandler<SyncReport>? Completed;

    public async Task<SyncReport> SyncNowAsync(CancellationToken ct = default)
    {
        if (!await _gate.WaitAsync(0, ct))
            return new SyncReport(SyncOutcome.Skipped, 0, 0, 0, "Sync already running");

        try
        {
            if (!_api.IsOnline)
                return Finish(new SyncReport(SyncOutcome.Skipped, 0, 0, 0, "Offline — data stays queued"));

            var pushed = await PushAsync(ct);
            var (pulled, conflicts) = await PullAsync(ct);

            _settings.SetLong("last_sync_at", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
            var outcome = conflicts > 0 ? SyncOutcome.PartialConflict : SyncOutcome.Success;
            return Finish(new SyncReport(outcome, pushed, pulled, conflicts, null));
        }
        catch (Exception ex)
        {
            return Finish(new SyncReport(SyncOutcome.Failed, 0, 0, 0, ex.Message));
        }
        finally { _gate.Release(); }
    }

    private SyncReport Finish(SyncReport report)
    {
        Completed?.Invoke(this, report);
        return report;
    }

    // ------------------------------------------------------------------ //

    private async Task<int> PushAsync(CancellationToken ct)
    {
        await using var db = await _factory.CreateDbContextAsync(ct);

        var categories = await db.Categories.Where(x => x.Dirty).ToListAsync(ct);
        var products = await db.Products.Where(x => x.Dirty).ToListAsync(ct);
        var customers = await db.Customers.Where(x => x.Dirty).ToListAsync(ct);
        var expenses = await db.Expenses.Where(x => x.Dirty).ToListAsync(ct);
        var orders = await db.Bills.Include(b => b.Items).Where(x => x.Dirty).ToListAsync(ct);

        var total = categories.Count + products.Count + customers.Count + expenses.Count + orders.Count;
        if (total == 0) return 0;

        var payload = new
        {
            categories = categories.Select(c => new { c.Uuid, name = c.Name, sort_order = c.SortOrder, updated_at = c.UpdatedAt, deleted_at = c.Deleted ? c.UpdatedAt : (long?)null }),
            products = products.Select(p => new
            {
                p.Uuid,
                category_uuid = p.CategoryUuid,
                name = p.Name,
                sku = p.Sku,
                barcode = p.Barcode,
                price = p.Price,
                cost_price = p.CostPrice,
                stock = p.Stock < 0 ? 0 : p.Stock,
                track_stock = p.Stock >= 0,
                tax_percent = p.TaxPercent,
                is_active = p.Available,
                updated_at = p.UpdatedAt,
                deleted_at = p.Deleted ? p.UpdatedAt : (long?)null
            }),
            customers = customers.Select(c => new { c.Uuid, name = c.Name, c.Phone, c.Email, c.Address, c.Balance, updated_at = c.UpdatedAt, deleted_at = c.Deleted ? c.UpdatedAt : (long?)null }),
            expenses = expenses.Select(e => new { e.Uuid, title = e.Title, category = e.Category, amount = e.Amount, note = e.Note, spent_at = e.SpentAt, updated_at = e.UpdatedAt, deleted_at = e.Deleted ? e.UpdatedAt : (long?)null }),
            orders = orders.Select(o => new
            {
                o.Uuid,
                invoice_no = o.InvoiceNo,
                customer_uuid = o.CustomerUuid,
                subtotal = o.Subtotal,
                discount = o.Discount,
                tax = o.Tax,
                total = o.Total,
                paid = o.Paid,
                change_due = o.ChangeDue,
                payment_method = o.PaymentMethod,
                status = o.Status,
                note = o.Notes,
                ordered_at = o.CreatedAt,
                items = o.Items.Select(i => new
                {
                    product_uuid = i.ProductUuid,
                    name = i.Name,
                    qty = i.Qty,
                    unit_price = i.Price,
                    discount = i.Discount,
                    tax_percent = i.TaxPercent,
                    line_total = i.LineTotal
                })
            })
        };

        var res = await _api.PostAsync("v1/sync/push", payload, authed: true, ct);
        if (!res.Success) return 0;

        var accepted = ReadAccepted(res.RawBody);
        ClearDirty(categories, accepted, "categories");
        ClearDirty(products, accepted, "products");
        ClearDirty(customers, accepted, "customers");
        ClearDirty(expenses, accepted, "expenses");
        ClearDirty(orders, accepted, "orders");
        await db.SaveChangesAsync(ct);

        return accepted.Values.Sum(v => v.Count);
    }

    private static void ClearDirty<T>(IEnumerable<T> rows, IReadOnlyDictionary<string, HashSet<string>> accepted, string key)
        where T : SyncEntity
    {
        if (!accepted.TryGetValue(key, out var uuids)) return;
        foreach (var row in rows.Where(r => uuids.Contains(r.Uuid))) row.Dirty = false;
    }

    private static Dictionary<string, HashSet<string>> ReadAccepted(string body)
    {
        var map = new Dictionary<string, HashSet<string>>();
        try
        {
            var root = JsonDocument.Parse(body).RootElement;
            if (root.TryGetProperty("data", out var d)) root = d;
            if (!root.TryGetProperty("accepted", out var accepted)) return map;
            foreach (var entity in accepted.EnumerateObject())
            {
                var set = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                foreach (var u in entity.Value.EnumerateArray())
                    if (u.GetString() is { } s) set.Add(s);
                map[entity.Name] = set;
            }
        }
        catch { /* treat as nothing accepted; rows stay dirty and retry */ }
        return map;
    }

    // ------------------------------------------------------------------ //

    private async Task<(int pulled, int conflicts)> PullAsync(CancellationToken ct)
    {
        var since = _settings.GetLong("sync_cursor", 0);
        var res = await _api.GetAsync("v1/sync/pull",
            new Dictionary<string, string> { ["since"] = since.ToString() }, authed: true, ct);
        if (!res.Success) return (0, 0);

        JsonElement root;
        try
        {
            root = JsonDocument.Parse(res.RawBody).RootElement;
            if (root.TryGetProperty("data", out var d)) root = d;
        }
        catch { return (0, 0); }

        await using var db = await _factory.CreateDbContextAsync(ct);
        var pulled = 0;

        if (root.TryGetProperty("changes", out var changes))
        {
            pulled += await MergeCategoriesAsync(db, changes, ct);
            pulled += await MergeProductsAsync(db, changes, ct);
            pulled += await MergeCustomersAsync(db, changes, ct);
        }
        await db.SaveChangesAsync(ct);

        // Cursor comes from the SERVER clock — never from this machine.
        if (root.TryGetProperty("server_time", out var st) && st.TryGetInt64(out var serverTime))
            _settings.SetLong("sync_cursor", serverTime);

        return (pulled, 0);
    }

    private static async Task<int> MergeCategoriesAsync(PosDbContext db, JsonElement changes, CancellationToken ct)
    {
        if (!changes.TryGetProperty("categories", out var rows)) return 0;
        var n = 0;
        foreach (var r in rows.EnumerateArray())
        {
            var uuid = Str(r, "uuid");
            if (uuid is null) continue;
            var name = Str(r, "name") ?? "Unnamed";

            var local = await db.Categories.FirstOrDefaultAsync(c => c.Uuid == uuid, ct)
                        ?? await db.Categories.FirstOrDefaultAsync(c => c.Name == name, ct);

            local ??= AddNew(db.Categories, new Category { Uuid = uuid });
            local.Uuid = uuid;                     // identity adoption
            local.Name = name;
            local.SortOrder = Int(r, "sort_order");
            local.Deleted = Str(r, "deleted_at") is not null;
            local.Dirty = false;
            local.UpdatedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            n++;
        }
        return n;
    }

    private static async Task<int> MergeProductsAsync(PosDbContext db, JsonElement changes, CancellationToken ct)
    {
        if (!changes.TryGetProperty("products", out var rows)) return 0;
        var n = 0;
        foreach (var r in rows.EnumerateArray())
        {
            var uuid = Str(r, "uuid");
            if (uuid is null) continue;
            var name = Str(r, "name") ?? "Unnamed";
            var barcode = Str(r, "barcode");

            var local = await db.Products.FirstOrDefaultAsync(p => p.Uuid == uuid, ct);
            if (local is null && !string.IsNullOrEmpty(barcode))
                local = await db.Products.FirstOrDefaultAsync(p => p.Barcode == barcode, ct);
            local ??= await db.Products.FirstOrDefaultAsync(p => p.Name == name, ct);
            local ??= AddNew(db.Products, new Product { Uuid = uuid });

            local.Uuid = uuid;
            local.Name = name;
            local.Barcode = barcode;
            local.Sku = Str(r, "sku");
            local.CategoryUuid = Str(r, "category_uuid");
            local.Price = Dbl(r, "price");
            local.CostPrice = Dbl(r, "cost_price");
            local.TaxPercent = Dbl(r, "tax_percent");
            local.Stock = Bool(r, "track_stock") ? (int)Dbl(r, "stock") : -1;
            local.Available = Bool(r, "is_active");
            local.Deleted = Str(r, "deleted_at") is not null;
            local.Dirty = false;
            local.UpdatedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            n++;
        }
        return n;
    }

    private static async Task<int> MergeCustomersAsync(PosDbContext db, JsonElement changes, CancellationToken ct)
    {
        if (!changes.TryGetProperty("customers", out var rows)) return 0;
        var n = 0;
        foreach (var r in rows.EnumerateArray())
        {
            var uuid = Str(r, "uuid");
            if (uuid is null) continue;
            var phone = Str(r, "phone");

            var local = await db.Customers.FirstOrDefaultAsync(c => c.Uuid == uuid, ct);
            if (local is null && !string.IsNullOrEmpty(phone))
                local = await db.Customers.FirstOrDefaultAsync(c => c.Phone == phone, ct);
            local ??= AddNew(db.Customers, new Customer { Uuid = uuid });

            local.Uuid = uuid;
            local.Name = Str(r, "name") ?? "Walk-in";
            local.Phone = phone;
            local.Email = Str(r, "email");
            local.Address = Str(r, "address");
            local.Balance = Dbl(r, "balance");
            local.Deleted = Str(r, "deleted_at") is not null;
            local.Dirty = false;
            local.UpdatedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            n++;
        }
        return n;
    }

    private static T AddNew<T>(DbSet<T> set, T entity) where T : class
    {
        set.Add(entity);
        return entity;
    }

    private static string? Str(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind is not (JsonValueKind.Null or JsonValueKind.Undefined)
            ? v.ToString() : null;

    private static double Dbl(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && double.TryParse(v.ToString(), out var d) ? d : 0;

    private static int Int(JsonElement e, string name) => (int)Dbl(e, name);

    private static bool Bool(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind switch
        {
            JsonValueKind.True => true,
            JsonValueKind.False => false,
            _ => v.ToString() is "1" or "true"
        };
}
