DECLARE
  l_count NUMBER;
  l_install_pr_id NUMBER;
  l_install_pr_label VARCHAR2(200);
BEGIN

  SELECT COUNT(*)
  INTO l_count
  FROM dba_objects
  WHERE owner = 'DBAMGR'
  AND object_name = 'PATCH_CONTROL_TABLE';
  
  IF l_count > 0 THEN 
    
    EXECUTE IMMEDIATE 
      q'{SELECT id, promotion_label    
      FROM promotion_runs
      WHERE promotion_label = ':INSTALL_LABEL'}'
    INTO l_install_pr_id, l_install_pr_label;  

    EXECUTE IMMEDIATE 
      'INSERT INTO patches (
        patch_label
      , patch_number
      , created_datetime
      , last_run_datetime
      )
      SELECT 
        patch_label
      , patch_no
      , MIN(start_datetime)
      , MAX(start_datetime)
      FROM dbamgr.patch_control_table
      GROUP BY patch_label, patch_no';
    
    EXECUTE IMMEDIATE 
      q'{INSERT INTO patch_runs (
        id
      , patch_label
      , patch_number
      , patch_description
      , ignore_flag
      , promotion_run_id
      , promotion_label
      , promotion_load_sequence
      , start_timestamp
      , end_timestamp
      , status
      , file_hash
      , file_version
      , file_contents
      , output_log
      ) 
      SELECT
        patch_runs_seq.NEXTVAL
      , patch_label
      , patch_no
      , patch_desc
      , ignore_flag
      , :l_install_pr_id
      , :l_install_pr_label
      , 0
      , start_datetime
      , end_datetime
      , CASE status WHEN 'ERROR' THEN 'FAILED' ELSE 'COMPLETE' END
      , patch_hash
      , revision
      , EMPTY_CLOB()
      , output_log
      FROM dbamgr.patch_control_table
      WHERE patch_label != 'FLATFILE'}'
    USING l_install_pr_id, l_install_pr_label;
      
  END IF;
  
  COMMIT;
  
END;
/