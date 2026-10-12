-- Apply after 050. Safe to execute again if a previous run succeeded.
-- Adds the optional end timestamp to older deployments of free-seat campaign.
SET @murshid_end_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'murshid_free_month_campaigns'
    AND COLUMN_NAME = 'ends_at_utc'
);
SET @murshid_end_ddl = IF(@murshid_end_exists = 0,
  'ALTER TABLE murshid_free_month_campaigns ADD COLUMN ends_at_utc DATETIME NULL DEFAULT NULL AFTER starts_at_utc',
  'SELECT 1');
PREPARE murshid_end_statement FROM @murshid_end_ddl;
EXECUTE murshid_end_statement;
DEALLOCATE PREPARE murshid_end_statement;

-- Default to one week for existing campaigns; the administrator may change it.
UPDATE murshid_free_month_campaigns
SET ends_at_utc = DATE_ADD(starts_at_utc, INTERVAL 7 DAY)
WHERE id = 1 AND ends_at_utc IS NULL;

-- Some older site databases did not record user registration timestamps.
-- Never mark existing accounts as newly created at migration time.
SET @murshid_user_created_exists = (
  SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'users'
    AND COLUMN_NAME = 'created_at'
);
SET @murshid_user_ddl = IF(@murshid_user_created_exists = 0,
  'ALTER TABLE users ADD COLUMN created_at DATETIME NOT NULL DEFAULT ''2000-01-01 00:00:00''',
  'SELECT 1');
PREPARE murshid_user_statement FROM @murshid_user_ddl;
EXECUTE murshid_user_statement;
DEALLOCATE PREPARE murshid_user_statement;

-- Only future sign-ups receive the live creation time. Preserve an existing
-- site's column definition if it was already present before this migration.
SET @murshid_created_default = (
  SELECT COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'created_at'
);
SET @murshid_user_default_ddl = IF(@murshid_user_created_exists = 0
  OR @murshid_created_default LIKE '2000-01-01%',
  'ALTER TABLE users ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP',
  'SELECT 1');
PREPARE murshid_user_default_statement FROM @murshid_user_default_ddl;
EXECUTE murshid_user_default_statement;
DEALLOCATE PREPARE murshid_user_default_statement;
