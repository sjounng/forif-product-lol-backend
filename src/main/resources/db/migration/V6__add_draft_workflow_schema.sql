-- draft-v1
-- 단일 브라우저에서 READY부터 선수 배정 확정까지 복원 가능한 Draft 상태를 저장한다.

ALTER TABLE drafts
  MODIFY COLUMN status ENUM(
    'WAITING',
    'READY',
    'IN_PROGRESS',
    'ASSIGNING',
    'PAUSED',
    'TECHNICAL_PAUSED',
    'COMPLETED',
    'ABORTED'
  ) NOT NULL DEFAULT 'WAITING',
  ADD COLUMN assignment_deadline_at DATETIME(3) NULL AFTER turn_deadline_at,
  ADD COLUMN blue_assignment_confirmed TINYINT(1) NOT NULL DEFAULT 0 AFTER red_ready,
  ADD COLUMN red_assignment_confirmed TINYINT(1) NOT NULL DEFAULT 0 AFTER blue_assignment_confirmed;

ALTER TABLE draft_actions
  ADD COLUMN player_id BIGINT UNSIGNED NULL AFTER champion_id,
  ADD KEY idx_da_player (player_id),
  ADD CONSTRAINT fk_da_player
    FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE SET NULL;

CREATE TABLE draft_assignments (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  draft_id            BIGINT UNSIGNED NOT NULL,
  side                ENUM('BLUE','RED') NOT NULL,
  player_id           BIGINT UNSIGNED NOT NULL,
  champion_id         SMALLINT UNSIGNED NOT NULL,
  assigned_by_user_id BIGINT UNSIGNED NULL,
  is_auto             TINYINT(1) NOT NULL DEFAULT 0,
  created_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                      ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_draft_assignment_player (draft_id, player_id),
  UNIQUE KEY uk_draft_assignment_champion (draft_id, champion_id),
  KEY idx_draft_assignment_actor (assigned_by_user_id),
  CONSTRAINT fk_draft_assignment_draft
    FOREIGN KEY (draft_id) REFERENCES drafts(id) ON DELETE CASCADE,
  CONSTRAINT fk_draft_assignment_player
    FOREIGN KEY (player_id) REFERENCES players(id),
  CONSTRAINT fk_draft_assignment_champion
    FOREIGN KEY (champion_id) REFERENCES champions(id),
  CONSTRAINT fk_draft_assignment_actor
    FOREIGN KEY (assigned_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
