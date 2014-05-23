WITH q1 AS (
  SELECT
    FIRST_VALUE(file_hash) OVER (PARTITION BY promotion_label, file_path ORDER BY promotion_start_timestamp DESC) last_file_hash
  , f.*
  FROM promotion_files f
  WHERE promotion_label = ?
  AND file_path = ?
  AND file_index = ?
)
SELECT 
  SUM(CASE WHEN ignore_flag = 'Y' THEN 1 ELSE 0 END) ignored_count
, SUM(CASE WHEN ignore_flag = 'Y' THEN 0 ELSE 1 END) not_ignored_count
, MAX(last_file_hash) last_file_hash --MAX() to flatten
FROM q1