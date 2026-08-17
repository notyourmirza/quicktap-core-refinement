using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using QuickTap.App.Services;
using QuickTap.App.ViewModels;
using QuickTap.Core.Abstractions;
using QuickTap.Infrastructure;

namespace QuickTap.App;

public partial class App : Application
{
    public static IServiceProvider Services { get; private set; } = default!;
    public static MainWindow? Shell { get; private set; }

    public App()
    {
        InitializeComponent();
        Services = ConfigureServices();
        Services.InitializeDatabase();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        Shell = new MainWindow();
        Shell.Activate();

        // Background housekeeping: never blocks the first paint.
        _ = Task.Run(async () =>
        {
            var session = Services.GetRequiredService<ISessionService>();
            if (!session.IsAuthenticated) return;
            await Services.GetRequiredService<IRemoteConfigService>().RefreshAsync();
            var backup = Services.GetRequiredService<IBackupService>();
            if (backup is Infrastructure.Services.BackupService b && b.IsDue())
                await b.RunWeeklyBackupAsync();
        });
    }

    private static IServiceProvider ConfigureServices()
    {
        var services = new ServiceCollection();

        services.AddQuickTapInfrastructure(api =>
        {
            api.BaseUrl = AppConfig.ApiBaseUrl;
            api.AppId = AppConfig.AppId;
            api.ApiKey = AppConfig.ApiKey;
            api.ApiSecret = AppConfig.ApiSecret;
        });

        services.AddSingleton<ThemeService>();
        services.AddSingleton<NavigationService>();

        services.AddSingleton<ShellViewModel>();
        services.AddTransient<LoginViewModel>();
        services.AddTransient<PosViewModel>();
        services.AddTransient<DashboardViewModel>();
        services.AddTransient<ProductsViewModel>();
        services.AddTransient<OrdersViewModel>();
        services.AddTransient<CustomersViewModel>();
        services.AddTransient<ReportsViewModel>();
        services.AddTransient<MarketplaceViewModel>();
        services.AddTransient<SettingsViewModel>();

        return services.BuildServiceProvider();
    }
}
