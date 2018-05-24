ALTER TABLE providers_set"
    IF EXISTS RENAME TO datasets2;

AlTER TABLE "sites2"
    RENAME "provider-set-id" TO "dataset-id";