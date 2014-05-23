SELECT COUNT(*)
FROM patch_run_statements
WHERE patch_label = ?
AND patch_number = ?
AND statement_hash = ?
AND ignore_flag IS NULL