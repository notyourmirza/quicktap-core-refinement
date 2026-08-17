using System.IO.Compression;
using QuickTap.Core.Abstractions;

namespace QuickTap.Infrastructure.Services;

/// <summary>
/// Weekly backup with replace semantics: a fresh archive is written and every
/// earlier archive is removed, so exactly one backup exists at any time (the
/// behaviour requested for Android, kept identical here).
/// </summary>
public sealed class BackupService : IBackupService
{
    private const string Prefix = "quicktap-backup-";
    private readonly AppPaths _paths;
    private readonly ISettingsStore _settings;

    public BackupService(AppPaths paths, ISettingsStore settings)
    {
        _paths = paths;
        _settings = settings;
    }

    public bool IsDue()
    {
        var last = _settings.GetLong("last_backup_at", 0);
        if (last == 0) return true;
        return DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - last >= TimeSpan.FromDays(7).TotalMilliseconds;
    }

    public Task<string> RunWeeklyBackupAsync(CancellationToken ct = default) => Task.Run(() =>
    {
        var stamp = DateTime.Now.ToString("yyyyMMdd-HHmmss");
        var target = Path.Combine(_paths.BackupDirectory, $"{Prefix}{stamp}.zip");

        using (var zip = ZipFile.Open(target, ZipArchiveMode.Create))
        {
            foreach (var file in Directory.EnumerateFiles(_paths.DataDirectory))
            {
                var name = Path.GetFileName(file);
                if (name is "session.bin") continue;             // never archive credentials
                using var source = File.Open(file, FileMode.Open, FileAccess.Read, FileShare.ReadWrite);
                using var entry = zip.CreateEntry(name).Open();
                source.CopyTo(entry);
            }
        }

        // Replace: drop every older archive.
        foreach (var old in Directory.EnumerateFiles(_paths.BackupDirectory, Prefix + "*.zip"))
        {
            if (!string.Equals(old, target, StringComparison.OrdinalIgnoreCase))
            {
                try { File.Delete(old); } catch { /* locked file — next run retries */ }
            }
        }

        _settings.SetLong("last_backup_at", DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
        return target;
    }, ct);

    public Task RestoreAsync(string archivePath, CancellationToken ct = default) => Task.Run(() =>
    {
        if (!File.Exists(archivePath)) throw new FileNotFoundException("Backup not found", archivePath);
        using var zip = ZipFile.OpenRead(archivePath);
        foreach (var entry in zip.Entries)
        {
            if (string.IsNullOrEmpty(entry.Name)) continue;
            entry.ExtractToFile(Path.Combine(_paths.DataDirectory, entry.Name), overwrite: true);
        }
    }, ct);

    public string? LatestBackup() =>
        Directory.EnumerateFiles(_paths.BackupDirectory, Prefix + "*.zip")
                 .OrderByDescending(f => f).FirstOrDefault();
}
