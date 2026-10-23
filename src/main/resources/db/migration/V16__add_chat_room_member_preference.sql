alter table chat_room_member add column if not exists hidden_at timestamp with time zone;
alter table chat_room_member add column if not exists pinned_at timestamp with time zone;
alter table chat_room_member add column if not exists notification_enabled boolean not null default true;
