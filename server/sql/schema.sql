-- ============================================================================
--  QuickTap POS — Enterprise MySQL Schema (v2)
--  Target: MySQL 5.7+/8.0 (Hostinger shared hosting compatible)
--  Engine: InnoDB, utf8mb4, foreign keys, soft deletes, sync-friendly columns
-- ============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. Subscription plans
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plans (
  id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
  code            VARCHAR(40)  NOT NULL,
  name            VARCHAR(120) NOT NULL,
  tagline         VARCHAR(200) NULL,
  tag             VARCHAR(40)  NULL,
  price           DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  price_yearly    DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  billing_cycle   ENUM('monthly','yearly','lifetime') NOT NULL DEFAULT 'monthly',
  max_devices     INT UNSIGNED NOT NULL DEFAULT 1,
  max_users       INT UNSIGNED NOT NULL DEFAULT 3,
  max_products    INT UNSIGNED NOT NULL DEFAULT 1000,
  features_json   JSON NULL,
  is_active       TINYINT(1) NOT NULL DEFAULT 1,
  sort_order      INT UNSIGNED NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_plans_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 2. Shops (tenants)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS shops (
  id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
  uuid            CHAR(36) NOT NULL,
  name            VARCHAR(160) NOT NULL,
  owner_name      VARCHAR(160) NULL,
  phone           VARCHAR(40)  NULL,
  email           VARCHAR(190) NULL,
  address         VARCHAR(400) NULL,
  currency        VARCHAR(10)  NOT NULL DEFAULT 'Rs',
  plan_id         INT UNSIGNED NULL,
  status          ENUM('active','suspended','expired','pending') NOT NULL DEFAULT 'pending',
  subscription_starts_at DATE NULL,
  subscription_ends_at   DATE NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at      DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_shops_uuid (uuid),
  KEY ix_shops_status (status),
  KEY ix_shops_plan (plan_id),
  CONSTRAINT fk_shops_plan FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. Users (shop staff) — username + password, device bound
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
  id              INT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id         INT UNSIGNED NOT NULL,
  username        VARCHAR(80)  NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  full_name       VARCHAR(160) NULL,
  role            ENUM('owner','manager','cashier') NOT NULL DEFAULT 'cashier',
  device_id       VARCHAR(191) NULL,           -- encrypted device fingerprint
  device_name     VARCHAR(191) NULL,
  device_bound_at DATETIME NULL,
  fingerprint_enabled TINYINT(1) NOT NULL DEFAULT 0,
  is_active       TINYINT(1) NOT NULL DEFAULT 1,
  last_login_at   DATETIME NULL,
  failed_attempts INT UNSIGNED NOT NULL DEFAULT 0,
  locked_until    DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted_at      DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_shop_username (shop_id, username),
  KEY ix_users_device (device_id),
  CONSTRAINT fk_users_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. Super admins (web panel)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS super_admins (
  id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  username      VARCHAR(80)  NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  full_name     VARCHAR(160) NULL,
  email         VARCHAR(190) NULL,
  is_active     TINYINT(1) NOT NULL DEFAULT 1,
  last_login_at DATETIME NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_super_admins_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. Devices (binding + reset audit)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS devices (
  id             INT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id        INT UNSIGNED NOT NULL,
  user_id        INT UNSIGNED NULL,
  device_id      VARCHAR(191) NOT NULL,
  device_name    VARCHAR(191) NULL,
  app_version    VARCHAR(40)  NULL,
  os_version     VARCHAR(40)  NULL,
  status         ENUM('active','reset','blocked') NOT NULL DEFAULT 'active',
  last_seen_at   DATETIME NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_devices_shop_device (shop_id, device_id),
  KEY ix_devices_user (user_id),
  CONSTRAINT fk_devices_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
  CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. Refresh tokens (JWT rotation)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     INT UNSIGNED NOT NULL,
  token_hash  CHAR(64) NOT NULL,
  device_id   VARCHAR(191) NULL,
  expires_at  DATETIME NOT NULL,
  revoked_at  DATETIME NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_refresh_hash (token_hash),
  KEY ix_refresh_user (user_id),
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. Theme (server-driven colors) — one row per shop, shop_id NULL = global
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS themes (
  id                INT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id           INT UNSIGNED NULL,
  theme_key         VARCHAR(40) NOT NULL DEFAULT 'material_you', -- premium design language assigned by Super Admin
  primary_color     CHAR(7) NOT NULL DEFAULT '#0E9F6E',
  secondary_color   CHAR(7) NOT NULL DEFAULT '#34D399',
  logo_url          VARCHAR(400) NULL,
  splash_url        VARCHAR(400) NULL,
  app_name          VARCHAR(80)  NOT NULL DEFAULT 'QuickTap POS',
  receipt_template  VARCHAR(40)  NOT NULL DEFAULT 'classic', -- receipt design published by Super Admin
  version           INT UNSIGNED NOT NULL DEFAULT 1,  -- bumped on change; app syncs when higher
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_themes_shop (shop_id),
  CONSTRAINT fk_themes_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 8. App settings (key/value; shop_id NULL = global default)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_settings (
  id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id     INT UNSIGNED NULL,
  setting_key VARCHAR(80) NOT NULL,
  value       TEXT NULL,
  value_type  ENUM('string','int','bool','json') NOT NULL DEFAULT 'string',
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_settings_shop_key (shop_id, setting_key),
  CONSTRAINT fk_settings_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 9. Feature toggles
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS feature_toggles (
  id          INT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id     INT UNSIGNED NULL,
  feature_key VARCHAR(80) NOT NULL,
  enabled     TINYINT(1) NOT NULL DEFAULT 1,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_toggle_shop_key (shop_id, feature_key),
  CONSTRAINT fk_toggle_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 10. App versions (force update)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_versions (
  id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  version_code  INT UNSIGNED NOT NULL,
  version_name  VARCHAR(40) NOT NULL,
  min_supported_code INT UNSIGNED NOT NULL DEFAULT 1,
  force_update  TINYINT(1) NOT NULL DEFAULT 0,
  changelog     TEXT NULL,
  download_url  VARCHAR(400) NULL,
  released_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_versions_code (version_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 11. Catalog: categories + products
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id     INT UNSIGNED NOT NULL,
  uuid        CHAR(36) NOT NULL,          -- client-generated, sync key
  name        VARCHAR(120) NOT NULL,
  sort_order  INT NOT NULL DEFAULT 0,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at  DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_categories_shop_uuid (shop_id, uuid),
  KEY ix_categories_updated (shop_id, updated_at),
  CONSTRAINT fk_categories_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id       INT UNSIGNED NOT NULL,
  uuid          CHAR(36) NOT NULL,
  category_uuid CHAR(36) NULL,
  name          VARCHAR(190) NOT NULL,
  sku           VARCHAR(80)  NULL,
  barcode       VARCHAR(80)  NULL,
  price         DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  cost_price    DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  stock         DECIMAL(12,3) NOT NULL DEFAULT 0.000,
  track_stock   TINYINT(1) NOT NULL DEFAULT 1,
  tax_percent   DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  image_url     VARCHAR(400) NULL,
  is_active     TINYINT(1) NOT NULL DEFAULT 1,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at    DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_products_shop_uuid (shop_id, uuid),
  KEY ix_products_updated (shop_id, updated_at),
  KEY ix_products_barcode (shop_id, barcode),
  KEY ix_products_category (shop_id, category_uuid),
  CONSTRAINT fk_products_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 12. Customers
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id     INT UNSIGNED NOT NULL,
  uuid        CHAR(36) NOT NULL,
  name        VARCHAR(160) NOT NULL,
  phone       VARCHAR(40)  NULL,
  email       VARCHAR(190) NULL,
  address     VARCHAR(400) NULL,
  balance     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at  DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_customers_shop_uuid (shop_id, uuid),
  KEY ix_customers_phone (shop_id, phone),
  KEY ix_customers_updated (shop_id, updated_at),
  CONSTRAINT fk_customers_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 13. Orders + items (idempotent upload via uuid)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS orders (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id        INT UNSIGNED NOT NULL,
  uuid           CHAR(36) NOT NULL,
  invoice_no     VARCHAR(60) NOT NULL,
  customer_uuid  CHAR(36) NULL,
  user_id        INT UNSIGNED NULL,
  device_id      VARCHAR(191) NULL,
  subtotal       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  discount       DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  tax            DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  total          DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  paid           DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  change_due     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  payment_method ENUM('cash','card','wallet','credit','mixed') NOT NULL DEFAULT 'cash',
  status         ENUM('completed','refunded','void') NOT NULL DEFAULT 'completed',
  note           VARCHAR(400) NULL,
  ordered_at     DATETIME NOT NULL,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at     DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_orders_shop_uuid (shop_id, uuid),
  KEY ix_orders_date (shop_id, ordered_at),
  KEY ix_orders_updated (shop_id, updated_at),
  KEY ix_orders_customer (shop_id, customer_uuid),
  CONSTRAINT fk_orders_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_items (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id     BIGINT UNSIGNED NOT NULL,
  shop_id      INT UNSIGNED NOT NULL,
  product_uuid CHAR(36) NULL,
  name         VARCHAR(190) NOT NULL,
  qty          DECIMAL(12,3) NOT NULL DEFAULT 1.000,
  unit_price   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  discount     DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  tax_percent  DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  line_total   DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  PRIMARY KEY (id),
  KEY ix_items_order (order_id),
  KEY ix_items_product (shop_id, product_uuid),
  CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 14. Expenses
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS expenses (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id     INT UNSIGNED NOT NULL,
  uuid        CHAR(36) NOT NULL,
  title       VARCHAR(190) NOT NULL,
  category    VARCHAR(80)  NULL,
  amount      DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  note        VARCHAR(400) NULL,
  spent_at    DATETIME NOT NULL,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted_at  DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_expenses_shop_uuid (shop_id, uuid),
  KEY ix_expenses_date (shop_id, spent_at),
  KEY ix_expenses_updated (shop_id, updated_at),
  CONSTRAINT fk_expenses_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 15. Backups (Google Drive metadata)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS backups (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id       INT UNSIGNED NOT NULL,
  user_id       INT UNSIGNED NULL,
  device_id     VARCHAR(191) NULL,
  provider      ENUM('gdrive','server') NOT NULL DEFAULT 'gdrive',
  file_id       VARCHAR(191) NULL,
  file_name     VARCHAR(191) NOT NULL,
  size_bytes    BIGINT UNSIGNED NOT NULL DEFAULT 0,
  checksum      CHAR(64) NULL,
  encrypted     TINYINT(1) NOT NULL DEFAULT 1,
  kind          ENUM('auto','manual') NOT NULL DEFAULT 'auto',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_backups_shop (shop_id, created_at),
  CONSTRAINT fk_backups_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 16. Activity logs + notifications
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS activity_logs (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id    INT UNSIGNED NULL,
  actor_type ENUM('user','admin','system') NOT NULL DEFAULT 'user',
  actor_id   INT UNSIGNED NULL,
  action     VARCHAR(80) NOT NULL,
  entity     VARCHAR(60) NULL,
  entity_id  VARCHAR(60) NULL,
  meta_json  JSON NULL,
  ip         VARCHAR(45) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_logs_shop_date (shop_id, created_at),
  KEY ix_logs_action (action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id    INT UNSIGNED NULL,             -- NULL = broadcast to all shops
  title      VARCHAR(190) NOT NULL,
  body       TEXT NOT NULL,
  level      ENUM('info','warning','critical') NOT NULL DEFAULT 'info',
  starts_at  DATETIME NULL,
  ends_at    DATETIME NULL,
  is_active  TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_notif_shop (shop_id, is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 17. API clients (encrypted keys per application)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_clients (
  id             INT UNSIGNED NOT NULL AUTO_INCREMENT,
  app_id         VARCHAR(80) NOT NULL,
  name           VARCHAR(120) NOT NULL,
  api_key_hash   CHAR(64) NOT NULL,   -- sha256(api_key)
  secret_hash    CHAR(64) NOT NULL,   -- sha256(secret_key)
  is_active      TINYINT(1) NOT NULL DEFAULT 1,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_api_clients_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
--  SEED DATA
-- ============================================================================
INSERT IGNORE INTO plans (id, code, name, price, billing_cycle, max_devices, max_users, max_products)
VALUES (1,'trial','Trial',0.00,'monthly',1,2,200),
       (2,'standard','Standard',999.00,'monthly',2,5,5000),
       (3,'enterprise','Enterprise',2999.00,'yearly',10,50,100000);

-- Default super admin — username: superadmin / password: Admin@12345
-- CHANGE THIS PASSWORD IMMEDIATELY AFTER FIRST LOGIN.
INSERT IGNORE INTO super_admins (id, username, password_hash, full_name, email)
-- Default password: Admin@12345  (CHANGE IT AFTER FIRST LOGIN)
VALUES (1,'superadmin','$2y$12$XkCt7SVoyNw.2dVKRBP42.7ERwvmABMHe20LnkX.LCckXXNYk807G','Super Admin','admin@example.com');

-- Global theme defaults (shop_id NULL)
INSERT IGNORE INTO themes (id, shop_id, theme_key, primary_color, secondary_color, app_name, receipt_template, version)
VALUES (1, NULL, 'material_you', '#0E9F6E', '#34D399', 'QuickTap POS', 'classic', 1);

-- Global app settings defaults
INSERT IGNORE INTO app_settings (shop_id, setting_key, value, value_type) VALUES
  (NULL,'idle_timeout_minutes','20','int'),
  (NULL,'auto_lock_enabled','1','bool'),
  (NULL,'fingerprint_enabled','1','bool'),
  (NULL,'maintenance_mode','0','bool'),
  (NULL,'maintenance_message','We are performing scheduled maintenance. Please try again shortly.','string'),
  (NULL,'sync_interval_minutes','15','int'),
  (NULL,'backup_enabled','1','bool'),
  (NULL,'backup_frequency_days','7','int'),
  (NULL,'receipt_footer','Thank you! Please visit again.','string'),
  (NULL,'paper_chars','32','int'),
  (NULL,'currency','Rs','string');

INSERT IGNORE INTO feature_toggles (shop_id, feature_key, enabled) VALUES
  (NULL,'expenses',1),
  (NULL,'customers',1),
  (NULL,'reports',1),
  (NULL,'gdrive_backup',1),
  (NULL,'thermal_printing',1),
  (NULL,'barcode_scanner',1);

INSERT IGNORE INTO app_versions (version_code, version_name, min_supported_code, force_update, changelog)
VALUES (2,'2.0.0',2,0,'Enterprise redesign: Material 3 UI, server themes, offline sync, Drive backup.');

-- ---------------------------------------------------------------------------
-- 18. Splash screen configuration (global row shop_id NULL + per-shop overrides)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS splash_config (
  id             INT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id        INT UNSIGNED NULL,
  enabled        TINYINT(1) NOT NULL DEFAULT 1,
  title          VARCHAR(120) NOT NULL DEFAULT 'QuickTap POS',
  tagline        VARCHAR(200) NOT NULL DEFAULT 'Fast. Simple. Reliable.',
  credit_text    VARCHAR(120) NOT NULL DEFAULT 'MA Technologies',
  credit_prefix  VARCHAR(60)  NOT NULL DEFAULT 'Powered by',
  logo_url       VARCHAR(400) NULL,
  background_color CHAR(7) NOT NULL DEFAULT '#0B0F19',
  text_color       CHAR(7) NOT NULL DEFAULT '#FFFFFF',
  accent_color     CHAR(7) NOT NULL DEFAULT '#0E9F6E',
  animation      ENUM('fade','zoom','slide_up','pulse','rotate') NOT NULL DEFAULT 'fade',
  duration_ms    INT UNSIGNED NOT NULL DEFAULT 1800,
  show_credit    TINYINT(1) NOT NULL DEFAULT 1,
  show_progress  TINYINT(1) NOT NULL DEFAULT 1,
  version        INT UNSIGNED NOT NULL DEFAULT 1,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_splash_shop (shop_id),
  CONSTRAINT fk_splash_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO splash_config (id, shop_id, enabled, title, tagline, credit_text, credit_prefix, version)
VALUES (1, NULL, 1, 'QuickTap POS', 'Fast. Simple. Reliable.', 'MA Technologies', 'Powered by', 1);

-- ---------------------------------------------------------------------------
-- 20. Marketplace / purchase requests (2026_08_10_marketplace.sql)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS market_requests (
  id            INT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id       INT UNSIGNED NULL,
  user_id       INT UNSIGNED NULL,
  item_code     VARCHAR(80)  NOT NULL,
  item_name     VARCHAR(160) NOT NULL,
  quantity      INT UNSIGNED NOT NULL DEFAULT 1,
  unit_price    DECIMAL(12,2) NOT NULL DEFAULT 0,
  total_price   DECIMAL(12,2) NOT NULL DEFAULT 0,
  contact_name  VARCHAR(120) NOT NULL,
  contact_phone VARCHAR(40)  NOT NULL,
  address       TEXT NULL,
  note          TEXT NULL,
  status        ENUM('new','contacted','approved','rejected','completed') NOT NULL DEFAULT 'new',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_market_status (status),
  KEY idx_market_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 21. WhatsApp support + marketplace catalog settings (2026_08_10_marketplace.sql)
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO app_settings (shop_id, setting_key, value, value_type) VALUES
  (NULL,'support_whatsapp','923000000000','string'),
  (NULL,'marketplace_catalog','[]','json');

