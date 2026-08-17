using System.Runtime.InteropServices;
using System.Text;
using QuickTap.Core.Abstractions;

namespace QuickTap.Infrastructure.Printing;

/// <summary>
/// Sends the rendered receipt straight to a Windows spooler queue as RAW
/// ESC/POS bytes — works with USB, network and Bluetooth thermal printers that
/// expose a Windows driver, matching the Android BluetoothPrinter output.
/// </summary>
public sealed class WindowsRawPrinter : IReceiptPrinter
{
    private readonly ISettingsStore _settings;

    public WindowsRawPrinter(ISettingsStore settings) => _settings = settings;

    public Task<IReadOnlyList<string>> ListPrintersAsync() => Task.FromResult<IReadOnlyList<string>>(
        OperatingSystem.IsWindows() ? EnumeratePrinters() : Array.Empty<string>());

    public Task PrintAsync(string rendered, string? printerName = null, CancellationToken ct = default) => Task.Run(() =>
    {
        var target = printerName ?? _settings.GetString("printer_name");
        if (string.IsNullOrWhiteSpace(target))
            throw new InvalidOperationException("No receipt printer selected.");

        var payload = new List<byte>();
        payload.AddRange(new byte[] { 0x1B, 0x40 });                       // ESC @ — reset
        payload.AddRange(Encoding.GetEncoding(437).GetBytes(rendered));
        payload.AddRange(new byte[] { 0x0A, 0x0A, 0x0A });                 // feed
        payload.AddRange(new byte[] { 0x1D, 0x56, 0x00 });                 // GS V — full cut
        if (_settings.GetBool("cash_drawer_kick"))
            payload.AddRange(new byte[] { 0x1B, 0x70, 0x00, 0x19, 0xFA }); // drawer pulse

        SendRaw(target!, payload.ToArray());
    }, ct);

    // ---------------- win32 spooler interop ----------------

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct DOCINFO
    {
        [MarshalAs(UnmanagedType.LPWStr)] public string DocName;
        [MarshalAs(UnmanagedType.LPWStr)] public string? OutputFile;
        [MarshalAs(UnmanagedType.LPWStr)] public string DataType;
    }

    [DllImport("winspool.drv", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool OpenPrinter(string src, out IntPtr handle, IntPtr defaults);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool ClosePrinter(IntPtr handle);

    [DllImport("winspool.drv", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool StartDocPrinter(IntPtr handle, int level, ref DOCINFO di);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool EndDocPrinter(IntPtr handle);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool StartPagePrinter(IntPtr handle);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool EndPagePrinter(IntPtr handle);

    [DllImport("winspool.drv", SetLastError = true)]
    private static extern bool WritePrinter(IntPtr handle, IntPtr bytes, int count, out int written);

    private static void SendRaw(string printer, byte[] data)
    {
        if (!OpenPrinter(printer, out var handle, IntPtr.Zero))
            throw new InvalidOperationException($"Cannot open printer '{printer}'.");

        var buffer = Marshal.AllocHGlobal(data.Length);
        try
        {
            Marshal.Copy(data, 0, buffer, data.Length);
            var doc = new DOCINFO { DocName = "QuickTap Receipt", DataType = "RAW" };
            if (!StartDocPrinter(handle, 1, ref doc)) throw new InvalidOperationException("Print job rejected.");
            StartPagePrinter(handle);
            WritePrinter(handle, buffer, data.Length, out _);
            EndPagePrinter(handle);
            EndDocPrinter(handle);
        }
        finally
        {
            Marshal.FreeHGlobal(buffer);
            ClosePrinter(handle);
        }
    }

    private static IReadOnlyList<string> EnumeratePrinters()
    {
        try
        {
            return System.Drawing.Printing.PrinterSettings.InstalledPrinters
                .Cast<string>().ToList();
        }
        catch
        {
            return Array.Empty<string>();
        }
    }
}

/// <summary>
/// USB barcode/QR guns behave as keyboards: characters arrive fast and end with
/// Enter. Feed key input here and a complete code is raised once.
/// </summary>
public sealed class HidBarcodeScanner : IBarcodeScanner
{
    private readonly StringBuilder _buffer = new();
    private DateTime _lastKey = DateTime.MinValue;
    private bool _running;

    public event EventHandler<string>? CodeScanned;

    public void Start() => _running = true;
    public void Stop() { _running = false; _buffer.Clear(); }

    /// <summary>Called by the UI layer for every key press.</summary>
    public void Feed(char character, bool isEnter)
    {
        if (!_running) return;

        var now = DateTime.UtcNow;
        if ((now - _lastKey).TotalMilliseconds > 120) _buffer.Clear(); // human typing, not a gun
        _lastKey = now;

        if (isEnter)
        {
            var code = _buffer.ToString().Trim();
            _buffer.Clear();
            if (code.Length >= 4) CodeScanned?.Invoke(this, code);
            return;
        }
        if (!char.IsControl(character)) _buffer.Append(character);
    }
}
