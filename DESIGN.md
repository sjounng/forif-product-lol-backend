# 점수 산정 설계 (DESIGN)

`V1__init_schema.sql`, 프론트 `types/index.ts`/`constants.ts`가 여기를 `§`로 인용한다.
구현할 때 숫자를 바꾸면 반드시 이 문서도 같이 고칠 것.

---

## §4. 점수 시스템

### §4.1 솔로/듀오 랭크 → `ladder_score` (0~4000)

**다이아 이하** (LP 0~99)

```
tier_index  = IRON(0) BRONZE(1) SILVER(2) GOLD(3) PLATINUM(4) EMERALD(5) DIAMOND(6)
division    = IV(0) III(1) II(2) I(3)

ladder_score = tier_index * 400 + division * 100 + LP
```

검증 예: **골드2 0LP** → 3×400 + 2×100 + 0 = **1400**

**마스터 이상** (LP 상한 없음)

```
ladder_score = 2800 + 1200 * log10(1 + LP/300) / log10(1 + 3000/300)      (상한 4000)
```

**티어 인덱스를 더하지 않는다.** Master/GM/Challenger 는 하나의 연속된 LP 풀을 공유하기 때문이다 — 챌린저는 정의상 GM 보다 LP 가 높으므로 **LP 만으로 이미 순서가 정해진다.** 여기에 `tier_index*400` 을 또 더하면 이중 계산이 되고, 실제로 챌린저는 LP 400 만 넘으면 전원 상한(4000)에 붙어 서로 구분이 사라진다.

> 실측으로 확인된 문제다. KR 챌린저 12명(LP 1901~2650)을 등록했더니 `ladder_score` 가 전부 4000, `rating` 이 전부 4100 으로 동점이 되어 **팀 밸런싱이 불가능**했다. 수정 후 3897~4044 로 벌어진다.

로그 압축을 쓰는 이유는, 선형으로 두면 LP 2000 인 사람이 LP 500 인 사람보다 4배 세다는 뜻이 되어 상위 구간을 과대평가하기 때문이다. 그 구간 전체가 이미 상위 0.01% 안이라 실력 차가 LP 차만큼 벌어지지 않는다.

| 구간 | ladder_score |
|---|---|
| 다이아 I 99LP | 2799 |
| 마스터 0LP | **2800** ← 연속 |
| 마스터 200LP | 3056 |
| GM 800LP | 3450 |
| 챌린저 1500LP | 3697 |
| 챌린저 2650LP | 3944 |
| 챌린저 3000LP+ | 4000 (상한) |

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

### §4.1-C 라인별 초기 RD (`player_lane_ratings.rd`)

§4.1-B 의 RD 를 5개 라인에 그대로 복사하면 안 된다. 미드만 하던 사람의 **서폿 라인까지 "신뢰도 높음"(RD 200)** 으로 출발하게 되는데, §4.2 의 Glicko 는 RD 가 작을수록 한 판의 영향을 줄이므로 **실측이 가장 필요한 라인일수록 점수가 가장 느리게 교정된다.** 방향이 반대로 걸린다.

그래서 §5.1-A 의 `lanePool`(그 라인을 얼마나 뛰었나)로 라인별 RD 를 가른다. §4.1-B 의 "근거 있는 시드는 RD 를 낮게"라는 원칙의 연장선이다.

| `lanePool` | 라인 RD |
|---|---|
| 5, 4 (주/부라인) | `baseRd` 그대로 |
| 3, 2 (가끔 간 라인) | `(baseRd + 350) / 2` |
| 1, 0 (최근에 간 적 없음) | 350 |

`baseRd` 는 §4.1-B 로 정해진 전체 시드 RD 다. 결과는 항상 350(Glicko 표준 초기값)을 넘지 않는다 — 조회 실패로 `baseRd` 가 이미 350이면 전 라인 350이다.

라인별 `rating` 은 전체 시드 값을 그대로 쓴다. 라인별로 시작 실력을 다르게 볼 근거가 없고, 차이는 실전이 쌓이면서 §4.2 로 자연히 갈라진다.

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

`player_lane_ratings`(라인별)도 같은 공식을 라인 전용 rating/RD로 병행 적용한다 — 단, **실제로 뛴 라인만** 갱신한다 (다른 라인 rating은 그대로 둔다). 이게 "정글 몇 판 뛴 게 서폿 점수를 오염시키지 않는다"는 근거다. 상대 기준값은 전체와 동일하게 **상대팀 평균(전체 rating/RD)** 을 쓰고, 내 rating/RD 만 라인 전용 값으로 바꿔 넣는다.

라인 행이 없는 선수(Riot 미연동 게스트 등)는 라인 갱신을 **건너뛴다**. 없는 행을 즉석에서 만들면 `self_proficiency` 를 0(=배정 금지)으로 넣게 되어, 점수 갱신이 그 선수의 라인 배정을 막아버린다.

### §4.2-A RD 하한

```
MIN_RD = 30
```

Glicko 는 판이 쌓일수록 RD 가 단조 감소하는데, 바닥이 없으면 RD 가 0 으로 수렴해 **한 판의 영향도 0 이 된다** — 실력이 변해도 점수가 따라가지 못하고 얼어붙는다. 표준 Glicko 관행대로 하한을 둔다. (§4.2 의 원식에는 없는, 구현에서 추가한 값이다.)

갱신된 rating 은 음수로 내려가지 않도록 0 에서 막는다.

### §4.2-B 점수 반영 시점

`scrim_sessions.rating_enabled = false` 인 세션(친선전)의 경기는 전적만 남기고 점수는 움직이지 않는다.
반영은 **결과가 확정될 때(양 팀 주장 합의) 판당 정확히 한 번**이며, `matches.rating_applied` 로 이중 반영을 막는다.
`player_ratings.is_locked = true`(관리자 수동 고정)인 선수는 갱신 대상에서 빠진다 — 단, 그 선수의 rating 은 상대팀 평균 계산에는 그대로 들어간다(실제 실력 데이터이므로).

팀 평균은 **아무도 갱신하기 전의 값으로 먼저 확정**한다. 루프 안에서 그때그때 계산하면 먼저 처리된 선수의 새 점수가 뒤 선수의 기준값을 오염시킨다.

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

판정은 **위 순서 그대로** 한다 — primaryLane 이 FILL 이어도 secondaryLane 을 지정해두고 거기 배정됐다면 FILL 이 아니라 SECONDARY 다.
선호 라인 정보가 아예 없는 선수(Riot 계정 미연동 게스트 등)는 `primaryLane` 이 비어 있는 셈이므로 FILL 로 본다.

매치 **생성 시점에 확정해서 `match_participants` 행에 박아둔다.** 나중에 선수가 선호 라인을 바꿔도 이미 끝난 판의 계수는 흔들리지 않아야 하기 때문이다.

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

### §5.1-A lanePool 초기값 자동 추천 (Match-v5)

**Riot API 에는 "선호 라인"이라는 개념이 없다.** 그래서 최근 솔랭 판에서 **실제로 간 라인**을 집계해 추정한다 — 선호가 아니라 실측이므로, 솔랭에서 강제로 오프롤 갔던 판도 그 라인으로 잡힌다. 어디까지나 **관리자가 화면에서 고치기 전의 시작값**이다.

분석 대상은 `app.riot.lane-analysis-match-count`(기본 20)판의 솔로/듀오 랭크(queueId 420). 라인 판정은 `participant.teamPosition` 을 쓴다 (레거시 `lane`/`role` 은 부정확한 경우가 있다). Riot 이름은 우리 enum 과 다르므로 변환한다:

| Riot teamPosition | 우리 Lane |
|---|---|
| TOP | TOP |
| JUNGLE | JUNGLE |
| MIDDLE | MID |
| BOTTOM | ADC |
| UTILITY | SUPPORT |
| `""` (리메이크 등) | 집계 제외 |

분석된 판 중 그 라인의 **비중**으로 등급을 매긴다 (판수 절대값이 아니라 비중 — 20판을 다 못 받아온 신규 계정에서도 동작해야 한다):

| 비중 | lanePool |
|---|---|
| ≥ 40% | 5 (주라인) |
| ≥ 20% | 4 |
| ≥ 10% | 3 |
| > 0% | 2 |
| 0% (최근에 간 적 없음) | **1** |

**0(배정 금지)은 자동으로 주지 않는다.** 최근에 안 갔다는 이유로 라인을 아예 막으면 인원이 빠듯할 때 팀 구성이 실패한다. 0 은 관리자가 명시적으로 지정할 때만 쓴다. 분석할 판이 아예 없으면(신규 계정·조회 실패) 전 라인 1.

검증 예 (실측: `hide on bush#KR1` 최근 솔랭 20판 = MIDDLE 14 / JUNGLE 6):

| 라인 | 판수(비중) | lanePool |
|---|---|---|
| MID | 14 (70%) | 5 |
| JUNGLE | 6 (30%) | 4 |
| TOP / ADC / SUPPORT | 0 | 1 |

**비용 주의:** 1(매치 id 목록) + N(매치 상세) 회 호출이라 20판이면 **21회**다. 개발 키는 20req/s 뿐 아니라 **100req/2min** 제한도 있어서 플레이어를 연달아 등록하면 429 가 난다. 그래서 중간에 끊기면 예외 대신 **그때까지 모은 분포로 진행**한다 (부분 데이터라도 전 라인 기본값보다 낫고, 등록 자체를 실패시키지 않는다).

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
