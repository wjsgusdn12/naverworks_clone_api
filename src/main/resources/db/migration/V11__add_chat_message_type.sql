ALTER TABLE chat_message ADD COLUMN message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT';
ALTER TABLE chat_message ADD CONSTRAINT ck_chat_message_type CHECK (message_type IN ('TEXT', 'SYSTEM'));
