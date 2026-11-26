CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX ix_notifications_user_id (user_id, id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_activity FOREIGN KEY (activity_id) REFERENCES activities (id)
) ENGINE=InnoDB;

CREATE TABLE operation_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX ix_operation_logs_action_id (action, id),
    INDEX ix_operation_logs_actor_id (actor_id, id),
    CONSTRAINT fk_operation_logs_actor FOREIGN KEY (actor_id) REFERENCES users (id)
) ENGINE=InnoDB;
