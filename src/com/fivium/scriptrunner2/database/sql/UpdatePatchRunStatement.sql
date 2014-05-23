UPDATE patch_run_statements
SET end_timestamp = SYSTIMESTAMP
, status = :status
WHERE patch_run_id = :patch_run_id
AND statement_hash = :hash