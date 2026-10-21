ALTER TABLE chat_room DROP CONSTRAINT ck_chat_room_type;
ALTER TABLE chat_room ADD CONSTRAINT ck_chat_room_type CHECK (room_type IN ('SELF', 'DIRECT', 'GROUP'));
ALTER TABLE chat_room DROP CONSTRAINT ck_chat_room_group_name;
ALTER TABLE chat_room ADD CONSTRAINT ck_chat_room_group_name CHECK ((room_type IN ('SELF', 'DIRECT') AND name IS NULL) OR (room_type = 'GROUP' AND name IS NOT NULL));
