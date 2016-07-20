CREATE TABLE facility_types  (
       id SERIAL PRIMARY KEY,
       name VARCHAR(255) NOT NULL);

ALTER TABLE facilities DROP COLUMN type;
ALTER TABLE facilities ADD COLUMN type_id INTEGER REFERENCES facility_types(id);

INSERT INTO facility_types (id, name) VALUES
  (1, 'hospital'), (2, 'general hospital'), (3, 'health center'), (4, 'dispensary');
