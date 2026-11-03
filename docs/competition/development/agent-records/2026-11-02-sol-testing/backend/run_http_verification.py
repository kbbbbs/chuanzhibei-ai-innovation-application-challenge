"""Exercise an isolated synthetic library against the running local backend."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone


HERE = Path(__file__).resolve().parent
BASE = "http://127.0.0.1:8080"
PREFIX = "sol-20261102"
TEXT1 = "SolSyntheticCourse\n梯度下降教学测试：本课程的学习率是0.05。批量大小是16。\n验证标记 SOL_SOURCE_V1。\n"
TEXT2 = "SolSyntheticCourse\n梯度下降教学测试：本课程更新后的学习率是0.02。批量大小是32。\n验证标记 SOL_SOURCE_V2。\n"
REPORT = {"startedUtc": datetime.now(timezone.utc).isoformat(), "checks": [], "modelRequestsAttempted": 0}


def persist():
    (HERE / "http-results.json").write_text(
        json.dumps(REPORT, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def request(method, path, token=None, payload=None, file_text=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    body = None
    if payload is not None:
        body = json.dumps(payload).encode()
        headers["Content-Type"] = "application/json"
    if file_text is not None:
        boundary = "sol-" + uuid.uuid4().hex
        body = (
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
            f"filename=\"{PREFIX}.txt\"\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n"
        ).encode() + file_text.encode() + f"\r\n--{boundary}--\r\n".encode()
        headers["Content-Type"] = "multipart/form-data; boundary=" + boundary
    started = time.monotonic()
    try:
        response = urllib.request.urlopen(
            urllib.request.Request(BASE + path, data=body, headers=headers, method=method),
            timeout=180,
        )
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read()
    content_type = response.headers.get("Content-Type", "")
    data = json.loads(raw) if "json" in content_type else None
    return {
        "httpStatus": response.status, "body": data,
        "elapsedSeconds": round(time.monotonic() - started, 3),
        "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest(),
    }


def data(result):
    assert result["httpStatus"] == 200 and str(result["body"].get("code")) == "200"
    return result["body"]["data"]


def check(name, passed, evidence):
    REPORT["checks"].append({"name": name, "status": "PASS" if passed else "FAIL", "evidence": evidence})
    persist()
    print(name, "PASS" if passed else "FAIL", flush=True)


def denied(result):
    return result["httpStatus"] in (401, 403, 404) or str((result["body"] or {}).get("code")) in ("401", "403", "404")


def wait_job(job, token):
    observed = []
    for _ in range(120):
        current = data(request("GET", "/api/rag/jobs/" + job, token))
        observed.append(current["stage"])
        if current["stage"] in ("COMPLETED", "FAILED", "CANCELLED"):
            return {"observedStages": list(dict.fromkeys(observed)), "final": current}
        time.sleep(0.5)
    raise RuntimeError("JOB_POLL_TIMEOUT")


if __name__ == "__main__":
    try:
        tokens = {}
        for role in ("teacher", "student"):
            login = request("POST", "/auth/login", payload={
                "username": role, "password": os.environ.get("SOL_DEMO_PASSWORD", "123456")
            })
            identity = data(login)
            tokens[role] = identity["token"]
            check("login-" + role, True, {"httpStatus": login["httpStatus"]})
        teacher, student = tokens["teacher"], tokens["student"]
        check("anonymous-library-denied", denied(request("GET", "/api/rag/knowledge-bases")), {})
        library = data(request("POST", "/api/rag/knowledge-bases", teacher, {
            "name": PREFIX + "-" + uuid.uuid4().hex[:8], "description": "Synthetic HTTP validation only"
        }))
        kb = library["id"]
        REPORT["knowledgeBaseId"] = kb
        check("student-private-library-denied", denied(request("GET", "/api/rag/knowledge-bases/" + kb, student)), {})
        empty = data(request("POST", "/api/rag/search", teacher, {"kbIds": [kb], "query": "梯度下降", "limit": 5}))
        check("empty-search", not empty["hits"] and empty["actualMode"] == "NONE", empty)
        uploaded = data(request("POST", f"/api/rag/knowledge-bases/{kb}/documents", teacher, file_text=TEXT1))
        doc, job = uploaded["documentId"], uploaded["jobId"]
        REPORT["documentId"] = doc
        first = wait_job(job, teacher)
        check("upload-async-ready", first["final"]["stage"] == "COMPLETED" and first["final"]["documentState"] == "READY", first)
        original = request("GET", f"/api/rag/documents/{doc}/download", teacher)
        check("original-roundtrip", original["sha256"] == hashlib.sha256(TEXT1.encode()).hexdigest(), {
            key: original[key] for key in ("httpStatus", "bytes", "sha256")
        })
        for resource in (f"/documents/{doc}", f"/documents/{doc}/download", f"/documents/{doc}/pages", f"/jobs/{job}"):
            denied_result = request("GET", "/api/rag" + resource, student)
            check("student-denied-" + resource.split("/")[-1], denied(denied_result), {
                "httpStatus": denied_result["httpStatus"], "code": (denied_result["body"] or {}).get("code")
            })
        student_search = request("POST", "/api/rag/search", student, {"kbIds": [kb], "query": "梯度下降", "limit": 5})
        check("student-search-denied", denied(student_search), {"httpStatus": student_search["httpStatus"], "code": (student_search["body"] or {}).get("code")})
        found = data(request("POST", "/api/rag/search", teacher, {"kbIds": [kb], "query": "梯度下降", "limit": 5}))
        check("keyword-source-version", found["actualMode"] == "KEYWORD" and any(
            h["documentId"] == doc and h["documentVersion"] == 1 and "0.05" in h["content"] and h["page"] is None
            for h in found["hits"]
        ), found)
        pages = data(request("GET", f"/api/rag/documents/{doc}/pages?version=1", teacher))
        check("text-page-checkpoints", bool(pages), pages)
        duplicate = data(request("POST", f"/api/rag/knowledge-bases/{kb}/documents", teacher, file_text=TEXT1))
        check("duplicate-content-idempotent", duplicate["documentId"] == doc and duplicate["duplicate"], duplicate)
        cancel = request("POST", f"/api/rag/jobs/{job}/cancel", teacher)
        check("completed-job-cancel-conflict", str((cancel["body"] or {}).get("code")) == "409" or cancel["httpStatus"] == 409, cancel)
        retry = data(request("POST", f"/api/rag/documents/{doc}/parse-retry", teacher, {"pages": [1], "forceOcr": False}))
        retried = wait_job(retry["jobId"], teacher)
        check("selected-text-page-retry", retried["final"]["stage"] == "COMPLETED" and retried["final"]["parseRevision"] > first["final"]["parseRevision"], retried)
        replacement = data(request("PUT", f"/api/rag/documents/{doc}/file", teacher, file_text=TEXT2))
        replaced = wait_job(replacement["jobId"], teacher)
        check("replace-version-ready", replaced["final"]["stage"] == "COMPLETED" and replaced["final"]["documentVersion"] == 2, replaced)
        current = data(request("POST", "/api/rag/search", teacher, {"kbIds": [kb], "query": "梯度下降", "limit": 5}))
        check("replacement-active-source-only", bool(current["hits"]) and all(
            h["documentId"] == doc and h["documentVersion"] == 2 and "0.02" in h["content"] and "0.05" not in h["content"] for h in current["hits"]
        ), current)
        REPORT["modelRequestsAttempted"] += 1
        chat = request("POST", "/api/rag/chat", teacher, {
            "knowledgeBaseIds": [kb], "query": "梯度下降教学测试中更新后的学习率和批量大小分别是多少？请引用资料。"
        })
        answer = (chat["body"] or {}).get("data") or {}
        check("real-rag-answer", str((chat["body"] or {}).get("code")) == "200" and "0.02" in answer.get("answer", "") and "32" in answer.get("answer", "") and bool(answer.get("citations")), chat)
        REPORT["chatSessionId"] = answer.get("sessionId")
        REPORT["remainingData"] = "Synthetic test library retained for UI verification; cleanup is explicit."
    except Exception as error:
        REPORT["failure"] = {"type": type(error).__name__, "message": str(error)[:200]}
        print("Execution stopped:", type(error).__name__, flush=True)
    finally:
        REPORT["finishedUtc"] = datetime.now(timezone.utc).isoformat()
        persist()
