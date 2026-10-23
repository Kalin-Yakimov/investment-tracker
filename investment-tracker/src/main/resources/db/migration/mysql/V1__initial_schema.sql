-- Initial schema for NEW installations. Existing databases require reviewed, explicit adoption.
-- Never use baseline-on-migrate to skip an unverified existing schema.
CREATE TABLE _user (
    id INT NOT NULL PRIMARY KEY,
    account_locked BIT NOT NULL,
    enabled BIT NOT NULL,
    created_date DATETIME(6) NOT NULL,
    last_modified_date DATETIME(6),
    email VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    password VARCHAR(255),
    username VARCHAR(255),
    CONSTRAINT uk_user_email UNIQUE (email),
    CONSTRAINT uk_user_username UNIQUE (username)
) ENGINE=InnoDB;

CREATE TABLE role (
    id INT NOT NULL PRIMARY KEY,
    created_date DATETIME(6) NOT NULL,
    last_modified_date DATETIME(6),
    name VARCHAR(255),
    CONSTRAINT uk_role_name UNIQUE (name)
) ENGINE=InnoDB;

CREATE TABLE _user_roles (
    users_id INT NOT NULL,
    roles_id INT NOT NULL,
    CONSTRAINT fk_user_roles_user FOREIGN KEY (users_id) REFERENCES _user(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (roles_id) REFERENCES role(id)
) ENGINE=InnoDB;

-- Hibernate's pooled ID generators use one-row tables on MySQL, allocationSize = 50.
CREATE TABLE _user_seq (next_val BIGINT) ENGINE=InnoDB;
INSERT INTO _user_seq VALUES (1);
CREATE TABLE role_seq (next_val BIGINT) ENGINE=InnoDB;
INSERT INTO role_seq VALUES (1);
CREATE TABLE token_seq (next_val BIGINT) ENGINE=InnoDB;
INSERT INTO token_seq VALUES (1);

CREATE TABLE cs_items (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    name VARCHAR(255),
    image VARCHAR(1000),
    market_hash_name VARCHAR(500),
    INDEX idx_cs_items_market_hash (market_hash_name),
    INDEX idx_cs_items_name (name)
) ENGINE=InnoDB;

CREATE TABLE item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_name VARCHAR(255),
    cs_item_id VARCHAR(255),
    item_status ENUM('BOUGHT','SOLD'),
    date DATE,
    quantity INT NOT NULL,
    price DECIMAL(38,2),
    user_id INT,
    market_hash_name VARCHAR(255),
    bought_price DECIMAL(18,2),
    bought_date DATE,
    show_in_dashboard BIT,
    price_notification DECIMAL(18,2),
    steam_found_check BIT,
    INDEX idx_item_user_id (user_id),
    INDEX idx_item_cs_item_id (cs_item_id),
    INDEX idx_item_market_hash (market_hash_name),
    INDEX idx_item_steam_found (steam_found_check),
    CONSTRAINT fk_item_user FOREIGN KEY (user_id) REFERENCES _user(id),
    CONSTRAINT fk_item_cs_item FOREIGN KEY (cs_item_id) REFERENCES cs_items(id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE token (
    id INT NOT NULL PRIMARY KEY,
    token VARCHAR(255),
    created_at DATETIME(6),
    expires_at DATETIME(6),
    validated_at DATETIME(6),
    type ENUM('ACCOUNT_ACTIVATION','PASSWORD_RESET'),
    user_id INT NOT NULL,
    CONSTRAINT fk_token_user FOREIGN KEY (user_id) REFERENCES _user(id)
) ENGINE=InnoDB;

CREATE TABLE auth_session (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    last_used_at DATETIME(6),
    revoked_at DATETIME(6),
    remembered BIT NOT NULL,
    user_agent VARCHAR(512),
    ip_address VARCHAR(64),
    INDEX idx_auth_session_user_status (user_id,revoked_at,expires_at),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES _user(id)
) ENGINE=InnoDB;

CREATE TABLE refresh_token (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6),
    revoked_at DATETIME(6),
    replaced_by_token_id VARCHAR(36),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_token_session (session_id),
    CONSTRAINT fk_refresh_token_session FOREIGN KEY (session_id) REFERENCES auth_session(id)
) ENGINE=InnoDB;

CREATE TABLE price_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    price DECIMAL(18,2),
    quantity INT NOT NULL,
    total_price DECIMAL(18,2),
    date_assigned DATETIME(6),
    date_extracted DATETIME(6),
    INDEX idx_ph_item_id (item_id),
    INDEX idx_ph_date_extracted (date_extracted),
    CONSTRAINT fk_price_history_item FOREIGN KEY (item_id) REFERENCES item(id)
) ENGINE=InnoDB;

CREATE TABLE market_price_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    market_hash_name VARCHAR(500) NOT NULL,
    source VARCHAR(50) NOT NULL,
    source_version VARCHAR(128) NOT NULL,
    observed_at DATETIME(6) NOT NULL,
    observation_date DATE NOT NULL,
    price_usd DECIMAL(18,4) NOT NULL,
    usd_to_eur_rate DECIMAL(18,8) NOT NULL,
    price_eur DECIMAL(18,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_mps_market_source_version UNIQUE (market_hash_name,source,source_version),
    CONSTRAINT uk_mps_market_source_day UNIQUE (market_hash_name,source,observation_date),
    INDEX idx_mps_market_observed (market_hash_name,observed_at),
    INDEX idx_mps_observed_at (observed_at)
) ENGINE=InnoDB;

CREATE TABLE market_price_write_lock (source VARCHAR(50) NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO market_price_write_lock (source) VALUES ('CSGOTRADER_24H');

CREATE TABLE activity_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id INT,
    target_user_id INT,
    category VARCHAR(40) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    reason VARCHAR(500),
    details VARCHAR(1000),
    ip_address VARCHAR(64),
    created_at DATETIME(6) NOT NULL,
    INDEX idx_activity_target_created (target_user_id,created_at),
    INDEX idx_activity_actor_created (actor_user_id,created_at),
    INDEX idx_activity_category_created (category,created_at),
    CONSTRAINT fk_activity_actor FOREIGN KEY (actor_user_id) REFERENCES _user(id),
    CONSTRAINT fk_activity_target FOREIGN KEY (target_user_id) REFERENCES _user(id)
) ENGINE=InnoDB;

CREATE TABLE support_ticket (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    account_username VARCHAR(255) NOT NULL,
    account_email VARCHAR(255) NOT NULL,
    contact_email VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    status ENUM('NEW','IN_PROGRESS','RESOLVED') NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_support_ticket_status_created (status,created_at),
    INDEX idx_support_ticket_user_created (user_id,created_at),
    CONSTRAINT fk_support_ticket_user FOREIGN KEY (user_id) REFERENCES _user(id) ON DELETE SET NULL
) ENGINE=InnoDB;
