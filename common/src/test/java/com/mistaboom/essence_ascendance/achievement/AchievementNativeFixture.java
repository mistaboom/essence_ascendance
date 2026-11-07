package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent;
import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleMenu;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.data.NbtVersionCompat;
import com.mistaboom.essence_ascendance.data.ReservoirService;
import com.mistaboom.essence_ascendance.essence.EssenceTypes;
import com.mistaboom.essence_ascendance.gameplay.DissolutionOutcome;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.attunement.AttunementActivityRegistry;
import com.mistaboom.essence_ascendance.attunement.AttunementService;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import com.mistaboom.essence_ascendance.gameplay.EquipmentObservations;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusContent;
import com.mistaboom.essence_ascendance.nexus.AscendanceNexusBlockEntity;
import com.mistaboom.essence_ascendance.progression.AscendanceAttemptResult;
import com.mistaboom.essence_ascendance.progression.AscendanceEngine;
import com.mistaboom.essence_ascendance.progression.AscendanceNexusTransactionService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Real native server resources, menus, transactions and advancement files in a disposable world. */
public final class AchievementNativeFixture {
    private static int checks;
    private AchievementNativeFixture() { }

    @SuppressWarnings("unchecked")
    public static void run(ServerLevel level) throws Exception {
        if (!Boolean.getBoolean("essence.achievement.fixture")) throw new IllegalStateException("Explicit fixture required");
        String restartStage = System.getProperty("essence.achievement.restart.stage", "");
        if (!restartStage.isEmpty()) { MachineRestartNativeFixture.run(level, restartStage); return; }
        checks = 0;
        var server = level.getServer();
        var playerList = server.getPlayerList();
        check(playerList.getPlayers().isEmpty(), "Fixture must not run with real connected players");
        var playersField = PlayerList.class.getDeclaredField("players");
        var idsField = PlayerList.class.getDeclaredField("playersByUUID");
        playersField.setAccessible(true); idsField.setAccessible(true);
        var online = (List<ServerPlayer>) playersField.get(playerList);
        var byId = (Map<UUID, ServerPlayer>) idsField.get(playerList);
        var owner = player(level, "AchOwner");
        var operator = player(level, "AchOperator");
        var legacyPlayer = player(level, "AchLegacy");
        online.add(owner); online.add(operator); online.add(legacyPlayer);
        byId.put(owner.getUUID(), owner); byId.put(operator.getUUID(), operator); byId.put(legacyPlayer.getUUID(), legacyPlayer);
        BlockPos position = new BlockPos(1, 120, 1);
        level.getChunkAt(position);
        owner.setPos(1, 120, 2); operator.setPos(2, 120, 2);
        try {
            var selected = server.getAdvancements().get(AchievementService.EQUIVALENT_EXCHANGE);
            var legacy = server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "water_skiing"));
            check(selected != null && legacy != null, "Actual datapack loader resolves selected and legacy resources");
            //? if >=26.1.2
            /*check(selected.value().display().orElseThrow().getIcon().create().is(EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().asItem()),*/
            //? if <26.1.2
            check(selected.value().display().orElseThrow().getIcon().is(EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().asItem()),
                    "Native selected advancement decodes its existing Crucible ItemStack icon");
            check(selected.value().criteria().containsKey(AchievementService.DISSOLVED)
                            && legacy.value().criteria().containsKey("travel"), "Stable native criteria remain available");
            check(server.getAdvancements().get(AchievementService.resource(1)) != null,
                    "Selected Jumped the Shark has its own native resource");
            presentationDiscovery(operator);
            check(legacy.value().display().isEmpty(), "Legacy progress has no seventieth visible challenge");

            owner.getAdvancements().award(legacy, "travel");
            owner.getAdvancements().save();
            owner.getAdvancements().revoke(legacy, "travel");
            owner.getAdvancements().reload(server.getAdvancements());
            check(done(owner, legacy) && !done(owner, selected) && !done(operator, selected) && !done(owner, 1),
                    "Legacy saved travel completion survives native reload without selected achievement backfill");

            var saved = EssenceSavedData.get(server);
            var team = saved.nativeMembership().create(owner.getUUID(), "Achievement fixture");
            var join = saved.nativeMembership().request(operator.getUUID(), team, true);
            saved.nativeMembership().decide(owner.getUUID(), join.token(), true);
            saved.getPlayerData(operator.getUUID()).setTier(AscendanceTiers.TRANSCENDENT);
            AchievementService.baseline(operator);
            check(!done(operator, 31) && !done(operator, 35),
                    "Explicit fixture/admin tier baseline does not manufacture historical player Ascensions");
            level.setBlock(position, EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState(), 3);
            var machine = (EssenceCrucibleBlockEntity) level.getBlockEntity(position);
            check(machine != null && machine.bindOnPlacement(owner), "Real placed Crucible binds in the native world");
            check(machine.machineBinding().operator() == null, "Placement does not manufacture an operator");
            check(EssenceCrucibleBlockEntity.isValidNewInput(input()), "Fixture input is supported by actual runtime mapping");

            var menu = menu(machine, operator);
            menu.setCarried(input());
            operator.setPos(100, 120, 100);
            menu.clicked(0, 0, ClickType.PICKUP, operator);
            check(machine.getItem(0).isEmpty() && machine.machineBinding().operator() == null,
                    "Rejected out-of-range native menu insertion cannot establish operation credit");
            operator.setPos(2, 120, 2);
            menu.clicked(0, 0, ClickType.PICKUP, operator);
            check(menu.getCarried().isEmpty() && operator.getUUID().equals(machine.machineBinding().operator()),
                    "Successful native menu insertion credits its actor independently of machine ownership");
            check(machine.setProcessingEnabled(operator, false), "Authorized Stop is accepted");
            tick(level, machine);
            check(!machine.getItem(0).isEmpty() && !done(operator, selected), "Stopped operation does not consume or grant");
            check(machine.setProcessingEnabled(operator, true), "Authorized Start is accepted");

            saved.reservoirs().administer(owner.getUUID(), Map.of(EssenceTypes.OFFENSE.id(), machine.localFillLimit().multiply(ReservoirService.SCALE)));
            tick(level, machine);
            check(!machine.getItem(0).isEmpty() && !done(operator, selected), "Capacity-rejected operation does not consume or grant");
            saved.reservoirs().administer(owner.getUUID(), Map.of(EssenceTypes.OFFENSE.id(), BigInteger.ZERO));

            machine.setItem(0, new ItemStack(Items.BARRIER));
            check(!EssenceCrucibleBlockEntity.isValidNewInput(machine.getItem(0)), "Unsupported-input fixture remains unmapped");
            tick(level, machine);
            check(machine.getItem(0).is(Items.BARRIER) && !done(operator, selected), "Unsupported old input neither dissolves nor grants");
            machine.setItem(0, ItemStack.EMPTY);

            operator.setGameMode(GameType.CREATIVE);
            insert(machine, operator);
            check(machine.machineBinding().operator() == null, "Creative native input clears credit rather than fabricating a survival action");
            operator.setGameMode(GameType.SURVIVAL);
            tick(level, machine);
            check(machine.getItem(0).isEmpty() && !done(operator, selected), "Returning to survival cannot claim prior creative insertion");

            insert(machine, operator);
            BigInteger before = machine.totalStoredEssence();
            tick(level, machine);
            check(machine.getItem(0).isEmpty() && machine.totalStoredEssence().compareTo(before) > 0,
                    "Actual supported dissolution consumes input and commits positive reservoir output");
            check(done(operator, selected) && !done(owner, selected),
                    "Actual transaction awards only the explicit operator, including at maximum Attunement tier");
            check(!operator.getAdvancements().award(selected, AchievementService.DISSOLVED), "Native completion is idempotent");

            operator.getAdvancements().save();
            var pathField = PlayerAdvancements.class.getDeclaredField("playerSavePath"); pathField.setAccessible(true);
            Path file = (Path) pathField.get(operator.getAdvancements());
            check(Files.isRegularFile(file) && Files.readString(file).contains(AchievementService.EQUIVALENT_EXCHANGE.toString()),
                    "Completion is saved in the real world's native player advancement JSON");
            operator.getAdvancements().revoke(selected, AchievementService.DISSOLVED);
            operator.getAdvancements().reload(server.getAdvancements());
            check(done(operator, selected), "Native reload restores the saved completion");

            command(level, "advancement revoke AchOperator only " + AchievementService.EQUIVALENT_EXCHANGE);
            check(!done(operator, selected), "Native advancement revoke is the authoritative completion reset");
            command(level, "advancement grant AchOperator only " + AchievementService.EQUIVALENT_EXCHANGE);
            check(done(operator, selected), "Native advancement grant directly updates the same authority");
            command(level, "advancement revoke AchOperator only " + AchievementService.EQUIVALENT_EXCHANGE);
            insert(machine, operator); tick(level, machine);
            check(done(operator, selected), "A fresh successful dissolution can re-earn a revoked completion");

            // This separate synthetic service-boundary check proves delivery deduplication;
            // the preceding menu/transaction checks are the gameplay evidence.
            var receipt = new DissolutionOutcome("fixture-duplicate", owner.getUUID(), level.dimension().location(),
                    position.asLong(), 1, BigInteger.ONE, server.overworld().getGameTime());
            AchievementService.onDissolution(server, receipt);
            check(done(owner, selected), "Synthetic receipt reaches the real native award boundary");
            owner.getAdvancements().revoke(selected, AchievementService.DISSOLVED);
            AchievementService.onDissolution(server, receipt);
            check(!done(owner, selected), "Duplicate receipt cannot regrant after administrative revoke");
            check(done(operator, selected), "One player's revoke and duplicate delivery leave the other completion intact");
            phaseTwo(level, owner, operator, legacyPlayer);
            MachineAchievementNativeFixture.run(level, owner, operator);
            CombatAchievementNativeFixture.run(level, operator, owner);
            RodAchievementNativeFixture.run(level, operator, owner);
            MovementAchievementNativeFixture.run(level, operator, owner);
            SurvivalAchievementNativeFixture.run(level, owner);
            CompanionAchievementNativeFixture.run(level, owner);
            FarmingAchievementNativeFixture.run(level, owner);
            GatheringAchievementNativeFixture.run(level, owner);
            owner.getAdvancements().save(); operator.getAdvancements().save();
            System.out.println("AchievementNativeFixture: " + checks + " assertions passed; native menus/crafts/equipment/purchases/tier commits/save/reload; fixture-controlled Attunement submissions and separate synthetic duplicate probe");
        } finally {
            operator.containerMenu = operator.inventoryMenu;
            owner.containerMenu = owner.inventoryMenu;
            online.remove(owner); online.remove(operator); online.remove(legacyPlayer);
            byId.remove(owner.getUUID()); byId.remove(operator.getUUID()); byId.remove(legacyPlayer.getUUID());
            level.setBlock(position, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static final List<Integer> PHASE_TWO_IDS = List.of(19, 26, 28, 30, 31, 32, 33, 34, 35, 36,
            61, 72, 139, 166, 169, 205, 208);

    private static void phaseTwo(ServerLevel level, ServerPlayer builder, ServerPlayer purchaser,
                                 ServerPlayer legacyPlayer) throws Exception {
        for (int id : PHASE_TWO_IDS) {
            var resource = advancement(builder, id);
            check(resource != null && resource.value().criteria().containsKey("achieved"),
                    "Native resource/criterion loads for phase-2 ID " + id);
            //? if >=26.1.2
            /*check(!resource.value().display().orElseThrow().getIcon().create().isEmpty(),*/
            //? if <26.1.2
            check(!resource.value().display().orElseThrow().getIcon().isEmpty(),
                    "Native existing item icon decodes for ID " + id);
        }
        reconciliation(legacyPlayer);
        nexusAndCraft(level, builder, purchaser);
        equipment(builder, purchaser);
        purchases(purchaser);
        progression(level, builder);
        // Each completion is saved/restored by the actual Minecraft advancement owner.
        builder.getAdvancements().save(); purchaser.getAdvancements().save();
        for (int id : List.of(19, 28, 30, 31, 32, 33, 34, 35, 36, 61, 166, 205)) {
            check(done(builder, id), "Native phase-2 gameplay/service completion exists before save reload: " + id);
            builder.getAdvancements().revoke(advancement(builder, id), "achieved");
        }
        builder.getAdvancements().reload(level.getServer().getAdvancements());
        for (int id : List.of(19, 28, 30, 31, 32, 33, 34, 35, 36, 61, 166, 205))
            check(done(builder, id), "Native advancement JSON restores phase-2 completion: " + id);
        for (int id : List.of(26, 72, 139, 169, 208)) {
            check(done(purchaser, id), "Native phase-2 purchase/equipment completion exists before reload: " + id);
            purchaser.getAdvancements().revoke(advancement(purchaser, id), "achieved");
        }
        purchaser.getAdvancements().reload(level.getServer().getAdvancements());
        for (int id : List.of(26, 72, 139, 169, 208))
            check(done(purchaser, id), "Native advancement JSON restores purchase/equipment completion: " + id);
    }

    private static void reconciliation(ServerPlayer legacyPlayer) {
        var data = EssenceSavedData.get(legacyPlayer.serverLevel().getServer()).getPlayerData(legacyPlayer.getUUID());
        data.setTier(AscendanceTiers.RESONANT); // Model a legitimate old saved tier before feature installation.
        check(!data.achievementProgress().tierObserved(), "Old player record starts without feature reconciliation metadata");
        AchievementService.reconcile(legacyPlayer);
        check(done(legacyPlayer, 31) && done(legacyPlayer, 32) && done(legacyPlayer, 33)
                        && !done(legacyPlayer, 34) && !done(legacyPlayer, 35),
                "First-install saved-tier reconciliation backfills precisely the reached milestones");
        check(!done(legacyPlayer, 19) && !done(legacyPlayer, 26) && !done(legacyPlayer, 28) && !done(legacyPlayer, 208),
                "Saved-tier reconciliation never invents historical build/use, purchases or crafts");
        legacyPlayer.getAdvancements().revoke(advancement(legacyPlayer, 33), "achieved");
        AchievementService.reconcile(legacyPlayer);
        check(!done(legacyPlayer, 33), "Repeated saved-tier reconciliation respects native administrative revoke");
        data.setTier(AscendanceTiers.TRANSCENDENT);
        AchievementService.baseline(legacyPlayer); AchievementService.reconcile(legacyPlayer);
        check(!done(legacyPlayer, 33) && !done(legacyPlayer, 34) && !done(legacyPlayer, 35),
                "Explicit admin tier checkpoint does not emit new or restore revoked Ascension milestones");
        data.setAvailable(EssenceTypes.UTILITY, 1_000_000_000_000L);
        for (var skill : List.of(SkillIds.THREAT_SENSE, SkillIds.WAYLIGHT)) {
            var purchase = transaction(legacyPlayer, investments(legacyPlayer), Map.of(skill, 1), false);
            check(purchase.accepted(), "Lifecycle actor commits a real Utility purchase before reset: " + purchase.status());
        }
        var lifetimeBeforeReset = NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(
                NbtVersionCompat.getCompound(data.achievementProgress().save(), "entries"), "a208"), "skills");
        check(lifetimeBeforeReset.size() == 2, "Lifecycle actor has exactly two historical committed Utility purchases");
        command(legacyPlayer.serverLevel(), "essence admin player tier set dormant AchLegacy");
        check(data.getTierId().equals(AscendanceTiers.DORMANT.id()), "Actual native admin command prepares the chapter-reset fixture tier");
        for (String method : List.of("heal_health", "restore_hunger")) {
            var contribution = AttunementService.submit(legacyPlayer, "fixture-legacy-variety-" + method,
                    method, "fixture-legacy-source-" + method, .000001);
            check(contribution.finalContribution() > 0, "Lifecycle actor earns a positive same-chapter method before reset: " + method);
        }
        String originalChapter = data.attunement().chapterId();
        check(!done(legacyPlayer, 30), "Two credited chapter methods remain below Training Montage's threshold");
        command(legacyPlayer.serverLevel(), "essence admin player reset AchLegacy");
        AchievementService.baseline(legacyPlayer);
        command(legacyPlayer.serverLevel(), "essence admin player tier set dormant AchLegacy");
        AchievementService.baseline(legacyPlayer);
        var afterReset = AttunementService.submit(legacyPlayer, "fixture-legacy-after-reset", "consume_hunger",
                "fixture-legacy-after-reset-source", .000001);
        check(afterReset.finalContribution() > 0 && data.attunement().chapterId().equals(originalChapter)
                        && !done(legacyPlayer, 30),
                "After actual admin progression reset and re-entry into the same chapter ID, one new method cannot combine with the old two");
        var lifetimeAfterReset = NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(
                NbtVersionCompat.getCompound(data.achievementProgress().save(), "entries"), "a208"), "skills");
        check(lifetimeAfterReset.equals(lifetimeBeforeReset) && data.getOwnedSkills().isEmpty(),
                "Actual admin reset clears current Skill ownership while preserving lifetime Utility purchase facts");
        legacyPlayer.getAdvancements().save();
    }

    private static void nexusAndCraft(ServerLevel level, ServerPlayer builder, ServerPlayer other) {
        BlockPos support = new BlockPos(5, 119, 1), nexus = support.above();
        BlockPos table = new BlockPos(8, 120, 1);
        BlockPos clonedNexus = new BlockPos(11, 120, 1);
        try {
            level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
            builder.setPos(5.5, 120, 3); other.setPos(5.5, 120, 3);
            var hit = new BlockHitResult(new Vec3(5.5, 120, 1.5), Direction.UP, support, false);
            builder.setItemInHand(InteractionHand.MAIN_HAND,
                    new ItemStack(AscendanceNexusContent.ASCENDANCE_NEXUS.get().asItem()));
            builder.getMainHandItem().useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit));
            check(level.getBlockState(nexus).is(AscendanceNexusContent.ASCENDANCE_NEXUS.get()),
                    "Survival BlockItem operation places a real Nexus");
            check(!done(builder, 19), "Placement alone does not complete build-and-use sequence");
            var placed = (AscendanceNexusBlockEntity) level.getBlockEntity(nexus);
            var serialized = placed.saveWithFullMetadata(level.registryAccess());
            var restored = (AscendanceNexusBlockEntity) BlockEntity.loadStatic(nexus, level.getBlockState(nexus),
                    serialized, level.registryAccess());
            if (restored != null) restored.setLevel(level);
            check(restored != null && builder.getUUID().equals(restored.survivalBuilder()),
                    "Actual native block-entity save/load preserves the placed Nexus survival builder UUID");
            var oldTag = serialized.copy(); oldTag.remove("survival_builder");
            var oldNexus = (AscendanceNexusBlockEntity) BlockEntity.loadStatic(nexus, level.getBlockState(nexus),
                    oldTag, level.registryAccess());
            check(oldNexus != null && oldNexus.survivalBuilder() == null,
                    "Legacy Nexus without placement provenance loads as unknown rather than guessing a builder");
            level.setBlockEntity(restored);
            var use = new BlockHitResult(Vec3.atCenterOf(nexus), Direction.UP, nexus, false);
            level.getBlockState(nexus).useWithoutItem(level, other, use);
            check(!done(other, 19) && !done(builder, 19), "A different player's successful Nexus use cannot complete either build sequence");
            other.closeContainer();
            level.getBlockState(nexus).useWithoutItem(level, builder, use);
            check(done(builder, 19), "The placing player's successful native Nexus menu opening completes build-and-use");
            builder.closeContainer(); builder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            builder.getAdvancements().revoke(advancement(builder, 19), "achieved");
            command(level, "clone 5 120 1 5 120 1 11 120 1");
            var copiedNexus = (AscendanceNexusBlockEntity) level.getBlockEntity(clonedNexus);
            check(copiedNexus != null && copiedNexus.survivalBuilder() == null,
                    "Actual native clone copies block data without valid placement provenance at the new address");
            builder.setPos(11.5, 120, 3);
            var cloneHit = new BlockHitResult(Vec3.atCenterOf(clonedNexus), Direction.UP, clonedNexus, false);
            level.getBlockState(clonedNexus).useWithoutItem(level, builder, cloneHit);
            check(!done(builder, 19), "A successful native menu opening on a command-cloned Nexus cannot replay the source's build credit");
            builder.closeContainer(); builder.setPos(5.5, 120, 3);
            level.getBlockState(nexus).useWithoutItem(level, builder, use);
            check(done(builder, 19), "The original survival-built Nexus retains valid build credit after cloning elsewhere");
            builder.closeContainer();
            builder.getAdvancements().revoke(advancement(builder, 19), "achieved");
            level.setBlock(nexus, Blocks.AIR.defaultBlockState(), 3);
            command(level, "clone 11 120 1 11 120 1 5 120 1");
            check(((AscendanceNexusBlockEntity) level.getBlockEntity(nexus)).survivalBuilder() == null,
                    "Cloning the invalid copy back to the original address cannot resurrect its erased placement provenance");
            level.getBlockState(nexus).useWithoutItem(level, builder, use);
            check(!done(builder, 19), "Native clone A-to-B-to-A followed by successful menu use cannot replay the original survival build");
            builder.closeContainer();
            level.setBlock(nexus, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(nexus, AscendanceNexusContent.ASCENDANCE_NEXUS.get().defaultBlockState(), 3);
            check(((AscendanceNexusBlockEntity) level.getBlockEntity(nexus)).survivalBuilder() == null,
                    "Same-address replacement starts a new Nexus with no old builder identity");
            level.getBlockState(nexus).useWithoutItem(level, builder, use);
            check(!done(builder, 19), "Opening an unknown replacement cannot replay the removed Nexus placement");
            builder.closeContainer(); level.setBlock(nexus, Blocks.AIR.defaultBlockState(), 3);
            builder.setGameMode(GameType.CREATIVE);
            builder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AscendanceNexusContent.ASCENDANCE_NEXUS_ITEM.get()));
            builder.getMainHandItem().useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit));
            check(((AscendanceNexusBlockEntity) level.getBlockEntity(nexus)).survivalBuilder() == null,
                    "Creative native Nexus placement stores no survival-builder evidence");
            builder.setGameMode(GameType.SURVIVAL);
            level.getBlockState(nexus).useWithoutItem(level, builder, use);
            check(!done(builder, 19), "Survival use cannot claim a prior creative Nexus placement");
            builder.closeContainer(); level.setBlock(nexus, Blocks.AIR.defaultBlockState(), 3);
            builder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AscendanceNexusContent.ASCENDANCE_NEXUS_ITEM.get()));
            builder.getMainHandItem().useOn(new UseOnContext(builder, InteractionHand.MAIN_HAND, hit));
            level.getBlockState(nexus).useWithoutItem(level, builder, use);
            check(done(builder, 19), "A fresh real survival placement and use can re-earn after replacement and revoke");
            builder.closeContainer(); builder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            level.setBlock(table, Blocks.CRAFTING_TABLE.defaultBlockState(), 3);
            builder.setPos(8.5, 120, 3);
            var crafting = new CraftingMenu(84, builder.getInventory(), ContainerLevelAccess.create(level, table));
            builder.containerMenu = crafting;
            crafting.clicked(0, 0, ClickType.PICKUP, builder);
            check(!done(builder, 28), "Empty result click cannot manufacture a Matrix craft");
            for (int slot : List.of(2, 4, 6, 8))
                crafting.getSlot(slot).set(new ItemStack(EssenceInfuserContent.LATENT_NUGGET.get()));
            crafting.getSlot(5).set(new ItemStack(Items.DIAMOND));
            check(crafting.getSlot(0).getItem().is(EssenceInfuserContent.ASCENDANCE_MATRIX.get()),
                    "Native crafting recipe prepares a Matrix result from its real ingredients");
            check(!done(builder, 28), "Prepared recipe output has not yet been crafted");
            crafting.clicked(0, 0, ClickType.PICKUP, builder);
            check(crafting.getCarried().is(EssenceInfuserContent.ASCENDANCE_MATRIX.get())
                            && crafting.getSlot(5).getItem().isEmpty() && done(builder, 28) && !done(other, 28),
                    "Actual native crafting result pickup consumes ingredients and awards its actor only");
            crafting.setCarried(ItemStack.EMPTY); builder.closeContainer();
        } finally {
            builder.containerMenu = builder.inventoryMenu; other.containerMenu = other.inventoryMenu;
            for (BlockPos pos : List.of(nexus, support, table, clonedNexus)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void equipment(ServerPlayer builder, ServerPlayer transcendent) {
        var head = equipment(AscendanceItems.ASCENDANCE_HELMET.get(), EquipmentTier.LATENT);
        var chest = equipment(AscendanceItems.ASCENDANCE_CHESTPLATE.get(), EquipmentTier.LATENT);
        var legs = equipment(AscendanceItems.ASCENDANCE_LEGGINGS.get(), EquipmentTier.LATENT);
        var feet = equipment(AscendanceItems.ASCENDANCE_BOOTS.get(), EquipmentTier.LATENT);
        FracturedEquipmentData.markFractured(feet);
        builder.setItemSlot(EquipmentSlot.HEAD, head); builder.setItemSlot(EquipmentSlot.CHEST, chest);
        builder.setItemSlot(EquipmentSlot.LEGS, legs); builder.setItemSlot(EquipmentSlot.FEET, feet);
        observe(builder);
        check(!done(builder, 61), "A fractured fourth native armor piece cannot complete a functional armor set");
        builder.setGameMode(GameType.CREATIVE);
        builder.setItemSlot(EquipmentSlot.FEET, equipment(AscendanceItems.ASCENDANCE_BOOTS.get(), EquipmentTier.LATENT));
        observe(builder);
        check(!done(builder, 61), "Creative equipment mutation does not fabricate a survival armor milestone");
        builder.setGameMode(GameType.SURVIVAL); observe(builder);
        check(!done(builder, 61), "Returning to survival with unchanged creative equipment cannot retroactively award it");
        builder.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); observe(builder);
        builder.setItemSlot(EquipmentSlot.FEET, equipment(AscendanceItems.ASCENDANCE_BOOTS.get(), EquipmentTier.LATENT));
        observe(builder);
        check(done(builder, 61), "Four intact native Ascendance armor pieces complete the set without an extra tier condition");
        builder.setItemSlot(EquipmentSlot.MAINHAND, equipment(AscendanceItems.ASCENDANCE_SWORD.get(), EquipmentTier.LATENT));
        builder.setItemSlot(EquipmentSlot.OFFHAND, equipment(AscendanceItems.ASCENDANCE_SHIELD.get(), EquipmentTier.LATENT));
        observe(builder);
        check(done(builder, 166), "Native main-hand Ascendance sword plus offhand shield completes their simultaneous loadout");
        builder.getAdvancements().revoke(advancement(builder, 61), "achieved");
        builder.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); observe(builder);
        command(builder.serverLevel(), "item replace entity AchOwner armor.feet with essence_ascendance:ascendance_boots");
        observe(builder);
        check(builder.getItemBySlot(EquipmentSlot.FEET).is(AscendanceItems.ASCENDANCE_BOOTS.get()) && !done(builder, 61),
                "Actual native item-replace command equips boots without fabricating a survival armor transition");
        builder.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); observe(builder);
        builder.setItemSlot(EquipmentSlot.FEET, equipment(AscendanceItems.ASCENDANCE_BOOTS.get(), EquipmentTier.LATENT)); observe(builder);
        check(done(builder, 61), "A fresh survival equipment transition can re-earn after the command baseline");
        builder.getAdvancements().revoke(advancement(builder, 166), "achieved");
        builder.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); observe(builder);
        command(builder.serverLevel(), "give AchOwner essence_ascendance:ascendance_sword");
        observe(builder);
        check(builder.getMainHandItem().is(AscendanceItems.ASCENDANCE_SWORD.get()) && !done(builder, 166),
                "Actual native give command filling the selected hand supplies no survival loadout evidence");
        builder.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); observe(builder);
        builder.setItemSlot(EquipmentSlot.MAINHAND, equipment(AscendanceItems.ASCENDANCE_SWORD.get(), EquipmentTier.LATENT)); observe(builder);
        check(done(builder, 166), "A fresh survival sword-and-shield transition can earn after an administrative give");

        transcendent.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        transcendent.setItemSlot(EquipmentSlot.HEAD, equipment(AscendanceItems.ASCENDANCE_HELMET.get(), EquipmentTier.TRANSCENDENT));
        transcendent.setItemSlot(EquipmentSlot.CHEST, equipment(AscendanceItems.ASCENDANCE_CHESTPLATE.get(), EquipmentTier.TRANSCENDENT));
        transcendent.setItemSlot(EquipmentSlot.LEGS, equipment(AscendanceItems.ASCENDANCE_LEGGINGS.get(), EquipmentTier.TRANSCENDENT));
        transcendent.setItemSlot(EquipmentSlot.FEET, equipment(AscendanceItems.ASCENDANCE_BOOTS.get(), EquipmentTier.TRANSCENDENT));
        transcendent.setItemSlot(EquipmentSlot.MAINHAND, equipment(AscendanceItems.ASCENDANCE_PICKAXE.get(), EquipmentTier.TRANSCENDENT));
        observe(transcendent);
        check(!done(transcendent, 72), "A Transcendent gathering tool is not the required main-hand weapon");
        transcendent.setItemSlot(EquipmentSlot.MAINHAND, equipment(AscendanceItems.ASCENDANCE_SWORD.get(), EquipmentTier.TRANSCENDENT));
        observe(transcendent);
        check(done(transcendent, 72), "Exactly four native Transcendent armor pieces plus its functional main-hand weapon earn the five-piece achievement");
        check(!done(transcendent, 139), "Transcendent player tier and sword do not substitute for a current glaive");
        transcendent.setItemSlot(EquipmentSlot.MAINHAND, equipment(AscendanceItems.ASCENDANCE_GLAIVE.get(), EquipmentTier.ASCENDANT));
        observe(transcendent);
        check(!done(transcendent, 139), "A lower-tier native glaive cannot meet the Transcendent conjunction");
        transcendent.setItemSlot(EquipmentSlot.MAINHAND, equipment(AscendanceItems.ASCENDANCE_GLAIVE.get(), EquipmentTier.TRANSCENDENT));
        observe(transcendent);
        check(done(transcendent, 139), "Transcendent player and functional current Transcendent glaive earn their joint milestone");
        transcendent.getAdvancements().revoke(advancement(transcendent, 72), "achieved");
        transcendent.getAdvancements().revoke(advancement(transcendent, 139), "achieved");
        transcendent.setItemSlot(EquipmentSlot.OFFHAND, equipment(AscendanceItems.ASCENDANCE_SHIELD.get(), EquipmentTier.LATENT));
        observe(transcendent);
        check(!TwoHandedEquipmentService.decision(transcendent).mayAttack(),
                "A real incompatible offhand makes the held Transcendent glaive nonfunctional under gameplay hand policy");
        command(transcendent.serverLevel(), "clear AchOperator essence_ascendance:ascendance_shield");
        observe(transcendent);
        check(transcendent.getOffhandItem().isEmpty() && !done(transcendent, 72) && !done(transcendent, 139),
                "Native clear command removing an incompatible offhand cannot fabricate a survival weapon/loadout transition");
        transcendent.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); observe(transcendent);
        transcendent.setItemSlot(EquipmentSlot.MAINHAND, equipment(AscendanceItems.ASCENDANCE_GLAIVE.get(), EquipmentTier.TRANSCENDENT)); observe(transcendent);
        check(done(transcendent, 72) && done(transcendent, 139),
                "A fresh survival wield transition can re-earn after the administrative clear baseline");
        var restoredRecord = PlayerEssenceData.load(EssenceSavedData.get(transcendent.serverLevel().getServer())
                .getPlayerData(transcendent.getUUID()).save());
        check(!restoredRecord.achievementProgress().observeEquipment("a139", true, true),
                "Native player-record serialization preserves a matching equipment checkpoint without replaying its award");
        transcendent.getAdvancements().revoke(advancement(transcendent, 72), "achieved");
        EquipmentObservations.refresh(transcendent); AchievementService.reconcile(transcendent);
        check(!done(transcendent, 72), "Unchanged native loadout and lifecycle reconciliation respect administrative revoke");
        transcendent.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY); observe(transcendent);
        transcendent.setItemSlot(EquipmentSlot.MAINHAND, equipment(AscendanceItems.ASCENDANCE_SWORD.get(), EquipmentTier.TRANSCENDENT));
        observe(transcendent);
        check(done(transcendent, 72), "A fresh qualifying equipment state transition can re-earn after revoke");
    }

    private static ItemStack equipment(Item item, EquipmentTier tier) {
        var stack = new ItemStack(item); EquipmentTierData.setTier(stack, tier); return stack;
    }
    private static void observe(ServerPlayer player) {
        TwoHandedEquipmentService.refresh(player); EquipmentObservations.observe(player);
    }

    private static void purchases(ServerPlayer player) {
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        var profile = EssenceConfigManager.get().balanceProfile();
        var targets = investments(player);
        long maximumHealthCap = profile.getInvestmentCap(data.getTier(), EssenceStats.MAX_HEALTH);
        check(maximumHealthCap > 0, "Runtime has a positive maximum-health Bonus purchase target");
        targets.put(EssenceStats.MAX_HEALTH.id(), maximumHealthCap);
        var rejected = transaction(player, targets, Map.of(), false);
        check(!rejected.accepted() && !done(player, 169), "Unfunded native Bonus transaction cannot earn a maximum-health purchase: " + rejected.status());
        data.setAvailable(EssenceTypes.VITALITY, Math.max(1_000_000_000L, maximumHealthCap));
        double before = player.getMaxHealth();
        var accepted = transaction(player, targets, Map.of(), false);
        check(accepted.accepted() && player.getMaxHealth() > before && done(player, 169),
                "Committed Vitality Bonus transaction actually raises native maximum health and awards Heart Container: " + accepted.status());

        check(!done(player, 26) && !done(player, 208), "Unrelated Bonus purchase does not fabricate historical Skill purchases");
        var emptyFunds = transaction(player, investments(player), Map.of(SkillIds.THREAT_SENSE, 1), false);
        check(!emptyFunds.accepted() && !done(player, 26), "Rejected unfunded Skill purchase has no first-purchase credit: " + emptyFunds.status());
        data.setAvailable(EssenceTypes.UTILITY, 1_000_000_000_000L);
        player.setGameMode(GameType.CREATIVE);
        var creative = transaction(player, investments(player), Map.of(SkillIds.FRIENDLY_FIRE_WARD, 1), false);
        check(creative.accepted() && !done(player, 26) && !done(player, 208),
                "Even a committed creative Skill mutation supplies no survival purchase evidence: " + creative.status());
        // The current catalog deliberately forbids Skill refunds; keep that gameplay policy intact.
        var creativeRefund = transaction(player, investments(player), Map.of(SkillIds.FRIENDLY_FIRE_WARD, 0), false);
        check(!creativeRefund.accepted() && data.skillRank(SkillIds.FRIENDLY_FIRE_WARD) == 1,
                "Native transaction rejects refund of a nonrefundable catalog Skill: " + creativeRefund.status());
        player.setGameMode(GameType.SURVIVAL);
        // These five registered Utility skills have no milestone gates; fixture stock/tier are setup only.
        var five = List.of(SkillIds.THREAT_SENSE, SkillIds.WAYLIGHT, SkillIds.RESTFUL_MENDING,
                SkillIds.METABOLIC_MENDING, SkillIds.MASTERWORK_TEMPERING);
        for (int i = 0; i < five.size(); i++) {
            var result = transaction(player, investments(player), Map.of(five.get(i), 1), false);
            check(result.accepted() && data.skillRank(five.get(i)) == 1, "Actual atomic Utility Skill transaction commits: " + five.get(i) + " " + result.status());
            check(done(player, 26), "First Skill completion remains earned after a successful purchase");
            check(done(player, 208) == (i == 4), "Five distinct Utility Skill threshold is exact at purchase " + (i + 1));
            if (i == 1) {
                var record = data.save();
                var restored = PlayerEssenceData.load(record);
                check(restored.achievementProgress().save().equals(data.achievementProgress().save()),
                        "Actual player-record codec restores the two-purchase lifetime partial and equipment checkpoints");
                var entries = NbtVersionCompat.getCompound(restored.achievementProgress().save(), "entries");
                check(NbtVersionCompat.getCompound(NbtVersionCompat.getCompound(entries, "a208"), "skills").size() == 2,
                        "Two committed Utility purchases remain two after same-process native player-record save/load");
            }
        }
        var refund = transaction(player, investments(player), Map.of(SkillIds.WAYLIGHT, 0), false);
        check(!refund.accepted() && data.skillRank(SkillIds.WAYLIGHT) == 1 && done(player, 208),
                "Rejected nonrefundable Skill transaction leaves the lifetime distinct purchase completion intact: " + refund.status());
        var respecTargets = investments(player); respecTargets.put(EssenceStats.MAX_HEALTH.id(), 0L);
        var respec = transaction(player, respecTargets, Map.of(), false);
        check(respec.accepted() && data.getInvested(EssenceStats.MAX_HEALTH) == 0 && done(player, 169) && done(player, 208),
                "Actual supported Bonus respec/refund preserves earned purchase completions and lifetime Utility history: " + respec.status());
    }

    private static Map<ResourceLocation, Long> investments(ServerPlayer player) {
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        Map<ResourceLocation, Long> targets = new LinkedHashMap<>();
        EssenceStatRegistry.values().forEach(stat -> targets.put(stat.id(), data.getInvested(stat))); return targets;
    }
    private static AscendanceNexusTransactionService.Result transaction(ServerPlayer player,
            Map<ResourceLocation, Long> targets, Map<ResourceLocation, Integer> purchases, boolean ascend) {
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        return AscendanceNexusTransactionService.apply(player, data.nexusRevision(), data.getTierId(),
                EssenceConfigManager.get().balanceProfile().id(), targets, purchases, Map.of(), ascend);
    }

    private static void progression(ServerLevel level, ServerPlayer player) {
        //? if >=26.1.2
        /*long originalTime = level.getDefaultClockTime();*/
        //? if <26.1.2
        long originalTime = level.getDayTime();
        command(level, "time set 1000"); level.updateSkyBrightness();
        var data = EssenceSavedData.get(player.server).getPlayerData(player.getUUID());
        check(data.getTierId().equals(AscendanceTiers.LATENT.id()), "Tier progression actor starts at native Latent state");
        check(AscendanceEngine.ascend(player).status() == AscendanceAttemptResult.Status.NOT_READY && !done(player, 31),
                "An unsuccessful real Ascension does not fabricate a tier milestone");
        var latent = EssenceConfigManager.serverRuntime().attunement().chapter(data.getTierId().toString());
        data.attunement().chapter(latent.id());
        var method = AttunementActivityRegistry.values().stream()
                .filter(activity -> latent.activities().get(activity.id()).stageAccessible()).findFirst().orElseThrow();
        // Controlled server-observed contribution inputs exercise the real accounting/auto-promotion commit.
        // They do not claim to prove the upstream damage, food or movement observer in this fixture.
        AttunementService.submit(player, "fixture-latent", method.id(), "fixture-latent-source", 1e100);
        check(data.getTierId().equals(AscendanceTiers.DORMANT.id()) && done(player, 31),
                "Real positive Attunement service contribution auto-promotes Latent to Dormant and earns ID31");
        check(!done(player, 36), "Latent automatic promotion cannot count as post-Latent six-seal mastery");
        check(!done(player, 205), "A daytime automatic Ascension in full armor cannot satisfy the nighttime celebration");
        int variety = 0;
        for (String activity : List.of("heal_health", "restore_hunger", "consume_hunger")) {
            var contribution = AttunementService.submit(player, "fixture-variety-" + activity, activity,
                    "fixture-variety-source-" + activity, .000001);
            check(contribution.finalContribution() > 0, "Real chapter accounting accepts a distinct positive method: " + activity);
            if (++variety == 2) {
                var restored = PlayerEssenceData.load(data.save());
                check(restored.achievementProgress().save().equals(data.achievementProgress().save())
                                && restored.attunement().chapterId().equals(data.attunement().chapterId()),
                        "Actual player-record save/load preserves chapter-scoped two-method partial and Attunement chapter");
                check(restored.achievementProgress().activity(data.attunement().chapterId(),
                                EssenceTypes.VITALITY.id().toString(), "consume_hunger"),
                        "A deserialized two-method chapter partial reaches its boundary on the third distinct credited method");
            }
        }
        check(done(player, 30), "Three positive method types in the same live category/chapter earn Training Montage");
        try {
            command(level, "time set 18000");
            level.updateSkyBrightness();
            for (int expectedOrder = 2; expectedOrder <= 5; expectedOrder++) {
                var chapter = EssenceConfigManager.serverRuntime().attunement().chapter(data.getTierId().toString());
                data.attunement().chapter(chapter.id());
                for (String category : chapter.categories().keySet()) {
                    var activity = AttunementActivityRegistry.values().stream().filter(a -> a.categoryId().equals(category)
                            && chapter.activities().get(a.id()).stageAccessible()).findFirst().orElseThrow();
                    var contribution = AttunementService.submit(player, "fixture-seal-" + expectedOrder + "-" + category,
                            activity.id(), "fixture-seal-source-" + expectedOrder + "-" + category, 1e100);
                    check(contribution.finalContribution() > 0, "Actual positive service contribution completes category seal: " + category);
                }
                check(AttunementService.completed(data.attunement(), chapter) == 6,
                        "Six category seals exist together in the same actual post-Latent ledger before Ascension");
                check(done(player, 36), "Six earned post-Latent seals are observed before the manual Ascension clears their chapter");
                var result = transaction(player, investments(player), Map.of(), true);
                check(result.accepted() && result.ascended() && data.getTier().order() == expectedOrder,
                        "Actual atomic manual Nexus Ascension commits next player tier " + expectedOrder + " " + result.status());
                check(done(player, 30 + expectedOrder), "Native tier milestone retained for actual transition " + expectedOrder);
                check(done(player, 36), "All six earned preclear seals count on post-Latent manual Ascension");
                check(done(player, 205), "Night and functional armor captured at actual Ascension commit earn celebration");
            }
        } finally { command(level, "time set " + originalTime); level.updateSkyBrightness(); }
        check(done(player, 31) && done(player, 32) && done(player, 33) && done(player, 34) && done(player, 35),
                "Every tier milestone remains complete after reaching Transcendent");
    }

    private static AdvancementHolder advancement(ServerPlayer player, int id) {
        return player.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("essence_ascendance", "achievements/a%03d".formatted(id)));
    }
    private static boolean done(ServerPlayer player, int id) { return done(player, advancement(player, id)); }

    private static ItemStack input() { return new ItemStack(Items.IRON_INGOT); }
    private static boolean done(ServerPlayer player, AdvancementHolder advancement) {
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
    private static void command(ServerLevel level, String command) {
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), command);
    }
    private static EssenceCrucibleMenu menu(EssenceCrucibleBlockEntity machine, ServerPlayer player) {
        var menu = new EssenceCrucibleMenu(83, player.getInventory(), machine);
        player.containerMenu = menu;
        return menu;
    }
    private static void insert(EssenceCrucibleBlockEntity machine, ServerPlayer player) {
        var menu = menu(machine, player);
        menu.setCarried(input());
        menu.clicked(0, 0, ClickType.PICKUP, player);
        check(menu.getCarried().isEmpty() && !machine.getItem(0).isEmpty(), "Native menu inserts supported input");
    }
    private static void tick(ServerLevel level, EssenceCrucibleBlockEntity machine) {
        int ticks = machine.structureStats().dissolutionTicksPerItem() + 1;
        for (int i = 0; i < ticks; i++)
            EssenceCrucibleBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
    }
    /** Real native load/flush/reload, separate from gameplay evidence and synthetic presentation grants. */
    @SuppressWarnings("unchecked")
    private static void presentationDiscovery(ServerPlayer player) throws Exception {
        var manager = player.serverLevel().getServer().getAdvancements();
        var advancements = player.getAdvancements();
        var visibleField = PlayerAdvancements.class.getDeclaredField("visible");
        visibleField.setAccessible(true);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 1) advancements.reload(manager);
            //? if >=26.1.2
            /*advancements.flushDirty(player, false);*/
            //? if <26.1.2
            advancements.flushDirty(player);
            var visible = (java.util.Set<AdvancementHolder>) visibleField.get(advancements);
            for (var id : AchievementPresentation.selectedIds()) {
                var holder = manager.get(id);
                check(holder != null && visible.contains(holder), "Unearned selection is sent on native join/reload: " + id);
                check(!advancements.getOrStartProgress(holder).isDone(), "Discovery never grants completion: " + id);
            }
            check(visible.stream().noneMatch(holder -> holder.id().getPath().equals("water_skiing")),
                    "Legacy compatibility never becomes a selected visible challenge");
        }
        // This deliberate native administrative grant tests layout independence, not gameplay.
        var child = manager.get(AchievementService.resource(35));
        advancements.award(child, "achieved");
        check(advancements.getOrStartProgress(child).isDone()
                        && !advancements.getOrStartProgress(manager.get(AchievementPresentation.ROOT)).isDone(),
                "A distant child can complete while its layout root remains incomplete");
        advancements.revoke(child, "achieved");
        //? if >=26.1.2
        /*advancements.flushDirty(player, false);*/
        //? if <26.1.2
        advancements.flushDirty(player);
        check(!advancements.getOrStartProgress(child).isDone(), "Discovery respects native revoke");
    }

    private static ServerPlayer player(ServerLevel level, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var client = ClientInformation.createDefault();
        var player = new ServerPlayer(level.getServer(), level, profile, client);
        var transport = new Connection(PacketFlow.SERVERBOUND);
        new ServerGamePacketListenerImpl(level.getServer(), transport, player, new CommonListenerCookie(profile, 0, client, false));
        var channel = new EmbeddedChannel(transport);
        channel.close().syncUninterruptibly();
        return player;
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
