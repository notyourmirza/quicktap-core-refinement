# QuickTap POS — Enterprise Backend (Step 1)

PHP 8+ / MySQL REST API for the QuickTap POS Android app. No Composer, no
extensions beyond `pdo_mysql` and `openssl` — runs as-is on Hostinger shared
hosting.

## Layout

```
server/
  sql/schema.sql            full MySQL schema + seed data
  api/
    index.php               front controller, all /v1 routes
    bootstrap.php           autoloader
    config.example.php      copy to config.php and fill in
    .htaccess               HTTPS redirect, routing, header hardening
    core/                   Config, Database, Request, Response, Router,
                            Jwt, Crypto, Auth, Validator
    controllers/            Auth, Config(theme/settings), Resource, Sync,
                            Report, Backup
    tools/install.php       creates API client credentials
```

## Install on Hostinger

1. **Database** — create a MySQL DB in hPanel, then import `sql/schema.sql`
   through phpMyAdmin.
2. **Upload** — copy `server/api/` to `public_html/api/`.
3. **Configure** — `cp config.example.php config.php` and fill in DB
   credentials, `jwt.secret` and `encryption_key`
   (`openssl rand -hex 32` for each).
4. **API client** — run `php api/tools/install.php "QuickTap Android"`, save the
   printed App ID / Key / Secret, then delete `tools/`.
5. **Verify** — `GET https://your-domain.com/api/v1/ping` with the three headers
   should return `{"success":true,...}`.
6. **Change the default super admin password** (`superadmin` / `Admin@12345`).

## Request contract

Every request must carry:

```
X-App-Id:      app_xxxxxxxxxxxx
X-Api-Key:     <api key>
X-Api-Secret:  <secret>
Content-Type:  application/json
```

Authenticated requests add `Authorization: Bearer <access_token>`.

Every response uses the same envelope:

```json
{ "success": true, "message": "OK", "data": { }, "timestamp": 1730000000000 }
```

## Endpoints (v1)

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| GET  | `/v1/ping` | key | Health check |
| GET/POST | `/v1/version` | key | Version check, force update, maintenance mode |
| POST | `/v1/auth/login` | key | Username + password, binds device on first login |
| POST | `/v1/auth/refresh` | key | Rotate refresh token |
| GET  | `/v1/auth/me` | JWT | Current user + shop |
| POST | `/v1/auth/logout` | JWT | Revoke refresh tokens |
| POST | `/v1/auth/fingerprint` | JWT | Enable/disable fingerprint unlock |
| POST | `/v1/auth/unlock` | JWT | Password check for the lock screen |
| GET  | `/v1/theme` | JWT | Primary/secondary colour, logo, splash |
| GET  | `/v1/settings` | JWT | Theme + settings + feature toggles + notices |
| GET/POST/DELETE | `/v1/products`, `/v1/categories`, `/v1/customers`, `/v1/orders`, `/v1/expenses` | JWT | CRUD |
| GET  | `/v1/sync/pull?since=<ms>` | JWT | Delta download |
| POST | `/v1/sync/push` | JWT | Delta upload, idempotent by uuid |
| GET  | `/v1/reports/summary｜daily｜top-products｜low-stock` | JWT | Analytics |
| POST | `/v1/backup/register` · GET `/v1/backup/latest` · `/v1/backup/history` | JWT | Google Drive backup registry |

## Security model

- `password_hash()` / `password_verify()` (bcrypt) — no plaintext, ever.
- HS256 JWT access tokens (1 h) + rotating, hashed refresh tokens (30 d).
- Device binding: the device ID is HMAC-fingerprinted, never stored raw. A
  mismatch returns `DEVICE_MISMATCH`; only the super admin can reset it.
- Brute-force lockout after 5 failed logins for 15 minutes.
- API-client keys stored as SHA-256 hashes, compared with `hash_equals`.
- 100% prepared statements; no user input is ever concatenated into SQL.
- Every write endpoint runs through `Validator` (length, type, range, enum).
- Security headers + forced HTTPS at both the PHP and `.htaccess` layers.

## Sync semantics

- Client rows carry a client-generated `uuid`; `UNIQUE(shop_id, uuid)` makes
  every push idempotent — retrying a failed upload never duplicates data.
- Conflicts resolve last-write-wins on `updated_at`; rows the server considers
  newer come back in `conflicts[]` so the device re-pulls them.
- Orders are immutable: an existing order is never rewritten, only its status
  may move to `refunded` / `void`.
- Stock decrements happen inside the same transaction as the order insert.

---

# Super Admin Web Panel (`/admin`)

A dependency-free PHP + Bootstrap 5 control panel that shares the API's core
classes (`Database`, `Config`, `Crypto`), so there is one config file and one
connection layer for the whole backend.

## Deployment (Hostinger / cPanel)

```
public_html/
  api/            <- REST API (Step 1)
    config.php    <- created from config.example.php; used by BOTH api and admin
  admin/          <- this panel
```

1. Upload the `admin/` folder next to `api/`.
2. Nothing else to configure — `admin/bootstrap.php` loads `../api/config.php`.
3. Visit `https://yourdomain.com/admin/`.
4. Sign in with the seeded account `superadmin` / `Admin@12345`, then
   **immediately** change the password under *My profile*.
5. Optional: protect `/admin` with an additional server-level password.

## Pages

| Page | What it does |
|---|---|
| Dashboard | Platform KPIs, 30-day revenue chart, expiring subscriptions, top shops, live activity |
| Shops | Search/filter, create shop + owner account, suspend/activate, archive |
| Shop details | Profile & subscription, staff CRUD, device binding, theme, feature toggles, app settings, sales, backups |
| Devices | Cross-tenant device registry: reset binding, block/unblock |
| Reports | Revenue, gross profit, expenses, net, payment mix, top products, CSV export |
| Plans | Subscription plan CRUD with device/user/product limits |
| App versions | Publish versions, force-update flag, installed-version breakdown |
| Notifications | Broadcast or per-shop in-app messages with schedule window |
| Backups | Google Drive backup registry + backup-health warnings |
| Activity logs | Full audit trail, filterable by actor and shop |
| API clients | Issue / rotate / revoke app keys (hashes stored, plaintext shown once) |
| Super admins | Manage panel accounts and passwords |

## Server-driven theming

Editing a shop's theme bumps `themes.version`. The Android app compares its
cached version on each `GET /config` call and re-applies colours, app name,
logo and splash without a release.

## Panel security

- Session cookie: `HttpOnly`, `SameSite=Strict`, `Secure` over HTTPS, with
  periodic ID rotation and 30-minute idle auto-lock.
- CSRF token required on every POST (`Csrf::verify()` in the front controller).
- Login throttling: 5 failed attempts trigger a 5-minute lock.
- All output escaped through `e()`; all SQL uses prepared statements, and
  sort/filter values pass through a whitelist (`pick()`).
- `core/`, `pages/` and `views/` are blocked from direct HTTP access.
- Every mutating action is written to `activity_logs` with actor and IP.
