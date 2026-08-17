using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using QuickTap.App.Services;
using QuickTap.Core.Abstractions;
using QuickTap.Core.Printing;
using QuickTap.Core.Theming;

namespace QuickTap.App.ViewModels;

public partial class SettingsViewModel : ObservableObject
{
    private readonly ISettingsStore _settings;
    private readonly ISyncService _sync;
    private readonly IBackupService _backup;
    private readonly IReceiptPrinter _printer;
    private readonly IRemoteConfigService _config;
    private readonly ThemeService _theme;

    public SettingsViewModel(ISettingsStore settings, ISyncService sync, IBackupService backup,
        IReceiptPrinter printer, IRemoteConfigService config, ThemeService theme)
    {
        _settings = settings;
        _sync = sync;
        _backup = backup;
        _printer = printer;
        _config = config;
        _theme = theme;
    }

    public string[] ThemeModes { get; } = ["system", "light", "dark"];
    public List<string> Printers { get; } = new();

    public string ActiveTheme => ThemePresets.ByKey(_config.ThemeKey).Name + " (set by admin)";
    public string ActiveTemplate => ReceiptTemplates.ByKey(_config.ReceiptTemplateKey).Name + " (set by admin)";
    public string? WhatsAppNumber => _config.WhatsAppNumber;
    public string Credit => AppConfig.Credit;
    public string LastSync => _sync.LastSyncAt?.ToLocalTime().ToString("dd MMM yyyy HH:mm") ?? "Never";

    [ObservableProperty] private string? status;

    public string ThemeMode
    {
        get => _theme.Mode;
        set { _theme.Mode = value; OnPropertyChanged(); }
    }

    public bool AutoSync
    {
        get => _sync.AutoSyncEnabled;
        set { _sync.AutoSyncEnabled = value; OnPropertyChanged(); }
    }

    public bool AutoPrint
    {
        get => _settings.GetBool("auto_print", true);
        set { _settings.SetBool("auto_print", value); OnPropertyChanged(); }
    }

    public bool CashDrawerKick
    {
        get => _settings.GetBool("cash_drawer_kick");
        set { _settings.SetBool("cash_drawer_kick", value); OnPropertyChanged(); }
    }

    public string? SelectedPrinter
    {
        get => _settings.GetString("printer_name");
        set { _settings.SetString("printer_name", value); OnPropertyChanged(); }
    }

    [RelayCommand]
    public async Task LoadAsync()
    {
        Printers.Clear();
        Printers.AddRange(await _printer.ListPrintersAsync());
        OnPropertyChanged(nameof(Printers));
    }

    [RelayCommand]
    private async Task SyncNowAsync()
    {
        var report = await _sync.SyncNowAsync();
        Status = $"{report.Outcome}: {report.Pushed} sent, {report.Pulled} received";
        OnPropertyChanged(nameof(LastSync));
    }

    [RelayCommand]
    private async Task BackupNowAsync()
    {
        var path = await _backup.RunWeeklyBackupAsync();
        Status = "Backup saved and previous copy removed: " + path;
    }

    [RelayCommand]
    private async Task RefreshConfigAsync()
    {
        await _config.RefreshAsync();
        OnPropertyChanged(nameof(ActiveTheme));
        OnPropertyChanged(nameof(ActiveTemplate));
        OnPropertyChanged(nameof(WhatsAppNumber));
        Status = "Admin settings refreshed.";
    }
}
