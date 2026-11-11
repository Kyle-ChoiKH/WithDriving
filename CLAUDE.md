# WithDriving (위드라이빙) — notes for Claude

**Start here:** read `docs/superpowers/HANDOFF.md`. It has the current state, next steps, blockers and rules for the
in-progress theme overhaul. The spec and plan it points to are the source of truth.

- Talk to the user in plain Korean.
- Be economical with tokens: at most 1–2 agents at a time, cheaper models for reviews and mechanical work, and state the
  expected size before a big job.
- Windows build: `powershell -ExecutionPolicy Bypass -File .\build.ps1 -Work C:\vroomi-build\<X>\Vroomi -Task "<gradle tasks>"`
  (the project path has non-ASCII characters, so the script builds from an ASCII mirror). Linux: `./gradlew`.
- Visual work is accepted only after you look at the recorded Paparazzi PNGs yourself.
- Never push to `main`; one branch + PR per task. Merging needs the user's approval.
- Never commit secrets (`keystore.properties`, `signing/*.jks`) or personal names/contacts.
- IP: no brand watch shapes, no Gundam elements, no "Nixie". Car data is read-only.
