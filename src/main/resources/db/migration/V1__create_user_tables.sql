CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(32) NOT NULL,
    password_hash VARCHAR(60) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT ck_users_status CHECK (
        CASE status WHEN 'ACTIVE' THEN 1 WHEN 'DISABLED' THEN 1 ELSE 0 END = 1
    )
) ENGINE=InnoDB;

CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(32) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_roles_role_code UNIQUE (role_code)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    INDEX ix_user_roles_role_user (role_id, user_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB;
