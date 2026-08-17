using Microsoft.UI.Xaml.Data;

namespace QuickTap.App.Converters;

public sealed class NotConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language) =>
        value is not bool b || !b;

    public object ConvertBack(object value, Type targetType, object parameter, string language) =>
        value is not bool b || !b;
}

public sealed class MoneyConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language) =>
        value is double d ? d.ToString("N2") : value?.ToString() ?? string.Empty;

    public object ConvertBack(object value, Type targetType, object parameter, string language) =>
        double.TryParse(value?.ToString(), out var d) ? d : 0d;
}

public sealed class TimestampConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, string language) =>
        value is long ms
            ? DateTimeOffset.FromUnixTimeMilliseconds(ms).ToLocalTime().ToString("dd MMM HH:mm")
            : string.Empty;

    public object ConvertBack(object value, Type targetType, object parameter, string language) =>
        throw new NotSupportedException();
}
