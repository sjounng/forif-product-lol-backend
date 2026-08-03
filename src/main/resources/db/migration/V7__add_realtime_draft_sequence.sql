-- realtime-v1
-- Draft 상태 버전과 독립적인 이벤트 순번을 저장해 HOVER와 재연결 replay를 지원한다.

ALTER TABLE drafts
  ADD COLUMN last_event_seq INT UNSIGNED NOT NULL DEFAULT 0 AFTER version;

ALTER TABLE draft_events
  ADD COLUMN version INT UNSIGNED NOT NULL DEFAULT 0 AFTER seq;

UPDATE draft_events
SET version = seq;

UPDATE drafts draft
SET last_event_seq = COALESCE((
  SELECT MAX(event.seq)
  FROM draft_events event
  WHERE event.draft_id = draft.id
), 0);
