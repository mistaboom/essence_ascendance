# Publishing Essence Ascendance

GitHub is the primary repository. CurseForge (project `1732850`) and Modrinth
(project `zIJXnoE8`) distribute the mod; GitHub Releases holds the same jars and
their SHA-256 checksums. Builds, releases, and project description updates run
through GitHub Actions.

## One-time setup

1. Use the canonical repository at
   https://github.com/mistaboom/essence_ascendance. For a new checkout, clone
   https://github.com/mistaboom/essence_ascendance.git.
2. Confirm this checkout's `origin` points to that GitHub Git URL before pushing.
   Keep `main` and release tags on GitHub, preserving their history. The maintained
   wiki sources are `docs/wiki/`; publish them to the separate GitHub wiki using
   [the wiki publishing guide](wiki-maintenance/PUBLISHING.md).
3. Enable GitHub Actions. Install GitHub CLI if needed, then run `gh auth login`
   and authenticate to GitHub with permission to push code and workflows and
   manage this repository's Actions secrets.
4. Run the following from the repository root in PowerShell, substituting the
   actual owner and repository:

   ```powershell
   ./tools/release/configure-secrets.ps1 -Repository OWNER/essence_ascendance
   ```

   This copies Windows environment variables `CURSEFORGE_ESSENCE_ASCENDANCE` and
   `MODRINTH_ESSENCE_ASCENDANCE` into encrypted GitHub Actions repository secrets
   with the same names. Values are sent through stdin, never printed or committed.
   Alternatively, add the two secrets under **Settings → Secrets and variables →
   Actions**. Local Windows variables are not available on GitHub-hosted runners.
5. Confirm the CurseForge key is an **author upload API token**, not a Studios
   discovery API key, and its account can upload to project `1732850`. Confirm the
   Modrinth token can **create versions** (`VERSION_CREATE`) and **read projects**
   (`PROJECT_READ`), including your draft project, and **edit projects**
   (`PROJECT_WRITE`) for description sync. The pipeline does not need
   permission to delete versions or read account email, and disables unfeaturing
   older versions so version-write permission is unnecessary.
6. Complete both project listings: description, icon/screenshots, categories,
   client/server compatibility, license, and links to GitHub source and issues.
   This repository and the mod metadata use **MIT**, and all four jars include the
   license notice. Keep both project listings consistent with that license. Submit the
   projects for review when the first suitable release is ready. Upload automation
   does not bypass either platform's moderation or approval requirements.
7. Run the **CI** workflow and confirm it succeeds on GitHub. Its downloadable
   `release-artifacts` bundle contains exactly the four production jars, checksums,
   release notes, and build metadata. CI never uploads to mod sites.

## Each release

1. Set `mod_version` in `gradle.properties` and commit/push the release-ready code.
   Use `1.0.0` for stable, `1.0.0-beta.1` for beta, or `1.0.0-alpha.1` for alpha.
   The version at the release's tagged commit is authoritative.
2. Wait for **CI** to pass and perform the relevant in-game acceptance checks.
   The automated Gradle checks do not replace testing actual gameplay on both
   loaders. A GitHub Release should be published only for a build ready to ship.
3. Create a GitHub Release at that tested commit with tag `v` plus the exact mod
   version, e.g. `v1.0.0-beta.1`. Add player-facing release notes. Check
   **Set as a pre-release** for alpha/beta versions; leave it unchecked for stable.
   Save a draft while preparing. Publishing the release starts distribution.
4. The **Publish release** workflow checks version/tag/notes, runs Gradle on Java
   25 with the declared target toolchains, runs `:checkSupportedTargets` (including
   all supported versions' normal checks and archive audits), and validates/stages
   all declared runtime jars. Only after all
   checks pass do its separate jobs attach downloads to GitHub and upload each
   loader's jar to CurseForge and Modrinth. The eight mod-site jobs run one at a
   time; GitHub download attachment remains independent. Do not attach jars manually.
5. Check that all nine publishing jobs succeed: GitHub downloads plus four target uploads on each mod site. Watch platform review status after upload.

Publishing a GitHub Release makes its announcement visible before the jars finish
building; allow the workflow to complete before announcing download availability.
Pushing commits or tags alone does not upload jars. Editing existing release notes
does not re-publish files.

## Shared multiversion release preparation (2026-10-22)

`gradle/targets.json` is the target authority. `gradle/production-targets.gradle` binds each declared target to its real archive provider; `:writeProductionManifest` writes exact paths and SHA-256 hashes. `tools/release/prepare.py` checks the complete declared set, its metadata and hashes, and stages a fresh directory with the four JARs, `SHA256SUMS`, `CHANGELOG.md` and variant-rich `metadata.json`. The workflows derive their publishing matrix from the validated staged variants.

From the repository root in PowerShell, with Java 25 available to Gradle and Java 21 available as a toolchain:

```powershell
# Fresh production outputs; run separately so fixture worlds and audit logs survive.
./gradlew.bat :cleanProductionOutputs --console=plain
./gradlew.bat :checkSupportedTargets --console=plain
python -m unittest discover -s tools/release -p "test_*.py"
python tools/release/prepare.py --stage build/release
```

The shared CI build installs the distinct Java versions declared in the catalog and
uses the highest one for Gradle. Local staging never invokes publishing or
description synchronization. The current workflow edits have been audited locally;
they require a normal pushed CI run before they count as remote execution evidence.

For a focused artifact refresh, use `./gradlew.bat :writeProductionManifest :assetJarInvariants1211 :assetJarInvariants2612 --console=plain`. Gradle rebuilds changed sources, resources and archives; use a new empty staging destination for each preparation.

| Target | Production provider | Verified output path | SHA-256 |
| --- | --- | --- | --- |
| 1.21.1 fabric | `:fabric:1.21.1:remapJar` | `fabric/versions/1.21.1/build/libs/essence_ascendance-1.0.0-beta.11+mc1.21.1-fabric.jar` | `0a873e29061a7ef6ba217423909e660ecd2832740889bef116b2a3341808e590` |
| 1.21.1 neoforge | `:neoforge:1.21.1:remapJar` | `neoforge/versions/1.21.1/build/libs/essence_ascendance-1.0.0-beta.11+mc1.21.1-neoforge.jar` | `b1facb89ccd651d65f938b4d8b948b9f66de77812f27c66e40830e2a9c194ef1` |
| 26.1.2 fabric | `:fabric:26.1.2:shadowJar` | `fabric/versions/26.1.2/build/libs/essence_ascendance-1.0.0-beta.11+mc26.1.2-fabric.jar` | `cae1fd77455702652ca1b45d79128a498a30506e2e119919ff292dc5b7384bd0` |
| 26.1.2 neoforge | `:neoforge:26.1.2:shadowJar` | `neoforge/versions/26.1.2/build/libs/essence_ascendance-1.0.0-beta.11+mc26.1.2-neoforge.jar` | `6154f47c7357b2998d3e74cf322238cde5dfdcc8553076200272f18d8a91675a` |

The final local clean gate and fresh `build/release-final-audit` bundle are recorded in the auxiliary `VALIDATION.md` independent-audit section. These hashes are from that clean gate; historical hashes in older handoff sections are superseded.

A future target addition updates `gradle/targets.json`, including `targets`, `release.requiredTargetIds`, the Java/dependency profile, reusable `nativeSources`, `resourceFormat`, `nativeAccess`, and platform dependency data when needed. It also supplies genuine native source/resource and mixin/access boundaries, then verifies the production provider and shared audit. The release script and workflow matrix enumerate the declared targets and refuse a missing member of the required release set.

The publishing inputs preserve required Architectury API, required Fabric API on Fabric, and optional JEI on both sites. CurseForge also lists optional FTB Teams using its separate Fabric and NeoForge project IDs. No official FTB Teams Modrinth project was established, so the Modrinth payload contains the other verified dependencies. This relationship affects listing metadata; the JAR's guarded FTB integration remains optional.

Automated packaged server checks and native fixtures are recorded in the handoff. Interactive client visuals, live multiplayer and restart acceptance remain prerequisites for publishing. GitHub and mod-site publishing also requires the configured platform tokens and approved project listings; these were not accessed here.

## Failure recovery

The beta.3 and beta.4 initial NeoForge Modrinth jobs failed with HTTP 500
`database_error` / `inserting version dependencies` / `deadlock detected`.
Both uploaded the unchanged verified artifact successfully on a failed-job rerun.
This is a Modrinth database failure, not evidence of a bad NeoForge jar or missing
token. Fabric/NeoForge jobs were scheduled in parallel, with overlapping upload
activity in beta.3. The release matrix now uses `max-parallel: 1` to remove that
source of contention. Beta.4's upload calls did not overlap, so concurrent jobs
are a contributing possibility, not a proven explanation of every deadlock.
Serialization cannot prevent unrelated Modrinth database contention; its live
effect remains to be checked on the next release. Publish one release at a time.

Evidence: [beta.3 failed job](https://github.com/mistaboom/essence_ascendance/actions/runs/38199052259/job/114650075807),
[beta.4 failed job](https://github.com/mistaboom/essence_ascendance/actions/runs/38307199091/job/115007695050),
and [beta.4 successful rerun](https://github.com/mistaboom/essence_ascendance/actions/runs/38307199091/job/115008859486).
Automatic blind upload retries remain disabled. Before any manual retry, inspect
the authenticated Modrinth file list; a project awaiting moderation can return
404 to unauthenticated reads even when its versions exist.

- If build/validation fails, no jars are uploaded. Fix the issue and use a new
  release version/tag for changed code. Do not move an already published tag.
- If an individual site/loader job fails, inspect its logs and the platform's file
  list for the exact `<mod_version>+mc<minecraft>-<loader>` identity. Use **Re-run
  failed jobs** only after confirming that upload did not already succeed.
  Successful jobs are left alone. Do not re-run the whole workflow after any
  mod-site upload succeeds: those APIs may create duplicate files/versions.
- An upload can succeed even if its HTTP response is lost. Automatic publishing
  retries are disabled. If a file already exists, keep it and resolve the failed
  status without creating another upload; compare the filename/checksum as needed.
- Missing/expired token: update the Windows variable, re-run the secret setup
  script, and retry only the failed publishing job after checking for duplicates.
- Draft/rejected listing: finish the site's review steps. Uploading a jar and
  making the project searchable are separate steps.
- Existing artifacts expire after 30 days. If recovery must happen later, inspect
  which uploads already exist before rebuilding; never silently replace a released
  jar with one rebuilt from changed dependencies.

The GitHub asset job and eight mod-site jobs are independent after the shared build
gate. A failure can leave some destinations published while others are pending;
publication across the three platforms is not atomic. Match filenames and checksums
against `metadata.json` and `SHA256SUMS` before recovering an individual job.

## Maintenance and security

### Shared project description

Edit **README.md on `main`** to update the player-facing description. GitHub renders
it directly. The **Sync project descriptions** workflow publishes that same Markdown
to Modrinth and CurseForge, using the existing encrypted repository secrets. It
runs when the README, sync script, or sync workflow changes, and can also be run
manually from Actions on `main`. A rerun reads the current `main` README.

Each site has its own job so one failure does not prevent the other update. The
workflow changes only the long description; it does not upload jars, edit existing
releases, alter categories, or change moderation status. It has read-only GitHub
permissions and does not run with secrets on pull requests.

Modrinth uses its documented project PATCH endpoint and verifies the saved body.
CurseForge uses the author API's `update-project` multipart endpoint with
`descriptionType: markdown`; this endpoint was tested successfully against project
1732850 on 2026-10-08, including checking the saved rendering in the author editor.
It is not listed in the official upload API guide, so a future platform change may
require updating this script. A failed job is visible in Actions; there is no silent
fallback to a different publishing API. Description replacement is idempotent and
can be rerun after resolving an error.

Use Markdown headings, lists, emphasis, and absolute HTTPS links. Keep development
instructions in `docs/DEVELOPMENT.md` and publishing instructions here. Screenshots
can be added to the README later; use publicly accessible absolute image URLs so
they render on all three sites. Changes made directly in either site's description
editor will be overwritten by the next README sync.

### Workflow security

Third-party actions are pinned to full commit hashes. Dependabot proposes weekly
action updates; review and merge these after CI passes. Workflow permissions default
to read-only. Only the GitHub download job gets `contents: write`; publishing tokens
are passed to the relevant release upload step. PR CI receives no publishing secrets
and does not use `pull_request_target`.

The Gradle distribution has a verified SHA-256 checksum, and setup-gradle validates
the Gradle wrapper. Review changes to workflows, Gradle files, and release tooling as
part of code review. Consider branch protection/rulesets requiring successful **CI**
and restricting who can publish releases or change release tags.

The current build uses Architectury snapshot plugins. Dependency versions should be
stabilized or locked with the mod-development work before claiming byte-for-byte
reproducible builds. This workflow publishes a single verified artifact set to all
sites; it does not claim that rebuilding the same commit months later yields the
same jar.

## References

- [mc-publish documentation](https://github.com/Kira-NT/mc-publish)
- [CurseForge author upload API](https://support.curseforge.com/support/solutions/articles/9000197321)
- [Modrinth create-version API](https://docs.modrinth.com/api/operations/createversion/)
- [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [GitHub Actions security](https://docs.github.com/en/actions/reference/security/secure-use)
