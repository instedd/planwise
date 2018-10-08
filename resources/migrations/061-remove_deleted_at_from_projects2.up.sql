DELETE FROM "scenarios" s
  WHERE s."project-id" = @(SELECT id FROM "projects2" p WHERE p."deleted-at" is NOT NULL);
DELETE FROM "projects2" WHERE "deleted-at" is NOT NULL;
ALTER TABLE "projects2" DROP COLUMN IF EXISTS "deleted-at";
