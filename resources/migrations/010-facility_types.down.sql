DROP TABLE facility_types;

ALTER TABLE facilities RENAME type_id TO type;
ALTER TABLE facilities ALTER COLUMN type TYPE varchar(255);
