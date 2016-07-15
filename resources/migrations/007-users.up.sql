CREATE TABLE users (
       id BIGSERIAL PRIMARY KEY,
       email VARCHAR(255) UNIQUE NOT NULL,
       full_name VARCHAR(255),
       last_login TIMESTAMP WITH TIME ZONE,
       created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX users_email_idx ON users (email);
