-- TLS settings for Data sources (ADR 0007). Verification is on unless an admin turns it off (audited in the
-- application); an optional custom CA certificate is stored sealed like any other credential.
ALTER TABLE data_sources
    ADD COLUMN tls_verify    BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN ca_ciphertext BYTEA,
    ADD COLUMN ca_key_id     TEXT,
    ADD CONSTRAINT data_sources_ca_sealed CHECK ((ca_ciphertext IS NULL) = (ca_key_id IS NULL));
