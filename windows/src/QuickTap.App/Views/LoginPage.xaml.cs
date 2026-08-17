using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using QuickTap.App.ViewModels;

namespace QuickTap.App.Views;

public sealed partial class LoginPage : Page
{
    public LoginViewModel ViewModel { get; }

    public LoginPage()
    {
        ViewModel = App.Services.GetRequiredService<LoginViewModel>();
        InitializeComponent();
        ViewModel.Succeeded += (_, _) => App.Shell?.ShowShell();
    }

    private void OnPasswordChanged(object sender, RoutedEventArgs e) =>
        ViewModel.Password = PasswordInput.Password;
}
