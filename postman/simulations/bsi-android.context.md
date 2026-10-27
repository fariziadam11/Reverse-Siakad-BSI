# bsi-android

Created and verified 2026-10-26. One local mock, `bsi`, models the BSI HTML contract using synthetic fixtures under `app/src/test/resources/bsi`.

## Purpose

Exercise the Android adapter's real HTTP requests and cookie handling without using a real account. The handler is local-only and reads fixture files from this repository. No owning-team mock was searched because the Postman CLI session is signed out.

## Execution

Start `postman simulation run postman/simulations/bsi-android.sim.yaml --port 4900 --no-history --output ndjson`. The observed listening address was `http://localhost:4900/bsi`. Native-form work also used the same simulation on port 4901 to avoid a concurrent server.

Set `BSI_SIMULATION_ORIGIN=http://127.0.0.1:4900/bsi`, then run `gradlew.bat :app:testDebugUnitTest --no-configuration-cache`. Run `postman collection run "postman/collections/Android Session Checks" --no-report-events` for the saved contract assertions. Stop the local simulation with Ctrl+C.

For the new native-form collection use `postman collection run "postman/collections/Native Form Checks" --env-var "baseUrl=http://127.0.0.1:4900/bsi" --no-report-events`; its default points at the separately verified port 4901.

## Assumptions

- 2026-10-26: Request shapes and HTML structure come from live inspection; account values, IDs, CAPTCHA answer and error responses are synthetic. A passing local test does not establish real attendance or submission behavior.
- 2026-10-26: The mock's redirect prefix is `/bsi`, matching simulation path routing. Removing that prefix makes redirects target the simulation router instead of the mock.
- 2026-10-26: The mock must keep `interceptRequests: false` to receive raw binary multipart bytes. Its handler parses raw bodies, retains its own cookie sessions and catches async errors. The CLI's interception layer decodes multipart as UTF-8 text and cannot preserve arbitrary PDF bytes.
- 2026-10-26: JVM HttpURLConnection suppresses restricted Origin headers; Referer, fields, cookie rotation and binary payload are verified by the local tests. The client sets Origin for the Android runtime, but the JVM tests do not claim that header was transmitted.

## Observations

<!-- begin:observations -->
- 2026-10-26: Session recovery run on port 4914 passed eight client integration tests and the eight-request/eight-assertion Android Session Checks collection. A stale-token rejection preserved authentication, and the client sent the rejected mutation once. The only member, `bsi`, served the traffic; no member was silent.
- 2026-10-26: Native form run passed 10 new real-HTTP integration tests plus all 6 existing client tests with zero skipped/failing tests. PDF payload SHA-256, duplicate fields, hidden method override, 422 HTML, streamed GET/POST downloads, filename sanitization, redirect isolation and non-replay of POST were exercised. Only the `bsi` member exists and it served all traffic.
- 2026-10-26: `Native Form Checks` passed 9 requests and 9 assertions against the local synthetic service; it exercised profile forms, a profile mutation, assignment URL submission, optional-file multipart discussion, POST-only PDF and server-controlled class availability. These writes did not touch BSI production.
- 2026-10-26: The single `bsi` member served all six client integration tests, including login cookie rotation, invalid CAPTCHA, CSRF expiry, expired-session recovery, external redirect isolation, logout and 503 handling. No member remained unused.
<!-- end:observations -->

## What didn't work

- 2026-10-26: The first collection run lost authenticated state after redirect handling: schedule returned login HTML. Explicit Cookie headers and capture of the POST response's rotated cookie fixed it. The JVM client's cookie-store tests passed independently.
- 2026-10-26: The first native-form test run had 2 failures: a JVM Origin-header assertion and a binary multipart digest mismatch in the Postman interceptor. Restricting the first claim to the transmitted Referer and disabling text interception for the raw mock resolved them; the subsequent 16-test run and 9-assertion collection passed.
