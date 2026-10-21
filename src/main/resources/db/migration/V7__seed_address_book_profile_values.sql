-- 초기 데모 계정도 주소록 상세 화면에서 실제 프로필 값을 보여주도록 보완.
UPDATE membership
SET phone_number = '010-0000-0000',
    hired_at = DATE '2026-10-20'
WHERE user_account_id = (SELECT id FROM user_account WHERE email = 'admin@naverworks.local');

UPDATE membership
SET phone_number = '010-9999-9999',
    hired_at = DATE '2026-10-20'
WHERE user_account_id = (SELECT id FROM user_account WHERE email = 'user@naverworks.local');
