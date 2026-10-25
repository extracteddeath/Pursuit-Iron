Pursuit Iron — Build 906
App 4.0.0 / Engine 0.65.9 / M320 adaptive-cycle certification

Release highlights
• Upgrade an existing custom program or saved fixed training cycle to future adaptive training blocks without erasing current work, history or manual set edits.
• Goal-optimized splits prioritize strength lift practice and preserve useful muscle training volumes across hypertrophy and strength.
• Select the most appropriate audited hypertrophy→strength finalist using actual executable weekly sets and muscle/lift coverage, not only the initial generator score.
• Continue or convert an authored custom training cycle, including history-aware advancement, program identity preservation and explicit refusal of unsafe training transitions.
• Reduce duplicate program-generation work. The automated slow-CPU mobile-Chromium benchmark and full regression suite guard the resulting training plans.

Release procedure
• Merge the exact CI-green Build 906 release pull request to main. GitHub Pages auto-publishes main at https://extracteddeath.github.io/Pursuit-Iron/ using its existing Pages workflow; confirm that workflow succeeds for the merge commit.
• Serve from the same Pages origin to retain browser-local workout data. Export a personal backup before upgrading; data cleared from the device cannot be recovered from this repository.
• Check the app's About/build display for 906. The content-addressed service-worker revision updates cached code on refresh/restart.
• CI is a browser/engine certification, not hands-on Android testing. Android Back/keyboard, foldables, notifications, workout interruptions and signed TWA install still require physical device testing.
• There is no built or signed .aab in this repository. Google Play submission, Digital Asset Links, signing association and Play Console declarations are NOT part of this web release. See play/README.md.
