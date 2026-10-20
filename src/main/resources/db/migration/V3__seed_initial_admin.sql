-- 개발 초기 진입점으로 사용할 회사·부서·관리자 계정을 한 번만 생성.
INSERT INTO company (name)
VALUES ('NAVER WORKS Clone');

INSERT INTO department (company_id, name)
SELECT id, '관리팀'
FROM company
WHERE name = 'NAVER WORKS Clone';

-- 비밀번호 1234의 BCrypt 해시. 평문 비밀번호는 DB에 저장하지 않음.
INSERT INTO user_account (email, password_hash, display_name, enabled)
VALUES (
    'admin@naverworks.local',
    '$2a$12$NxtFLOMkgp4BZF62K1ZiBuD3qilPfYO4ZXe4tdH4Dmt62ObaMuO/u',
    '관리자',
    TRUE
);

-- 초기 관리자는 NAVER WORKS Clone 회사의 ADMIN 권한을 가짐.
INSERT INTO membership (company_id, user_account_id, department_id, role)
SELECT company.id, user_account.id, department.id, 'ADMIN'
FROM company
JOIN user_account ON user_account.email = 'admin@naverworks.local'
LEFT JOIN department ON department.company_id = company.id AND department.name = '관리팀'
WHERE company.name = 'NAVER WORKS Clone';
