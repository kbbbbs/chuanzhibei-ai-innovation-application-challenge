# Paper search regression and local deployment

## Verification

- Backend focused regression: 9 tests passed, 0 failures. The earlier valid RED run had 8 tests and 6 failures.
- Full backend `mvn test`: exit 0, 413 tests, 0 failures, 0 errors, 15 skipped. The console output was not captured; counts come from the completed run and can be checked against `ai-learning-backend/target/surefire-reports/`. A model rate-limit interruption occurred while the test was running.
- Frontend `npx playwright test e2e/paper-search-errors.spec.js --reporter=line --timeout=30000`: exit 0, 4 passed. The first attempted RED was invalid because its route glob intercepted the Vite source module; after narrowing the route, a valid RED showed the two expected failure-state failures and the empty-state test passed.
- Frontend `npm run build`: exit 0, with existing third-party annotation and large-chunk warnings.
- Isolated backend `mvn -DskipTests package`: exit 0. The DOCTYPE test verifies rejection; it does not independently establish that no external resource was accessed.

## Local deployment and read-only checks

The previously deployed jar was backed up under ignored `.codex_tmp/paper-search-previous.jar`. Its SHA-256 was `6110936FA81791BDB0F1B56C533066D8E3EF5CFF195838B5CE19E33A1CCE1C54`. The isolated build and deployed jar both had SHA-256 `34CA0F77DD156272363AB1A2220424CA37E96178F09969E4F46F1404AD85ADCF`. The backend was restarted with the existing localhost contest-demo configuration; the frontend remained on port 5173.

Authenticated read-only checks before and after deployment agreed: 6 categories; course IDs 33 and 1; homework submission 1 score 90; exam submission 1 score 95.

The single post-deployment live `machine learning` arXiv query returned HTTP 200 and business code 200 in 4754 ms. It returned one valid paper, `2306.04338v1`, with a title, two named authors and a nonempty abstract. The response summary is in `post-deploy-query.json`. A historical request timed out after 8694 ms; the transient root cause was not established.

## Record limitation

The available environment exposes a parent session ID and a current thread ID, but no session file could be reliably associated with this child continuation and its actual start boundary. No inherited or unrelated session was exported as a substitute.
