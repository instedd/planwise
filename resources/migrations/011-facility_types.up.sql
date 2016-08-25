CREATE TABLE facility_types  (
       id SERIAL PRIMARY KEY,
       name VARCHAR(255) NOT NULL);

ALTER TABLE facilities DROP COLUMN type;
ALTER TABLE facilities ADD COLUMN type_id INTEGER REFERENCES facility_types(id);

