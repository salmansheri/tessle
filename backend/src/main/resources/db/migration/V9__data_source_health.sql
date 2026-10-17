-- Latest result of the background ping (Milestone 2). Separate from the connection test, which gates enabling:
-- a ping never changes updated_at, so it cannot make a concurrent edit fail its compare-and-swap.
ALTER TABLE data_sources
    ADD COLUMN ping_ok    BOOLEAN,
    ADD COLUMN ping_at    TIMESTAMPTZ,
    ADD COLUMN ping_error TEXT;
