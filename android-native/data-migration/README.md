# Non-destructive backup intake — stage 1

Pursuit Iron's existing backup panel is not the persistence authority. The App callbacks own backup preview, merge, compatibility, and Undo decisions; `modules/app-storage-ownership.js` owns the browser's exclusive storage lock. The native app **must not** attempt to open or mutate browser localStorage or reproduce merge decisions from guessed schemas.

This folder provides a fail-closed inspection tool for a user-supplied backup **copy**. It never writes a backup, imports it into Android storage, or silently changes records. Because the full production serializer/schema has not yet been certified, inspection is not migration approval.

Usage: `node android-native/data-migration/inspect-backup.mjs /path/to/backup.json`. Do not commit real backups, notes, injury details, or personal records. The tool emits structural field names and counts only; it does not print values or identifiers. Keep the backup outside the repository.

Next: map exact web serializer and restore validation, then implement schema-specific import adapters and golden round-trip tests. No Room database migration or import action until those tests pass.
