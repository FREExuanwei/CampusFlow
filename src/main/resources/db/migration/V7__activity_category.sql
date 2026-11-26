ALTER TABLE activities ADD COLUMN category VARCHAR(24) NOT NULL DEFAULT 'GENERAL'
    CONSTRAINT ck_activities_category CHECK (
        CASE category
            WHEN 'GENERAL' THEN 1
            WHEN 'ACADEMIC' THEN 1
            WHEN 'CULTURE' THEN 1
            WHEN 'SPORTS' THEN 1
            WHEN 'VOLUNTEER' THEN 1
            ELSE 0
        END = 1
    );
