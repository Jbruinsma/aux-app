-- Long-lived tokens that trade for a new access JWT, so a session outlives the 1-hour JWT. Only a SHA-256 of the token
-- is stored. Each token works once: using it marks it used and issues a successor in the same family (one login on one
-- device). Presenting a used token after the grace period means it leaked, so RefreshTokenService drops the whole family
CREATE TABLE refresh_tokens (
    token_hash VARCHAR NOT NULL PRIMARY KEY,
    user_id VARCHAR NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    family_id VARCHAR NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at);
