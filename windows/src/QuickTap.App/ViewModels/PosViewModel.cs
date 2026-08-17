using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using QuickTap.Core.Abstractions;
using QuickTap.Core.Entities;
using QuickTap.Core.Printing;

namespace QuickTap.App.ViewModels;

public partial class CartLine : ObservableObject
{
    public CartLine(Product product) => Product = product;

    public Product Product { get; }
    public string Name => Product.Name;
    public double Price => Product.Price;

    [ObservableProperty] private int qty = 1;
    [ObservableProperty] private double discount;

    public double LineTotal => (Price * Qty) - Discount;

    partial void OnQtyChanged(int value) => OnPropertyChanged(nameof(LineTotal));
    partial void OnDiscountChanged(double value) => OnPropertyChanged(nameof(LineTotal));
}

public partial class PosViewModel : ObservableObject
{
    private readonly IProductRepository _products;
    private readonly IRepository<Category> _categories;
    private readonly IBillRepository _bills;
    private readonly IReceiptPrinter _printer;
    private readonly IRemoteConfigService _config;
    private readonly ISettingsStore _settings;
    private readonly ISyncService _sync;

    public PosViewModel(IProductRepository products, IRepository<Category> categories, IBillRepository bills,
        IReceiptPrinter printer, IRemoteConfigService config, ISettingsStore settings, ISyncService sync)
    {
        _products = products;
        _categories = categories;
        _bills = bills;
        _printer = printer;
        _config = config;
        _settings = settings;
        _sync = sync;
    }

    public ObservableCollection<Product> Products { get; } = new();
    public ObservableCollection<Category> Categories { get; } = new();
    public ObservableCollection<CartLine> Cart { get; } = new();

    public string[] OrderTypes { get; } = [Bill.DineIn, Bill.TakeAway, Bill.Delivery];

    [ObservableProperty] private string search = string.Empty;
    [ObservableProperty] private Category? selectedCategory;
    [ObservableProperty] private string orderType = Bill.DineIn;
    [ObservableProperty] private string? tableNo;
    [ObservableProperty] private string? customerName;
    [ObservableProperty] private double discount;
    [ObservableProperty] private double paid;
    [ObservableProperty] private string paymentMethod = "cash";
    [ObservableProperty] private string? status;

    public double Subtotal => Cart.Sum(l => l.LineTotal);
    public double Tax => Cart.Sum(l => l.LineTotal * l.Product.TaxPercent / 100d);
    public double Total => Math.Max(0, Subtotal + Tax - Discount);
    public double ChangeDue => Math.Max(0, Paid - Total);
    public bool CartHasItems => Cart.Count > 0;

    partial void OnSearchChanged(string value) => _ = LoadProductsAsync();
    partial void OnSelectedCategoryChanged(Category? value) => _ = LoadProductsAsync();
    partial void OnDiscountChanged(double value) => RaiseTotals();
    partial void OnPaidChanged(double value) => OnPropertyChanged(nameof(ChangeDue));

    [RelayCommand]
    public async Task LoadAsync()
    {
        Categories.Clear();
        foreach (var c in (await _categories.ListAsync()).OrderBy(c => c.SortOrder).ThenBy(c => c.Name))
            Categories.Add(c);
        await LoadProductsAsync();
    }

    private async Task LoadProductsAsync()
    {
        var rows = await _products.SearchAsync(Search, SelectedCategory?.Id);
        Products.Clear();
        foreach (var p in rows) Products.Add(p);
    }

    [RelayCommand]
    public void AddToCart(Product? product)
    {
        if (product is null) return;
        var line = Cart.FirstOrDefault(l => l.Product.Id == product.Id);
        if (line is null)
        {
            line = new CartLine(product);
            line.PropertyChanged += (_, _) => RaiseTotals();
            Cart.Add(line);
        }
        else line.Qty++;
        RaiseTotals();
    }

    [RelayCommand]
    public void Increment(CartLine line) => line.Qty++;

    [RelayCommand]
    public void Decrement(CartLine line)
    {
        if (line.Qty > 1) line.Qty--;
        else Remove(line);
    }

    [RelayCommand]
    public void Remove(CartLine line)
    {
        Cart.Remove(line);
        RaiseTotals();
    }

    [RelayCommand]
    public void ClearCart()
    {
        Cart.Clear();
        Discount = 0;
        Paid = 0;
        RaiseTotals();
    }

    /// <summary>Barcode gun / QR scan entry point.</summary>
    public async Task ScanAsync(string code)
    {
        var product = await _products.ByBarcodeAsync(code);
        if (product is not null) AddToCart(product);
        else Status = $"No product for code {code}";
    }

    [RelayCommand]
    public async Task CheckoutAsync()
    {
        if (Cart.Count == 0) return;

        var bill = new Bill
        {
            OrderType = OrderType,
            TableNo = TableNo,
            CustomerName = CustomerName,
            Subtotal = Subtotal,
            Discount = Discount,
            Tax = Tax,
            Total = Total,
            Paid = Paid <= 0 ? Total : Paid,
            ChangeDue = ChangeDue,
            PaymentMethod = PaymentMethod,
            IsPaid = true
        };

        var items = Cart.Select(l => new BillItem
        {
            ProductId = l.Product.Id,
            ProductUuid = l.Product.Uuid,
            Name = l.Name,
            Price = l.Price,
            Qty = l.Qty,
            Discount = l.Discount,
            TaxPercent = l.Product.TaxPercent
        }).ToList();

        // Saved locally first — the sale never depends on the network.
        var saved = await _bills.SaveBillAsync(bill, items);
        Status = $"Saved {saved.InvoiceNo}";

        await TryPrintAsync(saved, items);

        if (_sync.AutoSyncEnabled) _ = _sync.SyncNowAsync();

        ClearCart();
        await LoadProductsAsync();
    }

    private async Task TryPrintAsync(Bill bill, IReadOnlyList<BillItem> items)
    {
        if (!_settings.GetBool("auto_print", true)) return;
        try
        {
            var store = new StoreProfile
            {
                StoreName = _config.Settings.TryGetValue("store_name", out var n) ? n : AppConfig.ProductName,
                Phone = _config.Settings.TryGetValue("store_phone", out var p) ? p : null,
                Address = _config.Settings.TryGetValue("store_address", out var a) ? a : null,
                Currency = _config.Settings.TryGetValue("currency", out var c) ? c : "Rs"
            };
            var template = ReceiptTemplates.ByKey(_config.ReceiptTemplateKey);
            var text = new ReceiptRenderer(_settings.GetLong("printer_width", 32) == 48 ? 48 : 32)
                .Render(bill, items, store, template);
            await _printer.PrintAsync(text);
        }
        catch (Exception ex)
        {
            Status = "Saved, but printing failed: " + ex.Message;
        }
    }

    private void RaiseTotals()
    {
        OnPropertyChanged(nameof(Subtotal));
        OnPropertyChanged(nameof(Tax));
        OnPropertyChanged(nameof(Total));
        OnPropertyChanged(nameof(ChangeDue));
        OnPropertyChanged(nameof(CartHasItems));
    }
}
