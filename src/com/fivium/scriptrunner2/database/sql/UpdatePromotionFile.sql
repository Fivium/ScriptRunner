UPDATE promotion_files
SET promotion_end_timestamp = SYSTIMESTAMP
, status = ?
WHERE id = ?