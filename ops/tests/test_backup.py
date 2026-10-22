"""No database or remote repository is used. Exercise the actual backup wrapper."""
import os
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASH = shutil.which("bash") if os.name != "nt" else r"C:\Program Files\Git\bin\bash.exe"


@unittest.skipIf(os.name == "nt", "Linux file ownership/mode and flock contract; run in Linux CI")
class BackupContractTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="investment-backup-test-")
        self.addCleanup(self.temporary.cleanup)
        self.directory = pathlib.Path(self.temporary.name)
        self.directory.chmod(0o700)
        self.defaults = self.directory / "mysql.cnf"
        self.defaults.write_text("[client]\npassword=synthetic-private-value\n")
        self.defaults.chmod(0o600)
        self.password = self.directory / "restic-password"
        self.password.write_text("synthetic-private-value\n")
        self.password.chmod(0o600)
        self.calls = self.directory / "calls"
        for name, contents in {
            "mysqldump": "#!/bin/sh\nexit 0\n",
            "restic": '#!/bin/sh\nprintf "%s\\n" "$@" > "$TEST_CALLS"\nexit "${TEST_FAILURE:-0}"\n',
        }.items():
            command = self.directory / name
            command.write_text(contents)
            command.chmod(0o700)
        self.env = dict(os.environ, PATH=f"{self.directory}:{os.environ['PATH']}",
                        MYSQL_BACKUP_DEFAULTS_FILE=str(self.defaults), RESTIC_PASSWORD_FILE=str(self.password),
                        BACKUP_STATE_DIRECTORY=str(self.directory), BACKUP_DATABASE="investment_test_fixture",
                        BACKUP_HOST="isolated-test", RESTIC_REPOSITORY="s3:https://storage.example.invalid/test",
                        TEST_CALLS=str(self.calls))

    def run_backup(self, action="backup"):
        return subprocess.run([BASH, str(ROOT / "ops/backup/backup-mysql.sh"), action], env=self.env,
                              text=True, capture_output=True, timeout=10)

    def test_success_uses_failure_aware_stream_and_never_password_in_arguments(self):
        result = self.run_backup()
        self.assertEqual(0, result.returncode, result.stderr)
        arguments = self.calls.read_text()
        self.assertIn("--stdin-from-command", arguments)
        self.assertIn("--single-transaction", arguments)
        self.assertNotIn("synthetic-private-value", arguments + result.stdout + result.stderr)
        self.assertTrue((self.directory / "last-backup-success-utc").is_file())
        self.assertFalse(any(self.directory.glob("*.sql")))
        self.assertNotIn("prune", arguments)

    def test_failure_does_not_create_or_replace_success_heartbeat(self):
        heartbeat = self.directory / "last-backup-success-utc"
        heartbeat.write_text("previous-success")
        self.env["TEST_FAILURE"] = "1"
        self.assertNotEqual(0, self.run_backup().returncode)
        self.assertEqual("previous-success", heartbeat.read_text())

    def test_insecure_permissions_or_local_repository_are_refused_before_execution(self):
        self.defaults.chmod(0o644)
        self.assertNotEqual(0, self.run_backup().returncode)
        self.assertFalse(self.calls.exists())
        self.defaults.chmod(0o600)
        self.env["RESTIC_REPOSITORY"] = str(self.directory / "local-only")
        self.assertNotEqual(0, self.run_backup().returncode)
        self.assertFalse(self.calls.exists())

    def test_check_reads_repository_without_backup_or_deletion(self):
        result = self.run_backup("check")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["check", "--read-data-subset=5%"], self.calls.read_text().splitlines())
        self.assertTrue((self.directory / "last-check-success-utc").is_file())


class TemplateSafetyTest(unittest.TestCase):
    def test_backup_shell_syntax(self):
        result = subprocess.run([BASH, "-n", str(ROOT / "ops/backup/backup-mysql.sh")], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_proxy_overwrites_forwarding_and_preserves_headers_in_api_location(self):
        proxy = (ROOT / "ops/nginx/investment.conf.example").read_text()
        self.assertIn("proxy_set_header X-Forwarded-For $remote_addr;", proxy)
        self.assertNotIn("$proxy_add_x_forwarded_for", proxy)
        self.assertEqual(2, proxy.count("include /etc/nginx/snippets/investment-security-headers.conf;"))
        self.assertIn("location ^~ /actuator/ { return 404; }", proxy)
        self.assertIn("proxy_next_upstream off;", proxy)

    def test_backend_service_is_unprivileged_and_does_not_run_migrations(self):
        service = (ROOT / "ops/systemd/investment-tracker.service").read_text()
        self.assertIn("User=investment\n", service)
        self.assertIn("SPRING_PROFILES_ACTIVE=prod", service)
        self.assertIn("KillSignal=SIGTERM", service)
        self.assertNotIn("migrations.jar", service)
        self.assertNotIn("DB_PASSWORD=", service)


if __name__ == "__main__":
    unittest.main()
