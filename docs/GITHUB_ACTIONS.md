# GitHub Actions verification

Prepared and approved for publication: 2026-10-12.
Workflow: `.github/workflows/verify.yml`.
Target repository requested by the owner:
`https://github.com/Kalin-Yakimov/investment-tracker.git`.

## What the workflow does

Independent frontend and backend jobs run on GitHub-hosted Ubuntu 24.04 runners
for pushes, pull requests and manual dispatch. Frontend checks use Node.js
24.20.0, locked `npm ci`, ESLint, Vitest with JUnit output, a Vite build and the
existing production HTTP smoke check. Backend checks use Temurin Java 21 and
the project's Gradle 8.14.6 wrapper for `test mysqlTest bootJar`. The backend job
includes an isolated MySQL 8.4.11 LTS service with synthetic test-only credentials.
It tests real migrations, safe adoption and InnoDB auth concurrency, not just H2.
See `DATABASE_MIGRATIONS.md` for local execution and data-safety boundaries.

The same application commands passed in GitLab pipeline
[2938937025](https://gitlab.com/yakimovs/Investment-tracker/-/pipelines/2938937025)
for commit `b1c8387`. This is not proof that the new GitHub-hosted workflow has
already run. Check the actual GitHub Actions run after publication; this local
validation checkpoint does not label an unexecuted workflow successful.

The workflow passed local actionlint 1.7.12 validation. Its official portable
archive was checksum-verified before use. A separate secret scan of the new
workflow directory reported no findings. No application source was changed.

## Safety boundaries

- The workflow has read-only repository permissions, no deployment steps and
  no request for personal access tokens, mail/database credentials or JWT keys.
- Official GitHub actions are pinned to full commit SHAs verified against their
  originating repositories. Checkout does not persist authentication credentials.
- Untrusted pull requests use `pull_request`, not `pull_request_target`.
- Package/tool dependency caches speed up repeat runs. They do not contain a
  database backup, local `.env`, mail configuration or production credentials.
- Only synthetic test/coverage reports are uploaded as artifacts for seven days;
  application binaries, backups, local settings and the workspace are not uploaded.
- Each job has a 20-minute timeout. New runs cancel older runs for the same ref
  or pull request. Hosting usage and quotas depend on the GitHub account's plan;
  no payment, quota or repository-visibility setting is changed by this file.

No production MySQL or SMTP service is started. The backend tests use isolated
H2 fixtures and mocked outbound services. The frontend smoke server is temporary,
loopback-only and closes after checking built routes/assets.

## Starting and interpreting checks

After publication, open the repository's **Actions** tab and select **Verify
application**. A push to a branch containing this workflow triggers verification
automatically. **Run workflow** becomes available once the workflow also exists
on the default branch; adding it to the feature branch alone does not authorize
merging that branch into `main`.

Both jobs must be successful. A green run only confirms the defined automated
checks; manual browser acceptance, external-provider behavior and production
deployment remain separate concerns.

## History transfer decision

The pre-transfer local history scan found literal database/mail credentials in
older `application.properties` versions and a historical JWT signing key. The
latest commit scan and a complete tracked-file export of the current committed
snapshot reported no findings under the same rules. The export contains no Git
metadata, ignored local `.env` or untracked private files. Findings differ from
the locally configured credentials; that comparison does not prove revocation.
Scanner reports are redacted and retained only under ignored `backups/`.

On 2026-10-12 the owner confirmed that the historical credentials were changed
or revoked and explicitly chose to copy the existing GitLab history unchanged
to the private GitHub repository. No history filtering, force push, branch merge
or repository-visibility change is part of this transfer. Old commits retain
their original IDs and content. Historical credential values are not reproduced
in this document.

The new workflow/documentation commit is added normally to
`security-reliability-refactor` and published to both sites. `main` remains at its
existing GitLab revision; copying branches does not merge the refactor branch.
Ignored `.env`, private database exports, local reports and backups are not
transferred. Before any future public exposure, reassess the preserved history
and any credentials that may have been reused elsewhere.

## Two hosting destinations

The local repository keeps `origin` for GitLab and adds `github` for GitHub.
Existing GitLab upstream tracking remains unchanged. A normal `git push` still
targets GitLab; it does not automatically update both services. Publish an
approved current branch to each explicitly:

```powershell
git push origin HEAD
git push github HEAD
```

These commands do not merge branches or deploy the application. Do not use
`--mirror`, `--force`, or a second `git init` for routine updates.

## Official references

- [GitHub workflow syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax)
- [GitHub Actions secure use](https://docs.github.com/en/actions/reference/security/secure-use)
- [GitHub Actions artifacts](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)
