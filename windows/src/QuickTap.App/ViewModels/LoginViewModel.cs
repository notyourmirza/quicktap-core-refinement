using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using QuickTap.Core.Abstractions;

namespace QuickTap.App.ViewModels;

public partial class LoginViewModel : ObservableObject
{
    private readonly ISessionService _session;

    public LoginViewModel(ISessionService session) => _session = session;

    [ObservableProperty] private string username = string.Empty;
    [ObservableProperty] private string password = string.Empty;
    [ObservableProperty] private bool busy;
    [ObservableProperty] private string? error;

    public string Credit => AppConfig.Credit;

    public event EventHandler? Succeeded;

    [RelayCommand]
    private async Task SignInAsync()
    {
        if (Busy) return;
        Error = null;

        if (string.IsNullOrWhiteSpace(Username) || string.IsNullOrWhiteSpace(Password))
        {
            Error = "Enter your username and password.";
            return;
        }

        Busy = true;
        try
        {
            // Login awaits only the auth call — theme/config sync happens after
            // the shell is already on screen, so sign-in feels instant.
            var ok = await _session.LoginAsync(Username.Trim(), Password);
            if (ok) Succeeded?.Invoke(this, EventArgs.Empty);
            else Error = "Sign in failed. Check your details or connection.";
        }
        finally { Busy = false; }
    }
}
