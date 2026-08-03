ALTER TABLE users
  ADD COLUMN riot_account_id BIGINT UNSIGNED NULL AFTER avatar_url,
  ADD UNIQUE KEY uk_users_riot_account (riot_account_id),
  ADD CONSTRAINT fk_users_riot_account
    FOREIGN KEY (riot_account_id) REFERENCES riot_accounts(id) ON DELETE SET NULL;

ALTER TABLE riot_accounts
  ADD COLUMN primary_lane ENUM('TOP','JUNGLE','MID','ADC','SUPPORT') NULL AFTER summoner_level,
  ADD COLUMN secondary_lane ENUM('TOP','JUNGLE','MID','ADC','SUPPORT') NULL AFTER primary_lane;
