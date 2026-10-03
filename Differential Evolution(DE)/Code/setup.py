"""Fetch pinned coverage dependencies and compile the generator for Java 11."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import urllib.request
import contextlib

ROOT = Path(__file__).resolve().parents[1]
ARTIFACTS = {
    "jacoco-agent.jar": "org/jacoco/org.jacoco.agent/0.8.12/org.jacoco.agent-0.8.12-runtime.jar",
    "jacoco-core.jar": "org/jacoco/org.jacoco.core/0.8.12/org.jacoco.core-0.8.12.jar",
    "asm.jar": "org/ow2/asm/asm/9.7/asm-9.7.jar",
    "asm-commons.jar": "org/ow2/asm/asm-commons/9.7/asm-commons-9.7.jar",
    "asm-tree.jar": "org/ow2/asm/asm-tree/9.7/asm-tree-9.7.jar",
    "junit.jar": "junit/junit/4.13.2/junit-4.13.2.jar",
    "hamcrest.jar": "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar",
}

@contextlib.contextmanager
def compilation_lock():
    with (ROOT / "Code" / ".build.lock").open("a+") as handle:
        if os.name == "posix":
            import fcntl
            fcntl.flock(handle, fcntl.LOCK_EX)
        yield


def build():
    with compilation_lock():
        _build()


def _build():
    jars = ROOT / "Configuration" / "lib"
    jars.mkdir(parents=True, exist_ok=True)
    manifest = jars / "sha256.json"
    expected_hashes = json.loads(manifest.read_text()) if manifest.exists() else {}
    hashes = {}
    for name, artifact in ARTIFACTS.items():
        path = jars / name
        if not path.is_file():
            url = "https://repo.maven.apache.org/maven2/" + artifact
            print("Downloading", name, flush=True)
            with urllib.request.urlopen(url, timeout=60) as response:
                data = response.read()
            # Maven's checksum detects incomplete or corrupted downloads.
            with urllib.request.urlopen(url + ".sha1", timeout=30) as response:
                expected = response.read().decode().strip().split()[0]
            if hashlib.sha1(data).hexdigest() != expected:
                raise RuntimeError("Checksum mismatch: " + name)
            path.with_suffix(".tmp").write_bytes(data)
            path.with_suffix(".tmp").replace(path)
        hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
        if name in expected_hashes and hashes[name] != expected_hashes[name]:
            raise RuntimeError("SHA256 mismatch for bundled dependency: " + name)
    manifest.write_text(json.dumps(hashes, indent=2), encoding="utf-8")
    classes = ROOT / "Code" / "build"
    classes.mkdir(exist_ok=True)
    sources = sorted((ROOT / "Code").glob("*.java"))
    fingerprint = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}
    fingerprint.update(hashes)
    stamp = classes / "build.json"
    if stamp.exists() and json.loads(stamp.read_text()) == fingerprint and (classes / "SearchMain.class").exists():
        print("Generator ready:", ROOT)
        return
    compiler = shutil.which("javac")
    if not compiler:
        raise SystemExit("javac not found. Install a JDK; Defects4J 3.x requires Java 11.")
    subprocess.run([compiler, "--release", "11", "-cp", str(jars / "*"), "-d", str(classes),
                    *map(str, sources)], check=True)
    stamp.write_text(json.dumps(fingerprint, indent=2), encoding="utf-8")
    print("Generator ready:", ROOT)

if __name__ == "__main__":
    build()
