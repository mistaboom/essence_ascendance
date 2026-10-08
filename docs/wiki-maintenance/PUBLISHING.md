# Maintaining and publishing the wiki

The maintained page sources are `docs/wiki/*.md` in this repository. The live wiki is a separate Git repository at https://github.com/mistaboom/essence_ascendance.wiki.git. Changes to the main repository do not automatically publish wiki pages.

## Review before publishing

1. Read applicable repository instructions and inspect Git status. Preserve unrelated work.
2. Update `AUDIT.md` when commands, configuration schemas, balance behavior, Archive presentation, integrations or supported versions change. Reinspect registrations **and handlers**, parser defaults/ranges, lifecycle/override order, provider loader/version gates and active-server presentation.
3. Update the affected canonical pages, command index, `_Sidebar.md`, `_Footer.md` and version/source reference together. Distinguish release behavior from unreleased work. Keep pack-dependent values in the Archive/reports.
4. Check every executable branch, argument/example, input key, cross-page URL/anchor, filename and source link. Parse TOML examples and compare them with the mod's bounded grammar and typed constraints. Check for private data, placeholders and unsupported promises.
5. Commit only reviewed documentation to the main repository and push under its contribution rules. Do not stage unrelated changes or run gameplay commands to verify prose.

## Synchronize the separate wiki checkout

Use a checkout outside the mod repository. These ordinary Git commands contain no credentials and can be run from the chosen parent directory:

```console
git clone https://github.com/mistaboom/essence_ascendance.wiki.git essence-ascendance-wiki
```

For either a new or reused checkout, inspect it before copying:

```console
git remote -v
git status --short --branch
git log -5 --oneline
git ls-remote --symref origin HEAD
git pull --ff-only
```

Run those inspection commands **inside the wiki checkout**. Determine the remote's actual default branch rather than assuming `main` or `master`. Stop and reconcile local work or diverged history before continuing.

Copy each reviewed `docs/wiki/*.md` file into the wiki checkout's root, with the same filename. Include `Home.md`, `_Sidebar.md` and `_Footer.md`. Inspect existing versions first and incorporate any relevant live edits into the canonical sources. Preserve other pages; do not blanket-delete the checkout or mirror-delete remote content.

```console
git diff --check
git diff --stat
git diff
git status --short
```

New files do not appear in an unstaged diff, so also inspect their content before staging. Stage only the reviewed page files, review the staged diff, commit with a descriptive documentation message, then use `git push` on the inspected default branch. Never force-push. Check the push result and remote commit.

If the wiki has never been initialized, an authorized maintainer must use GitHub's **Create the first page** flow to save the reviewed Home page once. Then clone and inspect its new history, and publish the remaining pages through Git. Do not add tokens to remote URLs or commit credentials.

## Verify the published result

Open https://github.com/mistaboom/essence_ascendance/wiki and check:

- Home and its audience routes;
- sidebar and footer on several pages;
- a detailed command page, including syntax tables and warnings;
- Configuration, including a TOML example and heading anchors;
- Procedural Balance and Balance Generation, including Mermaid diagrams;
- source/release/issue links and the documented version scope.

If formatting or links need repairs, edit the canonical sources, commit/push both repositories normally and recheck the live result. Record verification as documentation/render checks; it does not establish fresh gameplay, jar reproduction, performance benchmarks or universal compatibility.
