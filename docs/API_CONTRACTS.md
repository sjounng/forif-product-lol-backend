# API contracts

## Domain ownership

- `domain.room` owns the single `Room`, `RoomRepository`, and `RoomStatus` mapping for the `rooms` table.
- `domain.player` owns the single `Player`, `PlayerRepository`, and `Lane` mapping used by groups, sessions, matches, and drafts.
- `domain.riot` owns Riot API access and the `RiotAccount`/`RiotRankSnapshot` persistence model.
- `domain.group` extends the room model with memberships, invitations, guest admission, and group-oriented HTTP APIs; it does not map `rooms` again.

## Contract: auth-v1

- 로그인과 토큰 재발급은 액세스 토큰을 JSON으로 반환한다.
- 리프레시 토큰은 `scrim_refresh_token` HttpOnly 쿠키로만 전달한다.
- 액세스 토큰은 클라이언트 메모리에만 보관한다.
- 보호 API의 `401` 응답 시 클라이언트는 토큰 재발급을 한 번만 시도한다.

## Contract: group-v1

### 역할

- `GROUP_OWNER`: 그룹 생성자. 설정, 관리자, 초대, 참가자를 관리한다.
- `GROUP_MANAGER`: 초대와 참가자를 관리한다. 소유자 변경과 세션 생성 권한은 없다.
- `GROUP_MEMBER`: 로그인 그룹 참가자.
- 상대 팀장은 그룹 역할과 별도로 `rooms.opponent_captain_user_id`로 식별한다.

### 팀장 초대 상태 전이

```text
PENDING -> ACCEPTED
PENDING -> REJECTED
PENDING -> CANCELLED
PENDING -> EXPIRED
```

`ACCEPTED` 시 상대 팀장을 그룹 회원으로 추가하고 그룹의 상대 팀장으로 고정한다.
이미 상대 팀장이 있는 그룹이나 종료 상태의 초대는 다시 처리할 수 없다.

### 주요 API

```text
GET    /api/users/search?q=
GET    /api/rooms
POST   /api/rooms
GET    /api/rooms/{roomId}
PATCH  /api/rooms/{roomId}
GET    /api/rooms/{roomId}/members
PATCH  /api/rooms/{roomId}/members/{userId}
POST   /api/rooms/{roomId}/public-code

GET    /api/group-invitations
POST   /api/group-invitations/{invitationId}/accept
POST   /api/group-invitations/{invitationId}/reject

GET    /api/public/rooms/{publicCode}
POST   /api/public/rooms/{publicCode}/guests
GET    /api/rooms/{roomId}/guests
PATCH  /api/rooms/{roomId}/guests/{guestId}
DELETE /api/rooms/{roomId}/guests/{guestId}
```

그룹 생성은 이름, 설명, 상대 팀장 사용자 ID, 게스트 입장 허용 여부,
선택 입장 암호를 받는다. 이메일 원문은 사용자 검색 및 그룹 응답에 포함하지 않는다.

공개 코드를 재발급하면 이전 코드의 신규 입장만 차단한다. 기존 게스트 행과
`scrim_guest_session` 쿠키는 보존한다. 게스트 쿠키의 원문은 DB에 저장하지 않고
SHA-256 해시만 `guest_sessions.token`에 저장한다.

### 공통 오류 응답

```json
{
  "status": 409,
  "code": "INVITATION_NOT_PENDING",
  "message": "이미 처리된 초대입니다."
}
```

주요 코드:

- `AUTH_REQUIRED`, `AUTH_INVALID`
- `ROOM_NOT_FOUND`, `ROOM_ACCESS_DENIED`, `ROOM_MANAGEMENT_DENIED`
- `INVITATION_NOT_FOUND`, `INVITATION_NOT_PENDING`, `INVITATION_EXPIRED`
- `GUEST_ADMISSION_DISABLED`, `GUEST_ENTRY_PASSWORD_INVALID`, `GUEST_BLOCKED`
- `VALIDATION_ERROR`, `MALFORMED_REQUEST`, `DATA_CONFLICT`

## Contract: session-v1

### 제안과 확정

```text
PROPOSED -> CONFIRMED
PROPOSED -> CANCELLED (상대 팀장 거절 또는 제안자 취소)
CONFIRMED -> CANCELLED (매치 시작 전 제안자 취소)
```

- 그룹 소유자와 초대를 수락한 상대 팀장만 세션을 제안할 수 있다.
- 제안자는 `creatorSide`로 자기 진영을 선택하며 상대 팀장은 반대 진영에 배정된다.
- 상대 팀장만 제안을 수락하거나 거절할 수 있다.
- `CONFIRMED` 이후 경기 방식, 피어리스 방식, 팀, 기본 라인을 변경하는 API를 제공하지 않는다.
- `PREPARING`, `PROPOSED`, `CONFIRMED`, `IN_PROGRESS` 상태는 그룹당 동시에 하나만 허용한다.

### 고정 로스터

- BLUE와 RED는 각각 정확히 5명이다.
- 각 팀은 `TOP`, `JUNGLE`, `MID`, `ADC`, `SUPPORT`를 한 명씩 가진다.
- 한 참가자는 두 팀에 중복 배치할 수 없다.
- 그룹의 활성 회원 또는 차단되지 않은 활성 게스트만 제안에 포함할 수 있다.
- 회원/게스트는 `players`에 연결하고 세션 고정 구성은 `session_team_members`에 보존한다.

### 주요 API

```text
GET  /api/rooms/{roomId}/sessions
POST /api/rooms/{roomId}/sessions
GET  /api/sessions/{sessionId}
POST /api/sessions/{sessionId}/accept
POST /api/sessions/{sessionId}/reject
POST /api/sessions/{sessionId}/cancel
```

세션 관련 오류 코드는 `SESSION_NOT_FOUND`, `SESSION_ACCESS_DENIED`,
`SESSION_CREATION_DENIED`, `SESSION_REVIEW_DENIED`, `SESSION_ACTIVE_EXISTS`,
`SESSION_INVALID_ROSTER`, `SESSION_NOT_PROPOSED`를 사용한다.

## Contract: match-v1

### 매치 시작 합의

```text
세션 CONFIRMED 또는 IN_PROGRESS
-> 한 팀장이 다음 gameNo 시작 요청
-> 상대 팀장이 수락
-> Match(DRAFTING), MatchParticipant 10명, Draft(WAITING) 원자적 생성
-> 세션 IN_PROGRESS
```

- 요청자는 자신의 요청을 수락하거나 거절할 수 없다.
- 한 세션에는 시작 합의 대기 요청과 미완료 매치를 각각 하나만 허용한다.
- 이전 매치 결과가 `COMPLETED`가 아니면 다음 매치 요청을 차단한다.
- Match 참가자는 확정된 `session_team_members` 10명을 그대로 복사한다.

### 결과 상호 확인과 세션 종료

```text
LIVE -> RESULT_PENDING (한 팀장이 승리 진영 제안)
RESULT_PENDING -> COMPLETED (상대 팀장 확인)
RESULT_PENDING -> RESULT_DISPUTED (상대 팀장 거절)
RESULT_DISPUTED -> RESULT_PENDING (결과 재제안)
```

- `COMPLETED` 전까지 승리 수와 `scrim_sessions.game_count`에 반영하지 않는다.
- `BEST_OF_3`은 한 팀의 2승, `BEST_OF_5`는 3승에서 세션을 자동 종료한다.
- `UNLIMITED`는 활성 매치나 시작 요청이 없을 때 세션 생성자가 수동 종료한다.
- MVP 결과는 수동 입력이며 선택적으로 `riotMatchId`를 기록한다.

### 주요 API

```text
GET  /api/sessions/{sessionId}/matches
POST /api/sessions/{sessionId}/match-start-requests
POST /api/match-start-requests/{requestId}/accept
POST /api/match-start-requests/{requestId}/reject
POST /api/match-start-requests/{requestId}/cancel
POST /api/matches/{matchId}/start
POST /api/matches/{matchId}/results
POST /api/matches/{matchId}/results/accept
POST /api/matches/{matchId}/results/reject
POST /api/sessions/{sessionId}/finish
```

매치 관련 오류 코드는 `MATCH_NOT_FOUND`, `MATCH_START_REQUEST_NOT_FOUND`,
`MATCH_START_REQUEST_PENDING`, `MATCH_START_REQUEST_NOT_PENDING`,
`MATCH_START_REVIEW_DENIED`, `MATCH_CREATION_DENIED`, `MATCH_ACTIVE_EXISTS`,
`MATCH_RESULT_INVALID_STATE`, `MATCH_RESULT_REVIEW_DENIED`, `SESSION_FINISH_DENIED`를 사용한다.

## Contract: draft-v1

### 상태 전이

```text
WAITING -> READY (한 팀 READY)
READY -> IN_PROGRESS (양 팀 READY)
IN_PROGRESS -> ASSIGNING (20번째 LOCK)
ASSIGNING -> COMPLETED (양 팀 선수 배정 확정)
Match DRAFTING -> READY_TO_PLAY (Draft COMPLETED)
```

- 대회 표준 20단계는 `draft_ruleset_steps`를 원본으로 사용한다.
- 모든 변경 요청은 `expectedVersion`을 받고 Draft 행을 잠근 뒤 현재 버전과 비교한다.
- LOCK은 현재 단계, 현재 진영 팀장, 챔피언 중복, 피어리스 제한을 다시 검증한다.
- PICK에서는 자기 팀 선수를 선택적으로 임시 지정할 수 있다.
- 한 팀의 5 PICK이 끝나면 선수 배정을 바꿀 수 있고, 이미 배정된 챔피언을 선택하면 같은 팀 안에서 교환한다.
- 마지막 PICK 뒤 90초 동안 양 팀이 5명과 5챔피언을 일대일 배정한다.
- 90초가 지나면 미배정 챔피언을 자동 배정하고 미확정 팀을 자동 확정한다.
- 양 팀 확정 시 `match_participants.champion_id`를 기록하고 Match를 `READY_TO_PLAY`로 전환한다.
- 확정 상태와 이벤트는 DB에 저장하므로 새로고침 후 동일한 단계와 배정을 복원한다.

### 주요 API

```text
GET  /api/drafts/{draftId}
POST /api/drafts/{draftId}/ready
POST /api/drafts/{draftId}/hover
POST /api/drafts/{draftId}/locks
PUT  /api/drafts/{draftId}/assignments
POST /api/drafts/{draftId}/assignments/confirm
```

Draft 관련 오류 코드는 `DRAFT_NOT_FOUND`, `DRAFT_INVALID_STATE`,
`DRAFT_ACCESS_DENIED`, `DRAFT_STEP_MISMATCH`, `DRAFT_VERSION_CONFLICT`,
`DRAFT_CHAMPION_INVALID`, `DRAFT_CHAMPION_UNAVAILABLE`, `DRAFT_PLAYER_INVALID`,
`DRAFT_ASSIGNMENT_INVALID`, `DRAFT_ASSIGNMENT_CONFIRMED`를 사용한다.

## Contract: realtime-v1

### 연결과 복구

```text
WS /ws/drafts/{draftId}?accessToken={jwt}&lastSeq={seq}
```

- 브라우저 WebSocket 제약 때문에 JWT와 마지막 처리 `seq`를 연결 쿼리로 전달한다.
- 최초 연결 또는 이벤트 이력에 공백이 있으면 서버가 `SNAPSHOT`을 전송한다.
- 이력이 연속적이면 `lastSeq` 이후 이벤트만 순서대로 재생한다.
- 모든 변경 이벤트는 Draft 버전과 별도의 단조 증가 `seq`를 갖는다. HOVER처럼 버전을 올리지 않는 이벤트도 재접속으로 복원된다.
- 서버 인스턴스 간 이벤트는 Redis Pub/Sub 채널 `lol-scrim:draft-events`로 전달한다. Redis 발행 실패 시 같은 인스턴스의 연결에는 직접 전달한다.

### 클라이언트 명령

```text
READY
HOVER
LOCK
ASSIGN_CHAMPION
SWAP_CHAMPIONS
CONFIRM_ASSIGNMENT
```

- 모든 변경 명령은 `expectedVersion`을 포함하고 REST와 동일한 `DraftService` 규칙을 사용한다.
- 서버 이벤트는 `READY_UPDATED`, `HOVER_UPDATED`, `ACTION_LOCKED`,
  `ASSIGNMENT_UPDATED`, `ASSIGNMENT_CONFIRMED`, `ASSIGNMENT_AUTO_COMPLETED`,
  `DRAFT_COMPLETED`, `ERROR`를 사용한다.
- 클라이언트는 seq 중복을 무시하고 공백을 발견하면 REST Snapshot을 다시 조회한다.

턴별 서버 타이머와 합의형 일시정지/재개는 후속 단계에서 추가한다.
