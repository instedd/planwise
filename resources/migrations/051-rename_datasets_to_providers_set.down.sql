ALTER TABLE providers_coverage
    RENAME COLUMN "provider-id" TO "site-id";

ALTER TABLE "providers_coverage"
    RENAME TO "sites2_coverage";

ALTER TABLE providers
    RENAME TO sites2;

ALTER TABLE projects2
    RENAME COLUMN "provider-set-id" TO "dataset-id";

ALTER TABLE projects2
    RENAME COLUMN "provider-set-version" TO "dataset-version";

AlTER TABLE sites2
    RENAME COLUMN "provider-set-id" TO "dataset-id";

ALTER TABLE providers_set
    RENAME TO datasets2;