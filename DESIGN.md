# MyBest Android

Direction: port the supplied BSI MyBest portal, using its observed identity and content.
ENERGY 1 / RHYTHM 1 / MOTION 1. Students need their next class, learning materials and assignments.

- Organic restyle (Claude Design handoff "MyBest Android · Organic"): cream ground #F5EAD8, sand surface #EBDDC5, ink #201E1D, #645C50 secondary text.
- Terracotta #C67139 marks the one primary action per screen; sage #56633F carries Student V2. Tokens live in `res/values/colors.xml`.
- Caprasimo for titles and buttons over Figtree for body text, bundled in `res/font` (OFL); sizes are sp so platform font scaling still applies.
- Over-rounded shapes: 32dp cards, pill buttons, inputs and list rows, soft circles on the welcome, account, Student V2 and login heroes. Lucide icons at stroke 2.75.
- One vertical list replaces wide desktop tables, preserving real schedules and server-provided actions.
- At least 48dp controls, wrapping labels, scrollable forms, system insets and a 720dp content maximum serve phones and tablets.
- A fixed light theme. No decorative animation, invented statistics or avatars.
- The login screen uses the "MyBest" wordmark in Caprasimo instead of the portal logo image.
- Academic and profile forms use native controls reconstructed from the current server form, preserving CSRF, hidden context, file rules and validation. Disclosure buttons replace web modals; no e-learning WebView or browser fallback is used.
- Student V2 uses the same native list and form patterns, with its own login and session. Search and incremental rows keep large academic lists usable on phones. The exam portal remains outside this version's scope.
- Official Student V2 SSO connects to MyBest without storing SSO tokens. Server eligibility notices appear as text, including services not currently available for the account.
