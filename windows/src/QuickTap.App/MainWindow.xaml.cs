using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using QuickTap.App.Services;
using QuickTap.App.Views;
using QuickTap.Core.Abstractions;

namespace QuickTap.App;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        ExtendsContentIntoTitleBar = true;

        var session = App.Services.GetRequiredService<ISessionService>();
        RootFrame.Navigate(session.IsAuthenticated ? typeof(ShellPage) : typeof(LoginPage));

        App.Services.GetRequiredService<ThemeService>().Apply();
    }

    public void ShowShell() => RootFrame.Navigate(typeof(ShellPage));

    public void ShowLogin() => RootFrame.Navigate(typeof(LoginPage));
}
