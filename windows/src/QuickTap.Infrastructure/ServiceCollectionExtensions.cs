using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using QuickTap.Core.Abstractions;
using QuickTap.Infrastructure.Api;
using QuickTap.Infrastructure.Data;
using QuickTap.Infrastructure.Printing;
using QuickTap.Infrastructure.Services;

namespace QuickTap.Infrastructure;

public static class ServiceCollectionExtensions
{
    public static IServiceCollection AddQuickTapInfrastructure(this IServiceCollection services, Action<ApiOptions>? configureApi = null)
    {
        var paths = new AppPaths();
        services.AddSingleton(paths);

        services.AddDbContextFactory<PosDbContext>(o => o.UseSqlite($"Data Source={paths.DatabasePath}"));

        services.AddSingleton<ISettingsStore, SettingsStore>();
        services.AddSingleton<IRemoteConfigService, RemoteConfigService>();
        services.AddSingleton<ISyncService, SyncService>();
        services.AddSingleton<IBackupService, BackupService>();
        services.AddSingleton<IReceiptPrinter, WindowsRawPrinter>();
        services.AddSingleton<HidBarcodeScanner>();
        services.AddSingleton<IBarcodeScanner>(sp => sp.GetRequiredService<HidBarcodeScanner>());
        services.AddSingleton<IAnalyticsService, AnalyticsService>();

        services.AddSingleton<SessionService>();
        services.AddSingleton<ISessionService>(sp => sp.GetRequiredService<SessionService>());
        services.AddSingleton<ITokenProvider>(sp => sp.GetRequiredService<SessionService>());

        if (configureApi is not null) services.Configure(configureApi);
        services.AddHttpClient<IApiClient, ApiClient>();

        services.AddSingleton(typeof(IRepository<>), typeof(EfRepository<>));
        services.AddSingleton<IProductRepository, ProductRepository>();
        services.AddSingleton<IBillRepository, BillRepository>();

        return services;
    }

    /// <summary>Creates/updates the local SQLite file on first run.</summary>
    public static void InitializeDatabase(this IServiceProvider provider)
    {
        var factory = provider.GetRequiredService<IDbContextFactory<PosDbContext>>();
        using var db = factory.CreateDbContext();
        db.Database.EnsureCreated();
    }
}
