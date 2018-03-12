CREATE TABLE IF NOT EXISTS projects2 (
       id BIGSERIAL PRIMARY KEY,
       "owner-id" BIGINT NOT NULL REFERENCES users(id),
       name VARCHAR(255) NOT NULL
);
