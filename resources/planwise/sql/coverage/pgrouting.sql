-- :name compute-pgr-alpha-coverage :? :1
SELECT "result", "polygon"
FROM pgr_alpha_shape_coverage(:point, :threshold::integer) AS("result" TEXT, "polygon" GEOMETRY);

-- :name get-closest-node :? :1
SELECT lon,lat FROM ways_vertices_pgr
	WHERE id IN (SELECT * FROM closest_node(:point));
