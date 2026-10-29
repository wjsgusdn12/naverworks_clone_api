-- 구성원 상태 설정(바쁨·자리 비움·퇴근·부재·휴가·출장·병가). NULL이면 "설정 안 함".
-- 종료 시각은 기존 away_until 컬럼을 그대로 씀(엔티티 필드명은 statusUntil).
ALTER TABLE membership ADD COLUMN status_type VARCHAR(30);
