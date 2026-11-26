ALTER TABLE activities ADD COLUMN reviewed_by BIGINT;
ALTER TABLE activities ADD COLUMN reviewed_at DATETIME(6);
ALTER TABLE activities ADD COLUMN review_reason VARCHAR(500);
ALTER TABLE activities ADD COLUMN published_at DATETIME(6);

ALTER TABLE activities ADD CONSTRAINT fk_activities_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id);
ALTER TABLE activities ADD CONSTRAINT ck_activities_status CHECK (
    CASE status
        WHEN 'DRAFT' THEN 1
        WHEN 'SUBMITTED' THEN 1
        WHEN 'APPROVED' THEN 1
        WHEN 'REJECTED' THEN 1
        WHEN 'PUBLISHED' THEN 1
        ELSE 0
    END = 1
);
