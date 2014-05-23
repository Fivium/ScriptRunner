---------------------
-- Tablespaces
---------------------

DECLARE
  l_count NUMBER;
BEGIN
  SELECT COUNT(*)
  INTO l_count
  FROM dba_tablespaces
  WHERE tablespace_name = 'TBSCLOB';
          
  IF l_count = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLESPACE "TBSCLOB"';
  END IF;
END;
/

DECLARE
  l_count NUMBER;
BEGIN
  SELECT COUNT(*)
  INTO l_count
  FROM dba_tablespaces
  WHERE tablespace_name = 'TBSDATA';
          
  IF l_count = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLESPACE "TBSDATA"';
  END IF;
END;
/

DECLARE
  l_count NUMBER;
BEGIN
  SELECT COUNT(*)
  INTO l_count
  FROM dba_tablespaces
  WHERE tablespace_name = 'TBSIDX';
          
  IF l_count = 0 THEN
    EXECUTE IMMEDIATE 'CREATE TABLESPACE "TBSIDX"';
  END IF;
END;
/

---------------------
-- Promotion labels
---------------------

CREATE TABLE promotion_labels (
  label VARCHAR2(200)
, created_datetime DATE NOT NULL
, last_promoted_datetime DATE NOT NULL
)
TABLESPACE tbsdata
/

ALTER TABLE promotion_labels
ADD CONSTRAINT promotion_labels_pk
PRIMARY KEY (label)
USING INDEX TABLESPACE tbsidx
/

---------------------
-- Promotion runs
---------------------

CREATE TABLE promotion_runs (
  id NUMBER
, promotion_label VARCHAR2(200) NOT NULL
, scriptrunner_version VARCHAR2(100) NOT NULL
, ignore_flag VARCHAR2(1)
, start_datetime DATE NOT NULL
, end_datetime DATE
, status VARCHAR2(10)
, output_log CLOB
)
TABLESPACE tbsdata
LOB (output_log) STORE AS (TABLESPACE tbsclob)
/

ALTER TABLE promotion_runs
ADD CONSTRAINT promotion_runs_pk
PRIMARY KEY (id)
USING INDEX TABLESPACE tbsidx
/

ALTER TABLE promotion_runs
ADD CONSTRAINT promotion_runs_fk1
FOREIGN KEY (promotion_label)
REFERENCES promotion_labels(label)
/

ALTER TABLE promotion_runs
ADD CONSTRAINT promotion_runs_ck1
CHECK (ignore_flag IS NULL OR ignore_flag = 'Y')
/

ALTER TABLE promotion_runs
ADD CONSTRAINT promotion_runs_ck2
CHECK (status IN('STARTED', 'COMPLETE', 'FAILED'))
/


CREATE INDEX promotion_runs_idx1
ON promotion_runs(promotion_label)
TABLESPACE tbsidx
/

CREATE UNIQUE INDEX promotion_runs_uk1
ON promotion_runs(NVL2(ignore_flag, NULL, promotion_label))
TABLESPACE tbsidx
/


CREATE SEQUENCE promotion_runs_seq
/


---------------------
-- Promotion files
---------------------

CREATE TABLE promotion_files (
  id NUMBER
, promotion_run_id NUMBER NOT NULL
, promotion_label VARCHAR2(200) NOT NULL
, file_path VARCHAR2(4000) NOT NULL
, ignore_flag VARCHAR2(1)
, promotion_start_timestamp TIMESTAMP NOT NULL
, promotion_end_timestamp TIMESTAMP
, status VARCHAR2(10) NOT NULL
, load_sequence NUMBER NOT NULL
, loader_name VARCHAR2(500) NOT NULL
, file_hash VARCHAR2(1000) NOT NULL
, file_version VARCHAR2(4000)
)
TABLESPACE tbsdata
/


ALTER TABLE promotion_files
ADD CONSTRAINT promotion_files_pk
PRIMARY KEY (id)
USING INDEX TABLESPACE tbsidx
/

ALTER TABLE promotion_files
ADD CONSTRAINT promotion_files_fk1
FOREIGN KEY (promotion_run_id)
REFERENCES promotion_runs(id)
/

ALTER TABLE promotion_files
ADD CONSTRAINT promotion_files_fk2
FOREIGN KEY (promotion_label)
REFERENCES promotion_labels(label)
/

ALTER TABLE promotion_files
ADD CONSTRAINT promotion_files_ck1
CHECK (ignore_flag IS NULL OR ignore_flag = 'Y')
/

ALTER TABLE promotion_files
ADD CONSTRAINT promotion_files_ck2
CHECK (status IN('STARTED', 'COMPLETE', 'FAILED'))
/

CREATE INDEX promotion_files_idx1
ON promotion_files(promotion_run_id)
TABLESPACE tbsidx
/

CREATE INDEX promotion_files_idx2
ON promotion_files(promotion_label)
TABLESPACE tbsidx
/


CREATE SEQUENCE promotion_files_seq
/


---------------------
-- Patches
---------------------

CREATE TABLE patches (
  patch_label VARCHAR2(200)
, patch_number NUMBER
, created_datetime DATE NOT NULL
, last_run_datetime DATE NOT NULL
)
TABLESPACE tbsdata
/

ALTER TABLE patches
ADD CONSTRAINT patches_pk
PRIMARY KEY (patch_label, patch_number)
USING INDEX TABLESPACE tbsidx
/


---------------------
-- Patch runs
---------------------

CREATE TABLE patch_runs (
  id NUMBER
, patch_label VARCHAR2(200) NOT NULL
, patch_number NUMBER NOT NULL
, patch_description VARCHAR2(4000) NOT NULL
, ignore_flag VARCHAR2(1)
, promotion_run_id NUMBER NOT NULL
, promotion_label VARCHAR2(200) NOT NULL
, promotion_load_sequence NUMBER NOT NULL
, start_timestamp TIMESTAMP NOT NULL
, end_timestamp TIMESTAMP
, status VARCHAR2(10) NOT NULL
, file_hash VARCHAR2(1000) NOT NULL
, file_version VARCHAR2(4000)
, file_contents CLOB
, output_log CLOB
)
TABLESPACE tbsdata
LOB (output_log) STORE AS (TABLESPACE tbsclob)
LOB (file_contents) STORE AS (TABLESPACE tbsclob)
/


ALTER TABLE patch_runs
ADD CONSTRAINT patch_runs_pk
PRIMARY KEY (id)
USING INDEX TABLESPACE tbsidx
/

ALTER TABLE patch_runs
ADD CONSTRAINT patch_runs_fk1
FOREIGN KEY (patch_label, patch_number)
REFERENCES patches(patch_label, patch_number)
/

ALTER TABLE patch_runs
ADD CONSTRAINT patch_runs_fk2
FOREIGN KEY (promotion_label)
REFERENCES promotion_labels(label)
/

ALTER TABLE patch_runs
ADD CONSTRAINT patch_runs_fk3
FOREIGN KEY (promotion_run_id)
REFERENCES promotion_runs(id)
/

ALTER TABLE patch_runs
ADD CONSTRAINT patch_runs_ck1
CHECK (ignore_flag IS NULL OR ignore_flag = 'Y')
/

ALTER TABLE patch_runs
ADD CONSTRAINT patch_runs_ck2
CHECK (status IN('STARTED', 'COMPLETE', 'FAILED'))
/

CREATE INDEX patch_runs_idx1
ON patch_runs(patch_label, patch_number)
TABLESPACE tbsidx
/

CREATE INDEX patch_runs_idx2
ON patch_runs(promotion_label)
TABLESPACE tbsidx
/

CREATE INDEX patch_runs_idx3
ON patch_runs(promotion_run_id)
TABLESPACE tbsidx
/

CREATE UNIQUE INDEX patch_runs_uk1
ON patch_runs(NVL2(ignore_flag, NULL, patch_label || patch_number))
TABLESPACE tbsidx
/

CREATE SEQUENCE patch_runs_seq
/

---------------------
-- Patch run statements
---------------------

CREATE TABLE patch_run_statements (
  statement_hash VARCHAR2(1000) NOT NULL
, patch_label VARCHAR2(200) NOT NULL
, patch_number NUMBER NOT NULL
, patch_run_id NUMBER NOT NULL
, ignore_flag VARCHAR2(1)
, script_sequence NUMBER NOT NULL
, start_timestamp TIMESTAMP NOT NULL
, end_timestamp TIMESTAMP
, status VARCHAR2(10) NOT NULL
, statement_sql CLOB NOT NULL
)
TABLESPACE tbsdata
LOB (statement_sql) STORE AS (TABLESPACE tbsclob)
/

ALTER TABLE patch_run_statements
ADD CONSTRAINT patch_run_statements_fk1
FOREIGN KEY (patch_label, patch_number)
REFERENCES patches(patch_label, patch_number)
/

ALTER TABLE patch_run_statements
ADD CONSTRAINT patch_run_statements_fk2
FOREIGN KEY (patch_run_id)
REFERENCES patch_runs(id)
/

ALTER TABLE patch_run_statements
ADD CONSTRAINT patch_run_statements_ck1
CHECK (ignore_flag IS NULL OR ignore_flag = 'Y')
/

ALTER TABLE patch_run_statements
ADD CONSTRAINT patch_run_statements_ck2
CHECK (status IN('STARTED', 'COMPLETE', 'FAILED'))
/

CREATE INDEX patch_run_statements_idx1
ON patch_run_statements(statement_hash)
TABLESPACE tbsidx
/

CREATE INDEX patch_run_statements_idx2
ON patch_run_statements(patch_label, patch_number)
TABLESPACE tbsidx
/

CREATE INDEX patch_run_statements_idx3
ON patch_run_statements(patch_run_id)
TABLESPACE tbsidx
/

CREATE OR REPLACE TRIGGER promote_ddl_trigger
BEFORE DDL ON SCHEMA
DECLARE
  l_operation VARCHAR2(4000);
  l_owner VARCHAR2(4000);
  l_curr_schema VARCHAR2(4000);
BEGIN
  SELECT ora_sysevent, ora_dict_obj_owner, ora_login_user
  INTO l_operation, l_owner, l_curr_schema
  FROM DUAL;
  
  IF l_owner = l_curr_schema THEN
    RAISE_APPLICATION_ERROR(-20999, 'DDL ' || l_operation || ' not allowed on promotion schema');
  END IF;
    
END;
/