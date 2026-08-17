using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media.Animation;

namespace QuickTap.App.Services;

/// <summary>Frame navigation shared by the shell and the view models.</summary>
public sealed class NavigationService
{
    private Frame? _frame;

    public void Register(Frame frame) => _frame = frame;

    public bool CanGoBack => _frame?.CanGoBack ?? false;

    public void GoBack()
    {
        if (CanGoBack) _frame!.GoBack();
    }

    public void Navigate(Type pageType, object? parameter = null)
    {
        if (_frame is null || _frame.CurrentSourcePageType == pageType) return;
        _frame.Navigate(pageType, parameter, new DrillInNavigationTransitionInfo());
    }
}
