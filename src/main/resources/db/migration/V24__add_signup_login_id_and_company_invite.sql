-- 공개 가입을 위해 로그인 식별자를 이메일에서 아이디로 바꾸고, 회사에 체험용 표시와 초대 코드를 둠.

-- 1) 아이디 컬럼. 기존 계정은 이메일을 그대로 아이디로 사용해 지금처럼 로그인할 수 있게 함.
ALTER TABLE user_account ADD COLUMN login_id VARCHAR(100);
UPDATE user_account SET login_id = email WHERE login_id IS NULL;
ALTER TABLE user_account ALTER COLUMN login_id SET NOT NULL;
ALTER TABLE user_account ADD CONSTRAINT uk_user_account_login_id UNIQUE (login_id);

-- 2) 이메일은 선택 입력으로. 값이 있으면 여전히 유일해야 함(기존 유니크 제약 유지).
ALTER TABLE user_account ALTER COLUMN email DROP NOT NULL;

-- 3) 비밀번호 재설정용 복구 코드. 원문은 저장하지 않고 SHA-256 해시만 둠.
ALTER TABLE user_account ADD COLUMN recovery_code_hash VARCHAR(64);
CREATE INDEX idx_user_account_recovery_code_hash ON user_account (recovery_code_hash);

-- 4) 회사: 체험 회사 표시와 초대 코드. 초대 코드는 관리자가 처음 볼 때 발급하므로 비어 있을 수 있음.
ALTER TABLE company ADD COLUMN demo BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE company ADD COLUMN invite_code VARCHAR(20);
ALTER TABLE company ADD CONSTRAINT uk_company_invite_code UNIQUE (invite_code);

-- 초기 관리자가 속한 회사를 체험 회사로 지정. 가입자는 기본으로 이 회사에 들어감.
UPDATE company
SET demo = TRUE
WHERE id = (
    SELECT membership.company_id
    FROM membership
    JOIN user_account ON user_account.id = membership.user_account_id
    WHERE user_account.email = 'admin@naverworks.local'
);
