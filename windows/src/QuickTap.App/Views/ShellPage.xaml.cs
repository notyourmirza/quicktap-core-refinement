using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml.Controls;
using QuickTap.App.Services;
using QuickTap.App.ViewModels;

namespace QuickTap.App.Views;

public sealed partial class ShellPage : Page
{
    public ShellViewModel ViewModel { get; }

    public ShellPage()
    {
        ViewModel = App.Services.GetRequiredService<ShellViewModel>();
        InitializeComponent();

        App.Services.GetRequiredService<NavigationService>().Register(ContentFrame);
        Nav.SelectedItem = Nav.MenuItems[0];
        ContentFrame.Navigate(typeof(PosPage));
    }

    private void OnSelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        if (args.SelectedItem is not NavigationViewItem item) return;

        var page = item.Tag as string switch
        {
            "pos" => typeof(PosPage),
            "dashboard" => typeof(DashboardPage),
            "orders" => typeof(OrdersPage),
            "products" => typeof(ProductsPage),
            "customers" => typeof(CustomersPage),
            "reports" => typeof(ReportsPage),
            "marketplace" => typeof(MarketplacePage),
            "settings" => typeof(SettingsPage),
            _ => typeof(PosPage)
        };
        ContentFrame.Navigate(page);
    }
}
