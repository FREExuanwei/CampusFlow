CREATE TABLE enrollments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    enrolled_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_enrollments_activity_user UNIQUE (activity_id, user_id),
    INDEX ix_enrollments_activity_status (activity_id, status),
    INDEX ix_enrollments_user_id (user_id, id),
    CONSTRAINT fk_enrollments_activity FOREIGN KEY (activity_id) REFERENCES activities (id),
    CONSTRAINT fk_enrollments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_enrollments_status CHECK (
        CASE status
            WHEN 'ENROLLED' THEN CASE WHEN cancelled_at IS NULL THEN 1 ELSE 0 END
            WHEN 'CANCELLED' THEN CASE WHEN cancelled_at IS NOT NULL THEN 1 ELSE 0 END
            ELSE 0
        END = 1
    )
) ENGINE=InnoDB;
