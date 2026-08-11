-- match-v1
-- 양 팀장 매치 시작 합의와 수동 결과 상호 확인을 지원한다.

ALTER TABLE matches
  MODIFY COLUMN status ENUM(
    'SCHEDULED',
    'PROPOSED',
    'ACCEPTED',
    'DRAFTING',
    'READY_TO_PLAY',
    'LIVE',
    'RESULT_PENDING',
    'RESULT_DISPUTED',
    'COMPLETED',
    'CANCELLED',
    'VOIDED'
  ) NOT NULL DEFAULT 'PROPOSED';

UPDATE matches
SET status = 'PROPOSED'
WHERE status = 'SCHEDULED';

ALTER TABLE matches
  MODIFY COLUMN status ENUM(
    'PROPOSED',
    'ACCEPTED',
    'DRAFTING',
    'READY_TO_PLAY',
    'LIVE',
    'RESULT_PENDING',
    'RESULT_DISPUTED',
    'COMPLETED',
    'CANCELLED',
    'VOIDED'
  ) NOT NULL DEFAULT 'PROPOSED',
  ADD COLUMN result_proposed_by_user_id BIGINT UNSIGNED NULL AFTER winner_side,
  ADD COLUMN proposed_winner_side ENUM('BLUE','RED') NULL AFTER result_proposed_by_user_id,
  ADD COLUMN result_proposed_at DATETIME NULL AFTER proposed_winner_side,
  ADD COLUMN result_confirmed_at DATETIME NULL AFTER result_proposed_at,
  ADD KEY idx_matches_result_proposer (result_proposed_by_user_id),
  ADD CONSTRAINT fk_matches_result_proposer
    FOREIGN KEY (result_proposed_by_user_id) REFERENCES users(id) ON DELETE SET NULL;

CREATE TABLE match_start_requests (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scrim_session_id      BIGINT UNSIGNED NOT NULL,
  game_no               TINYINT UNSIGNED NOT NULL,
  proposed_by_user_id   BIGINT UNSIGNED NOT NULL,
  status                ENUM('PENDING','ACCEPTED','REJECTED','CANCELLED')
                        NOT NULL DEFAULT 'PENDING',
  responded_by_user_id  BIGINT UNSIGNED NULL,
  accepted_match_id     BIGINT UNSIGNED NULL,
  pending_session_id    BIGINT UNSIGNED
                        GENERATED ALWAYS AS (
                          CASE WHEN status = 'PENDING' THEN scrim_session_id ELSE NULL END
                        ) VIRTUAL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at          DATETIME NULL,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_start_one_pending (pending_session_id),
  UNIQUE KEY uk_match_start_accepted_match (accepted_match_id),
  KEY idx_match_start_session (scrim_session_id, created_at DESC),
  KEY idx_match_start_proposer (proposed_by_user_id),
  KEY idx_match_start_responder (responded_by_user_id),
  CONSTRAINT fk_match_start_session
    FOREIGN KEY (scrim_session_id) REFERENCES scrim_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_match_start_proposer
    FOREIGN KEY (proposed_by_user_id) REFERENCES users(id),
  CONSTRAINT fk_match_start_responder
    FOREIGN KEY (responded_by_user_id) REFERENCES users(id) ON DELETE SET NULL,
  CONSTRAINT fk_match_start_match
    FOREIGN KEY (accepted_match_id) REFERENCES matches(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE draft_rulesets
SET default_reserve_sec = 30
WHERE id = 'TOURNAMENT_STANDARD';

ALTER TABLE draft_rulesets
  MODIFY COLUMN default_reserve_sec SMALLINT UNSIGNED NOT NULL DEFAULT 30;

ALTER TABLE drafts
  MODIFY COLUMN blue_reserve_ms INT UNSIGNED NOT NULL DEFAULT 30000,
  MODIFY COLUMN red_reserve_ms INT UNSIGNED NOT NULL DEFAULT 30000;
