# Other business modules, 2026-11-03

Scope: real HTTP on the already running localhost:8080 service, no product edits, no service restart, no Maven, no browser usage. Baseline HEAD `6849731b1a211fdac8664a79f467c81975be116f`; workspace was already dirty with prior agent record directories. Machine clock at start: `2026-11-03T08:55:06+08:00`, although supplied environment declared Asia/Tokyo. Exam uses the actual backend local clock. These are API smoke results, not independent human acceptance or full module completion.

Evidence: `http-evidence.json` contains chronological sanitized requests, business responses, state checks, and corrections. Tokens only exist in process memory; passwords, tokens, email and phone values are redacted in the saved evidence. Demo seed accounts and entirely synthetic test material were used. `session/` is an explicit redacted snapshot from the actual child session, starting at verified source line 41, not inherited parent history.

## Matrix

24 scoped cases: **19 PASS, 1 FAIL, 1 BLOCKED, 3 NOT_RUN**. These are grouped acceptance cases, not a raw count of HTTP requests or the script's historical checks.

| ID | Result | Executed scope and observed result |
| --- | --- | --- |
| EX01 | PASS | Create independent course, class, membership, inline objective/subjective questions, published paper, draft examination, and publish; all business writes succeeded. |
| EX02 | PASS | Published exam opens 15 seconds in the future; early start rejected; start after opening succeeds. |
| EX03 | PASS | Actual start/end interval equals durationMinutes=1. Custom contradictory endTime was not tested. |
| EX04 | PASS | Student saves both answers and submits; objective 60, subjective 0, state PENDING_MANUAL_REVIEW. |
| EX05 | PASS | Repeated start returns same submission ID. |
| EX06 | PASS | Repeated submit rejected; teacher list contains exactly one record for the exam. |
| EX07 | PASS | Teacher awards subjective 35; total 95 and GRADED; student's subsequent read retains 95. |
| EX08 | PASS | Newly registered class outsider denied both submission read and exam start, HTTP403. |
| EX09 | PASS | After actual exam end, start rejected. Automatic submission for an unfinished exam was not exercised. |
| NT01 | PASS | Teacher creates class notice, member reads exact text, teacher updates, student refresh sees update, teacher deletes, subsequent read returns business404. |
| NT02 | PASS | Class outsider read denied and student modification denied; both HTTP403. |
| LP01 | PASS | Student progress45 saved, new read45; update70, new read70. |
| LP02 | PASS | Outsider cannot read or update progress; progress101 rejected and stored70 retained. |
| NO01 | PASS | Personal note Markdown/time saved and new read matches; class outsider cannot access lesson note. No shared-lesson second-user note isolation test. |
| AD01 | FAIL | Authenticated student can create and update category despite frontend admin-only category management. Reproduced twice; own synthetic categories removed by admin. |
| AD02 | PASS | Teacher cannot create user; admin creates synthetic student. Student user listing returns exactly self, password field absent, direct read of different synthetic user refused. |
| AD03 | PASS | Anonymous synthetic teacher application accepted; student review forbidden; admin rejects synthetic application and response persists status2. No approval workflow tested. |
| GR01 | PASS | Submit final exam results, aggregate95; CSV/XLSX contents actually parsed, 2 rows5 columns, same headers and score95. TXT format rejected; student export denied403. No rendered spreadsheet QA. |
| GR02 | NOT_RUN | Grade-file import not executed: no grade upload/preview/confirm endpoint found. ImportController exposes questions/papers/homeworks; GradeImportLogController exposes log CRUD, which is not grade import. No fictional API used. |
| PA01 | BLOCKED | Exactly one external arXiv query requested one result; HTTP200 with business500 `Failed to search arXiv: timeout` after8694ms. No retry or alternate external query. External search success not established. |
| VI01 | PASS | file URL rejected before download; member reads text lesson playback metadata with no play URL. |
| VI02 | NOT_RUN | Actual video retrieval/download/ready playback not exercised. Submit URL automatically schedules download, so no large or copyrighted material requested. |
| PR01 | PASS | Synthetic preparation created; student read denied403; exactly one outline generation, polled to SUCCEEDED100%, one chapter and one lesson matching positive-integer-addition topic. No source material; structural/topic smoke only, not teaching quality acceptance. |
| PR02 | NOT_RUN | Preparation source upload, lesson/question generation, editing and publication not exercised. |

## Confirmed defect

**P1: student can write course categories.** Minimal deterministic feedback loop:

`node docs/competition/development/agent-records/2026-11-03-ui-agent-modules/modules/smoke.mjs --permissions-only`

It logs in with the README demo student, verifies `roleCode=student`, tries anonymous and student category creation, finds only its synthetic category as admin, attempts student update, and removes the category as admin. Anonymous creation returns401. Student creation and update return HTTP200/business200, and creation is confirmed in persisted list. The loop prints FAIL for the intended requirement that student category mutations be rejected. It operates only on `other-20261103-...` category names.

Code evidence: `CategoryController.java:55` / `:56` add and `:71` / `:72` update have no role check; delete at `:89` / `:91` also lacks an explicit role check but student deletion was NOT tested. `AuthInterceptor.java:18` validates authentication but implements no category role policy. `router/index.js:255-258` gives CategoryManage `roles:['admin']`. Impact: any authenticated student can add or rename global course taxonomy through direct HTTP. Existing category mutation/deletion/cascade was never attempted. Recommended product fix and regression checks are handed to the root agent; no source edits made here.

User-list concern was a **test expectation error**, not a confirmed defect: controller deliberately returns only the current student. Actual response had one record, its ID matched the signed-in student, and fields were `id,username,nickname,email,phone,avatar,status,roleId,roleCode,createTime,updateTime`; password field absent (`User.java` has `@JsonIgnore`). No extra real-user enumeration performed. Email/phone values in evidence are redacted.

## Operation deviations and retained failures

- Initial inline question used invalid enum PUBLISHED; real DTO expects ACTIVE. HTTP400 preserved, payload corrected. This was a test harness error.
- Script resumed after setup and attempted the already-existing own class membership, returning409. No second membership created.
- Initial category update test name exceeded50 characters and returned400 validation. Shorter valid name reproduced business200. The initial rejection is not evidence of correct authorization.
- Historical check `student user listing denied` was overstrict; corrected to `student users list contains only self`, which passed twice. Earlier FAIL retained in chronological evidence.
- Historical check `paper search business response` is FAIL in the raw trace because success was not achieved; final acceptance classification is BLOCKED by the observed external timeout.
- No full suite, frontend route, all-role matrix, exam race/concurrency, cheating controls, or general model quality claim is made.

## Test data and reproducibility

Prefix `other-20261103-1793667444741`; course/class/chapter/lesson33; paper/exam/submission1; outsider user4; admin-created synthetic student5; rejected application1; preparation1; progress1. Notice1 and all this run's categories were removed; other newly created fixtures retained for inspection. No prior course/class/user/submission was edited or deleted. The preexisting demo student3 was added only to this newly created class. Exported student nickname is the README demo seed identity, not a real learner.

Run `smoke.mjs --permissions-only` for the narrow authorization repro. Running `smoke.mjs` without flags resumes from its evidence checkpoint; a completed trace intentionally avoids replaying the billed paper/preparation calls. `inspect-export.py` reads the application's exports and checks table structure/score using standard-library CSV/XML/ZIP parsing; no spreadsheet authoring library was required.
