# Route acceptance matrix

Baseline: `docs/implementation/2026-11-05-installation-ui/PAGE-COVERAGE.md`.
This is a test inventory, not a claim that a route has passed acceptance. Existing
test references below identify candidate coverage only; they do not cover every
operation in the plan. Mocked tests do not validate the backend or deployment.

For each route, acceptance still needs recorded results for applicable roles,
operations and permissions, light/dark themes, widths 375/768/1080/1440,
loading/empty/error states, keyboard access, refresh and deep links. Mark a
dimension N/A only with a reason. Test runs must record revision, date, fixture
type, browser, result and evidence. No route below is marked accepted.

## Current route evidence (2026-11-06)

The 50 entries below cover 93 route/role scenarios. Baseline results total
723/723: original nine routes 136/136, additional 13 routes 120/120, student
routes 200/200, teacher/admin routes 240/240, console 24/24, and Agent redirect
3/3. The first five batches use four widths (375, 768, 1080, 1440) and two
themes; Agent redirect runs once per role. These heterogeneous checks are not
business acceptance. Separate populated checks are student 16/16 and
teacher/admin 16/20, and are not included in the baseline total.

Roles: S = student, T = teacher, A = admin, anon = anonymous. “Not asserted”
means the specific empty or populated state was not established by the test,
even when a fixture happened to contain data. “Basic” means one Tab focus check
only; no business operation is implied. Basic route checks use synthetic
fixtures and generally check content or title, loading, theme, document
overflow, unknown backend requests and console/page errors. Console and Agent
checks have the narrower boundaries shown below. The teacher baseline checks
headings, not empty-state content. See the
`docs/competition/development/agent-records/2026-11-06-frontend-completion/terra-routes`
reports for evidence and limitations.

| Route | Roles | Empty / initial state | Populated state | Operation boundary |
|---|---|---|---|---|
| `/` | anon | Not asserted | Landing content | Basic |
| `/home` | anon | Not asserted | Home content | Basic |
| `/login` | anon | Login form | Not asserted | Basic; no login |
| `/register` | anon | Registration form | Not asserted | Basic; no registration |
| `/setup` | anon | Installed state | Not asserted | Basic; no installation |
| `/setup/recovery` | anon | No recovery needed | Not asserted | Basic; no recovery |
| `/:pathMatch(.*)*` (`/not-a-real-page`) | anon | Not-found view | N/A | Basic |
| `/settings/models` | A | Not asserted | Configuration version visible | Basic; no model configuration |
| `/teacher-applications` | A | Not asserted | Not asserted | Basic; pending tab visible |
| `/administrative-classes` | A | Not asserted | Not asserted | Basic; add control visible |
| `/home-course-recommendations` | A | No enabled manual recommendation | Not asserted | Basic |
| `/work-studies` | T/A | Not asserted | Not asserted | Basic; add control visible |
| `/work-study-students` | T/A | Not asserted | Not asserted | Basic; import control visible |
| `/my-grades` | S/A | No grade records | Not asserted | Basic |
| `/my-homeworks` | S/A | Current homework section | Not asserted | Basic |
| `/my-exams` | S/A | Not asserted | Sample exam | Basic |
| `/courses` | T/A | Not asserted | Sample course | Basic; separate admin 375px course-name obstruction RED |
| `/courses/create` | T/A | Course form | Not asserted | Basic; no submission |
| `/users` | A | Not asserted | Sample user | Basic; mobile diagnostic partial |
| `/profile` | S/T/A | Not asserted | Sample user | Basic; no save |
| `/categories` | A | Not asserted | Sample category | Basic; mobile diagnostic partial |
| `/my-classes` | S/A | Not asserted | Sample course | Basic |
| `/learning-routes` | S/A | Not asserted | Sample course | Basic |
| `/learning-map/5` | S/A | Not asserted | Sample lesson | Basic |
| `/course-learn/5` | S/A | Select lesson prompt | S only: chapter and lesson | S: Tab to lesson and Enter; selected lesson checked |
| `/exam-taking/21` | S/A | No questions | S only: question and answer card | S: Tab to option and Space; answered count checked |
| `/exam-result/31` | S/A | Not asserted | Sample result | Basic |
| `/recommendation` | S/A | No recommendations | Not asserted | Basic |
| `/paper-search` | S/T/A | Keyword prompt | Not asserted | Basic; no search |
| `/knowledge-base` | S/T/A | No available knowledge base | S only: knowledge base and document | S: Tab and ArrowRight select question-answer tab |
| `/knowledge-graph` | S/T/A | Select knowledge base prompt | Not asserted | Basic |
| `/work-study-jobs` | S/A | No jobs | S only: sample job | S: search input and Enter; no application |
| `/my-work-study` | S/A | No applications | Not asserted | Basic |
| `/teaching-classes` | T/A | Not asserted | Not asserted | Basic |
| `/notices` | T/A | Not asserted | Not asserted | Basic |
| `/questions` | T/A | Not asserted | Sample question; 375px obscured for both roles | Create dialog and Escape; populated 2/4 |
| `/papers` | T/A | Not asserted | Sample paper; 375px obscured for both roles | Create dialog and Escape; populated 2/4 |
| `/homework-papers` | T/A | Not asserted | Not asserted | Basic |
| `/exams` | T/A | Not asserted | Not asserted | Basic |
| `/homeworks` | T/A | Not asserted | Not asserted | Basic |
| `/homework/11/submissions` | T/A | Not asserted | Sample homework title | Basic |
| `/exams/21/grading` | T/A | Not asserted | Sample student | Open and cancel grading dialog, 4/4 |
| `/exams/21/results` | T/A | Not asserted | Sample exam title | Basic |
| `/grade-analysis` | T/A | Not asserted | Sample class and grade matrix | Select class, 4/4 |
| `/resource-gen` | T/A | Not asserted | Not asserted | Basic; no generation |
| `/teacher-knowledge-base` | T/A | Not asserted | Not asserted | Basic |
| `/courses/5` | T/A | Not asserted | Sample course, chapter and lesson | Expand chapter, 4/4 |
| `/operation-logs` | T/A | Not asserted | Not asserted | Basic |
| `/console` | S/T/A | Not asserted | Role-specific sample dashboard | Title/content, loading, theme, overflow, unknown request and pageerror; no Tab assertion |
| `/agent-chat` | S/T/A | Not asserted | Agent sidebar on dashboard | Redirect to console preserves query; once per role; no model call |

Student populated checks use 375/1440px and both themes, student only.
Teacher populated checks use 375/1440px and light theme for teacher and admin;
the four 375px question/paper failures stop before the final unknown-request
assertion. `/courses` has a separate 375px admin/light obstruction RED.
Diagnostics, initial failed attempts and populated checks are excluded from the
baseline. Screenshots are representative and were not all visually reviewed.
No real backend or model was accessed; broader workflows, permissions and
error states remain open.

## Historical run record (2026-11-05)

On 2026-11-05, revision `1f51881` with substantial shared uncommitted changes,
Chromium was run against the local Vite server with synthetic API responses and
synthetic authentication. Setup and model-settings tests passed 15/15, core
mocked UI regressions passed 44/44, and formula-rendering tests passed 8/8.
Grouped route smoke passed 5/5 across the initial run and the corrected targeted
rerun. Smoke checks only establish that routes render under their fixtures.

`visual-evidence.spec.js` passed on Chromium and generated 40 screenshots in
`docs/competition/development/agent-records/2026-11-05-installation-ui/terra-ui`: setup, model settings, lesson, teacher knowledge and login,
in light and dark themes at widths 375, 768, 1080 and 1440 pixels. Each image is
named `mocked-*`; the viewport height was 960 pixels. Route URLs and theme class
were checked, and no document-level horizontal overflow was measured. Selected
mobile screenshots were inspected. These captures are not a complete visual
review and do not establish acceptance, backend behavior or deployment status.
The eight lesson images were replaced after a targeted passing rerun corrected
the singular note endpoint and checked that loading masks disappeared and the
mock note rendered; the original lesson images had captured a loading state.
The dark 1440 replacement was visually inspected.
Role and permission coverage, real service workflows, interactions, error and
loading states, keyboard access, and full screenshot review remained open.

The following candidate inventory and “None identified” entries are historical
as of 2026-11-05; use the current evidence table above for later results.

| # | Route | Candidate automated coverage | Remaining acceptance |
|---|---|---|---|
| 1 | `/` | None identified | All planned dimensions |
| 2 | `/home` | None identified | All planned dimensions |
| 3 | `/login` | None identified | All planned dimensions |
| 4 | `/register` | None identified | All planned dimensions |
| 5 | `/console` | None identified | All planned dimensions |
| 6 | `/courses` | None identified | All planned dimensions |
| 7 | `/courses/create` | None identified | All planned dimensions |
| 8 | `/courses/:id` | None identified | All planned dimensions |
| 9 | `/teaching-classes` | None identified | All planned dimensions |
| 10 | `/notices` | None identified | All planned dimensions |
| 11 | `/questions` | None identified | All planned dimensions |
| 12 | `/papers` | None identified | All planned dimensions |
| 13 | `/exams` | None identified | All planned dimensions |
| 14 | `/homeworks` | None identified | All planned dimensions |
| 15 | `/homework-papers` | None identified | All planned dimensions |
| 16 | `/homework/:id/submissions` | `learning-layout-assessment.spec.js`: grading label | Full grading workflow and all other dimensions |
| 17 | `/exams/:id/grading` | `learning-layout-assessment.spec.js`: grading label | Full grading workflow and all other dimensions |
| 18 | `/exams/:id/results` | None identified | All planned dimensions |
| 19 | `/grade-analysis` | None identified | All planned dimensions |
| 20 | `/resource-gen` | None identified | All planned dimensions |
| 21 | `/teacher-knowledge-base` | `k02-processing-ui`, `k03-retrieval-ui`, `native-rag-ui`, `visual-rag-ui`; `native-rag-real` requires a real service | Execute applicable suites; remaining roles, operations and display dimensions |
| 22 | `/agent-chat` | None identified | Redirect, query preservation and all other dimensions |
| 23 | `/profile` | None identified | All planned dimensions |
| 24 | `/operation-logs` | None identified | All planned dimensions |
| 25 | `/my-classes` | None identified | All planned dimensions |
| 26 | `/learning-routes` | None identified | All planned dimensions |
| 27 | `/learning-map/:courseId` | None identified | All planned dimensions |
| 28 | `/course-learn/:courseId` | `learning-layout-assessment.spec.js`: lesson tools and mobile navigation | Full learning workflow and remaining dimensions |
| 29 | `/my-homeworks` | None identified | All planned dimensions |
| 30 | `/my-exams` | None identified | All planned dimensions |
| 31 | `/exam-taking/:examId` | None identified | All planned dimensions |
| 32 | `/exam-result/:submissionId` | `learning-layout-assessment.spec.js`: result labels and API selection | Full result workflow and remaining dimensions |
| 33 | `/my-grades` | None identified | All planned dimensions |
| 34 | `/recommendation` | None identified | All planned dimensions |
| 35 | `/paper-search` | `paper-search-errors.spec.js`: errors, retry and empty state | Search workflow, authorization and remaining dimensions |
| 36 | `/knowledge-base` | None identified | All planned dimensions |
| 37 | `/knowledge-graph` | None identified | All planned dimensions |
| 38 | `/work-study-jobs` | None identified | All planned dimensions |
| 39 | `/my-work-study` | None identified | All planned dimensions |
| 40 | `/users` | None identified | All planned dimensions |
| 41 | `/teacher-applications` | None identified | All planned dimensions |
| 42 | `/categories` | None identified | All planned dimensions |
| 43 | `/administrative-classes` | None identified | All planned dimensions |
| 44 | `/home-course-recommendations` | None identified | All planned dimensions |
| 45 | `/work-studies` | None identified | All planned dimensions |
| 46 | `/work-study-students` | None identified | All planned dimensions |
| 47 | `/:pathMatch(.*)*` | None identified | All planned dimensions |

## Added views

| Route or view | Candidate automated coverage | Remaining acceptance |
|---|---|---|
| `/setup` | `setup-acceptance.spec.js`: mocked NEW, ADOPT, CSRF, conflict, revision mismatch and viewport cases | Re-run on completed UI; all remaining planned dimensions |
| `/setup/recovery` | `setup-acceptance.spec.js`: mocked explicit rollback confirmation | Re-run on completed UI; remaining recovery workflows and dimensions |
| `/settings/models` | `model-settings-acceptance.spec.js`: mocked secret operations, locks, conflict and apply | Run on completed UI; remaining planned dimensions |
| Apply progress panel | Setup and model-settings mocked apply cases | Remaining states, reconnect behavior and dimensions |
| MainLayout and mobile navigation | Partial incidental coverage in route suites | Roles, navigation, theme, focus, back and required widths |
| Role dashboards | None identified | All planned dimensions for each role |
| AgentFloatingAssistant | None identified | All planned dimensions |
| NativeKnowledgeWorkspace | Knowledge-base suites listed above | Remaining role and workflow coverage |
| Lesson renderer and tools | `learning-layout-assessment.spec.js`; `formula-rendering.test.mjs` tests rendering logic | Complete interactive workflows and role coverage |
