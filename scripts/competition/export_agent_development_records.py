#!/usr/bin/env python3
"""Export a redacted, bounded-in-time snapshot of visible Codex development records.

This is an explicit export, not a platform hook. No reasoning or internal instructions
are exported. The original session and private environment files remain in place.
"""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import os
import re
from collections import Counter
from pathlib import Path
from typing import Any, Mapping, Sequence


REDACTED = "[REDACTED]"
SECRET_NAME = re.compile(r"(?:KEY|TOKEN|SECRET|PASSWORD)$", re.IGNORECASE)
STRUCTURED_SECRET_NAME = re.compile(r"(?:api[_-]?key|auth[_-]?token|access[_-]?token|token|secret|password)$", re.IGNORECASE)
SESSION_INSTRUCTION_FIELDS = {"base_instructions", "developer_instructions", "system_instructions"}
HIDDEN_FIELDS = {"encrypted_content", "reasoning", "analysis", "thinking", "summary"} | SESSION_INSTRUCTION_FIELDS
SESSION_INSTRUCTION_FIELD = re.compile(r'''["'](?:base_instructions|developer_instructions|system_instructions)["']\s*:''')
OPAQUE_CONTENT_FIELD = re.compile(r'''["']encrypted_content["']\s*:''')
OPAQUE_TRANSPORT = re.compile(r"gAAAA[A-Za-z0-9_\-=]{30,}")
BINARY_DATA_URL = re.compile(r"data:[A-Za-z0-9.+/\-]+;base64,[A-Za-z0-9_+/=\-]+")
CONTEXT_TAGS = (
    "environment_context", "in-app-browser-context", "external_codex_apps_open_page",
    "codex_apps_open_page", "context", "summary", "analysis", "thinking",
)
CONTEXT_BLOCK = re.compile(
    r"<(?P<tag>" + "|".join(map(re.escape, CONTEXT_TAGS)) + r")\b[^>]*>.*?</(?P=tag)\s*>",
    re.IGNORECASE | re.DOTALL,
)
BEARER = re.compile(r"(?i)\bBearer[ \t]+[A-Za-z0-9._~+/=\-]{4,}")
SK_KEY = re.compile(r"\bsk-[A-Za-z0-9_\-]{8,}\b")
URL_USERINFO = re.compile(r"(https?://)[^/\s\"'<>]+@", re.IGNORECASE)
URL_SECRET_QUERY = re.compile(
    r"([?&](?:api[_-]?key|key|token|access_token|auth_token|secret|password)=)[^&#\s\"'<>]+",
    re.IGNORECASE,
)
SECRET_ASSIGNMENT = re.compile(
    r"(?i)(\b(?:[A-Za-z][A-Za-z0-9_]*_)?(?:api[_-]?key|auth[_-]?token|access[_-]?token|token|secret|password)\b"
    r"[\"']?[ \t]*[:=][ \t]*)(\"[^\"\r\n]*\"|'[^'\r\n]*'|[^\s,;}&]+)"
)
CALL_TYPES = {"function_call", "custom_tool_call"}
OUTPUT_TYPES = {"function_call_output", "custom_tool_call_output"}
VISIBLE_CHANNELS = {"commentary", "final"}
MARKDOWN_PREVIEW = 12000


def parse_time(value: str) -> dt.datetime:
    parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("A timezone-aware ISO timestamp is required")
    return parsed.astimezone(dt.timezone.utc)


def collect_secrets(environ: Mapping[str, str], private_env_files: Sequence[Path]) -> set[str]:
    """Read secrets for replacement only; no values or secret hashes enter reports."""
    values = {str(value) for name, value in environ.items() if SECRET_NAME.search(name) and value}
    for filename in private_env_files:
        for line in Path(filename).read_text(encoding="utf-8-sig").splitlines():
            match = re.match(r"^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$", line)
            if not match or not SECRET_NAME.search(match[1]):
                continue
            value = match[2].strip()
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            if value:
                values.add(value)
    return values


class Redactor:
    def __init__(self, secrets: set[str]):
        self.secrets = sorted(secrets, key=len, reverse=True)

    def text(self, text: str) -> str:
        result = CONTEXT_BLOCK.sub("[CONTEXT OMITTED]", text)
        result = OPAQUE_TRANSPORT.sub("[OPAQUE PLATFORM PAYLOAD OMITTED]", result)
        result = BINARY_DATA_URL.sub("[BINARY DATA OMITTED]", result)
        for secret in self.secrets:
            if len(secret) >= 4:
                result = result.replace(secret, REDACTED)
            else:
                result = re.sub(r"(?<!\w)" + re.escape(secret) + r"(?!\w)", REDACTED, result)
        result = BEARER.sub("Bearer " + REDACTED, result)
        result = SK_KEY.sub(REDACTED, result)
        result = URL_USERINFO.sub(r"\1" + REDACTED + "@", result)
        result = URL_SECRET_QUERY.sub(r"\1" + REDACTED, result)

        def assignment(match: re.Match) -> str:
            prefix, value = match.groups()
            bare = value[1:-1] if value[:1] in "\"'" and value[-1:] == value[:1] else value
            if not bare or bare.startswith(("$", "%", "<", "[REDACTED]", "os.environ", "os.getenv", "System.getenv")):
                return match[0]
            quote = value[0] if value[:1] in "\"'" else ""
            return prefix + quote + REDACTED + quote

        return SECRET_ASSIGNMENT.sub(assignment, result)

    def value(self, value: Any) -> Any:
        if isinstance(value, str):
            return self.text(value)
        if isinstance(value, list):
            return [self.value(item) for item in value if not (isinstance(item, dict)
                    and (item.get("type") in {"reasoning", "encrypted_content"}
                         or item.get("channel") == "analysis" or item.get("phase") == "analysis"))]
        if isinstance(value, dict):
            return {key: (REDACTED if STRUCTURED_SECRET_NAME.search(str(key)) and item else self.value(item))
                    for key, item in value.items() if key not in HIDDEN_FIELDS}
        return value

    def tool_output(self, value: Any) -> Any:
        """MCP outputs often encode JSON inside text blocks; inspect these before export."""
        if isinstance(value, str):
            try:
                decoded = json.loads(value)
            except (json.JSONDecodeError, TypeError):
                if SESSION_INSTRUCTION_FIELD.search(value):
                    return "[INTERNAL SESSION METADATA OMITTED FROM MIXED TOOL OUTPUT]"
                if OPAQUE_CONTENT_FIELD.search(value):
                    return "[OPAQUE PLATFORM CONTENT OMITTED FROM MIXED TOOL OUTPUT]"
                return self.text(value)
            if not isinstance(decoded, (dict, list)):
                return self.text(value)
            safe = self.tool_output(decoded)
            # Do not reformat unchanged genuine output. Changed fields require explicit redaction.
            return value if safe == decoded else json.dumps(safe, ensure_ascii=False, separators=(",", ":"))
        if isinstance(value, list):
            return [self.tool_output(item) for item in value if not (isinstance(item, dict)
                    and (item.get("type") in {"reasoning", "encrypted_content"}
                         or item.get("channel") == "analysis" or item.get("phase") == "analysis"))]
        if isinstance(value, dict):
            if value.get("type") == "image":
                return "[IMAGE PIXELS OMITTED; SEE ORIGINAL LOCAL SOURCE]"
            if value.get("type") in {"reasoning", "encrypted_content"} or value.get("channel") == "analysis" or value.get("phase") == "analysis":
                return "[INTERNAL CONTENT OMITTED]"
            return {key: (REDACTED if STRUCTURED_SECRET_NAME.search(str(key)) and item else self.tool_output(item))
                    for key, item in value.items() if key not in HIDDEN_FIELDS}
        return value


def visible_content(payload: dict[str, Any]) -> list[dict[str, str]]:
    content = payload.get("content", [])
    if isinstance(content, str):
        return [{"type": "text", "text": content}]
    return [{"type": item["type"], "text": item["text"]} for item in content
            if isinstance(item, dict) and item.get("type") in {"input_text", "output_text", "text"}
            and isinstance(item.get("text"), str)]


def project_event(payload: dict[str, Any], source_line: int, timestamp: str,
                  session_id: str, calls: dict[str, tuple[str, int]]) -> dict[str, Any] | None:
    """Whitelist visible fields instead of copying the source payload wholesale."""
    kind = payload.get("type")
    base: dict[str, Any] = {"source_line": source_line, "timestamp": timestamp, "session_id": session_id}
    if kind == "message":
        role = payload.get("role")
        channel = payload.get("channel") or payload.get("phase")
        if channel == "final_answer":
            channel = "final"
        if role not in {"user", "assistant"} or (role == "assistant" and channel not in VISIBLE_CHANNELS):
            return None
        content = visible_content(payload)
        if not content:
            return None
        base.update(event_type="message", role=role, channel=channel, content=content)
    elif kind in CALL_TYPES:
        base.update(event_type="tool_call", call_id=payload.get("call_id"), name=payload.get("name"),
                    tool_type=kind)
        if "namespace" in payload:
            base["namespace"] = payload["namespace"]
        for field in ("arguments", "input"):
            if field in payload:
                base[field] = payload[field]
    elif kind in OUTPUT_TYPES:
        call_id = payload.get("call_id")
        source_call = calls.get(call_id)
        base.update(event_type="tool_output", call_id=call_id, tool_type=kind,
                    name=source_call[0] if source_call else None,
                    call_source_line=source_call[1] if source_call else None, output=payload.get("output"))
    elif kind == "agent_message":
        if payload.get("channel") == "analysis" or payload.get("phase") == "analysis":
            return None
        content = visible_content(payload)
        if not content:
            return None
        base.update(event_type="agent_message", author=payload.get("author"), recipient=payload.get("recipient"), content=content)
    else:
        return None
    if "id" in payload:
        base["id"] = payload["id"]
    return base


def record_text(record: dict[str, Any]) -> str:
    if "content" in record:
        return "\n".join(item["text"] for item in record["content"])
    value = record.get("arguments", record.get("input", record.get("output", "")))
    return value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, indent=2)


def write_markdown(filename: Path, exported: list[dict[str, Any]], manifest: dict[str, Any]) -> None:
    lines = ["# 开发 Agent 真实使用记录", "", "本文件是指定源文件截至最后读取位置的脱敏快照，不是永久自动记录钩子。",
             "原文中的分析/推理、系统/开发者指令、已识别的上下文/摘要块与加密内容不导出；不补造缺失步骤。",
             "", f"- 会话：`{manifest['session_id']}`", f"- 起始事件时间（含）：`{manifest['since']}`",
             f"- 导出记录：{manifest['record_count']}；源文件 SHA-256：`{manifest['source_sha256']}`",
             f"- 损坏行：{manifest['malformed_source_lines']}；缺失或无效时间行：{manifest['missing_or_invalid_timestamp_lines']}",
             "- 完整脱敏内容保存在 `records.jsonl`；下文超长部分仅节选，并明确标注。", ""]
    for record in exported:
        label = record["event_type"]
        if record.get("role"):
            label += " / " + record["role"] + (" / " + record["channel"] if record.get("channel") else "")
        if record.get("name"):
            label += " / " + record["name"]
        lines += [f"## 源行 {record['source_line']} · {record['timestamp']} · {label}", ""]
        if record.get("call_id"):
            lines += [f"call_id: `{record['call_id']}`；调用源行：{record.get('call_source_line', record['source_line'])}", ""]
        text = record_text(record)
        if len(text) > MARKDOWN_PREVIEW:
            text = text[:MARKDOWN_PREVIEW] + "\n\n[Markdown 节选：完整脱敏内容见 records.jsonl 对应源行。]"
        # Indented blocks avoid source content closing or opening Markdown fences.
        lines += ["    " + line for line in text.splitlines()] + [""]
    filename.write_text("\n".join(lines) + "\n", encoding="utf-8")


def export_records(session_file: Path, output_dir: Path, since: str,
                   *, private_env_files: Sequence[Path] = (), environ: Mapping[str, str] | None = None,
                   start_line: int | None = None) -> dict[str, Any]:
    threshold = parse_time(since)
    source = Path(session_file).resolve()
    target = Path(output_dir).resolve()
    if target == source or source.is_relative_to(target):
        raise ValueError("Output directory must not contain or overwrite the original session")
    redactor = Redactor(collect_secrets(os.environ if environ is None else environ, private_env_files))
    raw_snapshot = source.read_bytes()  # One bounded snapshot: later appended events belong to the next export.
    source_hash = hashlib.sha256(raw_snapshot).hexdigest()
    raw_lines = raw_snapshot.splitlines()
    session_metadata: dict[str, Any] = {}
    for raw_line in raw_lines:
        try:
            possible_metadata = json.loads(raw_line)
        except (json.JSONDecodeError, UnicodeDecodeError):
            continue
        if possible_metadata.get("type") == "session_meta":
            session_metadata = possible_metadata.get("payload", {})
            break
    source_origin = session_metadata.get("source")
    is_subagent = session_metadata.get("thread_source") == "subagent" or source_origin == "subagent" or (
        isinstance(source_origin, dict) and "subagent" in source_origin)
    if is_subagent and start_line is None:
        raise ValueError("Subagent sessions contain inherited replay; specify --start-line at the verified actual task boundary")
    if start_line is not None and (start_line < 1 or start_line > len(raw_lines) + 1):
        raise ValueError("--start-line must be a valid 1-based source boundary")
    selected_start_line = start_line if start_line is not None else 1
    exported: list[dict[str, Any]] = []
    malformed: list[int] = []
    invalid_times: list[int] = []
    # The first metadata belongs to this source file. Fork replay can contain the
    # parent's metadata later; it must not relabel this session or its records.
    session_id = str(session_metadata.get("id", session_metadata.get("session_id", "unknown")))
    calls: dict[str, tuple[str, int]] = {}
    counts: Counter = Counter()
    unknown_assistant_lines: list[int] = []
    for number, raw_line in enumerate(raw_lines, 1):
        if not raw_line.strip():
            continue
        try:
            event = json.loads(raw_line)
        except (json.JSONDecodeError, UnicodeDecodeError):
            malformed.append(number)
            continue
        payload = event.get("payload", {})
        if not isinstance(payload, dict):
            continue
        if event.get("type") == "session_meta":
            continue
        if number < selected_start_line:
            continue
        if event.get("type") != "response_item":
            continue
        kind = payload.get("type")
        if kind in CALL_TYPES and payload.get("call_id"):
            calls[payload["call_id"]] = (payload.get("name", "unknown"), number)
        try:
            timestamp = event["timestamp"]
            actual_time = parse_time(timestamp)
        except (KeyError, ValueError, TypeError):
            invalid_times.append(number)
            continue
        if actual_time < threshold:
            continue
        if kind == "message" and payload.get("role") == "assistant" and not (payload.get("channel") or payload.get("phase")):
            unknown_assistant_lines.append(number)
        record = project_event(payload, number, timestamp, session_id, calls)
        if record is None:
            continue
        if record["event_type"] == "tool_output":
            record["output"] = redactor.tool_output(record.get("output"))
        safe_record = redactor.value(record)
        exported.append(safe_record)
        counts[safe_record["event_type"]] += 1

    selected_call_ids = {r.get("call_id") for r in exported if r["event_type"] == "tool_call"}
    unlinked = sorted({r["call_id"] for r in exported if r["event_type"] == "tool_output"
                       and r.get("call_id") and r["call_id"] not in selected_call_ids})
    manifest = {
        "format_version": 1, "session_id": session_id, "since": since,
        "source_file": source.name, "source_sha256": source_hash, "source_size_bytes": len(raw_snapshot),
        "source_total_lines": len(raw_lines),
        "source_start_line": selected_start_line,
        "session_kind": "subagent" if is_subagent else "root_or_user",
        "fork_inherited_prefix_omitted": [2, selected_start_line - 1] if is_subagent and selected_start_line > 2 else None,
        "fork_boundary_selection": "Explicit --start-line; caller verifies the actual task boundary" if is_subagent else None,
        "source_line_range": [exported[0]["source_line"], exported[-1]["source_line"]] if exported else None,
        "event_timestamp_range": [exported[0]["timestamp"], exported[-1]["timestamp"]] if exported else None,
        "record_count": len(exported), "event_counts": dict(sorted(counts.items())),
        "source_parse_complete": not malformed, "malformed_source_lines": malformed,
        "missing_or_invalid_timestamp_lines": invalid_times,
        "assistant_messages_without_visible_channel_omitted": unknown_assistant_lines,
        "outputs_without_exported_call": unlinked,
        "redaction": {"known_secret_count": len(redactor.secrets),
            "methods": ["known environment and private-env literal replacement", "Bearer and sk-key patterns",
                        "credential assignments", "URL userinfo and secret query fields", "JSON-encoded tool-output hidden-field filtering"],
            "private_env_files_read": len(private_env_files), "redacted_before_write": True},
        "limitations": ["Explicit snapshot only; not a permanent platform hook.",
            "No hidden analysis/reasoning, system/developer messages, encrypted content, or context summaries are exported.",
            "Source may already contain truncated tool output; unavailable original output is not reconstructed.",
            "Child-agent internal operations require that child's own session export; root delegation and visible replies alone do not establish full child coverage.",
            "Logs do not automatically establish three complete contest prompt chains or eligibility.",
            "Later appends, including the export itself and final reply, require a subsequent refresh."],
    }
    target.mkdir(parents=True, exist_ok=True)
    # Only whitelisted, redacted records are written. Raw snapshots/private values never leave memory.
    jsonl = "".join(json.dumps(item, ensure_ascii=False, separators=(",", ":")) + "\n" for item in exported)
    (target / "records.jsonl").write_text(jsonl, encoding="utf-8")
    manifest["records_sha256"] = hashlib.sha256((target / "records.jsonl").read_bytes()).hexdigest()
    write_markdown(target / "records.md", exported, manifest)
    (target / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--session-file", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--since", required=True, help="Inclusive ISO timestamp with timezone")
    parser.add_argument("--start-line", type=int, help="1-based actual task boundary; required for subagent sessions to exclude fork replay")
    parser.add_argument("--private-env-file", type=Path, action="append", default=[])
    args = parser.parse_args()
    manifest = export_records(args.session_file, args.output_dir, args.since, private_env_files=args.private_env_file,
                              start_line=args.start_line)
    print(json.dumps({key: manifest[key] for key in ("record_count", "source_line_range", "source_parse_complete", "records_sha256")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
