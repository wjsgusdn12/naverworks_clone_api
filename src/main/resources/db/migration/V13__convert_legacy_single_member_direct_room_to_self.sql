-- SELF 타입 도입 전 생성된 1인 DIRECT 방은 나와의 채팅으로 보정.
UPDATE chat_room
SET room_type = 'SELF'
WHERE room_type = 'DIRECT'
  AND id IN (
    SELECT chat_room_id
    FROM chat_room_member
    GROUP BY chat_room_id
    HAVING COUNT(*) = 1
  );
