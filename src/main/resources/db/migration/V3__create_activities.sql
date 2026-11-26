CREATE TABLE activities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    location VARCHAR(160) NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6) NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_activities_status_starts (status, starts_at, id),
    CONSTRAINT fk_activities_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_activities_schedule CHECK (ends_at > starts_at),
    CONSTRAINT ck_activities_capacity CHECK (capacity > 0)
) ENGINE=InnoDB;
