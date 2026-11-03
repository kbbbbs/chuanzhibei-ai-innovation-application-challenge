"""Real temporary 4.1f checks. Secrets remain in memory/process environment only."""
from __future__ import annotations

import base64
import hashlib
import json
import math
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import tempfile
import time

import requests
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[4]
PRIVATE = ROOT / ".env.local"
BASE = "http://127.0.0.1:8081"
RESULT = {"purpose": "Temporary real interface checks; no semantic quality acceptance",
          "model": "opencode/deepseek-v4.1-flash", "events": {}}
SECRET_VALUES = []


def safe(value):
    if isinstance(value, str):
        for secret in SECRET_VALUES:
            if len(secret) >= 4:
                value = value.replace(secret, "[REDACTED]")
        return re.sub(r"(?i)Bearer\s+\S+", "Bearer [REDACTED]", value)
    if isinstance(value, dict):
        return {k: safe(v) for k, v in value.items()
                if not re.search(r"token|password|api.?key|reasoning", k, re.I)}
    if isinstance(value, list):
        return [safe(v) for v in value]
    return value


def save(name, value):
    RESULT["events"][name] = safe(value)
    (OUT / "results.json").write_text(
        json.dumps(RESULT, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(name + ": " + json.dumps(safe(value), ensure_ascii=False)[:1500], flush=True)


def listening(port):
    with socket.socket() as connection:
        connection.settimeout(2)
        return connection.connect_ex(("127.0.0.1", port)) == 0


def app(session, method, path, **kwargs):
    response = session.request(method, BASE + path, timeout=110, **kwargs)
    try:
        data = response.json()
    except ValueError:
        data = {"non_json": True}
    return {"http": response.status_code, "body": data}


def direct(url, body, key):
    started = time.monotonic()
    response = requests.post(url, json=body,
                             headers={"Authorization": "Bearer " + key},
                             timeout=95, allow_redirects=False)
    result = {"http": response.status_code,
              "contentType": response.headers.get("content-type"),
              "elapsedSeconds": round(time.monotonic() - started, 2)}
    try:
        data = response.json()
    except ValueError:
        return result | {"json": False}, None
    return result | {"json": True, "topLevelKeys": list(data) if isinstance(data, dict) else []}, data


def uploaded(session, kb, path):
    with path.open("rb") as source:
        response = app(session, "POST", f"/api/rag/knowledge-bases/{kb}/documents",
                       files={"file": (path.name, source)})
    save(path.suffix[1:] + "_upload", response)
    data = response["body"].get("data", {})
    job = data.get("jobId")
    document = data.get("documentId")
    if not job:
        return None
    seen = []
    deadline = time.monotonic() + 150
    while time.monotonic() < deadline:
        status = app(session, "GET", f"/api/rag/jobs/{job}")
        stage = status["body"].get("data", {}).get("stage")
        if stage not in seen:
            seen.append(stage)
        if stage in {"READY", "PARTIAL_READY", "FAILED", "CANCELLED", "COMPLETED"}:
            break
        time.sleep(0.5)
    save(path.suffix[1:] + "_job", {"observedStages": seen, "terminal": status})
    for endpoint in ["", "/pages", "/chunks"]:
        save(path.suffix[1:] + ("_document" if not endpoint else endpoint.replace("/", "_")),
             app(session, "GET", "/api/rag/documents/" + document + endpoint))
    return document


def main():
    before = hashlib.sha256(PRIVATE.read_bytes()).digest()
    env = dict(os.environ)
    for line in PRIVATE.read_text(encoding="utf-8-sig").splitlines():
        match = re.match(r"^\s*(?:export\s+)?([A-Z][A-Z0-9_]*)=(.*)$", line)
        if match:
            env[match[1]] = match[2].strip().strip("'\"")
    SECRET_VALUES.extend(v for k, v in env.items()
                         if re.search(r"KEY|TOKEN|SECRET|PASSWORD", k) and v)
    model = env.get("AI_GENERATION_MODEL") or env.get("AI_OPENAI_MODEL")
    endpoint = env.get("AI_GENERATION_BASE_URL") or env.get("AI_OPENAI_BASE_URL") or env.get("BASE_URL")
    key = env.get("AI_GENERATION_API_KEY") or env.get("AI_OPENAI_API_KEY") or env.get("API_KEY")
    assert model == RESULT["model"], "Expected the existing complete 4.1f model ID"
    assert endpoint and endpoint.startswith("https://") and key, "Missing existing HTTPS generation configuration"
    assert not listening(8081), "8081 must be unused"
    baseline = {str(p): listening(p) for p in [8080, 5173]}
    digits = "".join(str(secrets.randbelow(10)) for _ in range(10))
    truth = ["OCR TEST 2026", "RANDOM " + digits]
    image = Image.new("RGB", (1100, 450), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 64)
    for index, text in enumerate(truth):
        draw.text((50, 80 + index * 140), text, font=font, fill="black")
    image.save(OUT / "synthetic-ocr.png")
    image.save(OUT / "synthetic-scan.pdf", "PDF", resolution=100)
    (OUT / "synthetic-keyword.txt").write_text(
        "SYNTHETIC INTERFACE TEST 2026\n"
        "The unique marker ZEBRA2026 names a synthetic classroom exercise.\n"
        "Gradient descent updates parameters along the negative gradient.\n", encoding="utf-8")
    save("sample", {"truth": truth, "privateOriginalsRead": False, "pdfImageOnly": True})
    for prefix in ["OCR", "RAG_EMBEDDING"]:
        env[prefix + "_MODEL"] = model
        env[prefix + "_BASE_URL"] = endpoint
        env[prefix + "_API_KEY"] = key
        env[prefix + "_TIMEOUT_SECONDS"] = "60"
    env["RAG_EMBEDDING_ENABLED"] = "true"
    env["JWT_SECRET"] = secrets.token_hex(40)
    SECRET_VALUES.append(env["JWT_SECRET"])
    jar = ROOT / "ai-learning-backend/target/Final_exam_question_bank-0.0.1-SNAPSHOT.jar"
    save("isolation", {"port": 8081, "database": "independent H2 memory",
                       "baselineListeners": baseline, "jarSha256": hashlib.sha256(jar.read_bytes()).hexdigest()})
    process = None
    try:
        with tempfile.TemporaryDirectory(prefix="temp-4_1f-", ignore_cleanup_errors=True) as working:
            args = [r"D:\language\Java\bin\java.exe", "-jar", str(jar),
                    "--spring.profiles.active=contest-demo", "--server.address=127.0.0.1",
                    "--server.port=8081",
                    "--runtime.models.ocr.max-attempts=1",
                    "--spring.datasource.url=jdbc:h2:mem:temp4_1f;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                    "--rag.storage-path=" + str(Path(working) / "documents")]
            process = subprocess.Popen(args, cwd=working, env=env, stdout=subprocess.DEVNULL,
                                       stderr=subprocess.DEVNULL,
                                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            save("started", {"pid": process.pid, "secretsInArguments": False})
            deadline = time.monotonic() + 75
            while not listening(8081):
                assert process.poll() is None, "Isolated server exited"
                assert time.monotonic() < deadline, "Isolated startup timed out"
                time.sleep(0.5)
            session = requests.Session()
            login = app(session, "POST", "/auth/login",
                        json={"username": "admin", "password": "123456"})
            save("login", {"http": login["http"], "code": login["body"].get("code")})
            token = (login["body"].get("data") or {}).get("token")
            assert token, "Isolated demo login failed"
            SECRET_VALUES.append(token)
            session.headers["Authorization"] = "Bearer " + token
            save("status", app(session, "GET", "/api/admin/models/status"))
            for role in ["ocr", "embedding"]:
                save(role + "_probe", app(session, "POST", f"/api/admin/models/{role}/probe"))
            provider_base = endpoint.rstrip("/")
            api = provider_base if provider_base.endswith("/v1") else provider_base + "/v1"
            ocr_body = {"model": model, "messages": [{"role": "user", "content": [
                {"type": "image_url", "image_url": {"url": "data:image/png;base64," +
                    base64.b64encode((OUT / "synthetic-ocr.png").read_bytes()).decode()}},
                {"type": "text", "text": "<image>\n<|grounding|>OCR this image."}]}],
                "stream": False, "temperature": 0, "max_tokens": 4096}
            assert ocr_body["messages"][0]["content"][0]["image_url"]["url"].startswith(
                "data:" + "image/png;base64,")
            try:
                summary, data = direct(api + "/chat/completions", ocr_body, key)
                choice = (data or {}).get("choices", [{}])[0]
                text = choice.get("message", {}).get("content")
                summary.update({"finishReason": choice.get("finish_reason"),
                                "contentExcerpt": text[:650] if isinstance(text, str) else None,
                                "truthLinesFound": [line in (text or "") for line in truth]})
                save("direct_ocr", summary)
            except requests.RequestException as error:
                save("direct_ocr", {"transportError": type(error).__name__})
            try:
                summary, data = direct(api + "/embeddings",
                                       {"model": model,
                                        "input": ["synthetic gradient descent exercise",
                                                  "synthetic photosynthesis exercise"],
                                        "encoding_format": "float"}, key)
                vectors = (data or {}).get("data")
                summary["dataIsArray"] = isinstance(vectors, list)
                if isinstance(vectors, list):
                    summary["indices"] = [v.get("index") for v in vectors]
                    summary["dimensions"] = [len(v.get("embedding", [])) for v in vectors]
                    summary["allFinite"] = all(
                        isinstance(n, (int, float)) and math.isfinite(n)
                        for v in vectors for n in v.get("embedding", []))
                    summary["differentVectors"] = len(vectors) == 2 and vectors[0].get("embedding") != vectors[1].get("embedding")
                save("direct_embedding", summary)
            except requests.RequestException as error:
                save("direct_embedding", {"transportError": type(error).__name__})
            kb_response = app(session, "POST", "/api/rag/knowledge-bases",
                              json={"name": "Synthetic 4.1f interface check", "description": "Generated samples only"})
            save("create_base", kb_response)
            kb = kb_response["body"]["data"]["id"]
            uploaded(session, kb, OUT / "synthetic-scan.pdf")
            uploaded(session, kb, OUT / "synthetic-keyword.txt")
            save("search", app(session, "POST", "/api/rag/search",
                               json={"kbIds": [kb], "query": "ZEBRA2026", "limit": 5}))
    except Exception as error:
        save("runner_failure", {"type": type(error).__name__, "detail": str(error)[:200]})
        raise
    finally:
        if process is not None:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            save("cleanup", {"ownPid": process.pid, "exitCode": process.returncode,
                             "port8081Closed": not listening(8081),
                             "listenersAfter": {str(p): listening(p) for p in [8080, 5173]},
                             "envLocalUnchanged": before == hashlib.sha256(PRIVATE.read_bytes()).digest()})
            temporary_path = Path(working).resolve()
            assert temporary_path.parent == Path(tempfile.gettempdir()).resolve()
            assert temporary_path.name.startswith("temp-4_1f-")
            shutil.rmtree(temporary_path, ignore_errors=True)


if __name__ == "__main__":
    main()
