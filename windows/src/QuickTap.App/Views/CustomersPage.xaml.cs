using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Navigation;
using QuickTap.App.ViewModels;

namespace QuickTap.App.Views;

public sealed partial class CustomersPage : Page
{
    public CustomersViewModel ViewModel { get; }

    public CustomersPage()
    {
        ViewModel = App.Services.GetRequiredService<CustomersViewModel>();
        InitializeComponent();
    }

    protected override async void OnNavigatedTo(NavigationEventArgs e)
    {
        base.OnNavigatedTo(e);
        await ViewModel.LoadAsync();
    }
}
