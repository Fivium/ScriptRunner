SELECT COUNT(*)
FROM promotion_runs
WHERE promotion_label = ?
AND ignore_flag IS NULL