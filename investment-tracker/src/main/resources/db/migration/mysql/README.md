# MySQL migration location (not activated yet)

Flyway is installed but disabled in the default application configuration.
There are deliberately no executable application migrations in this directory yet.
Do not add an empty `V1` or enable Flyway against the existing database as a shortcut.

Before adding `V1__initial_schema.sql`, capture and review the existing schema,
verify a private backup by restoring it into a separate database, and agree how
existing installations will be adopted. See the repository's
`docs/DATABASE_MIGRATIONS.md` for the activation checklist.

Application migrations belong here and use `V<number>__<description>.sql` names.
The preparation tests use a different, test-only location that is never packaged
in the application.
