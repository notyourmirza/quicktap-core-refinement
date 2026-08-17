# QuickTap POS — REST API Documentation (v1)

Complete reference for building **any** client (Android, iOS, web, desktop) on top of
this API. Nothing here is Android-specific — the same contract works for every app.

- **Base URL:** `https://your-domain.com/api/v1`
- **Format:** JSON in, JSON out (`Content-Type: application/json`)
- **Transport:** HTTPS only (`HTTPS_REQUIRED` error otherwise)
- **Auth:** API-client keys (app level) + JWT access token (user level)
- **Multi-tenant:** every row is scoped by `shop_id` derived from the token — a client
  can never read another shop's data.

---

## 1. Authentication model

Two layers, both required for protected endpoints.

### Layer 1 — App credentials (every single request)

| Header | Required | Example |
|---|---|---|
| `X-App-Id` | ✅ | `com.quicktap.pos` |
| `X-Api-Key` | ✅ | `9f3c…` (48 hex chars) |
| `X-Api-Secret` | ✅ | `a71b…` (64 hex chars) |
| `X-App-Version` | optional | `2.0.0` |
| `X-Device-Id` | optional | your device fingerprint |
| `Content-Type` | on POST | `application/json` |

Issue these in **Super Admin → API clients** (or `php api/tools/install.php "My App"`).
Only SHA-256 hashes are stored server-side, so the plain values are shown once.

> Building a second app? Create a **separate API client** (own App ID/key/secret) so you
> can revoke or rotate it independently. The data and endpoints stay identical.

### Layer 2 — User session (JWT)

`POST /auth/login` returns an **access token** (HS256 JWT, 1 h) and a **refresh token**
(opaque random string, 30 d, single-use / rotating).

```
Authorization: Bearer <access_token>
```

Access-token claims: `sub` (user id), `shop` (shop id), `role`, `dev` (device
fingerprint), `typ: "access"`, `iat`, `exp`.

**Recommended client flow**

1. Call the endpoint with the current access token.
2. On `401` with `code` = `TOKEN_EXPIRED` or `NO_TOKEN`, call `/auth/refresh` once.
3. Retry the original request with the new access token.
4. If refresh also fails (`REFRESH_INVALID`) → clear session, show login.

### Device binding

The first successful login stores `HMAC(device_id)` on the user. Any later login from a
different device returns `403 DEVICE_MISMATCH` until a super admin resets the binding.
Send a **stable per-installation ID** as `device_id` (Android: `SSAID` hashed; web: a UUID
persisted in storage).

---

## 2. Response envelope

Every response — success or failure — uses the same shape.

**Success**
```json
{
  "success": true,
  "message": "OK",
  "data": { },
  "timestamp": 1730000000000
}
```

**Error**
```json
{
  "success": false,
  "message": "Session expired, please sign in again",
  "code": "TOKEN_EXPIRED",
  "detail": null,
  "timestamp": 1730000000000
}
```

`data` may be an object, an array, or `null`. Always branch on `success`, and use `code`
(not `message`) for logic — messages are user-facing text and can change.

### Error codes

| HTTP | `code` | Meaning / client action |
|---|---|---|
| 400 | `VALIDATION_FAILED` | `detail` lists the invalid fields |
| 401 | `NO_API_KEY` / `BAD_APP` / `BAD_API_KEY` / `BAD_API_SECRET` | app credentials wrong — do not retry |
| 401 | `BAD_CREDENTIALS` | wrong username/password |
| 401 | `NO_TOKEN` / `TOKEN_EXPIRED` | refresh, then retry once |
| 401 | `REFRESH_INVALID` | force re-login |
| 403 | `HTTPS_REQUIRED` | call over HTTPS |
| 403 | `USER_DISABLED` | account disabled by admin |
| 403 | `SHOP_INACTIVE` | subscription expired/suspended |
| 403 | `DEVICE_MISMATCH` | bound to another device |
| 403 | `FORBIDDEN` | role not allowed |
| 404 | `NOT_FOUND` | unknown route |
| 429 | `LOCKED` | 5 failed logins → 15-minute lockout |
| 500 | `SERVER_ERROR` | retry with backoff |

CORS is open (`Access-Control-Allow-Origin: *`) with the custom headers whitelisted, so a
browser/PWA client works without a proxy.

---

## 3. Endpoint reference

Legend — **Auth:** `key` = app headers only · `jwt` = app headers + Bearer token.

| Method | Path | Auth |
|---|---|---|
| GET | `/ping` | key |
| GET/POST | `/version` | key |
| POST | `/auth/login` | key |
| POST | `/auth/refresh` | key |
| GET | `/auth/me` | jwt |
| POST | `/auth/logout` | jwt |
| POST | `/auth/fingerprint` | jwt |
| POST | `/auth/unlock` | jwt |
| GET | `/theme` | jwt |
| GET | `/settings` | jwt |
| GET/POST | `/products` · DELETE `/products/{uuid}` | jwt |
| GET/POST | `/categories` | jwt |
| GET/POST | `/customers` | jwt |
| GET/POST | `/orders` | jwt |
| GET/POST | `/expenses` | jwt |
| GET | `/sync/pull` · POST `/sync/push` | jwt |
| GET | `/reports/summary` `/reports/daily` `/reports/top-products` `/reports/low-stock` | jwt |
| POST | `/backup/register` · GET `/backup/latest` `/backup/history` · POST `/backup/restored` | jwt |

---

### 3.1 Health & version

**`GET /ping`** → `{"status":"up","version":"1.0.0"}`

**`GET|POST /version`** — force-update and maintenance gate. Call on every app start.

Request (query or body): `version_code` (int, your build's versionCode).

```json
{
  "latest_version_code": 12,
  "latest_version_name": "2.1.0",
  "update_available": true,
  "force_update": false,
  "changelog": "Faster sync…",
  "download_url": "https://…/app.apk",
  "maintenance_mode": false,
  "maintenance_message": ""
}
```
If `force_update` is true, block the UI until the user updates. If `maintenance_mode` is
true, show `maintenance_message` and stop network work.

---

### 3.2 Auth

**`POST /auth/login`**
```json
{
  "username": "owner",
  "password": "secret",
  "device_id": "stable-installation-id",
  "device_name": "Pixel 8",
  "app_version": "2.0.0",
  "os_version": "Android 14"
}
```
`data`:
```json
{
  "user": { "id": 4, "username": "owner", "name": "Ali Raza", "role": "owner",
            "fingerprint_enabled": false, "device_bound": true, "first_login": true },
  "shop": { "id": 2, "uuid": "…", "name": "Ali Mart", "currency": "PKR",
            "subscription_ends_at": "2026-12-31 00:00:00" },
  "tokens": { "access_token": "eyJ…", "refresh_token": "3f8a…",
              "token_type": "Bearer", "expires_in": 3600 }
}
```
Roles: `owner`, `manager`, `cashier`.

**`POST /auth/refresh`** — body `{ "refresh_token": "…" }` → `{ "tokens": { … } }`.
The old refresh token is revoked immediately (rotation); always persist the new one.

**`GET /auth/me`** — current user + shop + plan name.

**`POST /auth/logout`** — revokes all refresh tokens for the user.

**`POST /auth/fingerprint`** — `{ "enabled": true }` → stores the biometric-unlock flag
server-side so it survives reinstall.

**`POST /auth/unlock`** — `{ "password": "…" }` → `200` when correct, `401
BAD_CREDENTIALS` otherwise. Use for the auto-lock screen without a full re-login.

---

### 3.3 Server-driven branding & settings

**`GET /theme`**
```json
{ "theme_key": "material_you",
  "primary_color": "#6750A4", "secondary_color": "#7D5260",
  "logo_url": null, "splash_url": null, "app_name": "QuickTap POS",
  "version": 3, "updated_at": "2026-07-11 09:20:00" }
```
`theme_key` is assigned by the Super Admin only and selects one of the ten premium
design languages: `material_you`, `minimal_luxury`, `glassmorphism`, `neo_banking`,
`dark_pro`, `modern_retail`, `elegant_business`, `soft_pastel`, `black_gold`,
`futuristic_ai`. The device applies the whole design (surfaces, ink, radius,
elevation, navigation, dialogs, charts) without any functional change.

Cache it and compare `version`; re-apply colours/logo/app name only when it increases —
no app release needed to rebrand.

**`GET /settings`** → `{ "theme": {…}, "settings": {…}, "features": {…}, "notices": [] }`

- `settings` — typed key/value map (`int`, `bool`, `json`, `string` cast server-side),
  e.g. `currency_symbol`, `tax_percent`, `receipt_footer`, `auto_lock_minutes`.
- `features` — `{ "expenses": true, "customers": false, … }`; hide UI when false.
- `notices` — in-app messages: `id, title, body, level (info|warning|critical), starts_at, ends_at`.

Shop-specific rows override global defaults automatically.

---

### 3.4 CRUD resources

All rows are identified by a **client-generated UUID** (v4). Saves are upserts:
`INSERT … ON DUPLICATE KEY UPDATE` on `(shop_id, uuid)` — so retrying is always safe.
Deletes are soft (`deleted_at`).

**`GET /products?q=&limit=200&offset=0`** → `{ "items": [...], "total": 128 }`

**`POST /products`**
```json
{ "uuid": "1f0c…", "name": "Coke 500ml", "sku": "CK500", "barcode": "890…",
  "category_uuid": "aa11…", "price": 120, "cost_price": 95, "stock": 40,
  "tax_percent": 0, "track_stock": true, "is_active": true, "image_url": null }
```

**`DELETE /products/{uuid}`** → soft delete.

**`GET /categories`** · **`POST /categories`** `{ uuid, name, sort_order }`

**`GET /customers?q=`** · **`POST /customers`** `{ uuid, name, phone, email, address, balance }`

**`GET /orders?from=YYYY-MM-DD&to=YYYY-MM-DD`** → orders with a nested `items[]` array.

**`POST /orders`**
```json
{ "uuid": "9b2f…", "invoice_no": "INV-1042", "customer_uuid": null,
  "subtotal": 480, "discount": 30, "tax": 0, "total": 450,
  "paid": 500, "change_due": 50, "payment_method": "cash",
  "items": [
    { "product_uuid": "1f0c…", "name": "Coke 500ml", "qty": 2,
      "unit_price": 120, "discount": 0, "tax_percent": 0, "line_total": 240 }
  ] }
```
`payment_method` ∈ `cash|card|wallet|credit|mixed`. The insert and the stock decrement
run in one transaction. Posting the same `uuid` twice returns
`{ "uuid": "…", "duplicate": true }` instead of creating a second order — orders are
immutable once recorded.

**`GET /expenses`** · **`POST /expenses`** `{ uuid, title, category, amount, note, spent_at }`

---

### 3.5 Sync (offline-first clients)

Use this instead of the CRUD endpoints for bulk device traffic.

**`GET /sync/pull?since=<epoch_ms>`** — everything changed after `since` (`0` = full seed).
```json
{
  "server_time": 1730000000000,
  "since": 1729900000000,
  "changes": { "categories": [], "products": [], "customers": [], "expenses": [], "orders": [] },
  "settings": { }, "theme": { }, "features": { }
}
```
Persist `server_time` and send it as the next `since`. Limits: 5000 rows per entity
(2000 for orders) — if you hit the limit, pull again with the newest `updated_at`.

**`POST /sync/push`** — batch upload, keyed by entity:
```json
{
  "products":   [ { "uuid": "…", "name": "…", "updated_at": 1729999999000, "deleted": false } ],
  "categories": [ ], "customers": [ ], "expenses": [ ], "orders": [ ]
}
```
Response:
```json
{ "server_time": 1730000001000,
  "accepted":  { "products": ["…"] },
  "conflicts": { "products": ["…"] } }
```
Rules:
- Idempotent by `uuid` — safe to replay a failed batch.
- Conflict resolution is **last-write-wins on `updated_at`**. A client row older than the
  server row is rejected and its uuid returned in `conflicts` → re-pull those rows.
- Send `deleted: true` for tombstones instead of removing rows locally only.
- Timestamps accept epoch ms, epoch s, or `YYYY-MM-DD HH:MM:SS`.

**Recommended client loop:** push local dirty rows → pull with the last `server_time` →
apply → store the new `server_time`. Run on app start, after each sale, and every ~15 min.

---

### 3.6 Reports

| Endpoint | Query | Returns |
|---|---|---|
| `GET /reports/summary` | `from`, `to` (default: this month) | `orders, revenue, discount, tax, avg_ticket, cogs, expenses, net_profit` |
| `GET /reports/daily` | `days` (1–180, default 30) | `[{ day, orders, revenue }]` |
| `GET /reports/top-products` | `limit` (1–100, default 20) | `[{ name, product_uuid, qty, revenue }]` |
| `GET /reports/low-stock` | `threshold` (default 5) | `[{ uuid, name, stock, price }]` |

Only orders with `status = completed` count toward revenue and profit.

---

### 3.7 Backup registry

The encrypted database blob lives in the user's own Google Drive `appDataFolder`; the
server stores only metadata so a reinstalled device can find the latest backup.

**`POST /backup/register`**
```json
{ "file_name": "quicktap-2026-08-01.db", "file_id": "1AbC…", "provider": "gdrive",
  "kind": "auto", "size_bytes": 184320, "checksum": "sha256…", "encrypted": true }
```
`provider` ∈ `gdrive|server`, `kind` ∈ `auto|manual`.

**`GET /backup/latest`** → `{ "backup": {…}|null, "restore_available": true }`
**`GET /backup/history`** → last 50 backups.
**`POST /backup/restored`** → `{ "backup_id": 12 }` (audit trail only).

---

## 4. Building a new app on this API — checklist

1. **Issue credentials** in Super Admin → API clients (one client per app).
2. **Store them outside source control** (Android `buildConfigField` / server env var).
   Never ship the secret in a public web bundle — for browser clients, proxy the three
   headers through your own tiny backend.
3. **Implement one HTTP wrapper** that:
   - attaches the 3 app headers + `X-Device-Id` + `X-App-Version` on every call,
   - attaches `Authorization: Bearer` when a session exists,
   - auto-refreshes once on `TOKEN_EXPIRED`/`NO_TOKEN` and retries,
   - parses the envelope into `{success, message, code, data}`.
   (Reference implementation: `android/app/src/main/java/com/quicktap/pos/net/ApiClient.java`.)
4. **Boot sequence:** `GET /version` → gate on force-update/maintenance → restore session
   or `POST /auth/login` → `GET /settings` (theme + features) → `GET /sync/pull?since=0`.
5. **Generate UUIDs client-side** for every row you create; never rely on server IDs.
6. **Handle the 4 blocking codes** explicitly in the UI: `DEVICE_MISMATCH`,
   `SHOP_INACTIVE`, `USER_DISABLED`, `LOCKED`.
7. **Respect `features`** — hide modules the admin turned off rather than erroring.

## 5. Quick test with cURL

```bash
BASE=https://your-domain.com/api/v1
H=(-H "X-App-Id: com.quicktap.pos" -H "X-Api-Key: KEY" -H "X-Api-Secret: SECRET" \
   -H "Content-Type: application/json")

curl "${H[@]}" $BASE/ping

TOKEN=$(curl -s "${H[@]}" -X POST $BASE/auth/login \
  -d '{"username":"owner","password":"secret","device_id":"dev-001"}' \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')

curl "${H[@]}" -H "Authorization: Bearer $TOKEN" $BASE/settings
curl "${H[@]}" -H "Authorization: Bearer $TOKEN" "$BASE/sync/pull?since=0"
```

## 6. Rate limits & operational notes

- Login: 5 failed attempts → 15-minute lock per user (`LOCKED`).
- Access token 1 h, refresh token 30 d (configurable in `api/config.php`:
  `jwt.access_ttl`, `jwt.refresh_ttl`, `max_login_attempts`, `lockout_minutes`).
- All SQL uses prepared statements; every write passes a validator (type, length, range, enum).
- Every mutating action is written to `activity_logs` with actor, shop, IP — visible in
  Super Admin → Activity logs.
