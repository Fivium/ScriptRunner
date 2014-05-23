UPDATE patch_runs
SET status = :status 
, end_timestamp = SYSTIMESTAMP
, output_log = :log
WHERE id = :id