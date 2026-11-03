"""Synthetic upload rejection and deletion checks; no model calls."""

import json
import os
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

import run_http_verification as http


def upload(kb, token, filename, content):
    boundary = "sol-" + uuid.uuid4().hex
    body = (
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
        "Content-Type: application/octet-stream\r\n\r\n"
    ).encode() + content + f"\r\n--{boundary}--\r\n".encode()
    req = urllib.request.Request(
        http.BASE + f"/api/rag/knowledge-bases/{kb}/documents", data=body, method="POST",
        headers={"Authorization": "Bearer " + token, "Content-Type": "multipart/form-data; boundary=" + boundary},
    )
    try:
        response = urllib.request.urlopen(req, timeout=60)
    except urllib.error.HTTPError as error:
        response = error
    raw = response.read()
    return {"httpStatus": response.status, "body": json.loads(raw)}


if __name__ == "__main__":
    report = {"startedUtc": datetime.now(timezone.utc).isoformat(), "checks": [], "modelRequests": 0}
    try:
        token = http.data(http.request("POST", "/auth/login", payload={
            "username": "teacher", "password": os.environ.get("SOL_DEMO_PASSWORD", "123456")
        }))["token"]
        kb = http.data(http.request("POST", "/api/rag/knowledge-bases", token, {
            "name": "sol-20261102-boundaries-" + uuid.uuid4().hex[:8], "description": "Synthetic upload boundaries"
        }))["id"]
        report["knowledgeBaseId"] = kb
        for name, filename, content in (
            ("empty", "empty.txt", b""),
            ("legacy-doc", "legacy.doc", b"synthetic legacy document"),
            ("corrupt-pdf", "corrupt.pdf", b"%PDF-1.7\ninvalid synthetic body\n%%EOF"),
            ("over-20mb", "large.txt", b"x" * (20 * 1024 * 1024 + 1)),
        ):
            result = upload(kb, token, filename, content)
            evidence = {"requestBytes": len(content), "response": result}
            response_body = result["body"]
            accepted = result["httpStatus"] == 200 and str(response_body.get("code")) == "200"
            if accepted:
                uploaded = response_body["data"]
                finished = http.wait_job(uploaded["jobId"], token)
                document = http.data(http.request("GET", "/api/rag/documents/" + uploaded["documentId"], token))
                evidence.update(job=finished, document=document)
                passed = finished["final"]["stage"] == "FAILED" and not document.get("active_index_version")
            else:
                passed = result["httpStatus"] in (400, 413) or str(response_body.get("code")) in ("400", "413")
            report["checks"].append({"name": name, "status": "PASS" if passed else "FAIL", "evidence": evidence})
        found = http.data(http.request("POST", "/api/rag/search", token, {"kbIds": [kb], "query": "synthetic", "limit": 5}))
        report["checks"].append({"name": "rejected-inputs-no-searchable-index", "status": "PASS" if not found["hits"] else "FAIL", "evidence": found})
        valid = http.data(upload(kb, token, "sol-20261102-delete.txt", "删除测试专用 SolDeleteUnique 内容。".encode()))
        completed = http.wait_job(valid["jobId"], token)
        doc = valid["documentId"]
        chunks = http.data(http.request("GET", f"/api/rag/documents/{doc}/chunks", token))
        chunk_list = chunks if isinstance(chunks, list) else chunks.get("items", chunks.get("data", []))
        chunk = chunk_list[0].get("id", chunk_list[0].get("chunkId"))
        deleted = http.request("DELETE", "/api/rag/documents/" + doc, token)
        original = http.request("GET", f"/api/rag/documents/{doc}/download", token)
        old_chunk = http.request("GET", "/api/rag/chunks/" + chunk, token)
        report["checks"].append({
            "name": "delete-own-document-revokes-original-and-chunk",
            "status": "PASS" if completed["final"]["stage"] == "COMPLETED" and http.denied(original) and http.denied(old_chunk) else "FAIL",
            "evidence": {"documentId": doc, "chunkId": chunk, "delete": deleted, "original": original, "chunk": old_chunk},
        })
    except Exception as error:
        report["failure"] = {"type": type(error).__name__, "message": str(error)[:200]}
    finally:
        report["finishedUtc"] = datetime.now(timezone.utc).isoformat()
        (http.HERE / "upload-boundary-results.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(json.dumps(report, ensure_ascii=False))
