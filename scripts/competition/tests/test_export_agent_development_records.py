import importlib.util
import hashlib
import json
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "export_agent_development_records.py"
spec = importlib.util.spec_from_file_location("development_records", SCRIPT)
records = importlib.util.module_from_spec(spec)
spec.loader.exec_module(records)


class DevelopmentRecordExportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.source = self.root / "session.jsonl"
        self.output = self.root / "export"

    def tearDown(self):
        self.temp.cleanup()

    def write(self, events, tail=""):
        self.source.write_text("".join(json.dumps(e, ensure_ascii=False) + "\n" for e in events) + tail, encoding="utf-8")

    def event(self, kind, payload, timestamp="2026-10-21T02:00:00Z"):
        return {"timestamp": timestamp, "type": kind, "payload": payload}

    def message(self, role, text, **extra):
        return self.event("response_item", {"type": "message", "role": role,
            "content": [{"type": "input_text" if role == "user" else "output_text", "text": text}], **extra})

    def export(self, environ=None, **kwargs):
        return records.export_records(self.source, self.output, "2026-10-21T00:00:00+08:00", environ=environ or {}, **kwargs)

    def exported(self):
        return [json.loads(line) for line in (self.output / "records.jsonl").read_text(encoding="utf-8").splitlines()]

    def test_preserves_real_prompts_visible_messages_and_tool_links(self):
        self.write([
            self.event("session_meta", {"id": "session-1"}),
            self.message("user", "请先写测试，再配置模型。"),
            self.message("assistant", "先检查配置。", phase="commentary"),
            self.event("response_item", {"type": "function_call", "id": "tool-item", "call_id": "call-1", "name": "exec_command", "arguments": '{"cmd":"mvn test"}'}),
            self.event("response_item", {"type": "function_call_output", "call_id": "call-1", "output": "Tests run: 5, Failures: 0"}),
            self.message("assistant", "测试已通过。", phase="final"),
        ])
        manifest = self.export()
        exported = self.exported()
        self.assertEqual(5, manifest["record_count"])
        self.assertEqual([2, 3, 4, 5, 6], [r["source_line"] for r in exported])
        self.assertEqual("请先写测试，再配置模型。", exported[0]["content"][0]["text"])
        self.assertEqual("commentary", exported[1]["channel"])
        self.assertEqual("call-1", exported[3]["call_id"])
        self.assertEqual("exec_command", exported[3]["name"])
        self.assertEqual(4, exported[3]["call_source_line"])
        self.assertEqual("session-1", exported[3]["session_id"])
        self.assertIn("Tests run: 5, Failures: 0", (self.output / "records.md").read_text(encoding="utf-8"))

    def test_python_repr_encrypted_platform_payload_is_not_published_from_tool_output(self):
        value = "source metadata: [{'type': 'encrypted_content', 'encrypted_content': 'opaque-platform-cipher'}]...truncated"
        safe = records.Redactor(set()).tool_output(value)
        self.assertNotIn('opaque-platform-cipher', safe)
        self.assertIn('OMITTED', safe)

    def test_opaque_transport_in_tool_call_arguments_is_omitted_but_prompt_stays(self):
        cipher = 'gAAAA' + 'opaqueTransportCapsule' * 5 + '=='
        value = json.dumps({'task_name':'audit', 'message':'visible user request ' + cipher})
        safe = records.Redactor(set()).value(value)
        self.assertNotIn(cipher, safe)
        self.assertIn('visible user request', safe)
        self.assertIn('OMITTED', safe)
        nested = json.dumps({'message':'prefix\\n' + cipher})
        self.assertNotIn(cipher, records.Redactor(set()).value(nested))

    def test_session_instruction_metadata_is_removed_from_structured_and_mixed_tool_output(self):
        self.write([
            self.event("response_item",{"type":"function_call_output","call_id":"metadata-1","output":{
                "id":"safe-session", "base_instructions":{"text":"PRIVATE_SYSTEM_MARKER"},
                "developer_instructions":"PRIVATE_DEVELOPER_MARKER", "system_instructions":"PRIVATE_SYSTEM_2"}}),
            self.event("response_item",{"type":"function_call_output","call_id":"metadata-2","output":
                'session path\n{"payload":{"base_instructions":{"text":"PRIVATE_MIXED_MARKER"}}}\n'}),
        ])
        self.export()
        text="".join(p.read_text(encoding="utf-8") for p in self.output.iterdir() if p.is_file())
        for marker in ["PRIVATE_SYSTEM_MARKER","PRIVATE_DEVELOPER_MARKER","PRIVATE_SYSTEM_2","PRIVATE_MIXED_MARKER"]:
            self.assertNotIn(marker,text)
        self.assertIn("safe-session",text)

    def test_binary_image_pixels_are_not_copied_into_public_development_records(self):
        encoded="UFJJVkFURV9DT1VSU0VfUElYRUxT"
        self.write([
            self.event("response_item",{"type":"function_call_output","call_id":"image-1","output":{
                "image_url":"data:image/png;base64,"+encoded,"source_path":"private/source-page.png"}}),
            self.event("response_item",{"type":"function_call_output","call_id":"image-2","output":{
                "type":"image","mimeType":"image/png","data":"PRIVATE_RAW_PIXEL_MARKER"}}),
        ])
        self.export()
        text="".join(p.read_text(encoding="utf-8") for p in self.output.iterdir() if p.is_file())
        self.assertNotIn(encoded,text)
        self.assertNotIn("PRIVATE_RAW_PIXEL_MARKER",text)
        self.assertIn("private/source-page.png",text)

    def test_filters_by_timezone_aware_event_time_not_archive_time(self):
        self.write([
            self.message("user", "current"),
            self.event("response_item", {"type": "message", "role": "user", "content": [{"type":"input_text", "text":"old"}]}, "2026-10-20T15:59:59Z"),
        ])
        self.export()
        self.assertEqual(1, len(self.exported()))
        self.assertEqual("current", self.exported()[0]["content"][0]["text"])

    def test_excludes_analysis_reasoning_system_developer_and_context_summaries(self):
        self.write([
            self.message("system", "system-secret-instruction"),
            self.message("developer", "developer-secret-instruction"),
            self.message("assistant", "hidden-internal-analysis", channel="analysis"),
            self.message("assistant", "hidden-phase-analysis", phase="analysis"),
            self.event("response_item", {"type":"reasoning", "summary":"reasoning-private", "encrypted_content":"hidden-cipher"}),
            self.event("compacted", {"summary":"compacted-private"}),
            self.message("user", "<environment_context>private-context</environment_context>\n真实需求\n<summary>summary-private</summary>"),
            self.message("assistant", "visible answer", phase="final"),
        ])
        self.export()
        data = (self.output / "records.jsonl").read_text(encoding="utf-8")
        for marker in ["system-secret", "developer-secret", "hidden-internal", "hidden-phase", "reasoning-private", "hidden-cipher", "compacted-private", "private-context", "summary-private"]:
            self.assertNotIn(marker, data)
        self.assertIn("真实需求", data)
        self.assertIn("visible answer", data)

    def test_redacts_environment_private_env_bearer_keys_and_url_credentials(self):
        envfile = self.root / ".env.local"
        envfile.write_text("OCR_API_KEY=private-file-credential-9\nDB_PASSWORD=database-private-9\n", encoding="utf-8")
        self.write([
            self.message("user", "env-value-8 private-file-credential-9 database-private-9"),
            self.event("response_item", {"type":"custom_tool_call", "call_id":"call-2", "name":"functions.exec", "input": 'Authorization: Bearer bearer-token-abcdef\n{"api_key":"unknown-key-abcdef"}\nhttps://bob:passwd@models.example.test/path?api_key=query-secret-abcdef&other=ok'}),
            self.event("response_item", {"type":"custom_tool_call_output", "call_id":"call-2", "output":"sk-arbitrarykey987654321"}),
        ])
        self.export(environ={"API_KEY":"env-value-8"}, private_env_files=[envfile])
        all_output = "".join(p.read_text(encoding="utf-8") for p in self.output.iterdir() if p.is_file())
        for secret in ["env-value-8", "private-file-credential-9", "database-private-9", "bearer-token-abcdef", "unknown-key-abcdef", "passwd", "query-secret-abcdef", "sk-arbitrarykey987654321"]:
            self.assertNotIn(secret, all_output)
        self.assertIn("[REDACTED]", all_output)
        self.assertIn("other=ok", all_output)

    def test_redacts_structured_tool_credentials_and_omits_nested_hidden_fields(self):
        self.write([self.event("response_item", {"type":"function_call_output", "call_id":"structured", "output":{
            "apiKey":"unknown-structured-key", "nested":{"password":"unlisted-password", "secret":"unlisted-secret"},
            "encrypted_content":"not-for-export", "summary":"internal-summary", "ok":True,
            "items":[{"type":"reasoning", "summary":"nested-reasoning"},{"type":"text", "text":"visible tool output"}],
        }})])
        self.export()
        data = (self.output / "records.jsonl").read_text(encoding="utf-8")
        for value in ["unknown-structured-key", "unlisted-password", "unlisted-secret", "not-for-export", "internal-summary", "nested-reasoning"]:
            self.assertNotIn(value, data)
        self.assertIn("visible tool output", data)

    def test_decodes_json_tool_output_for_filtering_but_preserves_visible_user_json_prompt(self):
        prompt = '{"analysis":"这是用户需要处理的业务字段","summary":"这是用户的原始要求"}'
        output = {"content":[{"type":"text", "text":json.dumps({"encrypted_content":"nested-cipher", "analysis":"hidden-output-analysis", "answer":"visible-result"})}]}
        self.write([
            self.message("user", prompt),
            self.event("response_item", {"type":"function_call_output", "call_id":"json-string", "output":json.dumps(output)}),
        ])
        self.export()
        exported = self.exported()
        self.assertEqual(prompt, exported[0]["content"][0]["text"])
        text = json.dumps(exported, ensure_ascii=False)
        self.assertNotIn("nested-cipher", text)
        self.assertNotIn("hidden-output-analysis", text)
        self.assertIn("visible-result", text)

    def test_records_corrupt_final_line_and_does_not_claim_completeness(self):
        self.write([self.message("user", "valid")], tail='{"timestamp":"2026-10-21')
        manifest = self.export()
        self.assertEqual(1, manifest["record_count"])
        self.assertEqual([2], manifest["malformed_source_lines"])
        self.assertFalse(manifest["source_parse_complete"])
        self.assertIn("快照", (self.output / "records.md").read_text(encoding="utf-8"))

    def test_repeated_export_is_stable_and_full_jsonl_preserves_long_tool_output(self):
        output = "x" * 30000 + "TAIL-END"
        self.write([self.event("response_item", {"type":"custom_tool_call_output", "call_id":"missing-call", "output":output})])
        first = self.export()
        first_bytes = {p.name: p.read_bytes() for p in self.output.iterdir()}
        second = self.export()
        self.assertEqual(first, second)
        self.assertEqual(first_bytes, {p.name: p.read_bytes() for p in self.output.iterdir()})
        self.assertEqual(output, self.exported()[0]["output"])
        self.assertEqual(["missing-call"], first["outputs_without_exported_call"])
        self.assertIn("节选", (self.output / "records.md").read_text(encoding="utf-8"))

    def test_manifest_hash_verifies_the_actual_jsonl_bytes_on_this_platform(self):
        self.write([self.message("user", "第一行\n第二行"),self.message("assistant", "真实记录", phase="commentary")])
        manifest=self.export()
        self.assertEqual(hashlib.sha256((self.output/"records.jsonl").read_bytes()).hexdigest(),
            manifest["records_sha256"])

    def test_keeps_visible_agent_message_without_encrypted_content(self):
        self.write([self.event("response_item", {"type":"agent_message", "author":"/root/a", "recipient":"/root", "content":[{"type":"input_text", "text":"真实审查发现"},{"type":"encrypted_content", "encrypted_content":"not-visible"}]})])
        self.export()
        self.assertEqual("真实审查发现", self.exported()[0]["content"][0]["text"])
        self.assertNotIn("not-visible", (self.output / "records.jsonl").read_text(encoding="utf-8"))

    def test_ignores_unknown_timestamps_honestly_and_rejects_naive_since(self):
        self.write([{"type":"response_item", "payload":{"type":"message", "role":"user", "content":[{"type":"input_text", "text":"missing-time"}]}}])
        manifest = self.export()
        self.assertEqual([1], manifest["missing_or_invalid_timestamp_lines"])
        self.assertEqual(0, manifest["record_count"])
        with self.assertRaises(ValueError):
            records.export_records(self.source, self.output, "2026-10-21", environ={})

    def test_subagent_requires_explicit_real_task_boundary_and_omits_inherited_replay(self):
        self.write([
            self.event("session_meta", {"id":"child-session", "source":{"subagent":{"thread_spawn":{"parent_thread_id":"parent-session","agent_path":"/root/test"}}}}),
            self.message("user", "inherited-parent-user-request"),
            self.event("response_item", {"type":"function_call", "call_id":"parent-call", "name":"old-parent-action", "arguments":"inherited-action"}),
            self.event("event_msg", {"type":"task_started", "turn_id":"child-turn", "root_turn_id":"root-turn"}),
            self.event("turn_context", {"turn_id":"child-turn", "root_turn_id":"root-turn"}),
            self.event("response_item", {"type":"agent_message", "author":"/root", "recipient":"/root/test", "content":[{"type":"input_text","text":"real-delegated-task"}]}),
            self.event("response_item", {"type":"function_call", "call_id":"child-call", "name":"own-action", "arguments":"actual-action"}),
            self.event("response_item", {"type":"function_call_output", "call_id":"child-call", "output":"actual-result"}),
            self.message("assistant", "actual-child-final", phase="final_answer"),
        ])
        with self.assertRaisesRegex(ValueError,"start-line"):
            self.export()
        manifest=self.export(start_line=4)
        exported=self.exported()
        self.assertEqual([6,7,8,9],[r["source_line"] for r in exported])
        self.assertEqual("real-delegated-task",exported[0]["content"][0]["text"])
        self.assertEqual("own-action",exported[2]["name"])
        self.assertEqual("final",exported[3]["channel"])
        self.assertEqual(4,manifest["source_start_line"])
        self.assertEqual([2,3],manifest["fork_inherited_prefix_omitted"])
        text=(self.output/"records.jsonl").read_text(encoding="utf-8")
        self.assertNotIn("inherited-parent-user-request",text)
        self.assertNotIn("old-parent-action",text)

    def test_forked_parent_metadata_cannot_relabel_the_child_manifest_or_records(self):
        self.write([
            self.event("session_meta", {"id":"actual-child", "source":"subagent"}),
            self.event("session_meta", {"id":"inherited-parent"}),
            self.message("user", "inherited request"),
            self.event("event_msg", {"type":"task_started", "turn_id":"actual-child-turn"}),
            self.event("response_item", {"type":"agent_message", "author":"/root", "recipient":"/root/test", "content":[{"type":"input_text","text":"actual task"}]}),
            self.event("response_item", {"type":"function_call", "call_id":"actual-call", "name":"own-action", "arguments":"actual-action"}),
            self.event("session_meta", {"id":"later-inherited-parent"}),
            self.event("response_item", {"type":"function_call_output", "call_id":"actual-call", "output":"actual-result"}),
        ])
        manifest=self.export(start_line=4)
        self.assertEqual("actual-child",manifest["session_id"])
        self.assertEqual({"actual-child"},{r["session_id"] for r in self.exported()})
        self.assertEqual([5,6,8],[r["source_line"] for r in self.exported()])
        self.assertEqual(6,self.exported()[-1]["call_source_line"])
        self.assertEqual([2,3],manifest["fork_inherited_prefix_omitted"])

    def test_root_identity_stays_with_its_first_metadata_across_later_metadata(self):
        self.write([
            self.event("session_meta", {"id":"actual-root"}),
            self.message("user", "root request"),
            self.event("session_meta", {"id":"other-replayed-session"}),
            self.message("assistant", "root response", phase="commentary"),
        ])
        manifest=self.export()
        self.assertEqual("actual-root",manifest["session_id"])
        self.assertEqual({"actual-root"},{r["session_id"] for r in self.exported()})
        self.assertEqual([2,4],[r["source_line"] for r in self.exported()])


if __name__ == "__main__":
    unittest.main()
