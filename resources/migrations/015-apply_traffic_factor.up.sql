CREATE OR REPLACE FUNCTION apply_traffic_factor(factor float)
RETURNS void AS $$
BEGIN
  UPDATE ways
         SET cost_s = length_m / (maxspeed_forward * 5 / 18) * factor;
END;
$$ LANGUAGE plpgsql;

UPDATE ways
       SET cost_s = length_m / (maxspeed_forward * 5 / 18) * 1.5;
