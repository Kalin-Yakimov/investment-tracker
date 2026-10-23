-- A single atomic InnoDB ALTER: refuse existing duplicates; never delete user records.
-- Portfolio rows produce NULL and are intentionally NOT unique per user/market.
ALTER TABLE item
    ADD COLUMN tracked_market_hash_name VARCHAR(255)
        GENERATED ALWAYS AS (CASE WHEN steam_found_check = TRUE THEN market_hash_name ELSE NULL END) VIRTUAL,
    ADD CONSTRAINT uk_item_tracked_owner_market UNIQUE (user_id, tracked_market_hash_name);
