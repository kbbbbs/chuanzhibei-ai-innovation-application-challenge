"""Probe the running isolated instance; never serialize credentials or login bodies."""
import datetime
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:8080"
OUT = Path(__file__).parent / "runtime-probes.json"


def request(path, token=None, data=None, timeout=180):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, headers=headers,
        data=json.dumps(data).encode() if data is not None else None)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        return json.load(response)


def main():
    started = datetime.datetime.now(datetime.timezone.utc).isoformat()
    login = request("/auth/login", data={"username": "admin", "password": "123456"})
    token = login["data"]["token"]
    report = {"started_at": started, "configuration": request("/api/admin/models/status", token),
              "probes": [], "excluded": {"ocr": "No supplied OCR credential", "embedding": "No supplied embedding credential"}}
    OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"configuration": report["configuration"]}, ensure_ascii=False), flush=True)
    for role in ("generation", "planning"):
        begin = time.monotonic()
        try:
            result = request("/api/admin/models/" + role + "/probe", token, {})
        except Exception as exc:
            result = {"status": "ERROR", "error_type": type(exc).__name__}
        report["probes"].append({"role": role, "seconds": round(time.monotonic() - begin, 3), "result": result})
        OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(report["probes"][-1], ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
