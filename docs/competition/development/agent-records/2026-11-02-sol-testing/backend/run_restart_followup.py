"""Recheck existing synthetic evidence after the coordinator restarts the backend."""

import hashlib
import json
import os
from datetime import datetime, timezone

import run_http_verification as http


if __name__ == "__main__":
    before = json.loads((http.HERE / "http-results.json").read_text(encoding="utf-8"))
    report = {"startedUtc": datetime.now(timezone.utc).isoformat(), "checks": [], "modelRequestsAttempted": 0}
    try:
        login = http.data(http.request("POST", "/auth/login", payload={
            "username": "teacher", "password": os.environ.get("SOL_DEMO_PASSWORD", "123456")
        }))
        token = login["token"]
        kb, doc = before["knowledgeBaseId"], before["documentId"]
        old = next(check["evidence"] for check in before["checks"] if check["name"] == "replacement-active-source-only")
        current = http.data(http.request("POST", "/api/rag/search", token, {
            "kbIds": [kb], "query": "梯度下降", "limit": 5
        }))
        old_versions = [(hit["chunkId"], hit["indexVersion"], hit["documentVersion"], hit["parseRevision"]) for hit in old["hits"]]
        current_versions = [(hit["chunkId"], hit["indexVersion"], hit["documentVersion"], hit["parseRevision"]) for hit in current["hits"]]
        report["checks"].append({
            "name": "restart-search-persistence",
            "status": "PASS" if old_versions == current_versions and current_versions else "FAIL",
            "evidence": current,
        })
        original = http.request("GET", f"/api/rag/documents/{doc}/download", token)
        report["checks"].append({
            "name": "restart-original-persistence",
            "status": "PASS" if original["sha256"] == hashlib.sha256(http.TEXT2.encode()).hexdigest() else "FAIL",
            "evidence": {key: original[key] for key in ("httpStatus", "bytes", "sha256")},
        })
        report["modelRequestsAttempted"] = 1
        answer_result = http.request("POST", "/api/rag/chat", token, {
            "knowledgeBaseIds": [kb], "query": "梯度下降教学测试中更新后的学习率和批量大小分别是多少？请引用资料。"
        })
        answer = (answer_result["body"] or {}).get("data") or {}
        supported = "0.02" in answer.get("answer", "") and "32" in answer.get("answer", "") and bool(answer.get("citations"))
        report["checks"].append({
            "name": "real-rag-answer-after-model-config-correction",
            "status": "PASS" if supported else "FAIL",
            "evidence": answer_result,
        })
        report["limitations"] = "One synthetic answer only; not a course answer accuracy or independent retrieval evaluation."
    except Exception as error:
        report["failure"] = {"type": type(error).__name__, "message": str(error)[:200]}
    finally:
        report["finishedUtc"] = datetime.now(timezone.utc).isoformat()
        (http.HERE / "restart-followup-results.json").write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(json.dumps(report, ensure_ascii=False))
