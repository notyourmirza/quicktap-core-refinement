# QuickTap POS — Windows Desktop

Desktop port of the existing Native Android POS. Same backend, same Super Admin
panel, same business rules — rebuilt for Windows 11 with WinUI 3.

## Stack

| Layer | Technology |
| --- | --- |
| UI | WinUI 3 (Windows App SDK 1.5), Fluent + Material 3 spacing/rounding |
| Pattern | MVVM (CommunityToolkit.Mvvm), DI, Clean Architecture, Repository |
| Data | EF Core 8 + SQLite (local offline store) |
| Backend | The existing PHP REST API (`/v1/...`), unchanged |

## Projects

```
windows/
  QuickTap.Pos.sln
  src/QuickTap.Core            domain entities, contracts, themes, receipt engine
  src/QuickTap.Infrastructure  EF Core store, API client, sync, backup, printing
  src/QuickTap.App             WinUI 3 shell, pages, view models
```

## Parity with Android

- Entities carry the same sync metadata (`uuid`, `updatedAt`, `dirty`, `deleted`).
- The same 10 theme presets and 15 receipt templates are ported verbatim, and
  the active one is chosen by the Super Admin, not locally.
- Sync uses the same `v1/sync/pull` / `v1/sync/push` contract.
- Marketplace "Buy now" posts to `v1/market/request`, landing in the admin panel.

## Offline behaviour

Every sale is written to local SQLite first; the network is never on the
critical path. Pending rows stay `dirty` until the server confirms them.

Duplicate protection (the defect fixed on Android and carried over here):
1. `Uuid` is the only upsert key and is unique in SQLite.
2. The pull cursor is the server's `server_time`, never the local clock.
3. Incoming rows match on uuid first, then a natural key (barcode/phone/name),
   so a locally created row adopts the server identity instead of duplicating.
4. `dirty` clears only for uuids the server reported as accepted.

Backups run weekly, write one archive and delete every older archive.

## Build

```powershell
cd windows
dotnet restore
dotnet build QuickTap.Pos.sln -c Release
dotnet publish src/QuickTap.App/QuickTap.App.csproj -c Release -r win-x64 --self-contained true
```

Set the backend credentials in `src/QuickTap.App/AppConfig.cs` (`ApiKey`,
`ApiSecret`) to the same values the Android `build.gradle` uses. For production
these should move to a protected store rather than source.

## Status

Implemented end to end: authentication, POS/checkout with barcode input, product
and customer management, order history, dashboard, reports (local + AI), the
marketplace with buy-now requests, receipt printing, sync, backup, theming and
light/dark mode.

Still to port from Android: suppliers, expenses, staff/attendance, multi-branch
switching, wallet and referral screens. Their entities, repositories and sync
plumbing already exist in `QuickTap.Core`/`QuickTap.Infrastructure`, so each
remaining screen is a view model plus a page on the existing shell.
