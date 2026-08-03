-- group-v1
-- 기존 rooms / guest_sessions 데이터를 보존하면서 그룹 권한과 팀장 초대를 확장한다.

ALTER TABLE rooms
  MODIFY COLUMN entry_code_hash VARCHAR(255) NULL COMMENT '선택 입장 암호 해시',
  ADD COLUMN guest_admission_enabled TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '신규 비회원 입장 허용 여부' AFTER guest_can_draft,
  ADD COLUMN opponent_captain_user_id BIGINT UNSIGNED NULL
    COMMENT '초대를 수락한 상대 팀장' AFTER owner_user_id,
  ADD KEY idx_rooms_opponent_captain (opponent_captain_user_id),
  ADD CONSTRAINT fk_rooms_opponent_captain
    FOREIGN KEY (opponent_captain_user_id) REFERENCES users(id) ON DELETE SET NULL;

UPDATE rooms
SET guest_admission_enabled = 1
WHERE guest_admission_enabled IS NULL;

CREATE TABLE room_memberships (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id    BIGINT UNSIGNED NOT NULL,
  user_id    BIGINT UNSIGNED NOT NULL,
  role       ENUM('GROUP_OWNER','GROUP_MANAGER','GROUP_MEMBER') NOT NULL,
  is_active  TINYINT(1) NOT NULL DEFAULT 1,
  joined_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_memberships_room_user (room_id, user_id),
  KEY idx_room_memberships_user (user_id, is_active),
  CONSTRAINT fk_room_memberships_room
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
  CONSTRAINT fk_room_memberships_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO room_memberships (room_id, user_id, role, is_active)
SELECT id, owner_user_id, 'GROUP_OWNER', 1
FROM rooms
ON DUPLICATE KEY UPDATE role = 'GROUP_OWNER', is_active = 1;

CREATE TABLE room_captain_invitations (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id         BIGINT UNSIGNED NOT NULL,
  invitee_user_id BIGINT UNSIGNED NOT NULL,
  invited_by_user_id BIGINT UNSIGNED NOT NULL,
  status          ENUM('PENDING','ACCEPTED','REJECTED','CANCELLED','EXPIRED')
                  NOT NULL DEFAULT 'PENDING',
  expires_at      DATETIME NOT NULL,
  responded_at    DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_captain_invites_invitee (invitee_user_id, status, created_at),
  KEY idx_captain_invites_room (room_id, status, created_at),
  CONSTRAINT fk_captain_invites_room
    FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE,
  CONSTRAINT fk_captain_invites_invitee
    FOREIGN KEY (invitee_user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_captain_invites_inviter
    FOREIGN KEY (invited_by_user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

