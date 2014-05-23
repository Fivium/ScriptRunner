SELECT NVL(max_patch, 0)
FROM (
  SELECT MAX(patch_number) max_patch
  FROM patch_runs
  WHERE patch_label = ?
  AND ignore_flag IS NULL
)