using Microsoft.EntityFrameworkCore;
using QuickTap.Core.Entities;

namespace QuickTap.Infrastructure.Data;

/// <summary>
/// Local SQLite mirror of the Android Room schema. Unique indexes on Uuid are
/// what stop the duplicate rows the Android build used to accumulate.
/// </summary>
public class PosDbContext : DbContext
{
    public PosDbContext(DbContextOptions<PosDbContext> options) : base(options) { }

    public DbSet<Category> Categories => Set<Category>();
    public DbSet<Product> Products => Set<Product>();
    public DbSet<Customer> Customers => Set<Customer>();
    public DbSet<Supplier> Suppliers => Set<Supplier>();
    public DbSet<Expense> Expenses => Set<Expense>();
    public DbSet<Bill> Bills => Set<Bill>();
    public DbSet<BillItem> BillItems => Set<BillItem>();
    public DbSet<Branch> Branches => Set<Branch>();
    public DbSet<Staff> Staff => Set<Staff>();
    public DbSet<Attendance> Attendance => Set<Attendance>();
    public DbSet<WalletTransaction> WalletTransactions => Set<WalletTransaction>();
    public DbSet<Referral> Referrals => Set<Referral>();
    public DbSet<NotificationItem> Notifications => Set<NotificationItem>();
    public DbSet<MarketRequest> MarketRequests => Set<MarketRequest>();
    public DbSet<RemoteConfigEntry> RemoteConfig => Set<RemoteConfigEntry>();

    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<RemoteConfigEntry>().HasKey(e => e.Key);

        UniqueUuid<Category>(b);
        UniqueUuid<Product>(b);
        UniqueUuid<Customer>(b);
        UniqueUuid<Supplier>(b);
        UniqueUuid<Expense>(b);
        UniqueUuid<Bill>(b);
        UniqueUuid<Branch>(b);
        UniqueUuid<Staff>(b);
        UniqueUuid<Attendance>(b);
        UniqueUuid<WalletTransaction>(b);
        UniqueUuid<Referral>(b);
        UniqueUuid<NotificationItem>(b);
        UniqueUuid<MarketRequest>(b);

        b.Entity<Product>().HasIndex(p => p.Name);
        b.Entity<Product>().HasIndex(p => p.Barcode);
        b.Entity<Bill>().HasIndex(x => x.CreatedAt);
        b.Entity<Bill>().HasIndex(x => x.InvoiceNo).IsUnique();
        b.Entity<Bill>().HasMany(x => x.Items).WithOne().HasForeignKey(i => i.BillId)
            .OnDelete(DeleteBehavior.Cascade);
        b.Entity<BillItem>().HasIndex(i => i.BillId);
        b.Entity<BillItem>().Ignore(i => i.LineTotal);

        base.OnModelCreating(b);
    }

    private static void UniqueUuid<T>(ModelBuilder b) where T : SyncEntity =>
        b.Entity<T>().HasIndex(e => e.Uuid).IsUnique();
}
