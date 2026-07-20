-- =====================================================================
--  LoL 내전 플랫폼 DB 스키마  v2
--  Target: MySQL 8.0+ / MariaDB 10.6+  (InnoDB, utf8mb4)
--
--  구조 요약
--    room          = 커뮤니티(영속 그룹). 관리자 1명 소유. 점수가 누적되는 단위
--    scrim_session = 내전 회차(하루 자리). ★ 피어리스 소진 범위 ★
--    match         = 세션 안의 1게임
--    draft         = 매치의 밴픽
--
--  권한
--    관리자(users)  : 로그인 필요. 방/명단/세션/팀구성 관리. Riot API 조회 주체
--    게스트         : 로그인 불필요. 방 입장 코드로 진입 → 밴픽 관전 or 밴픽 참여
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
--  0. 마스터
-- =====================================================================

CREATE TABLE champions (
  id              SMALLINT UNSIGNED NOT NULL COMMENT 'Riot champion key (266=Aatrox)',
  riot_id         VARCHAR(32)  NOT NULL COMMENT 'Riot id 문자열 (Aatrox)',
  name_ko         VARCHAR(32)  NOT NULL,
  name_en         VARCHAR(32)  NOT NULL,
  tags            JSON         NULL,
  image_url       VARCHAR(255) NULL,
  ddragon_version VARCHAR(16)  NULL,
  is_enabled      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '삭제 대신 소프트 비활성 (과거 전적 FK 보호)',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_champions_riot_id (riot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  1. 계정 (방 관리자만 로그인)
-- =====================================================================

CREATE TABLE users (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255) NOT NULL,
  password_hash VARCHAR(255) NULL COMMENT '소셜 전용이면 NULL',
  display_name  VARCHAR(50)  NOT NULL,
  avatar_url    VARCHAR(255) NULL,
  status        ENUM('ACTIVE','SUSPENDED','DELETED') NOT NULL DEFAULT 'ACTIVE',
  email_verified_at DATETIME NULL,
  last_login_at DATETIME NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_oauth_identities (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  provider     ENUM('GOOGLE','KAKAO','DISCORD','NAVER','RIOT') NOT NULL,
  provider_uid VARCHAR(191) NOT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_oauth_provider_uid (provider, provider_uid),
  KEY idx_oauth_user (user_id),
  CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_sessions (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id            BIGINT UNSIGNED NOT NULL,
  refresh_token_hash CHAR(64) NOT NULL,
  user_agent         VARCHAR(255) NULL,
  ip                 VARBINARY(16) NULL,
  expires_at         DATETIME NOT NULL,
  revoked_at         DATETIME NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_sessions_token (refresh_token_hash),
  KEY idx_user_sessions_user (user_id, expires_at),
  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  2. 방 (= 커뮤니티). 점수·명단·전적이 누적되는 영속 단위
-- =====================================================================

CREATE TABLE rooms (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_user_id    BIGINT UNSIGNED NOT NULL COMMENT '방 관리자 (단 1명)',
  name             VARCHAR(100) NOT NULL,
  description      VARCHAR(500) NULL,

  -- 입장: 공개 URL(/r/{public_code}) + 입장 코드 입력
  public_code      CHAR(8)      NOT NULL COMMENT 'URL 슬러그. 노출돼도 무방',
  entry_code_hash  VARCHAR(255) NOT NULL COMMENT '입장 코드 bcrypt/argon2 해시. 평문 저장 금지',
  entry_code_hint  VARCHAR(20)  NULL COMMENT '관리자 UI 표시용 마스킹 (예: A1**5)',
  entry_code_rotated_at DATETIME NULL,

  guest_can_draft  TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0이면 게스트는 관전만',
  team_size        TINYINT UNSIGNED NOT NULL DEFAULT 5,

  -- 점수/밸런싱 기본 설정 (세션에서 override 가능)
  rating_config    JSON NULL COMMENT '{"formulaVersion":"v1","kBase":32,"perfFactor":false,...}',
  balance_config   JSON NULL COMMENT '{"maxTotalDiff":150,"maxLaneDiff":300,"laneMult":{...}}',

  status           ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rooms_public_code (public_code),
  KEY idx_rooms_owner (owner_user_id, status),
  CONSTRAINT fk_rooms_owner FOREIGN KEY (owner_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- (선택) 만료형 초대 링크: 입장 코드를 알려주지 않고 원클릭 입장시킬 때
CREATE TABLE room_invites (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id    BIGINT UNSIGNED NOT NULL,
  token      CHAR(32) NOT NULL,
  label      VARCHAR(50) NULL COMMENT '"디코 공지용" 등 메모',
  max_uses   INT UNSIGNED NULL COMMENT 'NULL=무제한',
  used_count INT UNSIGNED NOT NULL DEFAULT 0,
  expires_at DATETIME NULL,
  revoked_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_room_invites_token (token),
  KEY idx_room_invites_room (room_id, revoked_at),
  CONSTRAINT fk_invites_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 입장 코드로 들어온 비로그인 접속자 (브라우저 1개 = 1행, 쿠키에 token 저장)
CREATE TABLE guest_sessions (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id        BIGINT UNSIGNED NOT NULL,
  token          CHAR(64) NOT NULL COMMENT '쿠키 저장. 새로고침/재접속 시 동일인 복원',
  nickname       VARCHAR(50) NULL COMMENT '게스트가 입력한 표시 이름',
  -- 이 접속자가 명단의 누구인지 (관리자가 매핑하거나 게스트가 self-claim)
  player_id      BIGINT UNSIGNED NULL,
  entered_via    ENUM('ENTRY_CODE','INVITE') NOT NULL DEFAULT 'ENTRY_CODE',
  invite_id      BIGINT UNSIGNED NULL,
  is_banned      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '관리자가 강퇴',
  ip             VARBINARY(16) NULL,
  last_seen_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  expires_at     DATETIME NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_guest_token (token),
  KEY idx_guest_room (room_id, last_seen_at DESC),
  KEY idx_guest_player (player_id),
  CONSTRAINT fk_guest_room   FOREIGN KEY (room_id)   REFERENCES rooms(id)        ON DELETE CASCADE,
  CONSTRAINT fk_guest_invite FOREIGN KEY (invite_id) REFERENCES room_invites(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  3. Riot 계정 / 솔랭 티어  (관리자가 Riot API로 조회·등록)
-- =====================================================================

CREATE TABLE riot_accounts (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  puuid           VARCHAR(78) NOT NULL,
  platform        ENUM('KR','JP1','NA1','EUW1','EUN1','BR1','OC1','TR1','RU','LA1','LA2','PH2','SG2','TH2','TW2','VN2')
                  NOT NULL DEFAULT 'KR',
  game_name       VARCHAR(32) NOT NULL COMMENT 'Riot ID 앞부분',
  tag_line        VARCHAR(10) NOT NULL COMMENT 'Riot ID 태그',
  summoner_id     VARCHAR(64) NULL,
  profile_icon_id INT NULL,
  summoner_level  INT NULL,
  last_synced_at  DATETIME NULL,
  sync_status     ENUM('OK','NOT_FOUND','RATE_LIMITED','ERROR') NOT NULL DEFAULT 'OK',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_riot_puuid (puuid),
  UNIQUE KEY uk_riot_name (platform, game_name, tag_line)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 솔랭 티어 스냅샷 (시드 점수의 원천 + 이력)
CREATE TABLE riot_rank_snapshots (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  riot_account_id BIGINT UNSIGNED NOT NULL,
  queue_type      ENUM('RANKED_SOLO_5x5','RANKED_FLEX_SR') NOT NULL DEFAULT 'RANKED_SOLO_5x5',
  season          VARCHAR(16) NULL,
  tier            ENUM('UNRANKED','IRON','BRONZE','SILVER','GOLD','PLATINUM','EMERALD','DIAMOND',
                       'MASTER','GRANDMASTER','CHALLENGER') NOT NULL,
  rank_division   ENUM('I','II','III','IV') NULL COMMENT '마스터 이상은 NULL',
  league_points   SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  wins            SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  losses          SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  ladder_score    INT NOT NULL DEFAULT 0 COMMENT '티어 환산 점수 0~4000 (설계문서 §4.1)',
  captured_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rank_snap (riot_account_id, queue_type, captured_at DESC),
  CONSTRAINT fk_rank_snap_account FOREIGN KEY (riot_account_id) REFERENCES riot_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  4. 플레이어 (방 명단). 관리자가 Riot ID로 등록. 로그인 계정 없음.
-- =====================================================================

CREATE TABLE players (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id         BIGINT UNSIGNED NOT NULL,
  riot_account_id BIGINT UNSIGNED NULL COMMENT 'Riot 조회 실패 시 NULL 허용(수동 등록)',
  display_name    VARCHAR(50) NOT NULL COMMENT '방에서 부르는 이름',
  memo            VARCHAR(255) NULL COMMENT '관리자 메모',
  is_active       TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=명단에서 내림 (전적은 보존)',
  added_by_user_id BIGINT UNSIGNED NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- 같은 방에 같은 Riot 계정 중복 등록 금지 (NULL은 중복 허용)
  UNIQUE KEY uk_players_room_riot (room_id, riot_account_id),
  KEY idx_players_room (room_id, is_active),
  CONSTRAINT fk_players_room  FOREIGN KEY (room_id)         REFERENCES rooms(id)         ON DELETE CASCADE,
  CONSTRAINT fk_players_riot  FOREIGN KEY (riot_account_id) REFERENCES riot_accounts(id) ON DELETE SET NULL,
  CONSTRAINT fk_players_addedby FOREIGN KEY (added_by_user_id) REFERENCES users(id)      ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE guest_sessions
  ADD CONSTRAINT fk_guest_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE SET NULL;


-- =====================================================================
--  5. 점수 (방 단위 누적)
-- =====================================================================

CREATE TABLE player_ratings (
  player_id      BIGINT UNSIGNED NOT NULL,
  room_id        BIGINT UNSIGNED NOT NULL,
  rating         INT NOT NULL DEFAULT 1500,
  rd             SMALLINT UNSIGNED NOT NULL DEFAULT 350 COMMENT '불확실성. 클수록 K가 커짐',
  peak_rating    INT NOT NULL DEFAULT 1500,
  games_played   INT UNSIGNED NOT NULL DEFAULT 0,
  wins           INT UNSIGNED NOT NULL DEFAULT 0,
  losses         INT UNSIGNED NOT NULL DEFAULT 0,
  win_streak     SMALLINT NOT NULL DEFAULT 0 COMMENT '음수면 연패',
  seed_source    ENUM('SOLO_RANK','FLEX_RANK','MANUAL','DEFAULT') NOT NULL DEFAULT 'DEFAULT',
  seed_rating    INT NULL COMMENT '최초 시드 (수식 재보정용 원본)',
  is_locked      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '관리자 수동 고정',
  last_played_at DATETIME NULL,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id),
  KEY idx_ratings_leaderboard (room_id, rating DESC),
  CONSTRAINT fk_ratings_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
  CONSTRAINT fk_ratings_room   FOREIGN KEY (room_id)   REFERENCES rooms(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 라인별 점수: 오프롤 보호의 근거. 정글 몇 판 뛴 게 서폿 점수를 오염시키지 않음
CREATE TABLE player_lane_ratings (
  player_id        BIGINT UNSIGNED NOT NULL,
  lane             ENUM('TOP','JUNGLE','MID','ADC','SUPPORT') NOT NULL,
  room_id          BIGINT UNSIGNED NOT NULL,
  rating           INT NOT NULL DEFAULT 1500,
  rd               SMALLINT UNSIGNED NOT NULL DEFAULT 350,
  games_played     INT UNSIGNED NOT NULL DEFAULT 0,
  wins             INT UNSIGNED NOT NULL DEFAULT 0,
  losses           INT UNSIGNED NOT NULL DEFAULT 0,
  self_proficiency TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=불가(배정 금지), 1~5',
  PRIMARY KEY (player_id, lane),
  KEY idx_lane_ratings_board (room_id, lane, rating DESC),
  CONSTRAINT fk_lane_ratings_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
  CONSTRAINT fk_lane_ratings_room   FOREIGN KEY (room_id)   REFERENCES rooms(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 점수 변동 이력. 수식 v2 전환 시 전체 재계산의 근거이자 "왜 이만큼 올랐냐"의 답
CREATE TABLE rating_history (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  player_id       BIGINT UNSIGNED NOT NULL,
  room_id         BIGINT UNSIGNED NOT NULL,
  match_id        BIGINT UNSIGNED NULL,
  scope           ENUM('OVERALL','LANE') NOT NULL DEFAULT 'OVERALL',
  lane            ENUM('TOP','JUNGLE','MID','ADC','SUPPORT') NULL,
  reason          ENUM('MATCH','SEED','MANUAL_ADJUST','DECAY','RECALC') NOT NULL,
  rating_before   INT NOT NULL,
  rating_after    INT NOT NULL,
  delta           INT NOT NULL,
  rd_before       SMALLINT UNSIGNED NULL,
  rd_after        SMALLINT UNSIGNED NULL,
  expected_score  DECIMAL(6,5) NULL COMMENT '기대 승률 E',
  k_factor        DECIMAL(6,2) NULL,
  off_role_factor DECIMAL(4,3) NULL,
  perf_factor     DECIMAL(4,3) NULL,
  formula_version VARCHAR(16) NOT NULL DEFAULT 'v1',
  note            VARCHAR(255) NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_rh_player (player_id, created_at DESC),
  KEY idx_rh_match (match_id),
  CONSTRAINT fk_rh_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
  CONSTRAINT fk_rh_room   FOREIGN KEY (room_id)   REFERENCES rooms(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  6. 내전 세션 (회차)  ★ 피어리스 소진 범위 = 이 테이블 ★
--     "오늘 저녁 내전" 1자리 = scrim_sessions 1행
-- =====================================================================

CREATE TABLE scrim_sessions (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id       BIGINT UNSIGNED NOT NULL,
  name          VARCHAR(100) NULL COMMENT '"7/14 정기 내전"',
  -- 피어리스
  --   NONE            : 일반
  --   FEARLESS        : 팀별 소진 (블루가 뽑은 챔프는 블루만 못 씀)
  --   GLOBAL_FEARLESS : 양 팀 통틀어 소진
  --   HARD_FEARLESS   : 픽 + 밴 모두 소진
  fearless_mode ENUM('NONE','FEARLESS','GLOBAL_FEARLESS','HARD_FEARLESS') NOT NULL DEFAULT 'NONE',
  status        ENUM('OPEN','IN_PROGRESS','FINISHED','CANCELLED') NOT NULL DEFAULT 'OPEN',
  rating_enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '0=친선(점수 미반영)',
  -- 방 설정 override
  balance_config JSON NULL,
  game_count    TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '진행된 게임 수 (캐시)',
  started_at    DATETIME NULL,
  ended_at      DATETIME NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sessions_room (room_id, status, created_at DESC),
  CONSTRAINT fk_sessions_room FOREIGN KEY (room_id) REFERENCES rooms(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 이번 회차 참가 명단 (방 명단의 부분집합). 10명 초과 허용 → 게임마다 로테이션
CREATE TABLE session_players (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scrim_session_id  BIGINT UNSIGNED NOT NULL,
  player_id         BIGINT UNSIGNED NOT NULL,
  --   ROSTERED : 참가 등록 (대기 포함)
  --   BENCHED  : 이번 게임은 쉼 (후보)
  --   PLAYING  : 이번 게임 출전 중
  --   WITHDRAWN: 중도 이탈 (이후 게임 배정 제외)
  status            ENUM('ROSTERED','BENCHED','PLAYING','WITHDRAWN') NOT NULL DEFAULT 'ROSTERED',
  primary_lane      ENUM('TOP','JUNGLE','MID','ADC','SUPPORT','FILL') NULL,
  secondary_lane    ENUM('TOP','JUNGLE','MID','ADC','SUPPORT','FILL') NULL,
  lane_pool         JSON NULL COMMENT '{"TOP":5,"MID":3,"SUPPORT":0} 라인별 가능도 0~5. 0=배정 금지',

  -- 로테이션 형평성: 11명 이상일 때 "누구를 다음 판에 넣을지" 판단 근거
  games_played      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '이번 세션에서 뛴 판수',
  games_benched     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '이번 세션에서 쉰 판수',
  last_played_game_no TINYINT UNSIGNED NULL COMMENT '마지막 출전 game_no. NULL=아직 못 뜀',
  bench_priority    SMALLINT NOT NULL DEFAULT 0 COMMENT '수동 우선순위. 클수록 먼저 투입',

  -- 세션 시작 시점 점수 스냅샷: 게임이 진행돼 점수가 흔들려도 팀 구성 기준을 고정
  entry_rating      INT NULL,
  entry_snapshot_id BIGINT UNSIGNED NULL COMMENT '당시 솔랭 스냅샷',
  joined_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sp_session_player (scrim_session_id, player_id),
  KEY idx_sp_player (player_id),
  KEY idx_sp_snapshot (entry_snapshot_id),
  -- 다음 게임 투입 순번 조회: 적게 뛴 사람 우선
  KEY idx_sp_rotation (scrim_session_id, status, games_played, bench_priority DESC),
  CONSTRAINT fk_sp_session  FOREIGN KEY (scrim_session_id) REFERENCES scrim_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_sp_player   FOREIGN KEY (player_id)        REFERENCES players(id)        ON DELETE CASCADE,
  CONSTRAINT fk_sp_snapshot FOREIGN KEY (entry_snapshot_id) REFERENCES riot_rank_snapshots(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  7. 팀 자동 구성
-- =====================================================================

CREATE TABLE balance_runs (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scrim_session_id      BIGINT UNSIGNED NOT NULL,
  target_game_no        TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '세션 내 몇 번째 게임용 구성인지',
  requested_by_user_id  BIGINT UNSIGNED NULL,
  algorithm             ENUM('BRUTE_FORCE','SIMULATED_ANNEALING','GREEDY','MANUAL') NOT NULL DEFAULT 'BRUTE_FORCE',
  params_json           JSON NOT NULL COMMENT '실행 당시 제약/가중치 스냅샷',
  pool_json             JSON NOT NULL COMMENT '이번 판에 투입할 10인의 유효점수·라인풀 스냅샷 (재현용)',
  relaxed               TINYINT(1) NOT NULL DEFAULT 0 COMMENT '제약을 완화해서 풀었는지',
  relax_note            VARCHAR(255) NULL COMMENT '"라인 차이 제한 300→350 완화"',
  candidate_count       SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  selected_candidate_id BIGINT UNSIGNED NULL,
  elapsed_ms            INT UNSIGNED NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_br_session (scrim_session_id, created_at DESC),
  CONSTRAINT fk_br_session FOREIGN KEY (scrim_session_id) REFERENCES scrim_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_br_user    FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE balance_candidates (
  id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  balance_run_id         BIGINT UNSIGNED NOT NULL,
  rank_no                SMALLINT UNSIGNED NOT NULL COMMENT '1이 최적',
  cost                   DECIMAL(10,3) NOT NULL COMMENT '목적함수 (낮을수록 좋음)',
  total_diff             INT NOT NULL COMMENT '팀 총합 점수 차',
  max_lane_diff          INT NOT NULL COMMENT '라인별 점수 차의 최댓값',
  off_role_count         TINYINT UNSIGNED NOT NULL DEFAULT 0,
  predicted_blue_winrate DECIMAL(5,4) NULL,
  assignment_json        JSON NOT NULL
    COMMENT '[{"playerId":1,"side":"BLUE","lane":"TOP","effRating":1620,"assignedFrom":"PRIMARY"}, ...]',
  created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bc_run_rank (balance_run_id, rank_no),
  CONSTRAINT fk_bc_run FOREIGN KEY (balance_run_id) REFERENCES balance_runs(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE balance_runs
  ADD CONSTRAINT fk_br_selected FOREIGN KEY (selected_candidate_id)
  REFERENCES balance_candidates(id) ON DELETE SET NULL;


-- =====================================================================
--  8. 매치 (세션 안의 1게임)
-- =====================================================================

CREATE TABLE matches (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  scrim_session_id     BIGINT UNSIGNED NOT NULL,
  room_id              BIGINT UNSIGNED NOT NULL COMMENT '통계 조회용 비정규화',
  game_no              TINYINT UNSIGNED NOT NULL COMMENT '세션 내 N번째 게임 (피어리스 순번)',
  balance_candidate_id BIGINT UNSIGNED NULL,
  is_manual_team       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '수동 팀 구성',
  status               ENUM('SCHEDULED','DRAFTING','LIVE','COMPLETED','CANCELLED','VOIDED')
                       NOT NULL DEFAULT 'SCHEDULED',
  winner_side          ENUM('BLUE','RED') NULL,
  riot_match_id        VARCHAR(32) NULL COMMENT '인게임 매치 연동 시',
  duration_sec         INT UNSIGNED NULL,
  started_at           DATETIME NULL,
  ended_at             DATETIME NULL,
  rating_applied       TINYINT(1) NOT NULL DEFAULT 0 COMMENT '점수 반영 완료 (중복 반영 방지)',
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_matches_session_game (scrim_session_id, game_no),
  UNIQUE KEY uk_matches_riot (riot_match_id),
  KEY idx_matches_room (room_id, status, ended_at DESC),
  KEY idx_matches_candidate (balance_candidate_id),
  CONSTRAINT fk_matches_session   FOREIGN KEY (scrim_session_id) REFERENCES scrim_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_matches_room      FOREIGN KEY (room_id)          REFERENCES rooms(id)          ON DELETE CASCADE,
  CONSTRAINT fk_matches_candidate FOREIGN KEY (balance_candidate_id) REFERENCES balance_candidates(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE rating_history
  ADD CONSTRAINT fk_rh_match FOREIGN KEY (match_id) REFERENCES matches(id) ON DELETE SET NULL;

CREATE TABLE match_participants (
  id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  match_id           BIGINT UNSIGNED NOT NULL,
  player_id          BIGINT UNSIGNED NOT NULL,
  room_id            BIGINT UNSIGNED NOT NULL COMMENT '통계용 비정규화',
  side               ENUM('BLUE','RED') NOT NULL,
  lane               ENUM('TOP','JUNGLE','MID','ADC','SUPPORT') NOT NULL,
  -- 오프롤 보호
  assigned_from      ENUM('PRIMARY','SECONDARY','OFF_ROLE','FILL') NOT NULL DEFAULT 'PRIMARY',
  off_role_factor    DECIMAL(4,3) NOT NULL DEFAULT 1.000 COMMENT '점수 보호 계수 (§4.3)',
  -- 점수 스냅샷
  effective_rating   INT NULL COMMENT '팀 구성에 실제 사용된 유효 점수',
  rating_before      INT NULL,
  rating_after       INT NULL,
  rating_delta       INT NULL,
  -- 결과
  champion_id        SMALLINT UNSIGNED NULL,
  is_win             TINYINT(1) NULL,
  kills              SMALLINT UNSIGNED NULL,
  deaths             SMALLINT UNSIGNED NULL,
  assists            SMALLINT UNSIGNED NULL,
  damage_to_champions INT UNSIGNED NULL,
  gold_earned        INT UNSIGNED NULL,
  cs                 SMALLINT UNSIGNED NULL,
  vision_score       SMALLINT UNSIGNED NULL,
  perf_score         DECIMAL(6,3) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_mp_match_player (match_id, player_id),
  -- 한 팀 한 라인 1명
  UNIQUE KEY uk_mp_match_slot (match_id, side, lane),
  KEY idx_mp_player (player_id, match_id DESC),
  KEY idx_mp_room_champ (room_id, champion_id, lane),
  CONSTRAINT fk_mp_match    FOREIGN KEY (match_id)    REFERENCES matches(id)   ON DELETE CASCADE,
  CONSTRAINT fk_mp_player   FOREIGN KEY (player_id)   REFERENCES players(id)   ON DELETE CASCADE,
  CONSTRAINT fk_mp_room     FOREIGN KEY (room_id)     REFERENCES rooms(id)     ON DELETE CASCADE,
  CONSTRAINT fk_mp_champion FOREIGN KEY (champion_id) REFERENCES champions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  9. 실시간 밴픽
-- =====================================================================

-- 밴픽 순서를 DB화 (하드코딩 X → "우리는 4밴만" 같은 커스텀 룰을 INSERT로 처리)
CREATE TABLE draft_rulesets (
  id                  VARCHAR(32) NOT NULL,
  name                VARCHAR(64) NOT NULL,
  ban_count           TINYINT UNSIGNED NOT NULL DEFAULT 10,
  pick_count          TINYINT UNSIGNED NOT NULL DEFAULT 10,
  default_timer_sec   SMALLINT UNSIGNED NOT NULL DEFAULT 30,
  default_reserve_sec SMALLINT UNSIGNED NOT NULL DEFAULT 60,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE draft_ruleset_steps (
  ruleset_id  VARCHAR(32) NOT NULL,
  step_no     TINYINT UNSIGNED NOT NULL,
  side        ENUM('BLUE','RED') NOT NULL,
  action_type ENUM('BAN','PICK') NOT NULL,
  phase       TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1차밴=1, 1차픽=2, 2차밴=3, 2차픽=4',
  PRIMARY KEY (ruleset_id, step_no),
  CONSTRAINT fk_drs_ruleset FOREIGN KEY (ruleset_id) REFERENCES draft_rulesets(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE drafts (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  match_id         BIGINT UNSIGNED NOT NULL,
  scrim_session_id BIGINT UNSIGNED NOT NULL COMMENT '피어리스 풀 조회 키',
  ruleset_id       VARCHAR(32) NOT NULL DEFAULT 'TOURNAMENT_STANDARD',
  status           ENUM('WAITING','READY','IN_PROGRESS','PAUSED','COMPLETED','ABORTED')
                   NOT NULL DEFAULT 'WAITING',
  -- 관전 링크 (방 입장자 전원 공유). 밴픽 조작 권한은 draft_seats 가 가짐
  spectator_token  CHAR(32) NOT NULL,
  blue_ready       TINYINT(1) NOT NULL DEFAULT 0,
  red_ready        TINYINT(1) NOT NULL DEFAULT 0,
  current_step     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=시작 전',
  turn_deadline_at DATETIME(3) NULL COMMENT '서버 소유 타이머',
  timer_sec        SMALLINT UNSIGNED NOT NULL DEFAULT 30,
  blue_reserve_ms  INT UNSIGNED NOT NULL DEFAULT 60000,
  red_reserve_ms   INT UNSIGNED NOT NULL DEFAULT 60000,
  version          INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '낙관적 락 (WS 동시 액션 충돌 방지)',
  started_at       DATETIME(3) NULL,
  completed_at     DATETIME(3) NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_drafts_match (match_id),
  UNIQUE KEY uk_drafts_spec_token (spectator_token),
  KEY idx_drafts_session (scrim_session_id),
  CONSTRAINT fk_drafts_match   FOREIGN KEY (match_id)         REFERENCES matches(id)        ON DELETE CASCADE,
  CONSTRAINT fk_drafts_session FOREIGN KEY (scrim_session_id) REFERENCES scrim_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_drafts_ruleset FOREIGN KEY (ruleset_id)       REFERENCES draft_rulesets(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 밴픽 조작 좌석: 팀당 1석. 이 토큰/게스트 세션만 확정(lock) 가능. 나머지는 전부 관전.
CREATE TABLE draft_seats (
  draft_id         BIGINT UNSIGNED NOT NULL,
  side             ENUM('BLUE','RED') NOT NULL,
  access_token     CHAR(32) NOT NULL COMMENT '주장에게 전달하는 링크 토큰',
  guest_session_id BIGINT UNSIGNED NULL COMMENT '좌석을 점유한 게스트',
  user_id          BIGINT UNSIGNED NULL COMMENT '관리자가 직접 잡을 때',
  claimed_at       DATETIME NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (draft_id, side),
  UNIQUE KEY uk_seat_token (access_token),
  KEY idx_seat_guest (guest_session_id),
  CONSTRAINT fk_seat_draft FOREIGN KEY (draft_id)         REFERENCES drafts(id)         ON DELETE CASCADE,
  CONSTRAINT fk_seat_guest FOREIGN KEY (guest_session_id) REFERENCES guest_sessions(id) ON DELETE SET NULL,
  CONSTRAINT fk_seat_user  FOREIGN KEY (user_id)          REFERENCES users(id)          ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 확정된 밴/픽 (append-only)
CREATE TABLE draft_actions (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  draft_id     BIGINT UNSIGNED NOT NULL,
  step_no      TINYINT UNSIGNED NOT NULL,
  side         ENUM('BLUE','RED') NOT NULL,
  action_type  ENUM('BAN','PICK') NOT NULL,
  champion_id  SMALLINT UNSIGNED NULL COMMENT 'NULL = 밴 패스/타임아웃 무밴',
  actor_guest_session_id BIGINT UNSIGNED NULL,
  actor_user_id BIGINT UNSIGNED NULL,
  is_auto      TINYINT(1) NOT NULL DEFAULT 0 COMMENT '타임아웃 자동 확정',
  acted_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  -- 같은 스텝 중복 확정 원천 차단
  UNIQUE KEY uk_da_draft_step (draft_id, step_no),
  -- 같은 드래프트 내 챔피언 중복(양 팀 동시 확정) 차단. NULL은 UNIQUE 검사 제외
  UNIQUE KEY uk_da_draft_champ (draft_id, champion_id),
  KEY idx_da_actor_guest (actor_guest_session_id),
  KEY idx_da_actor_user (actor_user_id),
  CONSTRAINT fk_da_draft    FOREIGN KEY (draft_id)    REFERENCES drafts(id)    ON DELETE CASCADE,
  CONSTRAINT fk_da_champion FOREIGN KEY (champion_id) REFERENCES champions(id),
  CONSTRAINT fk_da_guest    FOREIGN KEY (actor_guest_session_id) REFERENCES guest_sessions(id) ON DELETE SET NULL,
  CONSTRAINT fk_da_user     FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 호버(미확정 선택). 상대/관전자에게 실시간 표시. 드래프트당 side별 1행
CREATE TABLE draft_hovers (
  draft_id    BIGINT UNSIGNED NOT NULL,
  side        ENUM('BLUE','RED') NOT NULL,
  step_no     TINYINT UNSIGNED NOT NULL,
  champion_id SMALLINT UNSIGNED NULL,
  updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (draft_id, side),
  KEY idx_dh_champ (champion_id),
  CONSTRAINT fk_dh_draft FOREIGN KEY (draft_id)    REFERENCES drafts(id)    ON DELETE CASCADE,
  CONSTRAINT fk_dh_champ FOREIGN KEY (champion_id) REFERENCES champions(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 재접속/리플레이용 이벤트 로그. seq 이후만 밀어주면 상태 복원됨
CREATE TABLE draft_events (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  draft_id   BIGINT UNSIGNED NOT NULL,
  seq        INT UNSIGNED NOT NULL,
  event_type VARCHAR(32) NOT NULL COMMENT 'SEAT_CLAIM, READY, HOVER, LOCK, TIMEOUT, PAUSE, RESUME, ABORT',
  payload    JSON NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_de_draft_seq (draft_id, seq),
  CONSTRAINT fk_de_draft FOREIGN KEY (draft_id) REFERENCES drafts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  10. 피어리스 풀  ★ 세션 스코프 ★
--      side='BLUE'/'RED' → 해당 팀만 사용 불가 (FEARLESS)
--      side='ANY'        → 양 팀 사용 불가     (GLOBAL_FEARLESS)
--      세션이 끝나면 이 풀도 끝. 다음 세션은 백지에서 시작.
-- =====================================================================

CREATE TABLE session_champion_pool (
  scrim_session_id BIGINT UNSIGNED NOT NULL,
  champion_id      SMALLINT UNSIGNED NOT NULL,
  side             ENUM('BLUE','RED','ANY') NOT NULL,
  source           ENUM('PICK','BAN') NOT NULL DEFAULT 'PICK',
  used_in_match_id BIGINT UNSIGNED NULL,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (scrim_session_id, champion_id, side),
  KEY idx_scp_champ (champion_id),
  KEY idx_scp_match (used_in_match_id),
  CONSTRAINT fk_scp_session FOREIGN KEY (scrim_session_id) REFERENCES scrim_sessions(id) ON DELETE CASCADE,
  CONSTRAINT fk_scp_champ   FOREIGN KEY (champion_id)      REFERENCES champions(id)      ON DELETE CASCADE,
  CONSTRAINT fk_scp_match   FOREIGN KEY (used_in_match_id) REFERENCES matches(id)        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  11. 통계 (방 단위 누적. 경기 종료 시 증분 UPSERT)
-- =====================================================================

CREATE TABLE player_champion_stats (
  player_id      BIGINT UNSIGNED NOT NULL,
  champion_id    SMALLINT UNSIGNED NOT NULL,
  lane           ENUM('TOP','JUNGLE','MID','ADC','SUPPORT') NOT NULL,
  room_id        BIGINT UNSIGNED NOT NULL,
  games          INT UNSIGNED NOT NULL DEFAULT 0,
  wins           INT UNSIGNED NOT NULL DEFAULT 0,
  kills          INT UNSIGNED NOT NULL DEFAULT 0,
  deaths         INT UNSIGNED NOT NULL DEFAULT 0,
  assists        INT UNSIGNED NOT NULL DEFAULT 0,
  total_damage   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  total_cs       INT UNSIGNED NOT NULL DEFAULT 0,
  last_played_at DATETIME NULL,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id, champion_id, lane),
  -- 주요 챔피언 TOP N 조회
  KEY idx_pcs_most (player_id, games DESC),
  KEY idx_pcs_room_champ (room_id, champion_id, games DESC),
  CONSTRAINT fk_pcs_player FOREIGN KEY (player_id)   REFERENCES players(id)   ON DELETE CASCADE,
  CONSTRAINT fk_pcs_champ  FOREIGN KEY (champion_id) REFERENCES champions(id) ON DELETE CASCADE,
  CONSTRAINT fk_pcs_room   FOREIGN KEY (room_id)     REFERENCES rooms(id)     ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE player_lane_stats (
  player_id      BIGINT UNSIGNED NOT NULL,
  lane           ENUM('TOP','JUNGLE','MID','ADC','SUPPORT') NOT NULL,
  room_id        BIGINT UNSIGNED NOT NULL,
  games          INT UNSIGNED NOT NULL DEFAULT 0,
  wins           INT UNSIGNED NOT NULL DEFAULT 0,
  off_role_games INT UNSIGNED NOT NULL DEFAULT 0,
  total_kills    INT UNSIGNED NOT NULL DEFAULT 0,
  total_deaths   INT UNSIGNED NOT NULL DEFAULT 0,
  total_assists  INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id, lane),
  KEY idx_pls_room (room_id, lane),
  CONSTRAINT fk_pls_player FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
  CONSTRAINT fk_pls_room   FOREIGN KEY (room_id)   REFERENCES rooms(id)   ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 방 메타: 챔피언 밴픽률/승률
CREATE TABLE room_champion_stats (
  room_id      BIGINT UNSIGNED NOT NULL,
  champion_id  SMALLINT UNSIGNED NOT NULL,
  picks        INT UNSIGNED NOT NULL DEFAULT 0,
  bans         INT UNSIGNED NOT NULL DEFAULT 0,
  wins         INT UNSIGNED NOT NULL DEFAULT 0,
  total_drafts INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '밴픽률 분모',
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (room_id, champion_id),
  KEY idx_rcs_champ (champion_id),
  CONSTRAINT fk_rcs_room  FOREIGN KEY (room_id)     REFERENCES rooms(id)     ON DELETE CASCADE,
  CONSTRAINT fk_rcs_champ FOREIGN KEY (champion_id) REFERENCES champions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 듀오 시너지 (player_a_id < player_b_id 강제 → (A,B)/(B,A) 중복 방지)
CREATE TABLE player_duo_stats (
  player_a_id    BIGINT UNSIGNED NOT NULL,
  player_b_id    BIGINT UNSIGNED NOT NULL,
  room_id        BIGINT UNSIGNED NOT NULL,
  games_together INT UNSIGNED NOT NULL DEFAULT 0,
  wins_together  INT UNSIGNED NOT NULL DEFAULT 0,
  games_against  INT UNSIGNED NOT NULL DEFAULT 0,
  a_wins_against INT UNSIGNED NOT NULL DEFAULT 0,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (player_a_id, player_b_id),
  KEY idx_duo_b (player_b_id),
  KEY idx_duo_room (room_id),
  CONSTRAINT fk_duo_a    FOREIGN KEY (player_a_id) REFERENCES players(id) ON DELETE CASCADE,
  CONSTRAINT fk_duo_b    FOREIGN KEY (player_b_id) REFERENCES players(id) ON DELETE CASCADE,
  CONSTRAINT fk_duo_room FOREIGN KEY (room_id)     REFERENCES rooms(id)   ON DELETE CASCADE,
  CONSTRAINT chk_duo_order CHECK (player_a_id < player_b_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- =====================================================================
--  12. 감사 로그 (관리자 행위 추적)
-- =====================================================================

CREATE TABLE audit_logs (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id       BIGINT UNSIGNED NULL,
  actor_user_id BIGINT UNSIGNED NULL,
  actor_guest_session_id BIGINT UNSIGNED NULL,
  action        VARCHAR(64) NOT NULL COMMENT 'PLAYER_ADD, CODE_ROTATE, MANUAL_TEAM, RATING_ADJUST, MATCH_VOID, GUEST_BAN ...',
  target_type   VARCHAR(32) NULL,
  target_id     BIGINT UNSIGNED NULL,
  detail        JSON NULL,
  ip            VARBINARY(16) NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_room (room_id, created_at DESC),
  KEY idx_audit_actor (actor_user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


SET FOREIGN_KEY_CHECKS = 1;


-- =====================================================================
--  13. 기본 데이터 — 대회 표준 밴픽 순서 (6밴 → 6픽 → 4밴 → 4픽)
-- =====================================================================

INSERT INTO draft_rulesets (id, name, ban_count, pick_count, default_timer_sec, default_reserve_sec) VALUES
  ('TOURNAMENT_STANDARD', '대회 표준 (5밴 5픽)', 10, 10, 30, 60);

INSERT INTO draft_ruleset_steps (ruleset_id, step_no, side, action_type, phase) VALUES
  ('TOURNAMENT_STANDARD',  1, 'BLUE', 'BAN',  1),
  ('TOURNAMENT_STANDARD',  2, 'RED',  'BAN',  1),
  ('TOURNAMENT_STANDARD',  3, 'BLUE', 'BAN',  1),
  ('TOURNAMENT_STANDARD',  4, 'RED',  'BAN',  1),
  ('TOURNAMENT_STANDARD',  5, 'BLUE', 'BAN',  1),
  ('TOURNAMENT_STANDARD',  6, 'RED',  'BAN',  1),
  ('TOURNAMENT_STANDARD',  7, 'BLUE', 'PICK', 2),
  ('TOURNAMENT_STANDARD',  8, 'RED',  'PICK', 2),
  ('TOURNAMENT_STANDARD',  9, 'RED',  'PICK', 2),
  ('TOURNAMENT_STANDARD', 10, 'BLUE', 'PICK', 2),
  ('TOURNAMENT_STANDARD', 11, 'BLUE', 'PICK', 2),
  ('TOURNAMENT_STANDARD', 12, 'RED',  'PICK', 2),
  ('TOURNAMENT_STANDARD', 13, 'RED',  'BAN',  3),
  ('TOURNAMENT_STANDARD', 14, 'BLUE', 'BAN',  3),
  ('TOURNAMENT_STANDARD', 15, 'RED',  'BAN',  3),
  ('TOURNAMENT_STANDARD', 16, 'BLUE', 'BAN',  3),
  ('TOURNAMENT_STANDARD', 17, 'RED',  'PICK', 4),
  ('TOURNAMENT_STANDARD', 18, 'BLUE', 'PICK', 4),
  ('TOURNAMENT_STANDARD', 19, 'BLUE', 'PICK', 4),
  ('TOURNAMENT_STANDARD', 20, 'RED',  'PICK', 4);
