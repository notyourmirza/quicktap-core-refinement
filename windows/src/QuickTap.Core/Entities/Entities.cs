namespace QuickTap.Core.Entities;

/// <summary>
/// Every synced row carries the same offline metadata as the Android Room
/// entities (uuid / updatedAt / dirty / deleted) so the existing PHP sync
/// endpoints work unchanged.
/// </summary>
public abstract class SyncEntity
{
    public long Id { get; set; }
    public string Uuid { get; set; } = Guid.NewGuid().ToString();
    public long UpdatedAt { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    public bool Dirty { get; set; } = true;
    public bool Deleted { get; set; }

    public void Touch()
    {
        UpdatedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        Dirty = true;
    }
}

public class Category : SyncEntity
{
    public string Name { get; set; } = string.Empty;
    public int SortOrder { get; set; }
    public string? ColorHex { get; set; }
}

public class Product : SyncEntity
{
    public string Name { get; set; } = string.Empty;
    public double Price { get; set; }
    public double CostPrice { get; set; }
    public long CategoryId { get; set; }
    public string? CategoryUuid { get; set; }
    public string? ImageUri { get; set; }
    public string? Barcode { get; set; }
    public string? Sku { get; set; }
    public bool Available { get; set; } = true;
    public bool Favorite { get; set; }
    /// <summary>Negative means stock is not tracked (matches Android).</summary>
    public int Stock { get; set; } = -1;
    public double TaxPercent { get; set; }
    public int SoldCount { get; set; }
}

public class Customer : SyncEntity
{
    public string Name { get; set; } = "Walk-in";
    public string? Phone { get; set; }
    public string? Email { get; set; }
    public string? Address { get; set; }
    public double Balance { get; set; }
    public int LoyaltyPoints { get; set; }
}

public class Supplier : SyncEntity
{
    public string Name { get; set; } = string.Empty;
    public string? Phone { get; set; }
    public string? Email { get; set; }
    public string? Address { get; set; }
    public double Payable { get; set; }
}

public class Expense : SyncEntity
{
    public string Title { get; set; } = "Expense";
    public string? Category { get; set; }
    public double Amount { get; set; }
    public string? Note { get; set; }
    public DateTime SpentAt { get; set; } = DateTime.UtcNow;
}

public class Bill : SyncEntity
{
    public const string DineIn = "DINE_IN";
    public const string TakeAway = "TAKE_AWAY";
    public const string Delivery = "DELIVERY";

    public string InvoiceNo { get; set; } = string.Empty;
    public long CreatedAt { get; set; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
    public string OrderType { get; set; } = DineIn;
    public string? TableNo { get; set; }
    public string? CustomerName { get; set; }
    public string? CustomerPhone { get; set; }
    public string? CustomerUuid { get; set; }
    public string? Address { get; set; }
    public string? Notes { get; set; }
    public double Subtotal { get; set; }
    public double Discount { get; set; }
    public double Tax { get; set; }
    public double Total { get; set; }
    public double Paid { get; set; }
    public double ChangeDue { get; set; }
    public string PaymentMethod { get; set; } = "cash";
    public string Status { get; set; } = "completed";
    public bool IsPaid { get; set; }
    public long? BranchId { get; set; }
    public long? StaffId { get; set; }
    public List<BillItem> Items { get; set; } = new();
}

public class BillItem
{
    public long Id { get; set; }
    public long BillId { get; set; }
    public long ProductId { get; set; }
    public string? ProductUuid { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? CategoryName { get; set; }
    public double Price { get; set; }
    public double Discount { get; set; }
    public double TaxPercent { get; set; }
    public int Qty { get; set; } = 1;
    public double LineTotal => (Price * Qty) - Discount;
}

public class Branch : SyncEntity
{
    public string Name { get; set; } = string.Empty;
    public string? Address { get; set; }
    public string? Phone { get; set; }
    public bool IsPrimary { get; set; }
}

public class Staff : SyncEntity
{
    public string Name { get; set; } = string.Empty;
    public string? Phone { get; set; }
    public string Role { get; set; } = "cashier";
    public string? PinHash { get; set; }
    public long? BranchId { get; set; }
    public bool Active { get; set; } = true;
}

public class Attendance : SyncEntity
{
    public long StaffId { get; set; }
    public DateTime ClockIn { get; set; } = DateTime.UtcNow;
    public DateTime? ClockOut { get; set; }
    public string? Note { get; set; }
}

public class WalletTransaction : SyncEntity
{
    public string Type { get; set; } = "credit";
    public double Amount { get; set; }
    public string? Reference { get; set; }
    public string? Note { get; set; }
    public DateTime OccurredAt { get; set; } = DateTime.UtcNow;
}

public class Referral : SyncEntity
{
    public string Code { get; set; } = string.Empty;
    public string? InvitedShop { get; set; }
    public string Status { get; set; } = "pending";
    public double Reward { get; set; }
}

public class NotificationItem : SyncEntity
{
    public string Title { get; set; } = string.Empty;
    public string? Body { get; set; }
    public bool Read { get; set; }
    public DateTime ReceivedAt { get; set; } = DateTime.UtcNow;
}

public class MarketRequest : SyncEntity
{
    public string ItemCode { get; set; } = string.Empty;
    public string ContactName { get; set; } = string.Empty;
    public string ContactPhone { get; set; } = string.Empty;
    public int Quantity { get; set; } = 1;
    public double TotalPrice { get; set; }
    public string? Note { get; set; }
    public string Status { get; set; } = "pending";
}

/// <summary>Key/value cache of everything the Super Admin publishes.</summary>
public class RemoteConfigEntry
{
    public string Key { get; set; } = string.Empty;
    public string Value { get; set; } = string.Empty;
    public long FetchedAt { get; set; }
}
