ALTER TABLE promotion_files
ADD file_index NUMBER
/

MERGE INTO promotion_files p
USING (
  SELECT
    RANK() OVER(PARTITION BY promotion_label, file_path ORDER BY load_sequence) file_index
  , f.id
  FROM promotion_files f
  WHERE ignore_flag IS NULL
) q1
ON (p.id = q1.id)
WHEN MATCHED THEN UPDATE SET p.file_index = q1.file_index
/

-- Update anything which was missed
UPDATE promotion_files
SET file_index = -1
WHERE file_index IS NULL
/

COMMIT
/

ALTER TABLE promotion_files
MODIFY file_index NUMBER NOT NULL
/