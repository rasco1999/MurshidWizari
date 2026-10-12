-- Run ONCE on mur-iq.com in phpMyAdmin before uploading the new endpoints.
-- All campaign times are stored in UTC. The iPhone admin chooses Baghdad time.
CREATE TABLE IF NOT EXISTS murshid_free_month_campaigns (
  id TINYINT UNSIGNED NOT NULL PRIMARY KEY,
  enabled TINYINT(1) NOT NULL DEFAULT 0,
  starts_at_utc DATETIME NOT NULL,
  capacity SMALLINT UNSIGNED NOT NULL DEFAULT 100,
  claimed_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  updated_by INT UNSIGNED NULL,
  updated_at_utc DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS murshid_free_month_claims (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  campaign_id TINYINT UNSIGNED NOT NULL,
  user_id INT UNSIGNED NOT NULL,
  seat_number SMALLINT UNSIGNED NOT NULL,
  claimed_at_utc DATETIME NOT NULL,
  subscription_expires_at DATETIME NOT NULL,
  UNIQUE KEY uq_free_month_once_per_student (user_id),
  UNIQUE KEY uq_free_month_seat (campaign_id, seat_number),
  KEY idx_free_month_campaign (campaign_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO murshid_free_month_campaigns
  (id,enabled,starts_at_utc,capacity,claimed_count,updated_at_utc)
VALUES (1,0,'2099-01-01 00:00:00',100,0,UTC_TIMESTAMP());
