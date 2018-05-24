ALTER TABLE datasets2
    RENAME TO providers_set;

AlTER TABLE sites2
    RENAME "dataset-id" TO "provider-set-id";

ALTER TABLE projects2
    RENAME "dataset-id" TO "provider-set-id";

ALTER TABLE sites2
    RENAME TO providers;

ALTER TABLE sites2_coverage
    RENAME TO providers_coverage;

ALTER TABLE providers_coverage
    RENAME "site-id" TO "provider-id";
