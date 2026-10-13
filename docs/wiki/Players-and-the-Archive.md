# Players and the Ascendance Archive

[Home](https://github.com/mistaboom/essence_ascendance/wiki/Home) · [Player commands](https://github.com/mistaboom/essence_ascendance/wiki/Player-Commands)

**Your Ascendance Archive is the gameplay guide for the server you are connected to.** Equipment baselines, Bonus costs, Skill ranks, item yields and other displayed balance values come from its active resolved profile. A table from another pack may describe different values.

## Get your Archive

Normal gameplay builds Category Attunement seals during the Latent onboarding chapter. When enough seals are complete, the player automatically reaches Dormant. The mod delivers one Archive on the first powered tier. Direct operator tier changes can also trigger that delivery.

Delivery first uses inventory space. If your inventory is full, it drops the Archive with immediate pickup and ownership assigned to you. The gift is recorded only after successful delivery, so a failed delivery can retry. Losing or destroying a successfully delivered Archive does not trigger another automatic gift.

Craft a replacement with the shipped shapeless recipe:

```text
1 Raw Latent Ore + 2 sticks → 1 Ascendance Archive
```

The ingredients can be placed in any arrangement. This recipe and the item do not require Dormant. Finding Raw Latent Ore unlocks its recipe discovery advancement. Your server's datapacks can change effective recipes; check recipe browsing when a pack changes them.

An operator can also use `/essence admin player guide give`. See [Archive recovery](https://github.com/mistaboom/essence_ascendance/wiki/Administration-Commands#replace-the-archive).

## Open it

Use the Archive item in either hand. It opens the client screen directly; you do not need to stand near a Nexus or machine. It requires no operator permission or opening command.

Keep the mod and required dependencies installed on your client as well as the server. The screen needs synchronized runtime and player data for environment-specific and personal information.

## Guide, Reference and Search

| View | Use it for |
| --- | --- |
| **Guide** | Six linked lessons that introduce the systems and their relationships |
| **Reference** | Sections, entry lists, detailed articles and related links; browse equipment, machines, Bonuses and Skills |
| **Search** | Find explanations and synchronized Item Yield entries without navigating section by section |

Reference Bonus and Skill lists include entries regardless of whether you own them or can buy them yet. Their visibility in the Archive is not an unlock grant.

Search ignores case and normalizes spacing and Unicode. All query terms must match; title and phrase matches rank higher. Explanatory articles appear before the item group. It is a text search, so use another term when a misspelling produces no result. A loading yield table is distinguished from a completed search with no matches.

## Item Yields

Open **Reference → Essences → Item Yields**. Filter by localized item names, choose Essence filters, switch Any/All matching, and sort columns. Registry IDs are internal navigation identity, not a promised name-filter syntax. Selecting a search result for an item opens its canonical yield entry.

The table uses the server's synchronized active item-yield data. It does not generate an independent client economy. A zero yield can be an intentional supply or conservation decision; an unsupported item is not guaranteed to dissolve. Administrators can investigate the saved reasoning in [item valuation](https://github.com/mistaboom/essence_ascendance/wiki/Item-Valuation-and-Economy).

## Navigation

Follow related, previous and next links within articles. Back and Forward retain up to 32 visits. Scroll with the mouse wheel; Tab moves keyboard focus, and Enter/Space activates focused controls where supported. Escape closes the screen.

Page, scroll, search and yield-filter state are kept in bounded session memory. Reopening in the same valid session can restore them. They are not written into the book or world data, and do not promise persistence through restarting Minecraft or moving to another server.

## Understand the values you see

General concepts, names and registered relationships are common catalog information. Displayed prices, effects, meaningful ranks, equipment baselines, machine policy and yields use the active profile. Ownership, balances, investment and personal eligibility also need ready player synchronization.

**Loading, unavailable and zero mean different things.** The Archive does not substitute bootstrap defaults when the authoritative presentation context is missing. Allow synchronization to finish after joining or a rebuild. If the problem persists, ask the administrator to check installed authority and client compatibility.

## Cooperative play and exact amounts

**Beta.5 multiplayer:** joining shares Stored Essence and infrastructure; Available Essence and invested progression remain personal. Each player has one active incoming Crucible source, while many players/Infusers can share a source. Use the Archive for player operation; administrators have the focused [Teams and shared infrastructure](https://github.com/mistaboom/essence_ascendance/wiki/Teams-and-Shared-Infrastructure) reference. These changes were introduced in beta.5.

The completed October 13 Archive update keeps the existing Guide/Reference structure. Use these actual destinations on a matching beta.5 client/server:

| Need | Archive destination |
| --- | --- |
| Sharing, joining/leaving and cooperative infrastructure | **Reference → Mechanics → Teams and Shared Essence** |
| Stored versus personal currency and incoming selections | **Guide → Essence → Collecting Essence**, then **Reference → Machines → Essence Crucible** |
| Machine network and stable links | **Guide → Machines → Your First Machine Network**; **Reference → Machines → Essence Pylon / Essence Infuser** |
| Explicit equipment Start, Soulbinding and paid-work recovery | **Guide → Infusion → Beginning Infusion**; **Reference → Equipment → Equipment Infusion / Durability and Lifecycle** |
| General allocation, storage and capped investments | **Reference → Mechanics → Allocation and Storage / Over-Cap Investment** |

Search **Teams and Shared Essence** to find the same read-only reference. The article does not manage teams, start channels or display a live roster. Archive browsing and personal receiving toggles are separate controls; native membership uses the machine M popup when available.

Exact Archive values retain fractions. Whole-number half-up item tooltips/JEI and `<1` for positive sub-unit yields are a compact presentation distinction, not different stored currency. Receiving toggles are personal preferences, separate from the Item Yields browser's display filters.

## Quick troubleshooting

| Problem | Next step |
| --- | --- |
| No automatic book | Check whether you reached a powered tier, inventory space and nearby dropped items. Craft a replacement if it was already delivered. |
| Lost book | Craft one; an operator can give a replacement without resetting progress. |
| Screen does not open | Confirm that the correct mod/dependencies are installed client-side; report client errors with versions. |
| Values remain loading | Reconnect after the administrator checks profile install/synchronization. Do not assume that a loading value is zero. |
| Recipe or value differs from this wiki or another server | Use the active Archive and effective recipe browser; packs can resolve different data. |
| Need to see personal progress in chat | Use [player commands](https://github.com/mistaboom/essence_ascendance/wiki/Player-Commands), particularly `status`, `balance` and `attunement`. |

Player Ascension completes current-chapter Attunement seals and consumes no wallet Essence. Buying Bonuses or Skills can accelerate future eligible activity; equipment infusion is a separate process. Use the Guide for the gameplay path rather than treating administrative grants as progression.

Implementation references: [delivery](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/progression/DormantGuidebookService.java), [replacement recipe](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/resources/data/essence_ascendance/recipe/ascendance_archive.json), [screen](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/client/AscendanceArchiveScreen.java), [presentation readiness](https://github.com/mistaboom/essence_ascendance/blob/59e27446afbb6c9e73be38d0d407915fe15354fa/common/src/main/java/com/mistaboom/essence_ascendance/client/presentation/PresentationContext.java).
