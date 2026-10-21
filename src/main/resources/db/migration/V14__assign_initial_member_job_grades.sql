-- 초기 계정은 직급 테이블 생성 전에 만들어져 직급 연결이 비어 있었음.
-- 이미 관리자가 변경한 직급은 덮어쓰지 않도록 NULL인 데이터만 보정.
UPDATE membership AS membership
SET job_grade_id = job_grade.id
FROM user_account AS account,
     job_grade
WHERE membership.user_account_id = account.id
  AND job_grade.company_id = membership.company_id
  AND job_grade.name = CASE account.email
     WHEN 'admin@naverworks.local' THEN '대표'
     WHEN 'user@naverworks.local' THEN '사원'
   END
  AND membership.job_grade_id IS NULL
  AND account.email IN ('admin@naverworks.local', 'user@naverworks.local');
