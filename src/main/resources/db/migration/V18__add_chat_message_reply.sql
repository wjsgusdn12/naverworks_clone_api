-- 답장은 같은 방의 원문을 참조하고, 인용 내용은 전송 당시 값으로 보존.
ALTER TABLE chat_message ADD COLUMN reply_to_message_id BIGINT;
ALTER TABLE chat_message ADD COLUMN reply_to_sender_name VARCHAR(255);
ALTER TABLE chat_message ADD COLUMN reply_to_content VARCHAR(2000);

ALTER TABLE chat_message ADD CONSTRAINT fk_chat_message_reply
    FOREIGN KEY (reply_to_message_id) REFERENCES chat_message(id);
CREATE INDEX idx_chat_message_reply ON chat_message(reply_to_message_id);
