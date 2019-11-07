ALTER TABLE projects2 ADD COLUMN "coverage-algorithm" VARCHAR(100);
UPDATE projects2 AS p2
  SET "coverage-algorithm" = (
      SELECT "coverage-algorithm"
      FROM providers_set
      WHERE p2."provider-set-id" = providers_set.id
  );
ALTER TABLE providers_set DROP COLUMN "coverage-algorithm";
