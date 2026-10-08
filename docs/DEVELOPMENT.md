# Development

The root README is the shared player-facing project description. Keep build,
publishing, and maintainer instructions in `docs/` so they do not appear in mod
store descriptions.

With Java 21 installed, build and check both loader variants:

```powershell
./gradlew.bat :common:check :fabric:build :neoforge:build
```

On macOS/Linux, use `bash gradlew` instead of `./gradlew.bat`.
Production jars are written to `fabric/build/libs` and `neoforge/build/libs`.

GitHub Actions runs the checks on pull requests and updates to `main`. Publishing
a GitHub Release starts automated jar distribution to GitHub, CurseForge, and
Modrinth. See [the release guide](RELEASING.md) for versioning, setup, and recovery.
