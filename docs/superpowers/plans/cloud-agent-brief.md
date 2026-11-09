# Cloud agent brief — WithDriving theme overhaul

Every cloud agent working on this repository reads this first, then the plan
(`docs/superpowers/plans/2026-11-09-theme-overhaul.md`) and the spec
(`docs/superpowers/specs/2026-11-09-theme-overhaul-design.md`). The spec is in Korean; its tables are the source of
truth for names, steps and colours.

## 1. Environment (Linux container)

The project is an Android (AAOS) app: Kotlin 2.0.21, AGP 8.5.2, Gradle 8.9 (wrapper), JDK 17, compileSdk 34.
Snapshot tests use Paparazzi 1.3.5 and run on the JVM. No emulator is needed.

```bash
# JDK 17 (skip if `java -version` already says 17)
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk-headless unzip
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# Android SDK (cmdline-tools → platform 34 + build-tools 34)
export ANDROID_HOME=$HOME/android-sdk
mkdir -p $ANDROID_HOME/cmdline-tools && cd /tmp
curl -sSLo clt.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q clt.zip -d $ANDROID_HOME/cmdline-tools && mv $ANDROID_HOME/cmdline-tools/cmdline-tools $ANDROID_HOME/cmdline-tools/latest
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"
cd - && echo "sdk.dir=$ANDROID_HOME" > local.properties
```

- Pleos SDK artifacts come from `https://nexus-playground.pleos.ai/repository/maven-releases/` (public, no login).
- `build.ps1` is a Windows helper: do not use it. Run `./gradlew` directly.
- Run Gradle with `--no-daemon --console=plain` and at most one Gradle build at a time per container.

## 2. Commands

| Purpose | Command |
|---|---|
| All unit + snapshot tests | `./gradlew testDebugUnitTest` |
| One test class | `./gradlew :character:testDebugUnitTest --tests "io.vroomi.character.render.calibre.CalibreSnapshotTest"` |
| Record snapshots (PNG files) | `./gradlew :character:recordPaparazziDebug --tests "<class>"` → `character/src/test/snapshots/images/*.png` |
| App module snapshots | `./gradlew :app:recordPaparazziDebug --tests "<class>"` → `app/src/test/snapshots/images/*.png` |
| Compile check | `./gradlew assembleDebug` |

**Look at every PNG you record** with your image-reading tool. The review gate for every visual task is your own
critical look at the images against the spec. Paparazzi stores images at most 1000 px wide, so for fine detail use the
1000 × 1000 detail tests.

Commit the recorded PNGs that your task names into `docs/screenshots/` (copy, file name = the snapshot's short name,
e.g. `calibre_lv3_day.png`). Do not commit `src/test/snapshots/`.

## 3. Rules

- Work only on the branch you were given: create it from `main` (`git checkout -b <branch>`), commit there, and push it
  (`git push -u origin <branch>`). Never push to `main`.
- Touch only the files your task lists. If you truly need a shared file (SceneTheme, ThemeCatalog, ThemeRegistry,
  Ambience, Entrances, strings.xml, SettingsStore, SceneOptions), stop and say so in your report instead of editing it.
- Commit author is already configured by the repository owner's setup. If git asks, use
  `git config user.name "Kyle-ChoiKH"` and `git config user.email "253865254+Kyle-ChoiKH@users.noreply.github.com"`.
- End every commit message with the line: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
- Never commit secrets (`keystore.properties`, `signing/*.jks` are git-ignored; keep it that way).
- Global constraints in the plan are hard rules:
  - car is read-only
  - FlashGuard ≤ 3 pulses/s per region
  - no forbidden words
  - in-scene text ≥ 24 sp while driving
  - avoid the ChromeZones corners
  - 110 km/h cap
  - code-drawn art only
  - IP rules (no brand watch shapes, no Gundam elements, no "Nixie")
  - "realistic-ish, never over-rendered"
- Every test must pass before you push: `./gradlew testDebugUnitTest` (whole project, not only your module).

## 4. Report (your final message)

1. Branch name and the last commit hash.
2. Files changed (list).
3. Tests: the command you ran and its result line (`BUILD SUCCESSFUL`, number of tests).
4. Snapshots: the file names you committed to `docs/screenshots/`, and for each upgrade step one line saying what is
   new compared with the previous step as it actually appears in the image.
5. Anything you could not do, or any shared file you needed but did not touch.
