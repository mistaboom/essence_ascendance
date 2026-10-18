# Progression, power and Attunement

[Configuration](https://github.com/mistaboom/essence_ascendance/wiki/Configuration) · [Tuning examples](https://github.com/mistaboom/essence_ascendance/wiki/Worked-Scenarios)

Use the Ascendance Archive for player lessons, prerequisites and the server's current effects. This page explains which pack settings control those systems.

## Equipment, Bonuses and Skills

| System | Starting behavior | Pack-maker control |
| --- | --- | --- |
| Equipment | Latent begins around iron stats; later tiers scale toward measured pack equipment | Global/reference settings and advanced overrides |
| Bonuses | Generated effects over an active range of player tiers | `bonus_settings.toml` |
| Skills | Default unlock layout with generated ranks and effects | `skill_settings.toml` |
| Player Ascension | Category Attunement seals earned through gameplay | Main config's progression and Attunement settings |

The caster is intended as a faster, shorter-range alternative to the bow, with lower damage per shot. Exposed stats do not capture every special weapon or upgrade in a pack, so test the gear players will actually use.

Player tier and equipment tier are separate. A Transcendent player can channel earned Bonuses through intact, eligible Dormant equipment while retaining that equipment's Dormant innate stats. Latent and fractured equipment do not channel Bonuses.

## Automatic power targets

Global `[power]`, `[builds]` and `[budget]` settings guide automatic generation. They are not literal percentages added to every weapon or armor stat.

Under default planning targets, final-tier equipment aims around the measured pack reference. Reference gear with developed Bonuses or Skills is checked around twice that reference, and combined builds around three times it; a limited low-health burst can have more room. These are model targets, not promises of measured combat performance.

Meaningful starting effects and explicit Skill/Bonus settings can exceed the automatic soft budget. Mechanical limits still apply. For a particular effect, edit its settings directly rather than changing global budgets and expecting a precise increase.

Existing generated Skill/Bonus entries contain explicit values and are preserved. They do not all retune just because you raise `power.overall`. Remove the relevant controls to request new automatic defaults, then rebuild; [details](https://github.com/mistaboom/essence_ascendance/wiki/Balance-Generation#automatic-settings-versus-saved-choices).

## Bonus progression

Each Bonus has a first active player tier and a completion tier. It can finish before Transcendent. Higher player tiers retain the completed benefit; the tooltip shows `N/A` for tiers without another active step.

Set `min_tier`, `max_tier`, `bonus_at_max` and `minimum_bonus_at_lowest` in `bonus_settings.toml`. The maximum is exact; the minimum is a lower bound for the first active completed-tier effect. Intermediate steps must remain useful. Purchases between completed checkpoints remain continuous.

Damage Bonuses use added damage points. Other Bonuses can use percent, seconds, blocks or other stated units. Read the unit comment on the generated entry before changing it.

## Skill progression

The default tree supplies unlock tiers. You can change a Skill's first tier, rank count and the first/final value of each listed effect. The mod fills in the ranks between those endpoints, keeping its supported effect steps and validation.

Moving the first unlock does not remove prerequisite Skills, later rank requirements, milestones or exclusive choices. An on/off Skill cannot gain useful numeric ranks merely by increasing `ranks`. A one-rank stat needs equal first and maximum values.

Changing only effects preserves prices for an unchanged rank count. Changing the rank count generates a new cost progression. Review costs as well as effects after rebuilding.

## Changing Ascension pace

Player Ascension uses Category Attunement seals. It does not spend wallet Essence or require purchasing a particular Skill, Bonus or item. Investment in category Bonuses and Skills can accelerate future progress, while ordinary eligible activity still earns base credit.

- Increase `[attunement] pace` to complete seals faster without directly changing Essence prices.
- Adjust `[progression] length` for a broader change to the journey and investment scale.
- Adjust `[progression] cost_pressure` for generated investment prices.
- Use the remaining Attunement controls for repetition, variety and category breadth after reviewing their [descriptions](https://github.com/mistaboom/essence_ascendance/wiki/Configuration#attunement).

Equipment infusion is separate: it upgrades the item through its own costs and machine operation. Point players to the Archive for that workflow. Use a test world to check whether your intended player and equipment progression feel right together.
