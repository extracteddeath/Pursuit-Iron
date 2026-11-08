#!/usr/bin/env python3
"""Private owner setup and artifact verification for the independent Kotlin Lab.

Uses JDK keytool, GitHub CLI (owner setup only), and Android Build Tools
(APK verification only). Passwords and key bytes never enter command arguments,
source files, reports or console output. This is a development channel only.
"""

import argparse
import base64
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile


REPOSITORY = "extracteddeath/Pursuit-Iron"
PACKAGE = "io.github.extracteddeath.pursuitiron.kotlinlabstable"
LAUNCHER = "io.github.extracteddeath.pursuitiron.KotlinEnginePreviewActivity"
PIN = "KOTLIN_LAB_CERT_SHA256"
SECRETS = (
    "KOTLIN_LAB_KEYSTORE_B64", "KOTLIN_LAB_STORE_PASSWORD",
    "KOTLIN_LAB_KEY_ALIAS", "KOTLIN_LAB_KEY_PASSWORD",
)


class SigningError(Exception):
    pass


def run(command, *, input=None, env=None, purpose):
    # Do not relay subprocess errors: a failing credential utility can echo
    # its input or environment. All diagnostics here name the failed stage.
    try:
        result = subprocess.run(command, input=input, env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except OSError:
        raise SigningError(f"{purpose} could not start; check installed tools.") from None
    if result.returncode:
        raise SigningError(f"{purpose} failed; no credentials were printed.")
    return result.stdout


def fingerprint(value):
    value = value.strip().replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise SigningError("A pinned SHA-256 certificate fingerprint is required.")
    return value


def certificate(keystore, alias, password, key_password=None):
    env = dict(os.environ, PURSUIT_LAB_STORE_PASSWORD=password,
               PURSUIT_LAB_KEY_PASSWORD=key_password or password)
    check = Path(__file__).with_name("KotlinLabSigningKeyCheck.java")
    digest = run(["java", str(check), str(keystore), alias], env=env,
                 purpose="Keystore private-key and password check")
    return fingerprint(digest.decode("utf-8"))


def owner_inventory(repo):
    run(["gh", "auth", "status", "--hostname", "github.com"],
        purpose="GitHub CLI authentication")
    variables = json.loads(run(["gh", "variable", "list", "--repo", repo,
                                "--json", "name,value"],
                               purpose="Repository signing pin read"))
    secrets = json.loads(run(["gh", "secret", "list", "--repo", repo,
                              "--json", "name"],
                             purpose="Repository secret-name read"))
    pin = next((item["value"] for item in variables if item["name"] == PIN), None)
    existing = {item["name"] for item in secrets}.intersection(SECRETS)
    return fingerprint(pin) if pin is not None else None, existing


def configure(args):
    # Read only names and the public pin. Never read a remote secret value.
    pinned, existing = owner_inventory(args.repo)
    path = Path(args.create or args.keystore).expanduser().absolute()
    repo_root = Path(__file__).resolve().parents[2]
    if path.resolve().is_relative_to(repo_root):
        raise SigningError("Keep the private keystore outside the source checkout.")
    if args.create:
        if path.exists() or path.is_symlink():
            raise SigningError("Refusing to overwrite a keystore; reuse it with --keystore.")
        if pinned or existing:
            raise SigningError("Signing is already configured or partially configured. Reuse the original keystore.")
    elif not path.is_file() or path.is_symlink():
        raise SigningError("The existing keystore must be a regular local file.")
    if existing and pinned is None and not args.adopt_existing_key:
        raise SigningError("Existing signing secrets have no public pin. Reuse their key and pass --adopt-existing-key once.")

    password = getpass.getpass("Private keystore password: ")
    if not password:
        raise SigningError("A nonempty keystore password is required.")
    if args.create:
        if len(password) < 12 or password != getpass.getpass("Confirm password: "):
            raise SigningError("New passwords must match and contain at least 12 characters.")
        # Generate inside a private directory, then install without replacing
        # an existing path. A failed upload leaves this durable local key intact.
        path.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix=".pursuit-lab-", dir=path.parent) as temp:
            generated = Path(temp) / "lab.p12"
            env = dict(os.environ, PURSUIT_LAB_STORE_PASSWORD=password)
            run(["keytool", "-genkeypair", "-keystore", str(generated),
                 "-storetype", "PKCS12", "-keyalg", "RSA", "-keysize", "3072",
                 "-validity", "10000", "-alias", args.alias,
                 "-dname", "CN=Pursuit Iron Independent Development Lab",
                 "-storepass:env", "PURSUIT_LAB_STORE_PASSWORD",
                 "-keypass:env", "PURSUIT_LAB_STORE_PASSWORD"],
                env=env, purpose="Development signing key creation")
            with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as target:
                target.write(generated.read_bytes())
                target.flush()
                os.fsync(target.fileno())
        key_password = password
    else:
        key_password = getpass.getpass("Private-key password (Enter uses keystore password): ") or password

    actual = certificate(path, args.alias, password, key_password)
    if pinned is not None and actual != pinned:
        raise SigningError("This key differs from the pinned Lab certificate. Signing settings were not changed.")
    # Publish the public identity first. Partial credential writes then fail
    # closed, and a retry must use this same key rather than generating another.
    run(["gh", "variable", "set", PIN, "--repo", args.repo],
        input=actual.encode(), purpose="Public certificate pin save")
    values = (base64.b64encode(path.read_bytes()), password.encode(),
              args.alias.encode(), key_password.encode())
    for name, value in zip(SECRETS, values):
        run(["gh", "secret", "set", name, "--repo", args.repo], input=value,
            purpose=f"Private signing setting {name} save")
    print(f"Stable development signing configured for {args.repo}.")
    print(f"Public certificate SHA-256: {actual}")
    print("Keep the keystore and password backed up privately; future setup must reuse this key.")


def prepare(args):
    names = ("KEYSTORE_BASE64", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD",
             "EXPECTED_CERT_SHA256")
    if any(not os.environ.get(name, "").strip() for name in names):
        raise SigningError("Stable Kotlin Lab signing secrets or the public certificate pin are missing. See KOTLIN_LAB_SIGNING_SETUP.md.")
    expected = fingerprint(os.environ["EXPECTED_CERT_SHA256"])
    try:
        key_bytes = base64.b64decode(os.environ["KEYSTORE_BASE64"], validate=True)
    except ValueError:
        raise SigningError("The private keystore encoding is invalid.") from None
    if not key_bytes:
        raise SigningError("The private keystore is empty.")
    directory = Path(tempfile.mkdtemp(prefix="kotlin-lab-signing-", dir=args.runner_temp))
    path = directory / "lab-upload.keystore"
    try:
        with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "wb") as target:
            target.write(key_bytes)
        if certificate(path, os.environ["KEY_ALIAS"], os.environ["STORE_PASSWORD"], os.environ["KEY_PASSWORD"]) != expected:
            raise SigningError("The private key differs from the pinned Lab certificate; APK assembly is blocked.")
        if "\n" in str(path) or "\r" in str(path):
            raise SigningError("Invalid runner path.")
        with Path(args.github_env).open("a") as target:
            target.write(f"KOTLIN_LAB_SIGNING_STORE_FILE={path}\n")
    except BaseException:
        shutil.rmtree(directory)
        raise
    print("Private signing key validated against the pinned public certificate.")


def verify(args):
    expected = fingerprint(os.environ.get("EXPECTED_CERT_SHA256", ""))
    apk = Path(args.apk)
    tools = Path(args.build_tools)
    signed = run([str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)],
                 purpose="APK cryptographic signature verification").decode("utf-8")
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$",
                         signed, re.MULTILINE)
    if len(digests) != 1 or fingerprint(digests[0]) != expected:
        raise SigningError("APK signer does not match the single pinned Lab certificate.")
    badging = run([str(tools / "aapt2"), "dump", "badging", str(apk)],
                  purpose="Built APK manifest inspection").decode("utf-8")
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", badging, re.MULTILINE)
    launchers = re.findall(r"^launchable-activity: name='([^']+)'", badging, re.MULTILINE)
    if package is None or package[1] != PACKAGE or int(package[2]) != args.expected_version:
        raise SigningError("Built APK package or version does not match this stable Lab milestone.")
    if launchers != [LAUNCHER]:
        raise SigningError("Built APK does not expose the independent Kotlin Lab launcher.")
    if not re.fullmatch(r"[0-9a-f]{40}", args.source_commit):
        raise SigningError("A full source commit is required for the APK receipt.")
    data = apk.read_bytes()
    receipt = {"schemaVersion": 1, "kind": "kotlin-lab-signed-artifact",
               "applicationId": package[1], "versionCode": int(package[2]),
               "launcher": launchers[0], "certificateSha256": expected,
               "apkSha256": hashlib.sha256(data).hexdigest(), "sizeBytes": len(data),
               "sourceCommit": args.source_commit,
               "deviceAcceptance": "NOT_TESTED"}
    output = Path(args.output)
    output.write_text(json.dumps(receipt, indent=2) + "\n")
    apk.with_suffix(apk.suffix + ".sha256").write_text(f"{receipt['apkSha256']}  {apk.name}\n")
    print(f"Verified stable Lab APK: version {args.expected_version}, certificate {expected}.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    owner = commands.add_parser("configure", help="Run on the owner's trusted computer, using GitHub CLI")
    key = owner.add_mutually_exclusive_group(required=True)
    key.add_argument("--create", metavar="PRIVATE_KEYSTORE_PATH")
    key.add_argument("--keystore", metavar="EXISTING_KEYSTORE_PATH")
    owner.add_argument("--alias", default="pursuit-kotlin-lab")
    owner.add_argument("--repo", default=REPOSITORY)
    owner.add_argument("--adopt-existing-key", action="store_true")
    owner.set_defaults(action=configure)
    runner = commands.add_parser("prepare", help="Validate private runner inputs before assembly")
    runner.add_argument("--runner-temp", required=True)
    runner.add_argument("--github-env", required=True)
    runner.set_defaults(action=prepare)
    artifact = commands.add_parser("verify", help="Verify the built APK and write a public artifact receipt")
    artifact.add_argument("--apk", required=True)
    artifact.add_argument("--build-tools", required=True)
    artifact.add_argument("--expected-version", required=True, type=int)
    artifact.add_argument("--source-commit", required=True)
    artifact.add_argument("--output", required=True)
    artifact.set_defaults(action=verify)
    args = parser.parse_args()
    try:
        args.action(args)
    except (SigningError, ValueError, OSError):
        # Detailed internal exceptions are deliberately not printed if they
        # might contain a subprocess input. SigningError messages are ours.
        error = sys.exc_info()[1]
        print(f"ERROR: {error if isinstance(error, SigningError) else 'Signing setup or verification failed; credentials were not printed.'}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
