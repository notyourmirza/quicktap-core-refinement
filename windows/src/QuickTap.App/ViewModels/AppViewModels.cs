using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using QuickTap.Core.Abstractions;
using QuickTap.Core.Entities;

namespace QuickTap.App.ViewModels;

public partial class ShellViewModel : ObservableObject
{
    private readonly ISyncService _sync;
    private readonly IApiClient _api;
    private readonly IRemoteConfigService _config;
    private readonly ISessionService _session;

    public ShellViewModel(ISyncService sync, IApiClient api, IRemoteConfigService config, ISessionService session)
    {
        _sync = sync;
        _api = api;
        _config = config;
        _session = session;
        _sync.Completed += (_, r) => SyncStatus = Describe(r);
    }

    [ObservableProperty] private string syncStatus = "Not synced yet";
    [ObservableProperty] private bool syncing;

    public string ShopName => _config.Settings.TryGetValue("store_name", out var n) ? n : AppConfig.ProductName;
    public bool IsOnline => _api.IsOnline;
    public string Credit => AppConfig.Credit;

    public bool IsModuleEnabled(string key) => _config.IsEnabled(key);

    [RelayCommand]
    private async Task SyncNowAsync()
    {
        if (Syncing) return;
        Syncing = true;
        SyncStatus = "Syncing…";
        try { SyncStatus = Describe(await _sync.SyncNowAsync()); }
        finally { Syncing = false; }
    }

    [RelayCommand]
    private async Task SignOutAsync()
    {
        await _session.LogoutAsync();
        App.Shell?.ShowLogin();
    }

    private static string Describe(SyncReport r) => r.Outcome switch
    {
        SyncOutcome.Success => $"Synced • {r.Pushed} sent, {r.Pulled} received",
        SyncOutcome.PartialConflict => $"Synced with {r.Conflicts} conflicts",
        SyncOutcome.Skipped => r.Message ?? "Nothing to sync",
        _ => "Sync failed: " + (r.Message ?? "unknown error")
    };
}

public partial class DashboardViewModel : ObservableObject
{
    private readonly IAnalyticsService _analytics;
    private readonly IBillRepository _bills;

    public DashboardViewModel(IAnalyticsService analytics, IBillRepository bills)
    {
        _analytics = analytics;
        _bills = bills;
    }

    public ObservableCollection<Bill> RecentBills { get; } = new();

    [ObservableProperty] private double revenue;
    [ObservableProperty] private double orders;
    [ObservableProperty] private double averageTicket;
    [ObservableProperty] private double unpaidAmount;
    [ObservableProperty] private double expenses;
    [ObservableProperty] private double net;

    [RelayCommand]
    public async Task LoadAsync()
    {
        var s = await _analytics.DaySummaryAsync(DateTimeOffset.Now);
        Revenue = s["revenue"];
        Orders = s["orders"];
        AverageTicket = s["average_ticket"];
        UnpaidAmount = s["unpaid_amount"];
        Expenses = s["expenses"];
        Net = s["net"];

        var today = DateTimeOffset.Now;
        var rows = await _bills.BetweenAsync(today.AddDays(-7), today);
        RecentBills.Clear();
        foreach (var b in rows.Take(25)) RecentBills.Add(b);
    }
}

public partial class ProductsViewModel : ObservableObject
{
    private readonly IProductRepository _products;
    private readonly IRepository<Category> _categories;

    public ProductsViewModel(IProductRepository products, IRepository<Category> categories)
    {
        _products = products;
        _categories = categories;
    }

    public ObservableCollection<Product> Items { get; } = new();
    public ObservableCollection<Category> Categories { get; } = new();

    [ObservableProperty] private string search = string.Empty;
    [ObservableProperty] private Product? selected;

    partial void OnSearchChanged(string value) => _ = LoadAsync();

    [RelayCommand]
    public async Task LoadAsync()
    {
        Categories.Clear();
        foreach (var c in await _categories.ListAsync()) Categories.Add(c);

        Items.Clear();
        foreach (var p in await _products.SearchAsync(Search, null)) Items.Add(p);
    }

    [RelayCommand]
    public void New() => Selected = new Product();

    [RelayCommand]
    public async Task SaveAsync()
    {
        if (Selected is null || string.IsNullOrWhiteSpace(Selected.Name)) return;
        await _products.UpsertAsync(Selected);
        await LoadAsync();
    }

    [RelayCommand]
    public async Task DeleteAsync()
    {
        if (Selected is null || Selected.Id == 0) return;
        await _products.SoftDeleteAsync(Selected.Id);
        Selected = null;
        await LoadAsync();
    }
}

public partial class OrdersViewModel : ObservableObject
{
    private readonly IBillRepository _bills;

    public OrdersViewModel(IBillRepository bills) => _bills = bills;

    public ObservableCollection<Bill> Items { get; } = new();

    [ObservableProperty] private DateTimeOffset from = DateTimeOffset.Now.AddDays(-7);
    [ObservableProperty] private DateTimeOffset to = DateTimeOffset.Now;
    [ObservableProperty] private Bill? selected;

    [RelayCommand]
    public async Task LoadAsync()
    {
        Items.Clear();
        foreach (var b in await _bills.BetweenAsync(From, To)) Items.Add(b);
    }
}

public partial class CustomersViewModel : ObservableObject
{
    private readonly IRepository<Customer> _customers;

    public CustomersViewModel(IRepository<Customer> customers) => _customers = customers;

    public ObservableCollection<Customer> Items { get; } = new();

    [ObservableProperty] private Customer? selected;

    [RelayCommand]
    public async Task LoadAsync()
    {
        Items.Clear();
        foreach (var c in await _customers.ListAsync()) Items.Add(c);
    }

    [RelayCommand]
    public void New() => Selected = new Customer();

    [RelayCommand]
    public async Task SaveAsync()
    {
        if (Selected is null) return;
        await _customers.UpsertAsync(Selected);
        await LoadAsync();
    }
}

public partial class ReportsViewModel : ObservableObject
{
    private readonly IAnalyticsService _analytics;

    public ReportsViewModel(IAnalyticsService analytics) => _analytics = analytics;

    [ObservableProperty] private DateTimeOffset from = DateTimeOffset.Now.AddDays(-30);
    [ObservableProperty] private DateTimeOffset to = DateTimeOffset.Now;
    [ObservableProperty] private string report = string.Empty;
    [ObservableProperty] private bool busy;

    [RelayCommand]
    public async Task GenerateAsync()
    {
        Busy = true;
        try { Report = await _analytics.GenerateAiReportAsync(From, To); }
        finally { Busy = false; }
    }
}

public partial class MarketplaceViewModel : ObservableObject
{
    private readonly IApiClient _api;
    private readonly IRepository<MarketRequest> _requests;

    public MarketplaceViewModel(IApiClient api, IRepository<MarketRequest> requests)
    {
        _api = api;
        _requests = requests;
    }

    public ObservableCollection<MarketItem> Items { get; } = new();

    [ObservableProperty] private MarketItem? selected;
    [ObservableProperty] private string contactName = string.Empty;
    [ObservableProperty] private string contactPhone = string.Empty;
    [ObservableProperty] private int quantity = 1;
    [ObservableProperty] private string? note;
    [ObservableProperty] private string? status;

    [RelayCommand]
    public async Task LoadAsync()
    {
        var res = await _api.GetAsync("v1/market/catalog");
        if (!res.Success) { Status = "Marketplace unavailable offline."; return; }

        try
        {
            var root = System.Text.Json.JsonDocument.Parse(res.RawBody).RootElement;
            if (root.TryGetProperty("data", out var d)) root = d;
            if (!root.TryGetProperty("items", out var items)) return;

            Items.Clear();
            foreach (var i in items.EnumerateArray())
            {
                Items.Add(new MarketItem(
                    i.TryGetProperty("code", out var c) ? c.ToString() : Guid.NewGuid().ToString("N"),
                    i.TryGetProperty("title", out var t) ? t.ToString() : "Item",
                    i.TryGetProperty("description", out var de) ? de.ToString() : string.Empty,
                    i.TryGetProperty("price", out var p) && double.TryParse(p.ToString(), out var pv) ? pv : 0,
                    i.TryGetProperty("image_url", out var im) ? im.ToString() : null,
                    i.TryGetProperty("stock", out var s) && int.TryParse(s.ToString(), out var sv) ? sv : 0));
            }
            Status = null;
        }
        catch { Status = "Could not read the catalog."; }
    }

    /// <summary>Buy now → request lands in the Super Admin panel for approval.</summary>
    [RelayCommand]
    public async Task SubmitRequestAsync()
    {
        if (Selected is null) return;
        if (string.IsNullOrWhiteSpace(ContactName) || string.IsNullOrWhiteSpace(ContactPhone))
        {
            Status = "Name and phone are required.";
            return;
        }

        var request = new MarketRequest
        {
            ItemCode = Selected.Code,
            ContactName = ContactName.Trim(),
            ContactPhone = ContactPhone.Trim(),
            Quantity = Math.Max(1, Quantity),
            TotalPrice = Selected.Price * Math.Max(1, Quantity),
            Note = Note
        };

        // Stored locally first so the request survives an offline moment.
        await _requests.UpsertAsync(request);

        var res = await _api.PostAsync("v1/market/request", new
        {
            uuid = request.Uuid,
            item_code = request.ItemCode,
            name = request.ContactName,
            phone = request.ContactPhone,
            quantity = request.Quantity,
            total = request.TotalPrice,
            note = request.Note
        });

        Status = res.Success
            ? "Request sent — the admin team will contact you."
            : "Saved. It will be sent as soon as you are online.";
    }
}

public record MarketItem(string Code, string Title, string Description, double Price, string? ImageUrl, int Stock);
