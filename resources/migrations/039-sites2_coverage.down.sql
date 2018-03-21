DROP TABLE "sites2_coverage";

ALTER TABLE "sites2" DROP COLUMN "processing-status";
ALTER TABLE "sites2" DROP COLUMN "id";
ALTER TABLE "sites2" RENAME COLUMN "source-id" TO "id";
