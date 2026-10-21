-- contains 검색에 필요한 trigram 인덱스. 주소록 자동완성의 이름·이메일 검색을 가속.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_user_account_display_name_trgm
    ON user_account USING GIN (lower(display_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_user_account_email_trgm
    ON user_account USING GIN (lower(email) gin_trgm_ops);
