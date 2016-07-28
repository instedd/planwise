UPDATE ways
       SET cost_s = length_m / maxspeed_forward * 5 / 18 * 1.0;

DROP FUNCTION IF EXISTS apply_traffic_factor(float);
