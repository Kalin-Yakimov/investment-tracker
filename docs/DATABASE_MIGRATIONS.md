# Safe database changes

## Current status: preparation only

Flyway Core and the MySQL adapter are installed using the versions managed by
Spring Boot. Flyway is **disabled by default**. Stage 1 does not run a migration,
create a schema-history table, change existing application tables or read/export
the real database. The usual local Hibernate `update` behavior is unchanged;
the existing production profile still uses `validate`.

There is deliberately no application `V1` migration yet. An empty or guessed
baseline would give a false record of the existing database. The only SQL fixture
added in Stage 1 is test-only and runs on disposable H2 databases, not MySQL.

Do not enable Flyway on the real database until the checklist below is complete.

## 1. Create a private backup before the first real change

From the repository root, on Windows with MySQL 8 installed:

```powershell
.\scripts\Backup-LocalDatabase.ps1 -Username investment_app
```

Use your actual local MySQL username; for an installation still using `root`,
pass `-Username root`. Enter the MySQL password at the client prompt, not in the
command. The script does not read application passwords from the environment.
If script execution is blocked, use the existing Workbench **Server > Data Export**
instructions in `LOCAL_SETUP.md`; do not weaken machine-wide execution policies.

A read-only rehearsal is available:

```powershell
.\scripts\Backup-LocalDatabase.ps1 -Username investment_app -WhatIf
```

The default destination is the ignored `backups/database` directory. Each dump
gets a unique filename, so an existing backup is not overwritten. The script
uses the MySQL client's `--result-file`, avoiding PowerShell 5.1's UTF-16 output
redirection. It prints a SHA-256 checksum and file size, never SQL contents.

Important:

- Backups contain private data, including email addresses, password hashes,
  sessions/tokens and user records. They are not encrypted by this script.
  Transfer them to encrypted storage with restricted access; never upload them
  to GitLab or include them in a public/shared copy of the project.
- Use a local database account with sufficient export privileges. The script
  does not grant privileges or change passwords.
- `--single-transaction` gives a consistent dump of InnoDB table data. Avoid
  schema changes while exporting; stop the application before an eventual
  restore or migration. Non-InnoDB tables require a separate backup strategy.
- Failed exports remain marked `.partial`; they are not valid backups and may
  still contain private data. Do not restore them or continue with a migration.
- A successful exit and checksum are not proof of recoverability: a separate
  restore test is still required.

## 2. Verify recovery without touching the original

1. Stop application instances before the actual migration window.
2. Create a separate local verification database, such as
   `investment_restore_check`; never use `investment` as the restore-test target.
3. Restore the export into that verification database using Workbench Data Import.
4. Check table structure, row counts, catalogue links and required constraints.
   Use aggregate checks; do not copy passwords, tokens or user records into logs.
5. Compare the restored data to the original and retain the verified private
   backup and the matching source commit.

Do not start the full application merely to inspect a restored database:
startup initializers and scheduled jobs can modify it. Test any application
startup separately, with external initialization and jobs safely disabled.

## 3. Establish the real schema baseline

Before activating Flyway:

1. Inspect schema metadata only and compare it with the current JPA mappings.
   Hibernate `update` may have left old columns, indexes or constraints behind.
2. Prepare and review `V1__initial_schema.sql` in
   `investment-tracker/src/main/resources/db/migration/mysql` for a **new, empty**
   MySQL database. Include tables, indexes, foreign keys and identifier-generator
   state required by the current models. Do not include personal data or secrets.
3. Run `V1` on an isolated MySQL database and confirm Hibernate `validate` succeeds.
   H2 preparation tests do not establish MySQL SQL compatibility.
4. Test adoption of the verified restored database. Only if its schema matches
   the reviewed baseline, explicitly record it as baseline version 1; this must
   not execute `V1` over existing tables or reset sequence counters.
5. If the restored schema differs, review and test a non-destructive alignment
   first. Do not blindly baseline it or rewrite/drop existing tables.

`baseline-on-migrate=false` intentionally makes Flyway refuse an unadopted,
non-empty database. Do not bypass that safeguard by enabling automatic baselining.

## 4. Activate once the safeguards are verified

Activation is a later implementation step, not performed in Stage 1. It requires:

- a verified backup and restore;
- reviewed initial-schema and existing-installation adoption procedures;
- successful empty-MySQL and restored-MySQL checks;
- Flyway enabled and Hibernate switched to `validate`, so Flyway is the sole
  schema-change mechanism;
- a tested rollout and recovery procedure.

Future migrations use increasing names such as `V2__add_token_indexes.sql`.
Once applied, never edit an old migration; add a new one. Retain checksum
validation, strict migration naming, `baseline-on-migrate=false` and
`clean-disabled=true`. Never use `clean` or a blind `repair` to silence errors.

MySQL DDL is not generally rolled back as a multi-statement migration transaction.
If a migration fails, stop and inspect the actual state. Recover from the verified
backup or use a separately reviewed forward repair; switching Git branches alone
does not roll back database changes.

## References

- [Spring Boot 3.5 database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html)
- [Flyway baseline-on-migrate safety setting](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting)
- [MySQL 8 mysqldump reference](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)
