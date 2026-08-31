-- 점수 반영 이력의 중복 방지 백스톱.
--
-- matches.rating_applied 플래그만으로는 결과 확정이 동시에 들어올 때 이중 반영을 막지 못한다
-- (stale 엔티티가 가드를 통과할 수 있음). 이력 테이블에 유니크를 걸어 DB 레벨에서도 막는다.
--
-- lane 이 NULL(scope='OVERALL')인 행이 있어서 (match_id, player_id, scope, lane) 만으로는
-- 소용이 없다 — MySQL 의 UNIQUE 는 NULL 을 서로 다른 값으로 보므로 OVERALL 행은 얼마든지
-- 중복 삽입된다. 그래서 NULL 을 '-' 로 접은 생성 컬럼을 만들어 거기에 유니크를 건다.
--
-- match_id 가 NULL 인 행(SEED/MANUAL_ADJUST 등)은 유니크 대상에서 자연히 빠진다. 의도한 것이다.
ALTER TABLE rating_history
  ADD COLUMN lane_key VARCHAR(8)
    AS (COALESCE(lane, '-')) STORED
    COMMENT 'UNIQUE 용. lane 의 NULL 을 -로 접은 값',
  ADD UNIQUE KEY uk_rh_match_player_scope (match_id, player_id, scope, lane_key);
