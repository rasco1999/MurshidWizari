-- Run once with a database backup and the same MariaDB/MySQL connection used by the platform.
CREATE TABLE IF NOT EXISTS murshid_ios_push_tokens (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id INT UNSIGNED NOT NULL,
 token CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 environment ENUM('production','sandbox') NOT NULL DEFAULT 'production',
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uq_murshid_apns_token(token),
 KEY idx_murshid_apns_user(user_id),
 FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS murshid_school_push_events (
 id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
 ad_id BIGINT UNSIGNED NOT NULL,
 available_at DATETIME NOT NULL,
 cursor_token_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
 status ENUM('pending','working','done') NOT NULL DEFAULT 'pending',
 locked_until DATETIME NULL,
 attempts SMALLINT UNSIGNED NOT NULL DEFAULT 0,
 sent_count INT UNSIGNED NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_murshid_school_push_pending(status,available_at,locked_until),
 FOREIGN KEY (ad_id) REFERENCES school_ads(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
