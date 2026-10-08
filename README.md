# Essence Ascendance

A Minecraft progression mod for Fabric and NeoForge. Consume items to ascend your
character and permanently improve combat, mobility, defense, and other abilities.

## Compatibility

- Minecraft **1.21.1**, Java **21**.
- **Fabric:** Fabric Loader 0.19.3 or newer, Fabric API, and Architectury API
  13.0.11 or newer for Minecraft 1.21.1.
- **NeoForge:** NeoForge 21.1 and Architectury API 13.0.11 or newer for
  Minecraft 1.21.1.
- Install the appropriate loader's jar on **both client and server**.
- JEI integration is optional; JEI is not required to run the mod.

## Downloads and support

Verified releases will be distributed through CurseForge (project `1732850`),
[Modrinth](https://modrinth.com/mod/essence-ascendance), and
[GitHub Releases](https://github.com/mistaboom/essence_ascendance/releases).
New project listings may remain unavailable while awaiting their first release
and platform review.

Use the **Fabric** jar for Fabric or the **NeoForge** jar for NeoForge; install one
loader variant per instance. Put it and the required dependency mods into the
instance's `mods` folder. Use dependency files for the same Minecraft version and
loader. Development, source, and common jars are not installable releases.

[Report bugs or request features](https://github.com/mistaboom/essence_ascendance/issues).
Include the mod version, Minecraft version, loader, and relevant logs when reporting
a problem.

## Development and releases

With Java 21 installed, build and check both loader variants:

```powershell
./gradlew.bat :common:check :fabric:build :neoforge:build
```

On macOS/Linux, use `bash gradlew` instead of `./gradlew.bat`.
Production jars are written to `fabric/build/libs` and `neoforge/build/libs`.

GitHub Actions runs the checks on pull requests and updates to `main`. Publishing
a GitHub Release starts automated distribution to all three platforms.
See [the release guide](docs/RELEASING.md) for versioning, one-time setup, and recovery.

## License

[MIT](LICENSE). Both distributable jars include the license notice.
