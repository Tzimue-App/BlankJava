-- User accounts and refresh tokens (docs/technical_analysis.md §4.3, issue #29).

-- "user" is a reserved word in PostgreSQL, hence app_user.
CREATE TABLE app_user (
    id                 uuid         PRIMARY KEY,
    -- Stored trimmed and lower-cased by the backend; uniqueness is enforced here, so two
    -- concurrent registrations of the same address cannot both succeed.
    email              varchar(254) NOT NULL,
    display_name       varchar(50)  NOT NULL,
    -- {argon2}-prefixed Argon2id hash (DelegatingPasswordEncoder), never the password
    password_hash      varchar(255) NOT NULL,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    -- Tokens issued before this instant are refused: "log out everywhere", from M2 (§5.2)
    tokens_valid_after timestamptz,
    CONSTRAINT app_user_email_uk UNIQUE (email)
);

-- Server-side record of every refresh token issued; only the SHA-256 hash of the value is stored.
CREATE TABLE refresh_token (
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Chain of rotated tokens of one login (reuse detection revokes the whole family, M2)
    family_id   uuid        NOT NULL,
    -- Lower-case hex SHA-256 of the cookie value
    token_hash  char(64)    NOT NULL,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    revoked_at  timestamptz,
    -- Token that replaced this one on rotation (#30)
    replaced_by uuid,
    CONSTRAINT refresh_token_token_hash_uk UNIQUE (token_hash)
);

CREATE INDEX refresh_token_user_id_idx ON refresh_token (user_id);
CREATE INDEX refresh_token_family_id_idx ON refresh_token (family_id);
-- Purge of expired tokens
CREATE INDEX refresh_token_expires_at_idx ON refresh_token (expires_at);
