# MySQL migrations

`V1__initial_schema.sql` creates the current application schema for new empty
databases, including indexes, foreign keys and pooled ID-generator tables.

All profiles enable Flyway and Hibernate `validate`; `migrated` is retained as
a compatibility alias. Unadopted non-empty databases deliberately refuse startup.
Never edit an already-applied migration; add the next version instead.

Existing databases must be backed up, restored and checked against a disposable
V1 reference before an explicit baseline is recorded. Automatic baseline and
destructive clean remain disabled. Follow `docs/DATABASE_MIGRATIONS.md`.
