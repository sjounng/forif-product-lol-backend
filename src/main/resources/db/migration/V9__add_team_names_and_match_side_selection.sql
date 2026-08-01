ALTER TABLE session_teams
  ADD COLUMN team_name VARCHAR(30) NULL AFTER side;

UPDATE session_teams st
JOIN users u ON u.id = st.captain_user_id
SET st.team_name = CONCAT(u.display_name, ' 팀')
WHERE st.team_name IS NULL;

ALTER TABLE session_teams
  MODIFY COLUMN team_name VARCHAR(30) NOT NULL;

ALTER TABLE match_start_requests
  ADD COLUMN blue_team_side ENUM('BLUE','RED') NOT NULL DEFAULT 'BLUE' AFTER proposed_by_user_id;

ALTER TABLE matches
  ADD COLUMN blue_team_side ENUM('BLUE','RED') NOT NULL DEFAULT 'BLUE' AFTER is_manual_team;
