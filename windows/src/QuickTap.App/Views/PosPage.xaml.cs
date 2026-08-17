using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using QuickTap.App.ViewModels;
using QuickTap.Core.Entities;
using QuickTap.Infrastructure.Printing;

namespace QuickTap.App.Views;

public sealed partial class PosPage : Page
{
    private readonly HidBarcodeScanner _scanner;

    public PosViewModel ViewModel { get; }

    public PosPage()
    {
        ViewModel = App.Services.GetRequiredService<PosViewModel>();
        _scanner = App.Services.GetRequiredService<HidBarcodeScanner>();
        InitializeComponent();
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        _scanner.CodeScanned += OnCodeScanned;
        _scanner.Start();
        await ViewModel.LoadAsync();
    }

    protected override void OnNavigatedFrom(NavigationEventArgs e)
    {
        _scanner.CodeScanned -= OnCodeScanned;
        _scanner.Stop();
        base.OnNavigatedFrom(e);
    }

    private void OnCodeScanned(object? sender, string code) =>
        DispatcherQueue.TryEnqueue(async () => await ViewModel.ScanAsync(code));

    private void OnProductClick(object sender, ItemClickEventArgs e) =>
        ViewModel.AddToCart(e.ClickedItem as Product);

    private void OnIncrement(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is CartLine line) ViewModel.Increment(line);
    }

    private void OnDecrement(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is CartLine line) ViewModel.Decrement(line);
    }
}
