-- 메시지 전달: 전달로 만들어진 메시지가 어떤 원본에서 왔는지 추적(화면에는 "전달된 메시지" 라벨만 표시).
ALTER TABLE chat_message ADD COLUMN forwarded_from_message_id BIGINT NULL;
ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_forwarded_from
    FOREIGN KEY (forwarded_from_message_id) REFERENCES chat_message (id);

-- 방 공지: 방당 하나. 메시지를 공지로 등록하면 방 상단에 고정되고, 새로 등록하면 교체됨.
ALTER TABLE chat_room ADD COLUMN notice_message_id BIGINT NULL;
ALTER TABLE chat_room ADD COLUMN notice_set_by_membership_id BIGINT NULL;
ALTER TABLE chat_room ADD COLUMN notice_set_at TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE chat_room ADD CONSTRAINT fk_chat_room_notice_message
    FOREIGN KEY (notice_message_id) REFERENCES chat_message (id);
ALTER TABLE chat_room ADD CONSTRAINT fk_chat_room_notice_set_by
    FOREIGN KEY (notice_set_by_membership_id) REFERENCES membership (id);
