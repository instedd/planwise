ALTER TABLE datasets2
    RENAME TO providers_set;

AlTER TABLE sites2
    RENAME COLUMN "dataset-id" TO "provider-set-id";

ALTER TABLE projects2
    RENAME COLUMN "dataset-version" TO "provider-set-version";

ALTER TABLE projects2
    RENAME COLUMN "dataset-id" TO "provider-set-id";

ALTER TABLE sites2
    RENAME TO providers;

ALTER TABLE "sites2_coverage"
    RENAME TO "providers_coverage";

ALTER TABLE providers_coverage
    RENAME COLUMN "site-id" TO "provider-id";
