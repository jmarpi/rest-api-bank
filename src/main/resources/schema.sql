CREATE TABLE IF NOT EXISTS country (
    id           BIGSERIAL PRIMARY KEY,
    alpha2_code  VARCHAR(2)   NOT NULL UNIQUE,
    alpha3_code  VARCHAR(3),
    name         VARCHAR(255),
    capital      VARCHAR(255),
    region       VARCHAR(255),
    income_level VARCHAR(255),
    latitude     NUMERIC(9, 6),
    longitude    NUMERIC(9, 6),
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_country_alpha3 ON country (alpha3_code);
