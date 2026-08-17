using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using QuickTap.App.ViewModels;

namespace QuickTap.App.Views;

public sealed partial class MarketplacePage : Page
{
    public MarketplaceViewModel ViewModel { get; }

    public MarketplacePage()
    {
        ViewModel = App.Services.GetRequiredService<MarketplaceViewModel>();
        InitializeComponent();
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        await ViewModel.LoadAsync();
    }
}
