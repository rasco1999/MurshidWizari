-- Run once on the same MySQL/MariaDB database as the Murshid website.
-- This new table does not alter or erase the website's legacy activation_codes table.
-- Only a SHA-256 digest of each cryptographically random code is stored.
CREATE TABLE IF NOT EXISTS subscription_access_codes (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    months SMALLINT UNSIGNED NOT NULL,
    status ENUM('available', 'redeemed', 'revoked') NOT NULL DEFAULT 'available',
    redeemed_by INT UNSIGNED NULL,
    redeemed_at DATETIME NULL,
    valid_until DATETIME NULL,
    batch_label VARCHAR(80) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_subscription_access_code_hash (code_hash),
    KEY idx_subscription_access_status (status, valid_until),
    KEY idx_subscription_access_user (redeemed_by, redeemed_at),
    CONSTRAINT fk_subscription_access_user FOREIGN KEY (redeemed_by)
        REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
