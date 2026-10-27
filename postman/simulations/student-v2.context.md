# student-v2

Created 2026-10-26 from the partial observed interface in `postman/specs/student-v2.yaml`. All account values and fixture documents are synthetic.

## Purpose

Run the actual `StudentPortalClient` and shared `BsiClient` over HTTP against the local Student V2 stand-in. The mock is in `postman/mocks/student-v2/default.js`. The owning-team mock search was skipped because the CLI is signed out.

## Execution

Start `postman simulation run postman/simulations/student-v2.sim.yaml --port 4915 --no-history --output ndjson`. Set `STUDENT_SIMULATION_ORIGIN=http://127.0.0.1:4915/student`, then run `gradlew.bat :app:testDebugUnitTest --no-configuration-cache`. Run `postman collection run "postman/collections/Student V2 Checks" --no-report-events` for the local assertions. Stop the local simulation with Ctrl+C.

## Assumptions

- 2026-10-26: The route prefix `/student` is required for simulation redirects. Synthetic credentials are `test-student` / `test-password`; these are not BSI credentials.
- 2026-10-26: `interceptRequests: false` retains binary multipart bytes; the handler parses its own bodies and catches asynchronous failures. The mock is local-only.
- 2026-10-26: A passing run does not verify real profile/password applications, account eligibility, CAPTCHA policy, remote logout or server session duration.

## Observations

<!-- begin:observations -->
- 2026-10-27: Regression for intended MyLMS login redirects adds a twelfth Student HTTP test. After a pre-login SSO attempt, sign-in may finish with an external redirect despite valid Student auth; the client verifies its own home without visiting the external URL. Final combined suite has 58 tests, zero failed/skipped. This later run used Student port 4917 with the updated mock.
- 2026-10-27: Final combined run uses Student port 4916 and BSI port 4914. Eleven Student client HTTP tests cover raw JSON, member lookup and the original session/form cases. The Student collection passed ten requests and ten assertions; the sole student member served the requests, including JSON CSRF lookup. No live letter was submitted.
- 2026-10-26: The sole `student` member served all nine actual-client HTTP integration tests with zero failures or skipped tests. Login rotation, validation HTML, GET filtering, binary multipart, stale CSRF retention, non-replayed redirects, isolated SSO destination, PDF streaming and logout ran against the synthetic service; no member was silent.
- 2026-10-26: The saved collection passed seven requests and seven assertions, including the SSO destination. This checks the local fixture contract, not live BSI mutations.
<!-- end:observations -->
