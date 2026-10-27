# MyBest Android

Direction: port the supplied BSI MyBest portal, using its observed identity and content.
ENERGY 1 / RHYTHM 1 / MOTION 1. Students need their next class, learning materials and assignments.

- Navy #1E3A8A comes from the authenticated dashboard and identifies primary navigation.
- Purple #5B21B6 echoes the observed MyBest login and is reserved for sign-in.
- White surfaces and #F5F7FB separate the page from controls; #475569 is secondary text.
- Android sans typography follows platform font scaling and keeps long course names readable.
- One vertical list replaces wide desktop tables, preserving real schedules and server-provided actions.
- At least 48dp controls, wrapping labels, scrollable forms, system insets and a 720dp content maximum serve phones and tablets.
- A fixed light theme matches the supplied portal. No decorative animation, invented statistics, avatars or new logo.
- Original BSI logos are reused from the supplied portal; app text is the fallback.
- Academic and profile forms use native controls reconstructed from the current server form, preserving CSRF, hidden context, file rules and validation. Disclosure buttons replace web modals; no e-learning WebView or browser fallback is used.
- Student V2 uses the same native list and form patterns, with its own login and session. Search and incremental rows keep large academic lists usable on phones. The exam portal remains outside this version's scope.
- Official Student V2 SSO connects to MyBest without storing SSO tokens. Server eligibility notices appear as text, including services not currently available for the account.
