-- USER 권한 화면과 역할 기반 접근을 검증할 일반 사용자 계정을 한 번만 생성.
INSERT INTO user_account (email, password_hash, display_name, enabled)
VALUES (
    'user@naverworks.local',
    '$2a$12$NxtFLOMkgp4BZF62K1ZiBuD3qilPfYO4ZXe4tdH4Dmt62ObaMuO/u',
    '일반 사용자',
    TRUE
);

-- 초기 일반 사용자는 관리자와 같은 회사·부서에 USER 역할로 소속.
INSERT INTO membership (company_id, user_account_id, department_id, role)
SELECT company.id, user_account.id, department.id, 'USER'
FROM company
JOIN user_account ON user_account.email = 'user@naverworks.local'
LEFT JOIN department ON department.company_id = company.id AND department.name = '관리팀'
WHERE company.name = 'NAVER WORKS Clone';
