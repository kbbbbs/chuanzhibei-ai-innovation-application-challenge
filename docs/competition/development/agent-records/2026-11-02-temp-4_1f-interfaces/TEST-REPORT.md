# Temporary 4.1f OCR and Embedding Interface Checks

Executed on 2026-11-02 (Asia/Shanghai, UTC+08:00). Scope: the user's authorized temporary substitution of existing `opencode/deepseek-v4.1-flash` for OCR and Embedding, to test real interfaces. Product code and `.env.local` were not modified.

## Result

| Layer | Actual observation | Conclusion |
| --- | --- | --- |
| Temporary application configuration | Administrator status reports OCR and Embedding as `CONFIGURED_UNVERIFIED`, both with complete model ID `opencode/deepseek-v4.1-flash` | Process overrides applied; configuration alone does not prove capabilities |
| Administrator OCR probe | HTTP 200, application code 200, `PASS`, latency 20536 ms | Fixed short synthetic image probe passed |
| Direct OCR transport/protocol | HTTP 200 JSON, `choices[0].message.content`, `finish_reason=stop`, 2.55 seconds | Actual image input and compatible chat-completions response observed |
| Random sample recognition | Response: `OCR TEST 2026` / `RANDOM 8383069738`; both match generated pixels | This one clean English/digit sample passed; no general OCR quality conclusion |
| Administrator Embedding probe | HTTP 200 application envelope, capability `FAIL`, `MODEL_CALL_FAILED`, latency 1961 ms | Application correctly did not mark vectors as available |
| Direct Embedding transport/protocol | HTTP 503 JSON, top-level `error`, no `data` array | Real `/embeddings` unavailable in this attempt; no valid vector, index, dimension, or finite-value result |
| Real scanned PDF upload | Accepted and queued; observed `QUEUED -> OCR -> FAILED`, `MODEL_TIMEOUT` | Real PDF OCR path failed; scan parsing is not accepted as passing |
| PDF checkpoint and indexing | Page 1 `FAILED`, `OCR_OR_PARSE`, `needsReview=true`, `imageCoverage=1.0`, empty chosen text, no chunks or active index | Failure is exposed and missing text is not published as usable content |
| Real TXT upload | `QUEUED -> EMBEDDING -> COMPLETED`, document `READY`, one text chunk, `embedding_status=provider_unavailable` | Text ingestion succeeds with explicit vector failure |
| Retrieval | `ZEBRA2026` returns the synthetic TXT chunk; `mode=bm25`, `actualMode=KEYWORD`, `degradedReason=NO_COMPATIBLE_VECTORS`, dimensions 0 | Real keyword fallback observed; no semantic/HYBRID success claimed |
| Semantic quality | No vectors returned | Not evaluated; no quality acceptance |

## Requests and Samples

The final run generated `synthetic-ocr.png` and the image-only one-page `synthetic-scan.pdf` from the same raster. Ground truth is `OCR TEST 2026` and `RANDOM 8383069738`. The random digits occurred only in image pixels in the OCR request, not in its text prompt. The direct request used the project's image/text message structure, `temperature=0`, `max_tokens=4096`, `stream=false`, and the project's OCR prompt. Its image URL was locally asserted to begin with the real PNG data URI prefix; some tool displays redact such strings automatically.

The Embedding request used the same configured model and `/v1/embeddings` path with two different synthetic inputs and `encoding_format=float`. It did not ask a chat model to invent vectors. HTTP 503 JSON was recorded as failure. No further provider/model was tried.

Only the direct Embedding response status, content type, JSON top-level keys and absence of a vector array were retained. Its nested `error.code/type/message` were not retained, so this evidence cannot distinguish routing failure, temporary upstream outage, capacity, or rate limiting. No extra request was made to recover that detail.

The scanned PDF used the real application upload/job/page/chunk APIs. The TXT used synthetic content with the unique keyword `ZEBRA2026`. No private source document was read or uploaded.

The isolated OCR timeout was explicitly 60 seconds, including response-body delivery in the Java client. PDF page preview metadata reports a rendered raster of 1584 x 648, compared with the direct PNG's 1100 x 450. The PDF request timed out; its exact request start and elapsed duration were not instrumented separately from rendering and job polling. Upload timestamps show the PDF accepted at 20:10:44 and the next TXT upload at 20:11:45. These observations do not establish that the PDF recognition would succeed with a longer deadline.

Final run provider requests were bounded: administrator OCR once, direct OCR once, PDF OCR once; administrator Embedding once, direct Embedding once, and TXT indexing once. Application OCR adapter attempts were set to 1 in the isolated process. No retry was performed after the scanned PDF timeout. Rebuild and HYBRID checks were skipped because no compatible vectors existed.

## Isolation and Cleanup

The existing built JAR ran at `127.0.0.1:8081`, with `contest-demo` plus an explicitly overridden independent H2 memory database and temporary RAG storage path. Full existing generation credentials were loaded in memory and passed through process environment only; OCR and Embedding roles were temporarily assigned the same existing model, HTTPS endpoint, and key. Credentials were not put in arguments or output. Runtime output was discarded to avoid uncontrolled logging. A fresh temporary JWT secret was passed only to this process.

The successful test process was PID 20928, launched directly through `D:\language\Java\bin\java.exe`. It was terminated at the end; exit code 1 is the Windows process termination result, not a failed interface test. Port 8081 was confirmed closed. Existing 8080 and 5173 listeners remained running; post-test HTTP checks returned 200 for `8080/auth/info` and `5173/`. `.env.local` was hashed before and after in memory, with only `envLocalUnchanged=true` exported.

## Preserved Initial Failure

`attempt-1-results.json` preserves the initial harness attempt before any supplier model request. The Oracle Java launcher shim spawned actual Java child PID 22928 under launcher PID 4344. Login returned application code 500 with a safe configuration error because the inherited JWT was unsuitable. The first cleanup tried to remove the active Java working directory, producing a Windows sharing violation; launcher termination alone also left port 8081 open. The original `runner_failure` therefore reflects the cleanup exception, while the true earlier login failure is retained in the exported session.

The actual child's parent PID and executable path were verified, and only that owned child was stopped. The harness was corrected to launch the actual Java binary, use a fresh temporary JWT, and remove temporary files after stopping Java. The initial attempt's sample digits were `0919244964`; the reusable sample files intentionally correspond to the final run truth above. No original application service was stopped.

## Evidence and Reproduction

- `results.json`: actual final application envelopes, safe direct protocol observations, generated truth, and cleanup result.
- `attempt-1-results.json`: preserved initial harness failure and inaccurate initial cleanup closure result, superseded by the verified cleanup described above.
- `run_interface_checks.py`: reusable real-request harness; requires installed Python `requests`, Pillow, JDK 21+, the existing built JAR and private local generation configuration. It creates fresh random samples and performs billable real requests, so do not rerun merely to refresh evidence.
- `session/`: actual redacted child-session export, explicitly bounded from source line 31, verified as the new task event. Fork-inherited history is excluded. The export uses `--private-env-file .env.local`; hidden reasoning, private environment contents, credentials, and image bytes are excluded.
- `verification.json`: post-run HTTP checks and SHA-256 hashes of public generated samples, harness and final results.

The session export is an explicit snapshot, not a permanent capture hook. Snapshot manifests state their exact source line range. No product code changes or mock acceptance tests were needed for this interface-only task.
