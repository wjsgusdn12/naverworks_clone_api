-- 메시지 ID와 생성 시각을 유지하는 논리 삭제 및 동시 수정 충돌 검사.
ALTER TABLE chat_message ADD COLUMN edited_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE chat_message ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE chat_message ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
