using Microsoft.EntityFrameworkCore;
using QuickTap.Core.Abstractions;
using QuickTap.Core.Entities;

namespace QuickTap.Infrastructure.Data;

public class EfRepository<T> : IRepository<T> where T : SyncEntity
{
    protected readonly IDbContextFactory<PosDbContext> Factory;

    public EfRepository(IDbContextFactory<PosDbContext> factory) => Factory = factory;

    public virtual async Task<IReadOnlyList<T>> ListAsync(CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        return await db.Set<T>().Where(e => !e.Deleted).ToListAsync(ct);
    }

    public async Task<T?> GetAsync(long id, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        return await db.Set<T>().FirstOrDefaultAsync(e => e.Id == id, ct);
    }

    public async Task<T?> GetByUuidAsync(string uuid, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        return await db.Set<T>().FirstOrDefaultAsync(e => e.Uuid == uuid, ct);
    }

    public async Task<T> UpsertAsync(T entity, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        entity.Touch();
        var existing = await db.Set<T>().FirstOrDefaultAsync(e => e.Uuid == entity.Uuid, ct);
        if (existing is null)
        {
            db.Set<T>().Add(entity);
        }
        else
        {
            entity.Id = existing.Id;
            db.Entry(existing).CurrentValues.SetValues(entity);
        }
        await db.SaveChangesAsync(ct);
        return entity;
    }

    public async Task SoftDeleteAsync(long id, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        var row = await db.Set<T>().FirstOrDefaultAsync(e => e.Id == id, ct);
        if (row is null) return;
        row.Deleted = true;
        row.Touch();
        await db.SaveChangesAsync(ct);
    }

    public async Task<IReadOnlyList<T>> DirtyAsync(CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        return await db.Set<T>().Where(e => e.Dirty).ToListAsync(ct);
    }
}

public class ProductRepository : EfRepository<Product>, IProductRepository
{
    public ProductRepository(IDbContextFactory<PosDbContext> f) : base(f) { }

    public async Task<IReadOnlyList<Product>> SearchAsync(string term, long? categoryId, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        var q = db.Products.Where(p => !p.Deleted && p.Available);
        if (categoryId is > 0) q = q.Where(p => p.CategoryId == categoryId);
        if (!string.IsNullOrWhiteSpace(term))
            q = q.Where(p => EF.Functions.Like(p.Name, $"%{term}%") || p.Barcode == term || p.Sku == term);
        return await q.OrderByDescending(p => p.Favorite).ThenByDescending(p => p.SoldCount)
                      .ThenBy(p => p.Name).Take(300).ToListAsync(ct);
    }

    public async Task<Product?> ByBarcodeAsync(string barcode, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        return await db.Products.FirstOrDefaultAsync(p => p.Barcode == barcode && !p.Deleted, ct);
    }

    public async Task AdjustStockAsync(long productId, int delta, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        var p = await db.Products.FirstOrDefaultAsync(x => x.Id == productId, ct);
        if (p is null || p.Stock < 0) return; // negative == not tracked
        p.Stock = Math.Max(0, p.Stock + delta);
        p.Touch();
        await db.SaveChangesAsync(ct);
    }
}

public class BillRepository : EfRepository<Bill>, IBillRepository
{
    public BillRepository(IDbContextFactory<PosDbContext> f) : base(f) { }

    public async Task<Bill> SaveBillAsync(Bill bill, IEnumerable<BillItem> items, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        await using var tx = await db.Database.BeginTransactionAsync(ct);

        if (string.IsNullOrWhiteSpace(bill.InvoiceNo))
            bill.InvoiceNo = await NextInvoiceNoInternalAsync(db, ct);

        bill.Items = items.ToList();
        bill.Touch();
        db.Bills.Add(bill);
        await db.SaveChangesAsync(ct);

        foreach (var line in bill.Items.Where(i => i.ProductId > 0))
        {
            var product = await db.Products.FirstOrDefaultAsync(p => p.Id == line.ProductId, ct);
            if (product is null) continue;
            product.SoldCount += line.Qty;
            if (product.Stock >= 0) product.Stock = Math.Max(0, product.Stock - line.Qty);
            product.Touch();
        }
        await db.SaveChangesAsync(ct);
        await tx.CommitAsync(ct);
        return bill;
    }

    public async Task<IReadOnlyList<Bill>> BetweenAsync(DateTimeOffset from, DateTimeOffset to, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        long f = from.ToUnixTimeMilliseconds(), t = to.ToUnixTimeMilliseconds();
        return await db.Bills.Include(b => b.Items)
            .Where(b => !b.Deleted && b.CreatedAt >= f && b.CreatedAt <= t)
            .OrderByDescending(b => b.CreatedAt).ToListAsync(ct);
    }

    public async Task<IReadOnlyList<BillItem>> ItemsOfAsync(long billId, CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        return await db.BillItems.Where(i => i.BillId == billId).ToListAsync(ct);
    }

    public async Task<string> NextInvoiceNoAsync(CancellationToken ct = default)
    {
        await using var db = await Factory.CreateDbContextAsync(ct);
        return await NextInvoiceNoInternalAsync(db, ct);
    }

    private static async Task<string> NextInvoiceNoInternalAsync(PosDbContext db, CancellationToken ct)
    {
        var count = await db.Bills.CountAsync(ct);
        return $"INV-{count + 1:D6}";
    }
}
