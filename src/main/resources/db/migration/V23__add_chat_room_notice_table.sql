-- 방 공지를 방당 여러 개 둘 수 있도록 별도 테이블로 분리.
-- 내린 공지는 removed_at만 기록(논리 삭제). chat_room의 notice_* 컬럼은 더 이상 쓰지 않음.
CREATE TABLE chat_room_notice (
    id BIGSERIAL PRIMARY KEY,
    chat_room_id BIGINT NOT NULL REFERENCES chat_room (id),
    chat_message_id BIGINT NOT NULL REFERENCES chat_message (id),
    set_by_membership_id BIGINT NOT NULL REFERENCES membership (id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    removed_at TIMESTAMP WITH TIME ZONE NULL
);

CREATE INDEX idx_chat_room_notice_room ON chat_room_notice (chat_room_id, removed_at);
CREATE INDEX idx_chat_room_notice_message ON chat_room_notice (chat_message_id);

-- 기존에 방당 하나로 걸려 있던 공지를 새 테이블로 옮김.
INSERT INTO chat_room_notice (chat_room_id, chat_message_id, set_by_membership_id, created_at)
SELECT id, notice_message_id, notice_set_by_membership_id, COALESCE(notice_set_at, NOW())
FROM chat_room
WHERE notice_message_id IS NOT NULL AND notice_set_by_membership_id IS NOT NULL;
