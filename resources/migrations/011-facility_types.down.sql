DROP TABLE facility_types;

ALTER TABLE facilities DROP COLUMN type_id;
ALTER TABLE facilities ADD COLUMN type TYPE varchar(255);
