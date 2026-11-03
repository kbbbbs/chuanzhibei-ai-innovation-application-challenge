"""Run this task's backend checks and retain redacted evidence."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone


HERE = Path(__file__).resolve().parent
ROOT = next(parent for parent in HERE.parents if (parent / "AGENTS.md").exists())
BACKEND = ROOT / "ai-learning-backend"
spec = importlib.util.spec_from_file_location(
    "agent_exporter", ROOT / "scripts/competition/export_agent_development_records.py"
)
sys.dont_write_bytecode = True
exporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(exporter)
redactor = exporter.Redactor(exporter.collect_secrets(os.environ, ()))


def run(args: list[str], log_name: str) -> dict:
    started = datetime.now(timezone.utc).isoformat()
    clock = time.monotonic()
    with (HERE / log_name).open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            args,
            cwd=BACKEND,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        for line in process.stdout:
            log.write(redactor.text(line))
            log.flush()
        code = process.wait()
    return {
        "command": args[1:],
        "startedUtc": started,
        "finishedUtc": datetime.now(timezone.utc).isoformat(),
        "elapsedSeconds": round(time.monotonic() - clock, 3),
        "exitCode": code,
        "log": log_name,
    }


if __name__ == "__main__":
    mvn = shutil.which("mvn.cmd") or shutil.which("mvn")
    if not mvn:
        raise SystemExit("Maven unavailable")
    report = {
        "scope": "Fresh ordinary Maven regression and packaging; no live gates enabled.",
        "head": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
        ).strip(),
        "realModelRequests": 0,
        "test": run([mvn, "test"], "mvn-test.log"),
    }
    suites = []
    for path in sorted((BACKEND / "target/surefire-reports").glob("TEST-*.xml")):
        suite = ET.parse(path).getroot()
        data = {"name": suite.attrib["name"]}
        data.update(
            {key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        )
        data["passed"] = data["tests"] - data["failures"] - data["errors"] - data["skipped"]
        data["skipReasons"] = [
            {
                "test": case.get("name"),
                "reason": case.find("skipped").get("message") or (case.find("skipped").text or "").splitlines()[0],
            }
            for case in suite.findall("testcase")
            if case.find("skipped") is not None
        ]
        suites.append(data)
    report["suites"] = suites
    report["totals"] = {
        key: sum(suite[key] for suite in suites)
        for key in ("tests", "passed", "failures", "errors", "skipped")
    }
    (HERE / "results.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(json.dumps({"test": report["test"], "totals": report["totals"]}), flush=True)
    if report["test"]["exitCode"] == 0:
        report["package"] = run([mvn, "-DskipTests", "package"], "mvn-package.log")
        if report["package"]["exitCode"] == 0:
            report["jars"] = [
                {
                    "path": str(path.relative_to(ROOT)),
                    "bytes": path.stat().st_size,
                    "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                }
                for path in (BACKEND / "target").glob("*.jar")
            ]
    (HERE / "results.json").write_text(
        json.dumps(report, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    print(json.dumps({key: value for key, value in report.items() if key != "suites"}))
    raise SystemExit(
        report["test"]["exitCode"] or report.get("package", {}).get("exitCode", 0)
    )
