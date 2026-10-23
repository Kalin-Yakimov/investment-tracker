#!/usr/bin/env bash
# Linux operations helper. Never source this file or run with bash -x.
set -euo pipefail
umask 077
action=${1:-backup}
[[ $action == backup || $action == check ]] || { echo 'Use backup or check' >&2; exit 2; }
: "${BACKUP_DATABASE:?Set BACKUP_DATABASE}"
: "${BACKUP_HOST:?Set a stable, unique BACKUP_HOST}"
: "${MYSQL_BACKUP_DEFAULTS_FILE:?Set MYSQL_BACKUP_DEFAULTS_FILE}"
: "${RESTIC_REPOSITORY:?Set an off-host RESTIC_REPOSITORY}"
: "${RESTIC_PASSWORD_FILE:?Set RESTIC_PASSWORD_FILE}"
: "${BACKUP_STATE_DIRECTORY:?Set BACKUP_STATE_DIRECTORY}"
[[ $BACKUP_DATABASE =~ ^[A-Za-z0-9_]+$ && $BACKUP_HOST =~ ^[A-Za-z0-9_.-]+$ ]] || exit 2
# Local disks are not disaster recovery. Repository URLs may not contain passwords.
case "$RESTIC_REPOSITORY" in
  s3:https://*) [[ $RESTIC_REPOSITORY != *@* ]] || exit 2 ;;
  sftp:*) ;;
  rest:https://*) [[ $RESTIC_REPOSITORY != *@* ]] || exit 2 ;;
  *) echo 'An encrypted off-host HTTPS/SFTP repository is required' >&2; exit 2 ;;
esac
for path in "$MYSQL_BACKUP_DEFAULTS_FILE" "$RESTIC_PASSWORD_FILE"; do
  [[ -f $path && ! -L $path && $(stat -c %a -- "$path") =~ ^[46]00$ && -O $path ]] || {
    echo 'Credential files must be owned by the backup user, non-symlink, mode 400/600' >&2; exit 2;
  }
done
[[ -d $BACKUP_STATE_DIRECTORY && ! -L $BACKUP_STATE_DIRECTORY && -O $BACKUP_STATE_DIRECTORY
   && $(stat -c %a -- "$BACKUP_STATE_DIRECTORY") == 700 ]] || exit 2
command -v restic >/dev/null
command -v mysqldump >/dev/null
exec 9>"$BACKUP_STATE_DIRECTORY/operation.lock"
flock -n 9 || { echo 'A backup/check is already running' >&2; exit 1; }
if [[ $action == check ]]; then
  restic check --read-data-subset=5%
else
  # --stdin-from-command refuses a snapshot if mysqldump exits unsuccessfully.
  # No plaintext dump, credentials in CLI arguments, init, forget, prune or DB writes.
  restic backup --stdin-from-command --stdin-filename "$BACKUP_DATABASE.sql" \
    --host "$BACKUP_HOST" --tag investment-tracker -- \
    mysqldump --defaults-extra-file="$MYSQL_BACKUP_DEFAULTS_FILE" \
      --single-transaction --quick --hex-blob --no-tablespaces --set-gtid-purged=OFF \
      --column-statistics=0 --default-character-set=utf8mb4 --skip-lock-tables "$BACKUP_DATABASE"
fi
stamp=$(mktemp "$BACKUP_STATE_DIRECTORY/.${action}-success.XXXXXXXX")
trap '[[ ! -f ${stamp:-} ]] || rm -- "$stamp"' EXIT
date -u +%FT%TZ >"$stamp"
mv -- "$stamp" "$BACKUP_STATE_DIRECTORY/last-${action}-success-utc"
echo "Database $action completed"
