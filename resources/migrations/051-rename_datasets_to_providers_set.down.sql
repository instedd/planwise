ALTER TABLE providers_coverage
    RENAME "provider-id" TO "site-id";

ALTER TABLE providers_coverage
    RENAME TO sites2_coverage;

ALTER TABLE providers
    IF EXISTS RENAME TO sites2;

ALTER TABLE projects2
    RENAME "provider-set-id" to "dataset-id";

AlTER TABLE sites2
    RENAME "provider-set-id" TO "dataset-id";

ALTER TABLE providers_set
    IF EXISTS RENAME TO datasets2;
