# 점수 산정 설계 (DESIGN)

`V1__init_schema.sql`, 프론트 `types/index.ts`/`constants.ts`가 여기를 `§`로 인용한다.
구현할 때 숫자를 바꾸면 반드시 이 문서도 같이 고칠 것.

---

## §4. 점수 시스템

### §4.1 솔로/듀오 랭크 → `ladder_score` (0~4000)

```
tier_index  = IRON(0) BRONZE(1) SILVER(2) GOLD(3) PLATINUM(4) EMERALD(5) DIAMOND(6) MASTER(7) GRANDMASTER(8) CHALLENGER(9)
division    = IV(0) III(1) II(2) I(3)        ※ 마스터 이상은 division 없음

ladder_score = tier_index * 400 + division * 100 + LP     (0~4000 클램프)
```
마스터 이상은 `tier_index*400 + LP`. 4000 초과 시 4000으로 클램프.

검증 예: **골드2 0LP** → 3×400 + 2×100 + 0 = **1400**

`RANKED_SOLO_5x5`를 우선 조회하고, UNRANKED면 `RANKED_FLEX_SR`로 폴백한다. 둘 다 없으면 §4.1-B(수동 시드)로 간다.

### §4.1-B 초기 시드값

```
seed_rating = ladder_score + 100        (솔/듀랭 조회 실패·미조회 시 1500)
```
검증 예: 1400 + 100 = **1500** (`player_ratings.rating` 기본값과 일치)

초기 RD(`player_ratings.rd`)는 시드 출처에 따라 다르게 잡는다 — 실제 랭크 전적이 있으면 더 신뢰할 수 있으므로:

| `seed_source` | 초기 RD |
|---|---|
| `SOLO_RANK` / `FLEX_RANK` | 200 |
| `MANUAL` / `DEFAULT` | 350 (Glicko 표준 초기값) |

### §4.2 경기 후 갱신 — Glicko-1

`rd`가 클수록 한 판의 영향(=K)이 커지는 게 스키마 의도이므로, 고정 K값 Elo가 아니라 **Glicko-1**을 쓴다.
한 게임은 "나 vs 상대팀 평균"으로 근사한다(5v5를 1v1처럼 취급 — 팀 단위로 사고하는 기존 UI/스키마와 일관됨).

```
q = ln(10) / 400
g(RD) = 1 / sqrt(1 + 3·q²·RD² / π²)
E = 1 / (1 + 10^( -g(RD_상대팀평균)·(내rating - 상대팀평균rating) / 400 ))

d² = 1 / (q² · g(RD_상대팀평균)² · E·(1-E))
새rating = rating + [ q / (1/RD² + 1/d²) ] · g(RD_상대팀평균) · (S - E)     (S: 승=1, 패=0)
새RD    = sqrt( (1/RD² + 1/d²)⁻¹ )
```

`player_lane_ratings`(라인별)도 같은 공식을 라인 전용 rating/RD로 병행 적용한다 — 단, **실제로 뛴 라인만** 갱신한다 (다른 라인 rating은 그대로 둔다). 이게 "정글 몇 판 뛴 게 서폿 점수를 오염시키지 않는다"는 근거다.

### §4.3 오프롤 점수 보호 (경기 후 델타 감쇠용)

Glicko로 계산한 raw delta(`새rating - rating`)에 곱해서, 오프롤로 뛴 판은 **오르든 내리든** 흔들림을 줄인다. `match_participants.assigned_from`(경기 기록용 라벨)을 기준으로 판정한다:

| `assigned_from` | 계수 |
|---|---|
| PRIMARY | 1.000 |
| SECONDARY | 0.950 |
| FILL | 0.900 |
| OFF_ROLE | 0.850 |

`assigned_from`은 배정 라인을 `primaryLane`/`secondaryLane`/`lanePool`과 비교해서 매치 생성 시점에 파생시킨다:
- 배정라인 == primaryLane → PRIMARY
- 배정라인 == secondaryLane → SECONDARY
- primaryLane == "FILL" (아무거나 선택) → FILL
- 그 외 → OFF_ROLE

`lanePool[배정라인] == 0`인 라인은 애초에 배정 후보에서 제외한다 ("0=배정 금지").

#### §4.3-A 예시
정글 메인이 서폿으로 배정되어 진 경우: raw delta가 -30이었다면 실제 반영은 -30 × 0.85 = **-25.5**.

### §4-A 세션 로테이션 (다음 판 투입 순번)
**TODO — 미정.** `games_played ASC → bench_priority DESC → last_played_game_no ASC` 정렬 기준은 스키마/API 주석에 이미 있지만, 정확한 동률 처리·페널티 규칙은 아직 확정 안 함. 세션/로테이션 구현 착수 시 별도로 정한다.

---

## §5. 팀 밸런싱

### §5.1 밸런싱용 유효 점수 (`effRating`)

경기 후 갱신(§4.2/§4.3)과는 목적이 다른 **별도 계산**이다 — 저건 "경기 후 내 점수가 얼마나 변하는가", 이건 "이번 판 팀을 어떻게 짤 것인가".

```
laneWeight(lanePool)   = 0.70 + 0.06 × lanePool[배정라인]      (lanePool: 0~5)
effRating(player, lane) = roomRating(player) × laneWeight(lanePool[lane])
```
`roomRating`은 `player_ratings.rating`(§4.1-B 시드 → §4.2로 갱신된 값) 그대로 사용한다. `lanePool[lane] == 0`이면 그 라인 배정 후보에서 제외한다.

검증 예 (`roomRating=1893`, `lanePool={TOP:3, JUNGLE:5, MID:2, ADC:0, SUPPORT:1}`):

| 배정 라인 | laneWeight | effRating |
|---|---|---|
| JUNGLE (5) | 1.00 | 1893 |
| TOP (3) | 0.88 | 1666 |
| SUPPORT (1) | 0.76 | 1439 |
| ADC (0) | — | 배정 불가 |

**추후 확장 지점 (지금 구현 안 함):** `player_lane_ratings[lane]`에 실전 데이터가 충분히 쌓이면(예: `games_played >= 3`), 추정치인 `roomRating × laneWeight` 대신 그 라인의 실측 Glicko 값을 바로 쓴다.

### §5.2 하드 제약 (프론트 `constants.ts`와 동일)

```
DEFAULT_MAX_TOTAL_DIFF = 150   |블루 effRating 합 − 레드 effRating 합| 상한
DEFAULT_MAX_LANE_DIFF  = 300   같은 라인끼리 effRating 차이 상한
```
못 맞추면 서버가 제약을 완화하고 `relaxed=true` + `relaxNote`를 준다 (예: "라인 차이 제한 300→350 완화").

### §5.3 후보 정렬 (`cost` 목적함수)
**TODO — 미정.** `balance_candidates.cost`(낮을수록 좋음)가 `total_diff`/`max_lane_diff`/`off_role_count`를 어떤 가중치로 합치는지는 밸런싱 알고리즘 구현 시점에 정한다. 브루트포스로 전체 후보를 뽑은 뒤 이 함수로 랭킹만 매기면 되므로, 알고리즘 자체 구현과 별개로 늦게 정해도 무방하다.

---

## §6. 판별 성과 점수 (`perf_score`, MVP/에이스)
**TODO — 미정.** `match_participants.perf_score`는 §4와 무관한 별도 지표다(누적 실력이 아니라 "이번 한 판 잘했나"). OP.GG의 라인별 상대평가, 내전.LOL의 `(KDA×15)+(승률×0.8)+(log(게임수)×15)` 등을 참고하되, 라인 내 상대평가(같은 판 10명 안에서 z-score/순위) 방식으로 갈 가능성이 높음. 착수 시 별도 확정.
