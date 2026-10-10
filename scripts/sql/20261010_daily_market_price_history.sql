-- One-time adoption of daily market history on the existing MySQL 8 schema.
-- Stop every application instance; back up and restore-check market_price_snapshot first.
-- This is NOT a Flyway baseline and must not be run on a freshly generated schema.
-- observed_at values are UTC. The latest publication wins; ties keep the highest ID.
-- MySQL DDL commits implicitly; keep the verified backup until adoption is confirmed.

ALTER TABLE market_price_snapshot ADD COLUMN observation_date DATE NULL;

START TRANSACTION;
UPDATE market_price_snapshot SET observation_date = DATE(observed_at);
CREATE TEMPORARY TABLE market_price_daily_loser_ids AS
SELECT id
FROM (
  SELECT id, ROW_NUMBER() OVER (
    PARTITION BY market_hash_name, source, observation_date
    ORDER BY observed_at DESC, id DESC
  ) AS daily_position
  FROM market_price_snapshot
) ranked
WHERE daily_position > 1;
DELETE snapshot
FROM market_price_snapshot snapshot
JOIN market_price_daily_loser_ids loser ON loser.id = snapshot.id;
DROP TEMPORARY TABLE market_price_daily_loser_ids;
COMMIT;

ALTER TABLE market_price_snapshot
  MODIFY COLUMN observation_date DATE NOT NULL,
  ADD CONSTRAINT uk_mps_market_source_day UNIQUE (market_hash_name, source, observation_date);

CREATE TABLE market_price_write_lock (
  source VARCHAR(50) NOT NULL,
  PRIMARY KEY (source)
) ENGINE=InnoDB;
INSERT INTO market_price_write_lock (source) VALUES ('CSGOTRADER_24H');
