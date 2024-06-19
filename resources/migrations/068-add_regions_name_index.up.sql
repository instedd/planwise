ALTER TABLE regions ADD CONSTRAINT "regions_name_index" UNIQUE ("country", "name", "admin_level");
