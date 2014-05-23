DECLARE
  l_new_id NUMBER;
  p_label VARCHAR2(200) := :promotion_label;
BEGIN

  MERGE INTO promotion_labels pl
  USING (
    SELECT p_label label FROM dual
  ) q1
  ON (q1.label = pl.label)
  WHEN NOT MATCHED THEN INSERT (label, created_datetime, last_promoted_datetime)
  VALUES (p_label, SYSDATE, SYSDATE)
  WHEN MATCHED THEN UPDATE
  SET pl.last_promoted_datetime = SYSDATE;

  INSERT INTO promotion_runs(
    id
  , promotion_label  
  , scriptrunner_version
  , start_datetime  
  , status
  , output_log
  )
  VALUES (
    promotion_runs_seq.nextval
  , p_label
  , :version
  , SYSDATE
  , 'STARTED'
  , EMPTY_CLOB()
  )
  RETURNING id INTO l_new_id;
  
  :new_id := l_new_id;
  
END;