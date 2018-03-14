-- :name compute-pgr-alpha-coverage :? :1
SELECT "result", "polygon"
FROM pgr_alpha_shape_coverage(:point, :threshold::integer) AS("result" TEXT, "polygon" GEOMETRY);
