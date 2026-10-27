alter table chat_room_member add column if not exists notification_mode varchar(20) not null default 'ALL';

create table if not exists chat_message_mention (
    chat_message_id bigint not null,
    membership_id bigint not null,
    primary key (chat_message_id, membership_id),
    constraint fk_chat_message_mention_message foreign key (chat_message_id) references chat_message(id)
);

create index if not exists idx_chat_message_mention_membership on chat_message_mention(membership_id);
