DECLARE
  l_new_id NUMBER;
  l_existing_run_count NUMBER;
  p_patch_label VARCHAR2(100) := :patch_label;
  p_patch_number NUMBER := :patch_number;
BEGIN

  MERGE INTO patches p
  USING (SELECT p_patch_label patch_label, p_patch_number patch_number FROM dual) q1
  ON (p.patch_label = q1.patch_label AND p.patch_number = q1.patch_number)
  WHEN NOT MATCHED THEN INSERT (
    patch_label
  , patch_number
  , created_datetime
  , last_run_datetime
  )
  VALUES (
    p_patch_label
  , p_patch_number
  , SYSDATE
  , SYSDATE
  )
  WHEN MATCHED THEN UPDATE SET last_run_datetime = SYSDATE;

  INSERT INTO patch_runs (
    id
  , patch_label
  , patch_number
  , patch_description
  , promotion_run_id
  , promotion_label
  , promotion_load_sequence
  , start_timestamp
  , status
  , file_hash
  , file_version
  , file_contents
  )
  VALUES (
    patch_runs_seq.nextval
  , p_patch_label
  , p_patch_number
  , :description
  , :promotion_id
  , :promotion_label
  , :load_seq
  , SYSTIMESTAMP
  , 'STARTED'
  , :hash
  , :version
  , :file
  )
  RETURNING id INTO l_new_id;
  
  :patch_id := l_new_id;  
  
END;