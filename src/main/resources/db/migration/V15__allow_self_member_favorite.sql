-- 본인도 주소록 즐겨찾기에 추가할 수 있도록 자기 자신 금지 제약을 제거.
ALTER TABLE member_favorite
    DROP CONSTRAINT IF EXISTS ck_member_favorite_not_self;
