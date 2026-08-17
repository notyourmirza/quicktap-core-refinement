-- ============================================================================
--  QuickTap POS — Licence / Security upgrade (v3)
--  SAFE, ADDITIVE migration. No table is dropped, no row is deleted.
--  Run once against the existing database:
--      mysql -u USER -p DBNAME < server/sql/migration_license_v3.sql
--  It is idempotent: re-running it does nothing harmful.
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. Licences — the ONLY authority for whether an account is unlocked.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS licenses (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id        INT UNSIGNED NOT NULL,
  user_id        INT UNSIGNED NULL,
  device_id      VARCHAR(191) NULL,                 -- hashed installation id
  license_key    VARCHAR(64)  NOT NULL,
  plan_id        INT UNSIGNED NULL,
  status         ENUM('PENDING','ACTIVE','EXPIRED','REVOKED','BLOCKED') NOT NULL DEFAULT 'PENDING',
  duration_days  INT UNSIGNED NOT NULL DEFAULT 0,   -- 0 = lifetime
  activated_at   DATETIME NULL,
  expires_at     DATETIME NULL,                     -- always server-calculated
  issued_by      INT UNSIGNED NULL,                 -- super_admins.id
  note           VARCHAR(400) NULL,
  last_verified_at DATETIME NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_licenses_key (license_key),
  KEY ix_licenses_shop (shop_id),
  KEY ix_licenses_user (user_id),
  KEY ix_licenses_status (status),
  CONSTRAINT fk_licenses_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 2. Licence requests — created automatically on registration.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS license_requests (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  shop_id       INT UNSIGNED NOT NULL,
  user_id       INT UNSIGNED NULL,
  device_id     VARCHAR(191) NULL,
  device_name   VARCHAR(191) NULL,
  app_version   VARCHAR(40)  NULL,
  os_version    VARCHAR(40)  NULL,
  requested_plan_id INT UNSIGNED NULL,
  status        ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  note          VARCHAR(400) NULL,
  handled_by    INT UNSIGNED NULL,
  handled_at    DATETIME NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_lreq_status (status, created_at),
  KEY ix_lreq_shop (shop_id),
  CONSTRAINT fk_lreq_shop FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. Device -> account binding, GLOBAL (one device = one new account).
--    `devices` stays as-is (per-shop history); this table is the hard rule.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_accounts (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  device_id   VARCHAR(191) NOT NULL,                -- hashed installation id
  shop_id     INT UNSIGNED NOT NULL,
  user_id     INT UNSIGNED NULL,
  device_name VARCHAR(191) NULL,
  app_version VARCHAR(40)  NULL,
  os_version  VARCHAR(40)  NULL,
  status      ENUM('active','released','blocked') NOT NULL DEFAULT 'active',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_device_accounts_device (device_id),
  KEY ix_device_accounts_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. Credits audit trail (users.credits holds the current balance).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS credit_transactions (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    INT UNSIGNED NOT NULL,
  shop_id    INT UNSIGNED NULL,
  admin_id   INT UNSIGNED NULL,
  action     ENUM('add','remove','set','system') NOT NULL DEFAULT 'set',
  old_value  INT NOT NULL DEFAULT 0,
  new_value  INT NOT NULL DEFAULT 0,
  difference INT NOT NULL DEFAULT 0,
  reason     VARCHAR(400) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_credit_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. Super Admin audit log (old value / new value granularity).
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_audit_logs (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  admin_id   INT UNSIGNED NULL,
  action     VARCHAR(80) NOT NULL,
  entity     VARCHAR(60) NULL,
  entity_id  VARCHAR(60) NULL,
  old_value  TEXT NULL,
  new_value  TEXT NULL,
  ip         VARCHAR(45) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_audit_action (action, created_at),
  KEY ix_audit_admin (admin_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. API rate limiting buckets.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS api_rate_limits (
  bucket       VARCHAR(191) NOT NULL,
  window_start DATETIME NOT NULL,
  hits         INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. Additive columns (guarded so re-running is safe on MySQL 5.7/8.0).
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS qt_add_column_v3;
DELIMITER //
CREATE PROCEDURE qt_add_column_v3(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl TEXT)
BEGIN
  IF (SELECT COUNT(*) FROM information_schema.COLUMNS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = tbl AND COLUMN_NAME = col) = 0 THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END //
DELIMITER ;

CALL qt_add_column_v3('users', 'credits',        'credits INT NOT NULL DEFAULT 0');
CALL qt_add_column_v3('users', 'is_blocked',     'is_blocked TINYINT(1) NOT NULL DEFAULT 0');
CALL qt_add_column_v3('users', 'license_confirmed_at', 'license_confirmed_at DATETIME NULL');
CALL qt_add_column_v3('shops', 'license_status', "license_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'");

DROP PROCEDURE IF EXISTS qt_add_column_v3;

-- ---------------------------------------------------------------------------
-- 8. Backward compatibility — migrate EXISTING active subscriptions into the
--    new licences table so no current customer loses access. Nothing is
--    expired by the upgrade: existing end dates are preserved verbatim.
-- ---------------------------------------------------------------------------
INSERT INTO licenses (shop_id, user_id, device_id, license_key, plan_id, status,
                      duration_days, activated_at, expires_at, note)
SELECT s.id,
       (SELECT u.id FROM users u WHERE u.shop_id = s.id AND u.deleted_at IS NULL
         ORDER BY (u.role = 'owner') DESC, u.id ASC LIMIT 1),
       (SELECT u2.device_id FROM users u2 WHERE u2.shop_id = s.id AND u2.device_id IS NOT NULL
         ORDER BY u2.id ASC LIMIT 1),
       CONCAT('LEGACY-', LPAD(s.id, 6, '0')),
       s.plan_id,
       CASE
         WHEN s.status = 'active' AND (s.subscription_ends_at IS NULL OR s.subscription_ends_at >= CURDATE()) THEN 'ACTIVE'
         WHEN s.status = 'suspended' THEN 'REVOKED'
         WHEN s.status = 'expired' THEN 'EXPIRED'
         ELSE 'PENDING'
       END,
       CASE WHEN s.subscription_ends_at IS NULL OR s.subscription_starts_at IS NULL THEN 0
            ELSE GREATEST(0, DATEDIFF(s.subscription_ends_at, s.subscription_starts_at)) END,
       COALESCE(s.subscription_starts_at, s.created_at),
       CASE WHEN s.subscription_ends_at IS NULL THEN NULL
            ELSE TIMESTAMP(s.subscription_ends_at, '23:59:59') END,
       'Imported from subscription during v3 upgrade'
  FROM shops s
 WHERE s.deleted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM licenses l WHERE l.shop_id = s.id);

UPDATE shops s
   SET s.license_status = COALESCE(
       (SELECT l.status FROM licenses l WHERE l.shop_id = s.id ORDER BY l.id DESC LIMIT 1),
       'PENDING');

-- Existing bound devices become the authoritative global binding.
INSERT IGNORE INTO device_accounts (device_id, shop_id, user_id, device_name, status)
SELECT u.device_id, u.shop_id, u.id, u.device_name, 'active'
  FROM users u
 WHERE u.device_id IS NOT NULL AND u.device_id <> '' AND u.deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- 9. Default settings consumed by the app (support number etc.).
-- ---------------------------------------------------------------------------
INSERT INTO app_settings (shop_id, setting_key, value, value_type)
VALUES (NULL, 'support_whatsapp', '923000000000', 'string')
ON DUPLICATE KEY UPDATE setting_key = setting_key;

INSERT INTO app_settings (shop_id, setting_key, value, value_type)
VALUES (NULL, 'license_sync_minutes', '60', 'int')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
