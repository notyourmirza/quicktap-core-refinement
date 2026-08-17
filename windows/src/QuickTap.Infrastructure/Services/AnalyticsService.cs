using System.Globalization;
using System.Text;
using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using QuickTap.Core.Abstractions;
using QuickTap.Infrastructure.Data;

namespace QuickTap.Infrastructure.Services;

/// <summary>
/// Local analytics plus the AI narrative report. The AI call goes through the
/// same backend endpoint the Android app uses, so no key ever lives on the PC;
/// when it is unreachable a deterministic local summary is produced instead.
/// </summary>
public sealed class AnalyticsService : IAnalyticsService
{
    private readonly IDbContextFactory<PosDbContext> _factory;
    private readonly IApiClient _api;

    public AnalyticsService(IDbContextFactory<PosDbContext> factory, IApiClient api)
    {
        _factory = factory;
        _api = api;
    }

    public async Task<IReadOnlyDictionary<string, double>> DaySummaryAsync(DateTimeOffset day, CancellationToken ct = default)
    {
        var from = new DateTimeOffset(day.Date, day.Offset).ToUnixTimeMilliseconds();
        var to = new DateTimeOffset(day.Date.AddDays(1).AddTicks(-1), day.Offset).ToUnixTimeMilliseconds();

        await using var db = await _factory.CreateDbContextAsync(ct);
        var bills = await db.Bills.Where(b => !b.Deleted && b.CreatedAt >= from && b.CreatedAt <= to).ToListAsync(ct);
        var expenses = await db.Expenses.Where(e => !e.Deleted).ToListAsync(ct);
        var todayExpenses = expenses.Where(e => e.SpentAt.Date == day.Date).Sum(e => e.Amount);

        var revenue = bills.Sum(b => b.Total);
        return new Dictionary<string, double>
        {
            ["orders"] = bills.Count,
            ["revenue"] = revenue,
            ["paid_count"] = bills.Count(b => b.IsPaid),
            ["unpaid_count"] = bills.Count(b => !b.IsPaid),
            ["unpaid_amount"] = bills.Where(b => !b.IsPaid).Sum(b => b.Total),
            ["discount"] = bills.Sum(b => b.Discount),
            ["tax"] = bills.Sum(b => b.Tax),
            ["expenses"] = todayExpenses,
            ["net"] = revenue - todayExpenses,
            ["average_ticket"] = bills.Count == 0 ? 0 : revenue / bills.Count
        };
    }

    public async Task<string> GenerateAiReportAsync(DateTimeOffset from, DateTimeOffset to, CancellationToken ct = default)
    {
        await using var db = await _factory.CreateDbContextAsync(ct);
        long f = from.ToUnixTimeMilliseconds(), t = to.ToUnixTimeMilliseconds();

        var bills = await db.Bills.Include(b => b.Items)
            .Where(b => !b.Deleted && b.CreatedAt >= f && b.CreatedAt <= t).ToListAsync(ct);

        var lines = bills.SelectMany(b => b.Items).ToList();
        var top = lines.GroupBy(i => i.Name)
            .Select(g => new { Name = g.Key, Qty = g.Sum(i => i.Qty), Amount = g.Sum(i => i.LineTotal) })
            .OrderByDescending(x => x.Amount).Take(5).ToList();

        var facts = new
        {
            from = from.ToString("yyyy-MM-dd"),
            to = to.ToString("yyyy-MM-dd"),
            orders = bills.Count,
            revenue = bills.Sum(b => b.Total),
            average_ticket = bills.Count == 0 ? 0 : bills.Sum(b => b.Total) / bills.Count,
            top_products = top
        };

        var res = await _api.PostAsync("v1/reports/ai", facts, authed: true, ct: ct);
        if (res.Success)
        {
            try
            {
                var root = JsonDocument.Parse(res.RawBody).RootElement;
                if (root.TryGetProperty("data", out var d)) root = d;
                if (root.TryGetProperty("report", out var r) && r.GetString() is { Length: > 0 } text)
                    return text;
            }
            catch { /* fall through to the local summary */ }
        }

        var sb = new StringBuilder();
        sb.AppendLine($"Sales report {facts.from} to {facts.to}");
        sb.AppendLine($"Orders: {facts.orders}");
        sb.AppendLine($"Revenue: {facts.revenue.ToString("N2", CultureInfo.InvariantCulture)}");
        sb.AppendLine($"Average ticket: {facts.average_ticket.ToString("N2", CultureInfo.InvariantCulture)}");
        sb.AppendLine();
        sb.AppendLine("Best sellers:");
        foreach (var p in top)
            sb.AppendLine($"  • {p.Name} — {p.Qty} sold, {p.Amount.ToString("N2", CultureInfo.InvariantCulture)}");
        if (top.Count == 0) sb.AppendLine("  • No sales recorded in this period.");
        return sb.ToString();
    }
}
