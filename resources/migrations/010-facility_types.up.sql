CREATE TABLE facility_types  (
       id integer PRIMARY KEY,
       name VARCHAR(255) NOT NULL);

ALTER TABLE facilities RENAME type TO type_id;
ALTER TABLE facilities ALTER COLUMN type_id TYPE integer USING (trim(type_id)::integer);

INSERT INTO facility_types (id, name) VALUES
  (1, 'hospital'), (2, 'general hospital'), (3, 'health center'), (4, 'dispensary');
