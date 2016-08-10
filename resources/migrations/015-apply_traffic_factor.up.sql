DO $$
  BEGIN
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = 'ways') THEN
      UPDATE ways
      SET cost_s = length_m / (maxspeed_forward * 5 / 18) * 1.5;
    END IF;
  END;
$$
