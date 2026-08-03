-- session-v1
-- 기존 세션/매치 기록은 보존하고 새 세션부터 고정 5인 로스터와 팀장 합의를 사용한다.

ALTER TABLE players
  ADD COLUMN member_user_id BIGINT UNSIGNED NULL
    COMMENT '그룹 회원에서 생성된 선수' AFTER room_id,
  ADD COLUMN guest_session_id BIGINT UNSIGNED NULL
    COMMENT '그룹 게스트에서 생성된 선수' AFTER member_user_id,
  ADD UNIQUE KEY uk_players_room_member (room_id, member_user_id),
  ADD UNIQUE KEY uk_players_room_guest (room_id, guest_session_id),
  ADD CONSTRAINT fk_players_member_user
    FOREIGN KEY (member_user_id) REFERENCES users(id) ON DELETE SET NULL,
  ADD CONSTRAINT fk_players_guest_session
    FOREIGN KEY (guest_session_id) REFERENCES guest_sessions(id) ON DELETE SET NULL;

UPDATE scrim_sessions
SET fearless_mode = 'GLOBAL_FEARLESS'
WHERE fearless_mode = 'FEARLESS';

ALTER TABLE scrim_sessions
  MODIFY COLUMN fearless_mode ENUM('NONE','GLOBAL_FEARLESS','HARD_FEARLESS')
    NOT NULL DEFAULT 'NONE';

ALTER TABLE scrim_sessions
  MODIFY COLUMN status ENUM(
    'OPEN','PREPARING','PROPOSED','CONFIRMED','IN_PROGRESS','FINISHED','CANCELLED'
  ) NOT NULL DEFAULT 'PREPARING';

UPDATE scrim_sessions
SET status = 'PREPARING'
WHERE status = 'OPEN';

ALTER TABLE scrim_sessions
  MODIFY COLUMN status ENUM(
    'PREPARING','PROPOSED','CONFIRMED','IN_PROGRESS','FINISHED','CANCELLED'
  ) NOT NULL DEFAULT 'PREPARING';

ALTER TABLE scrim_sessions
  ADD COLUMN match_format ENUM('BEST_OF_3','BEST_OF_5','UNLIMITED')
    NOT NULL DEFAULT 'BEST_OF_3' AFTER name,
  ADD COLUMN created_by_user_id BIGINT UNSIGNED NULL
    COMMENT '세션 제안을 만든 그룹 팀장' AFTER room_id,
  ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER rating_enabled,
  ADD COLUMN proposed_at DATETIME NULL AFTER rejection_reason,
  ADD COLUMN confirmed_at DATETIME NULL AFTER proposed_at,
  ADD COLUMN active_room_id BIGINT UNSIGNED
    GENERATED ALWAYS AS (
      CASE
        WHEN status IN ('PREPARING','PROPOSED','CONFIRMED','IN_PROGRESS') THEN room_id
        ELSE NULL
      END
    ) VIRTUAL,
  ADD UNIQUE KEY uk_sessions_one_active_room (active_room_id),
  ADD KEY idx_sessions_creator (created_by_user_id);

ALTER TABLE scrim_sessions
  ADD CONSTRAINT fk_sessions_creator
    FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE session_teams (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scrim_session_id   BIGINT UNSIGNED NOT NULL,
  side               ENUM('BLUE','RED') NOT NULL,
  captain_user_id    BIGINT UNSIGNED NOT NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_teams_side (scrim_session_id, side),
  UNIQUE KEY uk_session_teams_captain (scrim_session_id, captain_user_id),
  CONSTRAINT fk_session_teams_session
    FOREIGN KEY (scrim_session_id) REFERENCES scrim_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_session_teams_captain
    FOREIGN KEY (captain_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE session_team_members (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scrim_session_id   BIGINT UNSIGNED NOT NULL,
  side               ENUM('BLUE','RED') NOT NULL,
  player_id          BIGINT UNSIGNED NOT NULL,
  lane               ENUM('TOP','JUNGLE','MID','ADC','SUPPORT') NOT NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_team_members_player (scrim_session_id, player_id),
  UNIQUE KEY uk_session_team_members_lane (scrim_session_id, side, lane),
  KEY idx_session_team_members_player (player_id),
  CONSTRAINT fk_session_team_members_session
    FOREIGN KEY (scrim_session_id) REFERENCES scrim_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_session_team_members_player
    FOREIGN KEY (player_id) REFERENCES players(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
