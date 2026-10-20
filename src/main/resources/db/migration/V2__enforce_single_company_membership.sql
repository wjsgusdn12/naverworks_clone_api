-- 초기 서비스 정책: 하나의 사용자 계정은 하나의 회사에만 소속.
-- 여러 회사 선택 로그인 기능을 도입할 때는 별도 요구사항과 마이그레이션으로 변경.
ALTER TABLE membership
    ADD CONSTRAINT uk_membership_user_account UNIQUE (user_account_id);
