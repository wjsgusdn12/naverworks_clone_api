-- 로그인 유지 선택 여부를 Refresh Token과 함께 보관해 재발급 시에도 쿠키 성격을 유지.
ALTER TABLE refresh_token
    ADD COLUMN persistent_login BOOLEAN NOT NULL DEFAULT FALSE;
