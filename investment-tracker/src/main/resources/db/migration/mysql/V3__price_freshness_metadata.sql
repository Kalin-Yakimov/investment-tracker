-- Unknown historical timestamps remain NULL. Never manufacture provider/FX dates.
ALTER TABLE market_price_snapshot
    ADD COLUMN source_published_at DATETIME(6) NULL,
    ADD COLUMN fx_rate_as_of DATETIME(6) NULL;
