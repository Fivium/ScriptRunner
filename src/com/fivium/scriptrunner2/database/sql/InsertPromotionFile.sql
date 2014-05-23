DECLARE
  l_new_id NUMBER;
BEGIN

  INSERT INTO promotion_files (
    id
  , promotion_run_id
  , promotion_label
  , file_path
  , promotion_start_timestamp
  , status
  , load_sequence
  , loader_name
  , file_hash
  , file_version
  , file_index
  ) 
  VALUES (
    promotion_files_seq.nextval
  , :run_id
  , :label
  , :path
  , SYSTIMESTAMP
  , 'STARTED'
  , :sequence
  , :loader
  , :hash
  , :version
  , :index
  )
  RETURNING id INTO l_new_id;

  :new_id := l_new_id;
  
END;