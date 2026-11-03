# Category security regression and deployment report

Date: 2026-11-03

## Verification

- Full Maven suite: 404 tests, 0 failures, 0 errors, 15 skipped (`full-suite-after-locks.log`).
- Skips: five `K00_LIVE_MODEL_CHECK`; one each `K02_LIVE_OCR_CHECK`, `K02_FORMULA_LIVE_CHECK`, `K02_VISUAL_LIVE_CHECK`, `K01_MYSQL_CHECK`, `K03_LIVE_RETRIEVAL_CHECK`, and `WEKNORA_INTEGRATION`; four symbolic-link tests because the Windows account lacks the necessary privilege.
- Focused concurrency and isolation tests passed (`targeted-after-locks.log`); the fresh isolated package succeeded (`final-package.log`).
- Earlier isolated HTTP security checks passed against synthetic in-memory data (`http-check.log`).
- `session-export/` covers only the original child session. Resumed checks are recorded in this report and `deployment-check.log`, not in that export.
- `category-http-check.ps1` preserves the isolated HTTP verification as a reusable script. It requires PowerShell 7, Java, a build directory containing the packaged JAR and test schema, and a separate output directory.

## Deployment and live checks

- The freshly built JAR was deployed to port 8080 with the `contest-demo` profile. Its SHA-256 and the deployed JAR SHA-256 both equal `6110936FA81791BDB0F1B56C533066D8E3EF5CFF195838B5CE19E33A1CCE1C54`. The previous JAR was backed up at `.codex_tmp/category-live-before-deploy-20261103.jar`.
- Authenticated category and course counts were 6 and 2 before and after deployment. The post-deployment course IDs were 1 and 33.
- Post-deployment reads found homework 1 and exam 1; homework submission 1 for student ID 3 had total score 90 and exam submission 1 for student ID 3 had total score 95. These agree with the previously reported baseline; this agent did not independently capture those grades immediately before restart.
- An admin category read succeeded. A student PUT to a nonexistent category returned HTTP 403 without modifying data.
- Frontend port 5173 remained listening. The agent-owned isolated server on 8082 was stopped and no longer listened.
- Deployment operations did not modify `.env.local`; no pre-deployment hash was captured by this agent.

## Evidence hashes

SHA-256:

- `full-suite-after-locks.log`: `81375D148D25387DE0353E1C984735908B5B8B7615D8EDE77F3EC8B8EC636A6F`
- `targeted-after-locks.log`: `6D396D82F2FE714BCD44251565496C0DB5606BE14B8100CBE56F1B66AB31B37E`
- `final-package.log`: `D666C55D39856E07BC0450B1ACE549BDA45DB97F53FF86A22D4234508E7AE5CC`

## Attribution limits

The parent session recorded a request for model ID `gpt-6.1-terra` at 2026-11-03T10:07:10.593Z. The actual server-side model and network exit were not independently verified. The child session metadata identifies the provider as `custom`; this is not proof of the actual model.
