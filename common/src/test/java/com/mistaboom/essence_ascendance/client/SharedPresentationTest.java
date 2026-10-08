package com.mistaboom.essence_ascendance.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mistaboom.essence_ascendance.attunement.AttunementSnapshot;
import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.client.nexus.*;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.SkillPurchase;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.network.*;
import com.mistaboom.essence_ascendance.progression.*;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.balance.SkillRankEffectScaling;
import com.mistaboom.essence_ascendance.skill.effect.*;
import com.mistaboom.essence_ascendance.skill.tooltip.SkillTooltipRegistry;
import com.mistaboom.essence_ascendance.stat.*;
import com.mistaboom.essence_ascendance.tier.*;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** End-to-end presentation contracts over generated values, actual components and persistent/network data. */
public final class SharedPresentationTest {
    private static int checks;
    private static JsonObject language;
    public static void main(String[] args) throws Exception {
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> e.printStackTrace(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err))));
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
        EssenceTypes.init(); EssenceStats.init(); AscendanceTiers.init(); MilestoneProviders.init(); Milestones.init(); Skills.init();
        try (var stream = SharedPresentationTest.class.getResourceAsStream("/assets/essence_ascendance/lang/en_us.json")) {
            language = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var runtime = args.length == 0 ? RuntimeBalanceDefinition.bootstrap() : RuntimeBalanceDefinition.fromJson(
                com.mistaboom.essence_ascendance.balance.generated.BalanceProfileStore.read(java.nio.file.Path.of(args[0])).section("runtime"));
        verify(runtime);
    }
    public static void verify(RuntimeBalanceDefinition runtime) throws Exception {
        if (language == null) try (var stream = SharedPresentationTest.class.getResourceAsStream("/assets/essence_ascendance/lang/en_us.json")) {
            language = JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)).getAsJsonObject();
        }
        EssenceConfigManager.installClient(runtime);
        latent(runtime); tooltips(runtime); lockedRankPreview(runtime); tooltipRequirements(); bonuses(runtime); cards(); preferences();
        EssenceConfigManager.clearClient();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out)).println("SharedPresentationTest: " + checks + " checks PASS");
    }

    private static void latent(RuntimeBalanceDefinition runtime) {
        check(!NexusHudControl.progressionAvailable(AscendanceTiers.LATENT.id()), "Latent has only the progress view");
        check(NexusHudControl.progressionAvailable(AscendanceTiers.DORMANT.id()), "Dormant unlocks progression controls");
        var context = SkillEvaluationContext.committed(AscendanceTiers.LATENT.id(), Map.of(), Map.of(), Set.of(), Set.of(), Map.of());
        for (var skill : SkillRegistry.values())
            check(!SkillStateEvaluator.evaluatePurchaseEligibility(skill, context).satisfied(), "Latent cannot buy " + skill.id());
        for (var stat : EssenceStatRegistry.values()) {
            var profile = runtime.config().balanceProfile();
            check(profile.getInvestmentCap(AscendanceTiers.LATENT, stat) == 0, "No Latent bonus slot");
            check(!TierInvestmentPolicy.validTarget(stat, AscendanceTiers.LATENT, profile, 0, 1), "No Latent funding");
            check(!TierInvestmentPolicy.validTarget(stat, AscendanceTiers.LATENT, profile, 10, 0), "No Latent allocation/refund controls");
            check(profile.bonusTrack(stat.id()).checkpoints().stream().noneMatch(p -> p.tierId().equals(AscendanceTiers.LATENT.id()) && p.purchasable()), "Latent is not an active bonus state");
        }
    }

    private static void tooltips(RuntimeBalanceDefinition runtime) {
        check(SkillTooltipRegistry.supportedIds().size() == 90, "Exactly 90 tooltip definitions");
        var presenter = new SkillTooltipPresentation();
        for (var skill : SkillRegistry.values()) {
            int max = skill.maximumRank();
            for (int rank = 0; rank <= max; rank++) {
                var context = SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(),
                        rank == 0 ? Map.of() : Map.of(skill.id(), rank), Map.of(), Set.of(), Set.of(), Map.of());
                var states = SkillStateEvaluator.evaluateAll(context);
                check(SkillTooltipPresentation.unmetPrerequisites(states.get(skill.id()), states, true).isEmpty()
                                && SkillTooltipPresentation.unmetRequirements(states.get(skill.id()), true).isEmpty(),
                        "Shift never shows requirement sections, including unowned and max-rank skills");
                var current = new SemanticTooltip(); presenter.append(current, skill, states, false);
                var preview = new SemanticTooltip(); presenter.append(preview, skill, states, true);
                var released = new SemanticTooltip(); presenter.append(released, skill, states, false);
                if (Math.max(1, rank) < max) {
                    current.hint(SkillTooltipPresentation.rankPurchaseHint(false));
                    preview.hint(SkillTooltipPresentation.rankPurchaseHint(true));
                    released.hint(SkillTooltipPresentation.rankPurchaseHint(false));
                    check(text(current).getLast().equals("Hold Shift to preview the next rank. Shift-click to stage it."), "Normal footer teaches the gesture");
                    check(text(preview).getLast().equals("Next rank preview. Shift-click to stage it.")
                            && text(preview).stream().filter(line -> line.contains("Next rank preview")).count() == 1
                            && text(preview).stream().noneMatch(line -> line.contains("Hold Shift")),
                            "Held Shift replaces the footer in place without duplicate hints");
                }
                check(text(current).equals(text(released)), "Releasing Shift immediately restores " + skill.id());
                int shown = Math.max(1, rank), next = Math.min(max, shown + 1);
                check(text(current).getFirst().equals("Rank " + shown + "/" + max), "Current purchased rank");
                check(text(preview).getFirst().equals("Rank " + next + "/" + max), "Actual next rank, including unowned/max");
                var resolved = SkillRankEffectScaling.applyResolved(runtime.config().skillEffects(), Map.of(skill.id(), next), runtime.skillCurves());
                var expected = SkillTooltipRegistry.lines(skill.id()).stream().map(line -> flatten(line.render(resolved))).toList();
                var paragraphs = preview.lines().stream().filter(line -> line.indentation() == 1).toList();
                check(paragraphs.stream().map(line -> flatten(line.content())).toList().equals(expected),
                        "Prospective native gameplay values in indented paragraphs: " + skill.id());
                for (int i = 0; i < expected.size(); i++) {
                    check(text(preview).get(1 + i * 2).isEmpty(), "Every effect paragraph has breathing room");
                    check(paragraphs.get(i).content().getStyle().getColor().getValue() == net.minecraft.ChatFormatting.GRAY.getColor(),
                            "Uniform neutral prose with contrasting dynamic values");
                }
                String nodeLabel = flatten(SkillTooltipPresentation.nodePurchaseLabel(skill,
                        runtime.config().balanceProfile(), rank, Long::toString));
                check(nodeLabel.equals(rank == max ? "Max" : Long.toString(skill.cost(runtime.config().balanceProfile(), rank + 1))),
                        "Node shows next generated cost after every purchase, then Max");
                check(text(preview).stream().noneMatch(line -> line.startsWith("Cost:") || line.startsWith("Required tier:")),
                        "Tooltip does not repeat node cost or interface tier");
                if (rank == max) check(text(current).equals(text(preview)) && text(current).contains("Max Rank"), "Max has no fabricated preview");
                for (var line : text(preview)) {
                    check(!line.contains("%s") && !line.contains(".resolved") && !line.contains("NaN"), "No unresolved values: " + line);
                    check(!line.matches(".*\\b(ticks?|generated|scalar|interpolation|event-hook|DMG|ATK)\\b.*"), "Player-facing prose: " + line);
                }
            }
        }
    }

    private static void lockedRankPreview(RuntimeBalanceDefinition runtime) {
        var presenter = new SkillTooltipPresentation();
        var chain = SkillRegistry.require(SkillIds.CHAIN_STRIKE);
        check(chain.maximumRank() > 1, "Chain Strike fixture has a real second rank");
        for (boolean owned : new boolean[]{false, true}) {
            for (boolean stagedEnabled : new boolean[]{false, true}) {
                var ranks = owned ? Map.of(SkillIds.STATIC_CHARGE, 1, SkillIds.CHAIN_STRIKE, 1)
                        : Map.of(SkillIds.STATIC_CHARGE, 1);
                var context = new SkillEvaluationContext(AscendanceTiers.TRANSCENDENT.id(), ranks, ranks,
                        Map.of(), stagedEnabled ? Map.of(SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT, SkillIds.STATIC_CHARGE) : Map.of(),
                        Set.of(), Set.of(), Map.of(), Map.of());
                var states = SkillStateEvaluator.evaluateAll(context);
                check(SkillTooltipPresentation.unmetPrerequisites(states.get(chain.id()), states, false).isEmpty() == stagedEnabled,
                        "Reproduce Static Charge purchased but disabled, then staged enabled");
                assertRankTwoPreview(presenter, runtime, chain, states);
            }
        }
        // Catalog-wide locked previews cover prerequisites only, requirements only, and both together.
        var locked = SkillStateEvaluator.evaluateAll(SkillEvaluationContext.committed(
                AscendanceTiers.TRANSCENDENT.id(), Map.of(), Map.of(), Set.of(), Set.of(), Map.of()));
        int prerequisiteOnly = 0, requirementOnly = 0, both = 0;
        for (var skill : SkillRegistry.values()) {
            if (skill.maximumRank() <= 1) continue;
            var state = locked.get(skill.id());
            boolean prerequisites = !SkillTooltipPresentation.unmetPrerequisites(state, locked, false).isEmpty();
            boolean requirements = !SkillTooltipPresentation.unmetRequirements(state, false).isEmpty();
            if (prerequisites && requirements) both++;
            else if (prerequisites) prerequisiteOnly++;
            else if (requirements) requirementOnly++;
            assertRankTwoPreview(presenter, runtime, skill, locked);
        }
        check(prerequisiteOnly > 0 && requirementOnly > 0 && both > 0, "Exercise all three unmet-gate combinations");
    }

    private static void assertRankTwoPreview(SkillTooltipPresentation presenter, RuntimeBalanceDefinition runtime,
            SkillDefinition skill, Map<ResourceLocation, SkillEvaluationResult> states) {
        var normal = new SemanticTooltip(); presenter.append(normal, skill, states, false);
        var shifted = new SemanticTooltip(); presenter.append(shifted, skill, states, true);
        var released = new SemanticTooltip(); presenter.append(released, skill, states, false);
        check(text(normal).getFirst().equals("Rank 1/" + skill.maximumRank())
                        && text(shifted).getFirst().equals("Rank 2/" + skill.maximumRank()),
                "Locked or inactive skill previews rank two: " + skill.id());
        var expected = SkillTooltipPresentation.resolve(runtime, skill.id(), Map.of(skill.id(), 2))
                .stream().map(SharedPresentationTest::flatten).toList();
        check(shifted.lines().stream().filter(line -> line.indentation() == 1)
                        .map(line -> flatten(line.content())).toList().equals(expected),
                "Preview uses actual resolved rank-two values, not merely a new rank label");
        check(text(normal).equals(text(released)), "Releasing Shift restores rank-one values after a cached preview");
        if (skill.id().equals(SkillIds.CHAIN_STRIKE))
            check(!normal.lines().stream().filter(line -> line.indentation() == 1).map(line -> flatten(line.content())).toList().equals(expected),
                    "Chain Strike's rank-two effect differs from rank one with Static Charge disabled or staged");
    }

    private static void tooltipRequirements() {
        // Real selectable prerequisite: committed and staged ownership/selection are independent.
        for (boolean owned : new boolean[]{false, true}) {
            for (boolean purchased : new boolean[]{false, true}) {
                for (boolean enabled : new boolean[]{false, true}) {
                    for (boolean stagedEnabled : new boolean[]{false, true}) {
                        var current = owned ? Map.of(SkillIds.FROSTBITE, 1, SkillIds.SHATTER, 1)
                                : Map.<ResourceLocation, Integer>of();
                        var projected = new HashMap<>(current);
                        if (purchased) projected.put(SkillIds.FROSTBITE, 1);
                        var context = new SkillEvaluationContext(AscendanceTiers.TRANSCENDENT.id(), current, projected,
                                enabled ? Map.of(SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT, SkillIds.FROSTBITE) : Map.of(),
                                stagedEnabled ? Map.of(SkillGroups.OFFENSE_ELEMENTAL_IMBUEMENT, SkillIds.FROSTBITE) : Map.of(),
                                Set.of(), Set.of(), Map.of(), Map.of());
                        var states = SkillStateEvaluator.evaluateAll(context);
                        var child = states.get(SkillIds.SHATTER);
                        var missing = SkillTooltipPresentation.unmetPrerequisites(child, states, false);
                        boolean satisfied = (owned || purchased) && stagedEnabled;
                        check(missing.isEmpty() == satisfied,
                                "Prerequisite visibility follows staged purchase and enable/disable, not committed ownership alone");
                        check(SkillTooltipPresentation.unmetPrerequisites(child, states, true).isEmpty(),
                                "Even an inactive owned skill has no prerequisite section in Shift preview");
                        if (!satisfied) check(missing.size() == 1 && missing.getFirst().skillId().equals(SkillIds.FROSTBITE),
                                "Only the outstanding prerequisite survives the normal tooltip filter");
                    }
                }
            }
        }
        for (boolean complete : new boolean[]{false, true}) {
            var ranks = Map.of(SkillIds.IMPACT_CONTROL, 1, SkillIds.FATIGUE_FLIGHT, 1, SkillIds.ESSENCE_WINGS, 1);
            var context = SkillEvaluationContext.committed(AscendanceTiers.TRANSCENDENT.id(), ranks,
                    Map.of(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT, SkillIds.ESSENCE_WINGS),
                    complete ? Set.of(Milestones.SKY_LIMIT.id()) : Set.of(), Set.of(), Map.of());
            var states = SkillStateEvaluator.evaluateAll(context);
            var wings = states.get(SkillIds.ESSENCE_WINGS);
            var requirements = SkillTooltipPresentation.unmetRequirements(wings, false);
            check(requirements.isEmpty() == complete,
                    "Normal tooltip removes a completed purchase milestone and retains an unmet one");
            check(SkillTooltipPresentation.unmetRequirements(wings, true).isEmpty(),
                    "Shift omits unmet as well as completed purchase requirements");
            check(SkillTooltipPresentation.unmetPrerequisites(wings, states, false).isEmpty(),
                    "Replacement target is not falsely reported inactive when the replacement suppresses it");
            if (complete) check(wings.projectedEffective() && !states.get(SkillIds.FATIGUE_FLIGHT).projectedEffective(),
                    "Replacement fixture exercises the real suppressed-target state");
            for (var id : List.of(SkillIds.DAMAGE_CEILING, SkillIds.FEAST_REFLEX)) {
                check(SkillTooltipPresentation.unmetPrerequisites(states.get(id), states, false).isEmpty()
                                && SkillTooltipPresentation.unmetRequirements(states.get(id), false).isEmpty(),
                        "Already-clean reference tooltips retain no requirement sections");
            }
        }
    }

    private static void bonuses(RuntimeBalanceDefinition runtime) {
        for (var stat : EssenceStatRegistry.values()) {
            check(language.has(BonusTooltipPresentation.descriptionKey(stat)), "Description for every bonus: " + stat.id());
            String description = flatten(BonusTooltipPresentation.description(stat, 18));
            check(description.contains("18") && description.endsWith(".") && description.length() > 20, "A real sentence, not a raw value");
            if (stat == EssenceStats.FALL_RESISTANCE)
                check(description.contains("Reduces fall damage by 18%")
                                && description.contains("With Impact Control, it also reduces damage from wall collisions and pointed dripstone"),
                        "Fall Resistance describes the conditional movement-impact benefit");
            var definition = runtime.config().balanceProfile().bonusTrack(stat.id());
            var facts = BonusTrackSnapshot.from(definition, runtime.config().balanceProfile());
            long cap = definition.checkpoints().getLast().cumulativeCap();
            var state = new ClientEssenceState.StatSnapshot(stat.id(), 0, 0, cap, 0, definition.maximumEffect(), definition.maximumEffect(), 0, facts);
            var track = new NexusProgressionTrack(stat, state, List.of());
            var current = text(BonusTooltipPresentation.tooltip(track, AscendanceTiers.TRANSCENDENT.id(), 0));
            check(current.stream().noneMatch(s -> s.contains("Latent")), "Only Dormant+ in bonus presentation");
            check(current.contains(flatten(BonusTooltipPresentation.description(stat, 0))), "Default bonus is current state");
            var next = definition.checkpoints().stream().filter(p -> p.purchasable()).findFirst();
            if (next.isPresent()) {
                long target = next.get().cumulativeCap();
                var preview = text(BonusTooltipPresentation.tooltip(track, AscendanceTiers.TRANSCENDENT.id(), target));
                check(preview.contains("Staged allocation preview"), "Drag target explicitly marks prospective state");
                check(preview.contains(flatten(BonusTooltipPresentation.description(stat, definition.maximumEffect() * next.get().effectFraction()))), "Generated next bonus benefit");
                if (target > 1) check(track.progression(target - 1, AscendanceTiers.TRANSCENDENT.id()) > 0,
                        "Partial investment grants partial benefit before the first tier cap");
            }
            long previousCap = 0;
            double previousEffect = 0;
            for (var checkpoint : definition.checkpoints()) {
                if (!checkpoint.purchasable()) continue;
                var tier = AscendanceTierRegistry.get(checkpoint.tierId()).orElseThrow();
                long capAtTier = checkpoint.cumulativeCap();
                check(TierInvestmentPolicy.validTarget(stat, tier, runtime.config().balanceProfile(), previousCap, capAtTier),
                        "Each available tier can fully fund its benefit");
                check(track.progression(capAtTier, tier.id()) == checkpoint.effectFraction()
                                && checkpoint.effectFraction() > previousEffect,
                        "Every purchasable tier attains its generated improvement at its own cap");
                if (capAtTier - previousCap > 1) {
                    long partial = previousCap + (capAtTier - previousCap) / 2;
                    check(TierInvestmentPolicy.validTarget(stat, tier, runtime.config().balanceProfile(), previousCap, partial),
                            "Authoritative validator accepts partial funding inside each tier");
                    check(track.fundingProgression(partial, tier.id()) > previousEffect
                                    && track.fundingProgression(partial, tier.id()) < checkpoint.effectFraction(),
                            "Funding advances between existing tier markers");
                    double benefit = track.progression(partial, tier.id());
                    double actual = StatScalingService.realizedProgressionForInvestment(stat, partial, tier, runtime.config().balanceProfile());
                    check(benefit > previousEffect && benefit < checkpoint.effectFraction() && benefit == actual,
                            "Partial investment grants matching fractional gameplay and preview benefits within every tier");
                    var partialTooltip = text(BonusTooltipPresentation.tooltip(track, tier.id(), partial));
                    check(partialTooltip.contains(flatten(BonusTooltipPresentation.description(stat, definition.maximumEffect() * actual))),
                            "Partial tooltip displays actual earned benefit");
                    check(partialTooltip.stream().anyMatch(line -> line.startsWith("Next tier target:"))
                                    && partialTooltip.stream().noneMatch(line -> line.startsWith("Next benefit:")),
                            "Tier targets are milestones rather than benefit locks");
                }
                previousCap = capAtTier; previousEffect = checkpoint.effectFraction();
            }
        }
    }

    private static void cards() {
        var definitions = SkillEffectHudCards.definitions();
        check(definitions.size() == 90 && definitions.stream().map(SkillEffectHudCards.Definition::skill).distinct().count() == 90, "Exactly 90 distinct card definitions");
        for (var definition : definitions) {
            check(definition.height() == 44, "Every card has fixed four-row height");
            check(definition.accent() == AscendancePalette.categoryArgb(SkillRegistry.require(definition.skill()).essenceId()), "Home Essence owns accent");
            check(tr(definition.titleKey()).length() * 6 <= SkillEffectHudLayout.CONTENT_WIDTH, "Full localized title fits: " + definition.titleKey());
            check(SkillEffectRegistry.get(definition.skill()) != null, "Every card has a runtime provider");
            checkTextContrast(definition.accent());
        }
        for (int lines = 0; lines <= 20; lines++) check(SkillEffectHudLayout.height(lines) == 44, "Content cannot enlarge cards");
        check(SkillEffectHudLayout.WIDTH == 200 && SkillEffectHudLayout.FILL_WIDTH == 197
                && SkillEffectHudLayout.ACCENT_WIDTH == 3 && SkillEffectHudLayout.CONTENT_WIDTH == 186,
                "Background progress covers the card body while preserving the left accent");
        check(SkillEffectHudLayout.DETAIL_ROWS == 2
                        && SkillEffectHudLayout.DETAIL_Y + 2 * SkillEffectHudLayout.ROW_HEIGHT <= SkillEffectHudLayout.HEIGHT,
                "Both full-size detail rows remain inside the four-row card");
        java.util.function.ToIntFunction<String> width = text -> text.length() * 6;
        check(SkillEffectHudLayout.timerText("2/5", "CHAIN", "3s", width).equals("CHAIN 3s"),
                "Status and labelled timer share one row when space permits");
        check(SkillEffectHudLayout.timerText("CD", "CD", "100s", width).equals("100s"),
                "Cooldown status does not repeat its timer label");
        check(SkillEffectHudLayout.timerText("100.0/100.0 BUFFER", "REMAINING", "100s", width).equals("100s"),
                "A long status keeps all numbers and the countdown without ellipses");
        var meter = SkillEffectHudEntry.Meter.none();
        check(SkillEffectHudLayout.progressWidth(meter) == 0
                        && SkillEffectHudLayout.progressWidth(SkillEffectHudEntry.Meter.timer("CD", 100)) == 0,
                "State and countdown cards never invent a progress fill");
        int previousWidth = -1;
        for (int percent = 0; percent <= 100; percent++) {
            int fillWidth = SkillEffectHudLayout.progressWidth(SkillEffectHudEntry.Meter.progress(percent / 100.0));
            check(fillWidth >= previousWidth && Math.abs(fillWidth - 197 * percent / 100.0) <= 0.5,
                    "Background grows monotonically with actual resource progress, independent of text length");
            previousWidth = fillWidth;
        }
        check(SkillEffectHudLayout.progressWidth(SkillEffectHudEntry.Meter.progress(-1)) == 0
                        && SkillEffectHudLayout.progressWidth(SkillEffectHudEntry.Meter.progress(2)) == 197
                        && SkillEffectHudLayout.progressWidth(SkillEffectHudEntry.Meter.progress(Double.NaN)) == 0,
                "Empty, full and malformed progress stay within the card body");
        for (int color : new int[]{0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF})
            checkTextContrast(color);
        check(Arrays.equals(SkillEffectHudEntry.MeterKind.values(), new SkillEffectHudEntry.MeterKind[]{SkillEffectHudEntry.MeterKind.NONE, SkillEffectHudEntry.MeterKind.TIMER, SkillEffectHudEntry.MeterKind.PROGRESS}), "Bounded common indicator vocabulary");
        for (String key : language.keySet()) if (key.startsWith("hud.") && !key.contains(".term.")) {
            String sample = tr(key).replaceAll("%(?:[0-9]+\\$)?s", "100.0").replace("%%", "%");
            sample = SkillHudVocabulary.compact(sample, SharedPresentationTest::tr);
            check(sample.length() * 6 <= SkillEffectHudLayout.CONTENT_WIDTH, "HUD translation fits fixed rows: " + key + " = " + sample);
        }
        check(SkillHudVocabulary.compact("Attack Damage / Attack Speed / Movement Speed", SharedPresentationTest::tr).equals("ATK DMG / ATK SPD / MOVE SPD"), "One localized vocabulary");
        var event = new SkillHudEvents.Event(10, SkillEffectHudEntry.Text.literal("Proc"), List.of());
        check(!event.active(9) && event.active(10) && event.active(29) && !event.active(30), "Passive event expiry never depends on snapshots");
        check(!NexusHudControl.visible(false, true, true) && !NexusHudControl.visible(true, false, true) && !NexusHudControl.visible(true, true, false), "Circle appears only purchased, effective, powered");
        check(NexusHudControl.visible(true, true, true) && NexusHudControl.glyph(true).equals("●") && NexusHudControl.glyph(false).equals("○"), "Circle semantics");
        check(NexusHudControl.hit(89, 89, 100, 100) && !NexusHudControl.hit(86, 89, 100, 100) && !NexusHudControl.hit(99, 89, 100, 100), "Exclusive bounded hit region");
    }

    private static void checkTextContrast(int accent) {
        int fill = SkillEffectHudLayout.fillColor(accent);
        check((fill >>> 24) == 255 && (SkillEffectHudLayout.BACKGROUND >>> 24) == 255,
                "Opaque backing makes text contrast independent of the world behind the HUD");
        for (int preferred : new int[]{0xFFF4F1E8, 0xFFB4BAC7, accent}) {
            int text = SkillEffectHudLayout.textColor(preferred, fill);
            check(SkillEffectHudLayout.contrast(text, fill) >= 4.5
                            && SkillEffectHudLayout.contrast(text, SkillEffectHudLayout.BACKGROUND) >= 4.5,
                    "Title, details and status stay readable on both sides of any fill boundary");
        }
    }

    private static void preferences() {
        var skill = SkillRegistry.require(SkillIds.FRENZY);
        var data = new PlayerEssenceData(); data.setTier(AscendanceTiers.DORMANT);
        data.recordSkillPurchase(skill.id(), new SkillPurchase(skill.essenceId(), 17));
        check(data.skillHudEnabled(skill.id()), "Default-on purchase");
        var before = data.save(); long revision = data.nexusRevision();
        data.setSkillHudEnabled(skill.id(), false);
        var after = data.save(); before.remove("skill_hud_preferences"); after.remove("skill_hud_preferences");
        check(before.equals(after) && revision == data.nexusRevision(), "Visibility is independent of every gameplay field and revision");
        var loaded = PlayerEssenceData.load(data.save());
        check(!loaded.skillHudEnabled(skill.id()), "Preference persists after reload");
        loaded.setLoadoutSelection(skill.id(), skill.id()); loaded.clearLoadoutSelection(skill.id()); loaded.setLoadoutSelection(skill.id(), skill.id());
        check(!loaded.skillHudEnabled(skill.id()), "Disable/re-enable retains hidden preference");
        check(new PlayerEssenceData().skillHudEnabled(skill.id()), "Preferences isolated per player");
        var request = new SkillHudPreferencePayload(9, skill.id(), false);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        SkillHudPreferencePayload.CODEC.encode(buffer, request);
        check(SkillHudPreferencePayload.CODEC.decode(buffer).equals(request) && buffer.readableBytes() == 0, "Isolated presentation command codec"); buffer.release();
        var progress = new PlayerEssenceSyncPayload.ProgressState(PlayerEssenceSyncPayload.ProgressStatus.MAX_TIER, "", 0, 0, 0, 0, 0, 0, 0, List.of(), true, false);
        var payload = new PlayerEssenceSyncPayload(PlayerEssenceSyncPayload.CURRENT_SCHEMA_VERSION, revision, AscendanceTiers.DORMANT.id().toString(), "essence_ascendance:test", List.of(), List.of(), List.of(), List.of(),
                List.of(new PlayerEssenceSyncPayload.OwnedSkillState(skill.id().toString(), skill.essenceId().toString(), List.of(17L), false)), List.of(), AttunementSnapshot.empty(), progress);
        buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        PlayerEssenceSyncPayload.CODEC.encode(buffer, payload);
        check(PlayerEssenceSyncPayload.CODEC.decode(buffer).equals(payload) && buffer.readableBytes() == 0, "Preference round-trips in real player payload"); buffer.release();
    }

    private static List<String> text(SemanticTooltip tooltip) { return tooltip.lines().stream().map(line -> flatten(line.content())).toList(); }
    private static String tr(String key) { return language.has(key) ? language.get(key).getAsString() : key; }
    private static String flatten(Component component) {
        if (!(component.getContents() instanceof TranslatableContents content)) return component.getString();
        Object[] args = Arrays.stream(content.getArgs()).map(a -> a instanceof Component c ? flatten(c) : a).toArray();
        String value = String.format(Locale.ROOT, tr(content.getKey()), args);
        for (var sibling : component.getSiblings()) value += flatten(sibling);
        return value;
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
