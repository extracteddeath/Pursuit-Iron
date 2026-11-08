#!/usr/bin/env python3
"""Exercise real temporary JDK keys and the signing boundaries without GitHub writes."""

import argparse
import base64
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import stat
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SOURCE = Path(__file__).resolve().parents[1] / "tools/kotlin_lab_signing.py"
spec = importlib.util.spec_from_file_location("kotlin_lab_signing", SOURCE)
signing = importlib.util.module_from_spec(spec)
spec.loader.exec_module(signing)


class SigningTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.keys = tempfile.TemporaryDirectory(prefix="lab-signing-tests-")
        cls.keystore = Path(cls.keys.name) / "temporary-test-only.p12"
        cls.password = "temporary-test-password-only"
        cls.alias = "temporary-test-only"
        signing.run(["keytool", "-genkeypair", "-keystore", str(cls.keystore),
                     "-storetype", "PKCS12", "-keyalg", "RSA", "-keysize", "2048",
                     "-validity", "2", "-alias", cls.alias, "-dname", "CN=Temporary test only",
                     "-storepass:env", "PURSUIT_TEST_PASSWORD"],
                    env=dict(os.environ, PURSUIT_TEST_PASSWORD=cls.password),
                    purpose="Temporary test key creation")
        cls.pin = signing.certificate(cls.keystore, cls.alias, cls.password)

    @classmethod
    def tearDownClass(cls):
        cls.keys.cleanup()

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="lab-signing-boundary-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.env_file = self.root / "github-env"
        self.prepare_args = argparse.Namespace(runner_temp=str(self.root), github_env=str(self.env_file))
        self.env = {"KEYSTORE_BASE64": base64.b64encode(self.keystore.read_bytes()).decode(),
                    "STORE_PASSWORD": self.password, "KEY_PASSWORD": self.password,
                    "KEY_ALIAS": self.alias, "EXPECTED_CERT_SHA256": self.pin}

    def prepare(self, changes=None):
        env = dict(self.env, **(changes or {}))
        with patch.dict(os.environ, env, clear=True), contextlib.redirect_stdout(io.StringIO()):
            signing.prepare(self.prepare_args)

    def test_real_key_preparation_pins_certificate_and_restricts_private_file(self):
        self.prepare()
        line = self.env_file.read_text().strip()
        self.assertTrue(line.startswith("KOTLIN_LAB_SIGNING_STORE_FILE="))
        path = Path(line.split("=", 1)[1])
        self.assertEqual(self.keystore.read_bytes(), path.read_bytes())
        self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
        self.assertEqual(stat.S_IMODE(path.parent.stat().st_mode), 0o700)
        self.assertNotIn(self.password, line)

    def test_missing_inputs_and_invalid_encoding_leave_no_key_or_environment(self):
        for changes in ({"KEY_PASSWORD": ""}, {"EXPECTED_CERT_SHA256": ""},
                        {"EXPECTED_CERT_SHA256": "unconfigured"}, {"KEYSTORE_BASE64": "not-base64!"}):
            with self.subTest(changes=list(changes)), self.assertRaises(signing.SigningError):
                self.prepare(changes)
            self.assertEqual(list(self.root.iterdir()), [])

    def test_wrong_pin_alias_and_both_passwords_are_rejected_and_cleaned(self):
        for changes in ({"EXPECTED_CERT_SHA256": "0" * 64}, {"KEY_ALIAS": "missing"},
                        {"STORE_PASSWORD": "wrong-password"}, {"KEY_PASSWORD": "wrong-password"}):
            with self.subTest(changes=list(changes)), self.assertRaises(signing.SigningError):
                self.prepare(changes)
            self.assertEqual(list(self.root.iterdir()), [])

    def owner_args(self, **changes):
        return argparse.Namespace(repo=signing.REPOSITORY, create=None,
                                  keystore=str(self.keystore), alias=self.alias,
                                  adopt_existing_key=False, **changes)

    def test_owner_setup_uploads_same_key_only_via_stdin_and_public_pin_first(self):
        writes = []
        original_run = signing.run

        def local_only(command, **kwargs):
            if command[0] == "gh":
                writes.append((command, kwargs.get("input")))
                return b""
            return original_run(command, **kwargs)

        with patch.object(signing, "owner_inventory", return_value=(self.pin, set(signing.SECRETS))), \
             patch.object(signing.getpass, "getpass", side_effect=[self.password, ""]), \
             patch.object(signing, "run", side_effect=local_only), \
             contextlib.redirect_stdout(io.StringIO()) as output:
            signing.configure(self.owner_args())
        self.assertEqual(writes[0][0][1:4], ["variable", "set", signing.PIN])
        self.assertEqual([row[0][3] for row in writes[1:]], list(signing.SECRETS))
        self.assertEqual(base64.b64decode(writes[1][1]), self.keystore.read_bytes())
        self.assertNotIn(self.password, str([row[0] for row in writes]))
        self.assertNotIn(self.password, output.getvalue())

    def test_owner_setup_wont_rotate_a_pinned_key(self):
        with patch.object(signing, "owner_inventory", return_value=("0" * 64, set())), \
             patch.object(signing.getpass, "getpass", side_effect=[self.password, ""]), \
             patch.object(signing, "run", wraps=signing.run) as calls:
            with self.assertRaisesRegex(signing.SigningError, "differs"):
                signing.configure(self.owner_args())
        self.assertFalse(any(c.args[0][0] == "gh" for c in calls.call_args_list))

    def test_owner_setup_wont_generate_over_existing_or_partial_configuration(self):
        for pinned, existing in ((self.pin, set()), (None, {signing.SECRETS[0]})):
            args = self.owner_args()
            args.create = str(self.root / "must-not-exist.p12")
            args.keystore = None
            with patch.object(signing, "owner_inventory", return_value=(pinned, existing)), \
                 patch.object(signing.getpass, "getpass") as prompt:
                with self.assertRaises(signing.SigningError):
                    signing.configure(args)
                prompt.assert_not_called()
                self.assertFalse(Path(args.create).exists())

    def test_owner_setup_requires_explicit_adoption_of_unpinned_existing_secrets(self):
        with patch.object(signing, "owner_inventory", return_value=(None, {signing.SECRETS[0]})), \
             patch.object(signing.getpass, "getpass") as prompt:
            with self.assertRaisesRegex(signing.SigningError, "adopt-existing-key"):
                signing.configure(self.owner_args())
            prompt.assert_not_called()

    def test_secret_upload_failure_preserves_existing_local_key_for_retry(self):
        original = self.keystore.read_bytes()
        original_run = signing.run
        writes = []

        def local_only(command, **kwargs):
            if command[0] == "gh":
                writes.append(command)
                if command[1] == "secret":
                    raise signing.SigningError("Test upload failure")
                return b""
            return original_run(command, **kwargs)

        with patch.object(signing, "owner_inventory", return_value=(None, set())), \
             patch.object(signing.getpass, "getpass", side_effect=[self.password, ""]), \
             patch.object(signing, "run", side_effect=local_only):
            with self.assertRaisesRegex(signing.SigningError, "Test upload failure"):
                signing.configure(self.owner_args())
        self.assertEqual(self.keystore.read_bytes(), original)
        self.assertEqual(writes[0][1:4], ["variable", "set", signing.PIN])

    def test_subprocess_failures_do_not_relay_sensitive_output(self):
        result = subprocess.CompletedProcess([], 1, stdout=b"sensitive-input", stderr=b"sensitive-input")
        with patch.object(signing.subprocess, "run", return_value=result):
            with self.assertRaises(signing.SigningError) as error:
                signing.run(["tool"], purpose="Test credential utility")
        self.assertNotIn("sensitive-input", str(error.exception))

    def test_new_private_key_survives_failed_upload_and_cannot_be_overwritten(self):
        args = self.owner_args()
        args.create = str(self.root / "private-new-key.p12")
        args.keystore = None
        original_run = signing.run
        saved_pins = []

        def local_only(command, **kwargs):
            if command[0] == "gh":
                if command[1] == "variable":
                    saved_pins.append(kwargs["input"].decode())
                    return b""
                raise signing.SigningError("Test upload interrupted")
            return original_run(command, **kwargs)

        with patch.object(signing, "owner_inventory", return_value=(None, set())), \
             patch.object(signing.getpass, "getpass", side_effect=[self.password, self.password]), \
             patch.object(signing, "run", side_effect=local_only):
            with self.assertRaisesRegex(signing.SigningError, "interrupted"):
                signing.configure(args)
        created = Path(args.create)
        self.assertEqual(stat.S_IMODE(created.stat().st_mode), 0o600)
        self.assertEqual(saved_pins, [signing.certificate(created, self.alias, self.password)])
        original = created.read_bytes()
        with patch.object(signing, "owner_inventory", return_value=(None, set())), \
             patch.object(signing.getpass, "getpass") as prompt:
            with self.assertRaisesRegex(signing.SigningError, "overwrite"):
                signing.configure(args)
            prompt.assert_not_called()
        self.assertEqual(original, created.read_bytes())

    def test_jks_distinct_key_password_is_checked_with_java_signing_api(self):
        path = self.root / "temporary-jks.jks"
        key_password = "temporary-distinct-key-password"
        signing.run(["keytool", "-genkeypair", "-keystore", str(path),
                     "-storetype", "JKS", "-keyalg", "RSA", "-keysize", "2048",
                     "-validity", "2", "-alias", self.alias, "-dname", "CN=Temporary test only",
                     "-storepass:env", "PURSUIT_TEST_STORE_PASSWORD",
                     "-keypass:env", "PURSUIT_TEST_KEY_PASSWORD"],
                    env=dict(os.environ, PURSUIT_TEST_STORE_PASSWORD=self.password,
                             PURSUIT_TEST_KEY_PASSWORD=key_password), purpose="Temporary JKS key creation")
        self.assertRegex(signing.certificate(path, self.alias, self.password, key_password), "^[0-9a-f]{64}$")
        with self.assertRaises(signing.SigningError):
            signing.certificate(path, self.alias, self.password, "wrong-key-password")

    def verify_args(self):
        apk = self.root / "test.apk"
        apk.write_bytes(b"unit-test fixture; not an APK")
        return argparse.Namespace(apk=str(apk), build_tools="unused-test-tools",
                                  expected_version=12, source_commit="a" * 40,
                                  output=str(self.root / "receipt.json"))

    def verify_fixture(self, args, *, digest=None, badging=None, extra_signer=""):
        signed = f"Signer #1 certificate SHA-256 digest: {digest or self.pin}\n{extra_signer}"
        manifest = badging if badging is not None else (
            f"package: name='{signing.PACKAGE}' versionCode='12' versionName='test'\n"
            f"launchable-activity: name='{signing.LAUNCHER}' label='Lab Stable' icon=''\n")
        with patch.dict(os.environ, {"EXPECTED_CERT_SHA256": self.pin}), \
             patch.object(signing, "run", side_effect=[signed.encode(), manifest.encode()]), \
             contextlib.redirect_stdout(io.StringIO()):
            signing.verify(args)

    def test_verified_receipt_binds_apk_bytes_identity_certificate_and_source(self):
        args = self.verify_args()
        self.verify_fixture(args)
        receipt = json.loads(Path(args.output).read_text())
        self.assertEqual(receipt["applicationId"], signing.PACKAGE)
        self.assertEqual(receipt["certificateSha256"], self.pin)
        self.assertEqual(receipt["versionCode"], 12)
        self.assertEqual(receipt["sourceCommit"], "a" * 40)
        self.assertEqual(receipt["apkSha256"], hashlib.sha256(Path(args.apk).read_bytes()).hexdigest())
        self.assertEqual(receipt["deviceAcceptance"], "NOT_TESTED")
        self.assertEqual(Path(args.apk + ".sha256").read_text(), f"{receipt['apkSha256']}  test.apk\n")

    def test_artifact_certificate_mismatch_and_extra_signers_cannot_get_receipts(self):
        for changes in ({"digest": "0" * 64},
                        {"extra_signer": f"Signer #2 certificate SHA-256 digest: {self.pin}\n"}):
            args = self.verify_args()
            with self.subTest(changes=list(changes)), self.assertRaises(signing.SigningError):
                self.verify_fixture(args, **changes)
            self.assertFalse(Path(args.output).exists())
            self.assertFalse(Path(args.apk + ".sha256").exists())

    def test_artifact_wrong_package_version_or_launcher_cannot_get_receipts(self):
        valid = (f"package: name='{signing.PACKAGE}' versionCode='12' versionName='test'\n"
                 f"launchable-activity: name='{signing.LAUNCHER}'\n")
        for manifest in (valid.replace(signing.PACKAGE, "io.other.app"),
                         valid.replace("versionCode='12'", "versionCode='1'"),
                         valid.replace(signing.LAUNCHER, "io.other.MainActivity"), ""):
            args = self.verify_args()
            with self.subTest(manifest=manifest), self.assertRaises(signing.SigningError):
                self.verify_fixture(args, badging=manifest)
            self.assertFalse(Path(args.output).exists())

    def test_corrupt_apk_signature_stops_before_manifest_or_receipt(self):
        args = self.verify_args()
        with patch.dict(os.environ, {"EXPECTED_CERT_SHA256": self.pin}), \
             patch.object(signing, "run", side_effect=signing.SigningError("Signature invalid")) as calls:
            with self.assertRaises(signing.SigningError):
                signing.verify(args)
        self.assertEqual(calls.call_count, 1)
        self.assertFalse(Path(args.output).exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
