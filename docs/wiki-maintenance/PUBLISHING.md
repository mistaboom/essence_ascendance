# Maintaining and publishing the wiki

**Edit only `docs/wiki/*.md` in this repository.** These are the maintained originals. GitHub serves the live wiki from a separate repository at https://github.com/mistaboom/essence_ascendance.wiki.git, but no permanent second local checkout is needed. Pushing the main repository alone does not publish wiki pages.

**Historical October 13 beta.5 refresh:** the multiplayer implementation, Archive and wiki were published together. That refresh targeted beta.5 and documented its new-world requirement. Earlier local-only statements in [PASSDOWN.md](PASSDOWN.md) retain their historical scope; use [PUBLICATION.md](PUBLICATION.md) for publication receipts. Preserve unrelated work and preview the selected pages before publishing.

**October 18 beta.9 publication:** the canonical balance/configuration pages retain the beta.8 system and now target beta.9, including the glaive/Archive additions. The user authorized committing all current implementation, Archive and wiki changes and publishing through the existing release pipeline and wiki publisher. Review the source diff and the wiki preview before publishing; a repository push alone does not update the live wiki. Earlier dated release/publication receipts remain historical.

## Review before publishing

1. Read applicable repository instructions and inspect Git status. Preserve unrelated work.
2. Update `AUDIT.md` when commands, configuration schemas, balance behavior, Archive presentation, integrations or supported versions change. Reinspect registrations **and handlers**, parser defaults/ranges, lifecycle/override order, provider loader/version gates and active-server presentation.
3. Update the affected canonical pages, command index, `_Sidebar.md`, `_Footer.md` and version/source reference together. Distinguish release behavior from unreleased work. Keep pack-dependent values in the Archive/reports.
4. Check every executable branch, argument/example, input key, cross-page URL/anchor, filename and source link. Parse TOML examples and compare them with the mod's bounded grammar and typed constraints. Check for private data, placeholders and unsupported promises.
5. Commit only reviewed documentation to the main repository and push under its contribution rules. Do not stage unrelated changes or run gameplay commands to verify prose. Selected wiki pages must be tracked and committed; unrelated working-tree changes do not block the publisher.

## Preview and publish

Run from the repository root in PowerShell. Git and your existing GitHub Git authentication are required. The script does not create credentials or use release-site tokens.

Preview the complete maintained page set:

```powershell
./tools/wiki/publish.ps1
```

Read the diff before publishing. The comparison starts from the wiki's current default branch, so it includes direct edits made through GitHub. Incorporate any useful wiki-side edits into `docs/wiki/`, commit/push the canonical changes, and preview again before replacing overlapping pages.

Publish the reviewed set explicitly:

```powershell
./tools/wiki/publish.ps1 -Publish -Message 'Update reviewed wiki documentation'
```

For a focused change, select exact filenames. Repeat the same selection for preview and publish:

```powershell
./tools/wiki/publish.ps1 -Pages Version-and-Maintenance.md
./tools/wiki/publish.ps1 -Pages Version-and-Maintenance.md -Publish -Message 'Update wiki maintenance instructions'
```

`-Pages` accepts one or more existing Markdown filenames, including `Home.md`, `_Sidebar.md` and `_Footer.md`. Without it, all root Markdown files in `docs/wiki/` are selected. Include navigation files when changing page names or links. A removed source file does not delete a published page: review removals separately through normal wiki Git maintenance.

The publisher:

- creates a unique temporary checkout under the operating system's temporary directory;
- discovers the wiki's actual default branch and preserves its existing history;
- copies and stages only selected pages, preserving all other wiki files;
- checks for uncommitted source changes and whitespace errors, and displays the proposed diff;
- pushes only when `-Publish` is supplied, using an ordinary push;
- verifies the remote commit and removes its temporary checkout after a successful preview, publication or no-change check.

Publishing is manual. There is no scheduled job, automatic rewrite or background synchronization. The published wiki remains a separate remote copy; `docs/wiki/` is the sole permanent local editing location.

## Failure and recovery

If authentication fails, sign in through your existing GitHub Git setup and retry. Never put tokens in remote URLs. An uninitialized wiki needs its first reviewed Home page saved through GitHub's **Create the first page** flow once before Git cloning can work.

If publication fails, the script reports the retained temporary directory. Inspect it before retrying. A rejected push can mean another editor updated the wiki: run a fresh preview and reconcile those edits in the canonical sources. Never force-push. If a push succeeded but verification failed, inspect the live wiki and remote history first; a fresh run should report no changes when the selected pages already match.

After recovery, remove only the reported temporary directory after verifying its contents are published or otherwise preserved. The publisher checks the resolved absolute cleanup path before removing its own temporary directory.

`-SourceDirectory` and `-WikiRemote` allow focused tests against a disposable local Git repository; ordinary publishing uses their built-in project defaults. They do not select a different credential or configure authentication.

Run the publisher's focused checks without contacting GitHub:

```powershell
./tools/wiki/test-publish.ps1
```

These checks exercise preview without pushing, selective and full publication, default-branch discovery, preservation of wiki-only pages and pending source-code edits, unchanged source staging, no-change publication, invalid inputs, and temporary-checkout cleanup.

## Verify the published result

Open https://github.com/mistaboom/essence_ascendance/wiki and check the changed pages. For a full publication, also check:

- Home and its audience routes;
- sidebar and footer on several pages;
- a detailed command page, including syntax tables and warnings;
- Configuration, including a TOML example and heading anchors;
- Procedural Balance and Balance Generation, including Mermaid diagrams;
- source/release/issue links and the documented version scope.

If formatting or links need repairs, edit the canonical sources, commit/push normally, and republish using the script. Record verification as documentation/render checks; it does not establish fresh gameplay, jar reproduction, performance benchmarks or universal compatibility.
