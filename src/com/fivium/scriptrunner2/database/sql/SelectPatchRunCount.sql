WITH q1 AS (
  SELECT
    pr.*
  , FIRST_VALUE(file_hash) OVER (PARTITION BY patch_label, patch_number ORDER BY start_timestamp DESC) last_file_hash
  FROM patch_runs pr
  WHERE patch_label = ?
  AND patch_number = ?
)
SELECT 
  SUM(CASE WHEN ignore_flag = 'Y' THEN 1 ELSE 0 END) ignored_count
, SUM(CASE WHEN ignore_flag = 'Y' THEN 0 ELSE 1 END) not_ignored_count
, MAX(last_file_hash) last_file_hash
FROM q1