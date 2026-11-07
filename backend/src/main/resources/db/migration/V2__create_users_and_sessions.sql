CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(32),
    avatar_url VARCHAR(500),
    gender VARCHAR(16) NOT NULL DEFAULT 'UNSPECIFIED',
    birthday DATE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE user_sessions (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_user_sessions_user ON user_sessions (user_id);
CREATE INDEX idx_user_sessions_expiry ON user_sessions (expires_at);

-- Preserve legacy bills without assigning them to an arbitrary new account.
ALTER TABLE bills ADD COLUMN user_id BIGINT;
ALTER TABLE bills ADD CONSTRAINT fk_bills_user FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX idx_bills_user_date_id ON bills (user_id, billing_date, id);
