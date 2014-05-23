DECLARE
  l_clob CLOB;
BEGIN

  UPDATE promotion_runs
  SET
    end_datetime = SYSDATE
  , status = :status
  WHERE id = :id
  RETURNING output_log INTO l_clob;
  
  :log := l_clob;
  
END;