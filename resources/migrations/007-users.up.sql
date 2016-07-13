CREATE TABLE users (
       id BIGINT PRIMARY KEY,
       email VARCHAR(255) NOT NULL,
       full_name VARCHAR(255),
       last_login TIMESTAMP,
       created_at TIMESTAMP NOT NULL
);

CREATE INDEX users_email_idx ON users (email);
