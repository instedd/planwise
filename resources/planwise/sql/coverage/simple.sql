-- :name compute-simple-buffer-coverage :? :1
SELECT "result", "polygon"
FROM simple_buffer_coverage(:point, :distance::integer) AS("result" TEXT, "polygon" GEOMETRY);
