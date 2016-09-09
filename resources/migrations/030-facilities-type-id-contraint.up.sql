DELETE FROM facilities WHERE type_id IS NULL;
ALTER TABLE facilities ALTER COLUMN type_id SET NOT NULL;
