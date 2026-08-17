using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml.Controls;
using QuickTap.App.ViewModels;

namespace QuickTap.App.Views;

public sealed partial class ReportsPage : Page
{
    public ReportsViewModel ViewModel { get; }

    public ReportsPage()
    {
        ViewModel = App.Services.GetRequiredService<ReportsViewModel>();
        InitializeComponent();
    }
}
