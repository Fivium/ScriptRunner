INSERT INTO patch_run_statements (
  statement_hash
, patch_label
, patch_number
, patch_run_id
, script_sequence
, start_timestamp
, status
, statement_sql
)
VALUES (
  :hash
, :patch_label
, :patch_number
, :patch_run_id
, :script_seq
, SYSTIMESTAMP
, 'STARTED'
, :sql
)