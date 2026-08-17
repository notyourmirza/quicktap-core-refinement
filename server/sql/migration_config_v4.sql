-- ---------------------------------------------------------------------------
--  QuickTap POS — migration v4: licence SUSPENDED state + remote configuration
--
--  Safe to run on a live database: it only ADDS values and settings rows.
--  No table is dropped and no existing row is deleted.
-- ---------------------------------------------------------------------------

-- 1. Licence status gains SUSPENDED (temporary hold, distinct from BLOCKED).
ALTER TABLE licenses
  MODIFY status ENUM('PENDING','ACTIVE','EXPIRED','REVOKED','SUSPENDED','BLOCKED')
  NOT NULL DEFAULT 'PENDING';

-- 2. Remote configuration keys consumed by the Android bootstrap endpoint
--    (/v1/app/bootstrap). These are non-secret client identifiers only:
--    never database credentials, admin passwords or JWT signing secrets.
INSERT IGNORE INTO app_settings (shop_id, setting_key, value, value_type) VALUES
  (NULL, 'api_base_url',         'https://aifinzo.pro/api/', 'string'),
  (NULL, 'firebase_project_id',  '', 'string'),
  (NULL, 'firebase_app_id',      '', 'string'),
  (NULL, 'firebase_api_key',     '', 'string'),
  (NULL, 'firebase_sender_id',   '', 'string');

-- 3. Support number stays server-owned (created by migration_license_v3.sql).
--    The app has no hard-coded fallback: an empty value renders
--    "contact support unavailable" instead of a fake number.
