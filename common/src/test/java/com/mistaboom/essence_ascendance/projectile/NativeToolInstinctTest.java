package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.gathering.GatheringToolResolver;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.*;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Native transformed break-speed prediction before any successful harvest or momentum event. */
public final class NativeToolInstinctTest {
    private static int checks;
    private NativeToolInstinctTest() { }

    static void run(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var previous = EssenceConfigManager.serverRuntime();
        var player = f.player(NativeGuardOutcomeTest.NativePlayer.class, Vec3.ZERO, "tool_instinct_startup");
        var oldTags = new HashMap<TagKey<Block>, List<Holder<Block>>>();
        BuiltInRegistries.BLOCK.getTags().forEach(entry -> oldTags.put(entry.getFirst(), entry.getSecond().stream().toList()));
        var tags = new HashMap<>(oldTags);
        tags.put(BlockTags.MINEABLE_WITH_PICKAXE, List.of(Blocks.STONE.builtInRegistryHolder()));
        tags.put(BlockTags.INCORRECT_FOR_WOODEN_TOOL, List.of(Blocks.STONE.builtInRegistryHolder()));
        BuiltInRegistries.BLOCK.bindTags(tags);
        try {
            var json = RuntimeBalanceDefinition.bootstrap().toJson();
            json.getAsJsonObject("equipment").getAsJsonObject(AscendanceTiers.TRANSCENDENT.id().toString()).addProperty("miningSpeed", 12);
            EssenceConfigManager.install(RuntimeBalanceDefinition.fromJson(json));
            ProjectileNativeInterceptionTest.set(Entity.class, player, "onGround", true);
            ProjectileNativeInterceptionTest.set(Entity.class, player, "fluidOnEyes", new HashSet<>());
            var data = f.saved.getPlayerData(player.getUUID());
            data.setTier(AscendanceTiers.TRANSCENDENT);
            data.clearAllSkillsForAdmin();
            var stone = Blocks.STONE.defaultBlockState();
            var held = new ItemStack(Items.STICK);
            var pick = new ItemStack(AscendanceItems.ASCENDANCE_PICKAXE.get());
            EquipmentTierData.setTier(pick, EquipmentTier.TRANSCENDENT);
            float staleNative = pick.getDestroySpeed(stone);
            check(staleNative < 12, "A fresh stack begins with its static item Tool component before any inventory tick");
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.getInventory().setItem(12, pick);
            float without = player.getDestroySpeed(stone);
            check(GatheringToolResolver.resolve(player, stone) == null, "Unpurchased skill cannot use an inventory tool");
            data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.TOOL_INSTINCT)));
            // Deliberately no tick, refresh, successfulHarvest or Mining Momentum purchase.
            float first = player.getDestroySpeed(stone);
            equal(pick.getDestroySpeed(stone), 12, "The first native query refreshes a newly upgraded tool without a manual sync or tick");
            check(first > without && first > pick.getDestroySpeed(stone),
                    "The first native break-speed query receives carried-tool speed and the correct-tool skill bonus: " + without + " -> " + first);
            check(player.hasCorrectToolForDrops(stone) && player.getMainHandItem() == held
                            && player.getInventory().getItem(12) == pick,
                    "First-query harvest correctness uses the carried tool and restores both inventory slots");
            check(!CommittedSkillService.effectiveIds(player).contains(SkillIds.MINING_MOMENTUM),
                    "No first-break momentum is present in this startup measurement");
            for (int i = 0; i < 4; i++) equal(player.getDestroySpeed(stone), first, "Repeated pre-harvest query is stable");
            try (var scope = GatheringToolResolver.beginHarvest(player, stone)) {
                check(scope.activeTool() == pick, "Native harvest presents the same resolved tool");
            }
            GatheringMiningEffects.successfulHarvest(player, net.minecraft.core.BlockPos.ZERO, stone, pick);
            equal(player.getDestroySpeed(stone), first, "A first successful harvest cannot be required to activate Tool Instinct");
            EquipmentTierData.setTier(pick, EquipmentTier.DORMANT);
            float dormant = player.getDestroySpeed(stone);
            check(dormant < first && pick.getDestroySpeed(stone) < 12,
                    "Changing physical tier refreshes carried mining on the next query, before an inventory tick");
            EquipmentTierData.setTier(pick, EquipmentTier.TRANSCENDENT);
            equal(player.getDestroySpeed(stone), first, "A just-upgraded carried tool immediately recovers its native physical speed");
            var iron = new ItemStack(Items.IRON_PICKAXE);
            player.getInventory().setItem(12, iron);
            check(player.getDestroySpeed(stone) > iron.getDestroySpeed(stone) && player.hasCorrectToolForDrops(stone),
                    "Tool Instinct applies its native speed advantage to external Tool-component equipment");
            check(player.getMainHandItem() == held && player.getInventory().getItem(12) == iron,
                    "External tool prediction restores the original inventory slots");
            var wood = new ItemStack(Items.WOODEN_PICKAXE);
            player.getInventory().setItem(12, wood);
            check(!wood.isCorrectToolForDrops(stone) && !player.hasCorrectToolForDrops(stone),
                    "Native incorrect-drop tools never gain harvest authorization");
            equal(player.getDestroySpeed(stone), wood.getDestroySpeed(stone),
                    "Incorrect-drop tools receive no correct-tool speed advantage");
            nativeRankFloors(f, player, stone, wood);
            data.clearAllSkillsForAdmin();
            equal(player.getDestroySpeed(stone), without, "Removing the skill immediately restores normal held-item mining");
            System.out.println("Native Tool Instinct startup checks passed: " + checks);
        } finally {
            BuiltInRegistries.BLOCK.bindTags(oldTags);
            EquipmentAttributeService.forget(player); SkillEffectRuntime.forget(player); CommittedSkillService.forget(player);
            f.players.remove(player.getUUID()); f.onlinePlayers.remove(player); f.level.entities.remove(player);
            if (previous == null) EssenceConfigManager.reset(); else EssenceConfigManager.install(previous);
        }
    }
    private static void nativeRankFloors(ProjectileNativeInterceptionTest.Fixture f,
            net.minecraft.server.level.ServerPlayer player, net.minecraft.world.level.block.state.BlockState stone,
            ItemStack incorrectTool) {
        var data = f.saved.getPlayerData(player.getUUID());
        var skill = SkillRegistry.require(SkillIds.TOOL_INSTINCT);
        var floor = skill.progressionRequirements().outcomes().getFirst();
        var tools = new ArrayList<ItemStack>();
        for (float speed : new float[]{1.01F, 2, 12}) {
            var tool = new ItemStack(Items.STICK);
            tool.set(net.minecraft.core.component.DataComponents.TOOL, new net.minecraft.world.item.component.Tool(
                    List.of(net.minecraft.world.item.component.Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, speed)), 1, 1));
            tools.add(tool);
        }
        var noAdvantage = new ItemStack(Items.STICK);
        noAdvantage.set(net.minecraft.core.component.DataComponents.TOOL, new net.minecraft.world.item.component.Tool(
                List.of(net.minecraft.world.item.component.Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, 1)), 1, 1));
        double[] previous = new double[tools.size()];
        for (int rank = 1; rank <= skill.maximumRank(); rank++) {
            data.applyNexusTransaction(Map.of(), Map.of(), Map.of(skill.id(),
                    new com.mistaboom.essence_ascendance.data.SkillPurchase(skill.essenceId(), Collections.nCopies(rank, 0L))),
                    Map.of(), data.getTierId());
            SkillEffectRuntime.refresh(player);
            double expected = SkillEffectRuntime.resolvedSettings(player).gathering().toolInstinct().maximumLowerTierSpeedBonus();
            for (int i = 0; i < tools.size(); i++) {
                var tool = tools.get(i);
                player.getInventory().setItem(12, tool);
                double speed = tool.getDestroySpeed(stone);
                double benefit = player.getDestroySpeed(stone) / speed - 1;
                check(tool.isCorrectToolForDrops(stone) && player.hasCorrectToolForDrops(stone),
                        "Native floor fixture uses a correct Tool component at speed " + speed);
                equal(benefit, expected, "Every eligible native tool receives the full generated percentage at rank " + rank);
                check(benefit - previous[i] + 1e-6 >= (rank == 1 ? floor.first() : floor.later()),
                        "Actual mining gain meets the authored first/later floor at speed " + speed + ", rank " + rank);
                previous[i] = benefit;
            }
            player.getInventory().setItem(12, incorrectTool);
            check(!player.hasCorrectToolForDrops(stone), "No published rank bypasses incorrect native drops");
            equal(player.getDestroySpeed(stone), incorrectTool.getDestroySpeed(stone),
                    "Incorrect tools gain no Skill speed at rank " + rank);
            player.getInventory().setItem(12, noAdvantage);
            check(player.hasCorrectToolForDrops(stone), "A native speed-one tool retains its real drop authorization");
            equal(player.getDestroySpeed(stone), 1, "Tools without a native speed advantage gain no Skill speed at rank " + rank);
        }
    }
    private static void equal(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1e-5, message + ": " + actual + " != " + expected);
    }
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
