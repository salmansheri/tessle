CREATE TABLE users (
    id         UUID PRIMARY KEY,
    email      TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Emails are stored lower-cased by the application; the index enforces it.
CREATE UNIQUE INDEX users_email_key ON users (lower(email));

-- One live sign-in code per email: requesting a new one replaces the old.
CREATE TABLE otp_codes (
    id         UUID PRIMARY KEY,
    email      TEXT NOT NULL UNIQUE,
    code_hash  TEXT NOT NULL,
    attempts   INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
