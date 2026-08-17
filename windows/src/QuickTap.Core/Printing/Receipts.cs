using System.Globalization;
using System.Text;
using QuickTap.Core.Entities;

namespace QuickTap.Core.Printing;

/// <summary>Port of com.quicktap.pos.print.ReceiptTemplates — same 15 keys.</summary>
public sealed record ReceiptTemplate(
    string Key,
    string Name,
    char Divider,
    bool DoubleTitle,
    bool CenteredMeta,
    bool ShowItemPrice,
    bool BoxedTotal,
    bool ThankYouBlock);

public static class ReceiptTemplates
{
    public static IReadOnlyList<ReceiptTemplate> All { get; } =
    [
        new("classic",    "Classic",       '=', true,  false, true,  false, true),
        new("minimal",    "Minimal",       ' ', false, false, false, false, false),
        new("compact",    "Compact",       '-', false, false, false, false, false),
        new("boutique",   "Boutique",      '*', true,  true,  true,  true,  true),
        new("corporate",  "Corporate",     '=', false, false, true,  true,  false),
        new("cafe",       "Cafe",          '-', true,  true,  true,  false, true),
        new("retail",     "Retail",        '=', true,  false, true,  true,  false),
        new("wholesale",  "Wholesale",     '-', false, false, true,  true,  false),
        new("elegant",    "Elegant",       '*', true,  true,  false, true,  true),
        new("thermal58",  "Thermal 58",    '-', false, true,  false, false, true),
        new("thermal80",  "Thermal 80",    '=', true,  false, true,  true,  true),
        new("delivery",   "Delivery",      '-', true,  false, true,  false, true),
        new("kitchen",    "Kitchen ticket",'=', true,  true,  false, false, false),
        new("luxury",     "Luxury",        '*', true,  true,  true,  true,  true),
        new("invoice",    "Tax invoice",   '=', false, false, true,  true,  false),
    ];

    public static ReceiptTemplate ByKey(string? key) =>
        All.FirstOrDefault(t => string.Equals(t.Key, key, StringComparison.OrdinalIgnoreCase)) ?? All[0];
}

public sealed class StoreProfile
{
    public string StoreName { get; set; } = "QuickTap Store";
    public string? Phone { get; set; }
    public string? Address { get; set; }
    public string Currency { get; set; } = "Rs";
    public string Footer { get; set; } = "Thank you! Please visit again.";
}

/// <summary>
/// Renders a monospaced receipt identical to the Android ESC/POS output so both
/// platforms print the same paper.
/// </summary>
public sealed class ReceiptRenderer
{
    private readonly int _width;

    public ReceiptRenderer(int charactersPerLine = 32) => _width = charactersPerLine;

    public string Render(Bill bill, IEnumerable<BillItem> items, StoreProfile store, ReceiptTemplate template)
    {
        var sb = new StringBuilder();
        void Rule() { if (template.Divider != ' ') sb.AppendLine(new string(template.Divider, _width)); }
        void Center(string s) => sb.AppendLine(s.PadLeft((_width + s.Length) / 2).TrimEnd());

        Center(template.DoubleTitle ? store.StoreName.ToUpperInvariant() : store.StoreName);
        if (!string.IsNullOrWhiteSpace(store.Address)) Center(store.Address!);
        if (!string.IsNullOrWhiteSpace(store.Phone)) Center(store.Phone!);
        Rule();

        var created = DateTimeOffset.FromUnixTimeMilliseconds(bill.CreatedAt).LocalDateTime;
        var meta = new[]
        {
            $"Invoice : {bill.InvoiceNo}",
            $"Date    : {created:dd MMM yyyy HH:mm}",
            $"Type    : {bill.OrderType.Replace('_', ' ')}",
        };
        foreach (var line in meta)
        {
            if (template.CenteredMeta) Center(line); else sb.AppendLine(line);
        }
        if (!string.IsNullOrWhiteSpace(bill.CustomerName)) sb.AppendLine($"Customer: {bill.CustomerName}");
        Rule();

        foreach (var item in items)
        {
            sb.AppendLine(item.Name);
            var right = Money(item.LineTotal, store.Currency);
            var left = template.ShowItemPrice
                ? $"  {item.Qty} x {Money(item.Price, store.Currency)}"
                : $"  x{item.Qty}";
            sb.AppendLine(Pair(left, right));
        }
        Rule();

        sb.AppendLine(Pair("Subtotal", Money(bill.Subtotal, store.Currency)));
        if (bill.Discount > 0) sb.AppendLine(Pair("Discount", "-" + Money(bill.Discount, store.Currency)));
        if (bill.Tax > 0) sb.AppendLine(Pair("Tax", Money(bill.Tax, store.Currency)));

        var total = Pair("TOTAL", Money(bill.Total, store.Currency));
        if (template.BoxedTotal)
        {
            sb.AppendLine(new string('-', _width));
            sb.AppendLine(total);
            sb.AppendLine(new string('-', _width));
        }
        else sb.AppendLine(total);

        if (bill.Paid > 0)
        {
            sb.AppendLine(Pair("Paid", Money(bill.Paid, store.Currency)));
            sb.AppendLine(Pair("Change", Money(bill.ChangeDue, store.Currency)));
        }

        if (template.ThankYouBlock)
        {
            sb.AppendLine();
            Center(store.Footer);
        }
        sb.AppendLine();
        return sb.ToString();
    }

    private string Pair(string left, string right)
    {
        var pad = Math.Max(1, _width - left.Length - right.Length);
        return left + new string(' ', pad) + right;
    }

    private static string Money(double v, string currency) =>
        currency + " " + v.ToString("N2", CultureInfo.InvariantCulture);
}
