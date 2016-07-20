CREATE TABLE tokens (
       id BIGSERIAL PRIMARY KEY,
       user_id BIGINT NOT NULL REFERENCES users(id),
       scope VARCHAR(255) NOT NULL,
       token VARCHAR(255) NOT NULL,
       refresh_token VARCHAR(255) NOT NULL,
       expires TIMESTAMP WITH TIME ZONE
);
