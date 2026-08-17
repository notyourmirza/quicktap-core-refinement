# QuickTap POS — Android (Java)

Offline-first billing and Bluetooth thermal-printing POS for small restaurants,
cafés, bakeries, juice bars, tea stalls and retail shops.

## Stack

- Java 17, Android Studio, minSdk 21 / targetSdk 34
- Material Design 3 (light + dark), ViewBinding, RecyclerView
- Room database (all business data stays on the device)
- MVVM (Activity/Fragment → ViewModel → Repository → DAO)
- Bluetooth ESC/POS printing over SPP (58 mm and 80 mm)
- Only the licence API talks to a server

## Open in Android Studio

1. Android Studio → **Open** → select the `android/` folder.
2. Let Gradle sync (it downloads the wrapper and dependencies).
3. Run on a device. Pair your thermal printer in Android Bluetooth settings first.

## Build the APK from GitHub

`.github/workflows/android.yml` builds both a debug and an unsigned release APK
on every push to `main` that touches `android/`, and on manual dispatch
(Actions → *Build QuickTap POS APK* → **Run workflow**). Download the APKs from
the run's **Artifacts** section.

To ship a signed release, add your keystore as repository secrets and a
`signingConfigs` block in `app/build.gradle`.

## Configure the licence server

A ready-made self-hosted PHP implementation lives in `/php-api` at the repo
root — see `php-api/README.md` to deploy it, then point the app at it:

`app/build.gradle` → `defaultConfig`:

```groovy
buildConfigField "String", "LICENSE_BASE_URL", "\"https://your-server.com/quicktap-api/\""
buildConfigField "String", "API_KEY", "\"same string as php-api/config.php's API_KEY\""
```

The contract the app expects is documented in [LICENSE_API.md](LICENSE_API.md).

## Package map

```
com.quicktap.pos
├── data/            Room entities, DAOs, database, repository, models
├── license/         Device registration + subscription checks (the only network code)
├── print/           ESC/POS byte builder, Bluetooth transport, print jobs
├── ui/              Activities and fragments
│   ├── billing/     Home billing screen, product grid, cart, ViewModel
│   ├── dashboard/   Today's numbers, recent bills, quick actions
│   ├── products/    Product + category management
│   ├── history/     Bill history, reprint, delete
│   ├── reports/     Today / week / month, sales by product & category, CSV export
│   └── settings/    Store profile, tax, theme, printer, backup/restore
└── util/            Prefs, executors, money/date formatting, backup, CSV
```

## Cashier flow (10–15 seconds)

Tap products → they land in the live bill (tap again to increase, long press to
type a quantity) → pick order type and paid/unpaid → **Print Receipt**. The bill
is saved, printed and the cart clears automatically. No confirmation dialogs.

## Notes

- Reports export as CSV, which Excel and Google Sheets open directly. PDF export
  is not included.
- Backup/restore copies the Room database file through the Android file picker;
  Google Drive sync is not included.
