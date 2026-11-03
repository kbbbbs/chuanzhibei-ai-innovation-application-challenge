# Sol backend verification, 2026-11-02

Original user request: "你先通过6.1sol/terra子代理进行测试，所需要的模型以及密钥我放在./新建文档里了".

This subagent actually ran the checks below on HEAD `6849731b1a211fdac8664a79f467c81975be116f`. The starting worktree was clean. No product source, test, database schema or private configuration was changed by this subagent. The coordinator managed the shared backend process and model configuration.

## Fresh regression and package

Java 21.0.11, Maven 3.9.16, Windows 11. Ordinary `mvn test` completed in 144.344 seconds: **397 total, 382 passed, 0 failures, 0 errors, 15 skipped**. These are this run's counts, not reused history.

Eleven skips are gated live model/OCR/retrieval/MySQL/WeKnora checks. Four `RagFileStoreTest` cases were aborted because this OS account could not create symbolic links; their symlink safeguards remain unverified on this host. The initial JSON collector stored a null reason for these four because Surefire puts their reason in element text; the exact cause was subsequently inspected from the XML. The collector was corrected for future runs without rerunning Maven or rewriting this run's original results.

`mvn -DskipTests package` completed in 5.922 seconds. Artifact:

`ai-learning-backend/target/Final_exam_question_bank-0.0.1-SNAPSHOT.jar`

SHA256: `3e7d65b6a1a1214b5eeb986409dec80b2636d0c2c2864c648c98779cf89c9b3e`

The build covers existing model policies/HTTP contracts, ingestion/checkpoints/versions, RAG permissions/retrieval and other repository regression cases. Mock/local HTTP contracts are not actual provider quality checks. No external model live gate was enabled for Maven.

## Live local HTTP checks

The coordinator started the isolated contest-demo backend on `127.0.0.1:8080`. This subagent used teacher/student demo identities but retained no credentials or login tokens. Synthetic UTF-8 TXT only, owned by the teacher:

- Library: `35e4d75e-636a-48c6-8865-7c1f9df3947a`
- Document: `5cb924fd-373e-447f-a8b8-25f2e6a3b243`
- Final active index: `2eab1c61-9c29-4f4c-bc63-6e0e67b4da82`, document version 2, parse revision 1.
- Final chunk: `33010021-276b-430a-9bfc-7b4d7ce4c53f`.
- Final original SHA256: `79b4d42677379ef99d32508e1f0a9fc92d10fa3356ec8ac0f963eec548b7ed7a`.

Nineteen functional assertions passed: teacher/student login, anonymous rejection, private library rejection, empty search reporting NONE, asynchronous TXT READY, original-byte roundtrip, student document/download/pages/job/search rejection, KEYWORD sources with accurate version and no invented TXT page number, page checkpoints, duplicate idempotency, completed-job cancellation returning conflict, selected-page retry increasing parse revision, replacement creating version 2, and active retrieval containing only v2.

The first real RAG answer attempt returned HTTP 400 after 4.157 seconds with an explicit model unavailable/timeout message. Its failure is retained in `http-results.json`. The coordinator then corrected the model identifier from the incomplete name to `opencode/deepseek-v4.1-flash` and restarted the shared service.

After that restart, this subagent independently confirmed identical chunk/index/document/parse versions and original-file SHA256, **2/2 persistence checks passed**. One further real RAG answer request returned HTTP 200 and ANSWERED after 16.484 seconds: it gave learning rate **0.02**, batch size **32**, and citation `[1]` to the current v2 chunk. **One synthetic supported-answer check passed**. Both attempts are retained; there was no repeated sampling or selection of the best answer.

There were two application RAG model request attempts, one failed and one successful. Provider-side retry count/billing is unknown. Embedding and OCR were not called because their credentials were unavailable. Retrieval correctly reported KEYWORD and EMBEDDING_NOT_CONFIGURED.

Six supplementary boundary assertions passed without model calls: empty TXT, legacy DOC, corrupt PDF and 20 MB + 1 byte TXT were explicitly rejected with business code 400; the isolated boundary library then had no searchable index (NONE/EMPTY_INDEX). A separately uploaded valid TXT was processed and deleted: its original returned HTTP 404 and its old chunk returned business code 404. Evidence is in `upload-boundary-results.json`. The original v2 library remains intact.

## Tool checks and boundaries

Python tool tests initially ran 22 cases with 21 passes and one precondition failure: the launcher test lacked a JDK path. Setting `LAUNCHER_TEST_JAVA_HOME` to the existing JDK for the test process, then rerunning, yielded **22/22 passes**. This did not change product code. The initial failure is preserved in the exported real session and the successful output is in `python-tests.log`.

No running-job cancellation race was induced; only completed-job conflict was checked. No full course PDF/OCR/formula/visual accuracy, hybrid semantic quality, independent teacher dataset, MySQL live migration, or complete teacher-to-student teaching workflow was evaluated here. One synthetic answer is not an answer accuracy percentage. Next steps are independent source-aligned course evaluation, OCR/embedding credentials and live validation, and symlink tests on a capable host.

Synthetic library/session data remain in the coordinator's demo database for UI inspection. Do not run these scripts against a non-disposable business instance.

## Authentic session evidence

Source session: `rollout-2026-11-02T17-25-38-01a19b94-200c-71e0-b5e5-bc2f03bc3ef3.jsonl`. The actual task assignment is response-item `agent_message` at **source line 11**, followed by this subagent's first commentary at line 15 and first tool call at line 16. The session has environment/developer setup before line 11, and no inherited parent work was counted.

Exports explicitly use `--start-line 11 --since 2026-11-02T09:25:38Z` and the repository exporter, with `.env.local` supplied privately for literal secret redaction during final refresh. `session/manifest.json` gives the exact bounded snapshot and source/hash information. Hidden reasoning, private source sessions and private configuration are not copied. Some tool output was already truncated at source; unavailable portions are not reconstructed. Refreshes are snapshots, not an automatic capture hook; the final reply may require the coordinator's later export.
