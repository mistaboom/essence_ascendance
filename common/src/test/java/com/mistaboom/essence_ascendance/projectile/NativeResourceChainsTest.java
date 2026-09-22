package com.mistaboom.essence_ascendance.projectile;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.gathering.FishingLootService;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.utility.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Native loot, processor ticker, food use, trading and potion joins with in-memory world storage. */
public final class NativeResourceChainsTest {
    private static int checks;
    private NativeResourceChainsTest() { }
    public static void run() throws ReflectiveOperationException {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit native fixture required");
        var f = new ProjectileNativeInterceptionTest.Fixture(NativeGuardOutcomeTest.NativePlayer.class);
        NativeGuardOutcomeTest.registry(f);
        var inventoryMenu = new net.minecraft.world.inventory.InventoryMenu(f.player.getInventory(), true, f.player);
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.player.Player.class, f.player, "inventoryMenu", inventoryMenu);
        f.player.containerMenu = inventoryMenu;
        f.level.memoryBlocks = new HashMap<>();
        var oldTags = new HashMap<net.minecraft.tags.TagKey<Block>, List<Holder<Block>>>();
        BuiltInRegistries.BLOCK.getTags().forEach(entry -> oldTags.put(entry.getFirst(), entry.getSecond().stream().toList()));
        var tags = new HashMap<>(oldTags);
        tags.put(PlayerAttributedBlockHarvestService.CROP_YIELD_ELIGIBLE, List.of(Blocks.POTATOES.builtInRegistryHolder()));
        tags.put(ProcessingAccelerationService.INDUSTRIOUS_PROCESSORS, List.of(Blocks.FURNACE.builtInRegistryHolder()));
        BuiltInRegistries.BLOCK.bindTags(tags);
        java.lang.reflect.Field loaderLoot = null;
        Object previousLoaderLoot = null;
        try {
            loaderLoot = Class.forName("net.neoforged.neoforge.common.NeoForgeEventHandler").getDeclaredField("INSTANCE");
            loaderLoot.setAccessible(true); previousLoaderLoot = loaderLoot.get(null);
            if (previousLoaderLoot == null) loaderLoot.set(null,
                    Class.forName("net.neoforged.neoforge.common.loot.LootModifierManager").getConstructor().newInstance());
        } catch (ClassNotFoundException fabric) { /* Fabric has no global loot-modifier manager. */ }
        Runnable restoreFuel = nativeFuelData();
        try {
            resources(f);
            harvestCookEat(f);
            fishingEat(f);
            tradeRestock(f);
            potionRelay(f);
            sanctuary(f);
            containment(f);
        } finally {
            restoreFuel.run();
            if (loaderLoot != null) loaderLoot.set(null, previousLoaderLoot);
            BuiltInRegistries.BLOCK.bindTags(oldTags);
            f.close(); SkillEffectRuntime.clearAll();
        }
        System.out.println("Native resource chain checks passed: " + checks
                + " (vanilla loot/recipe, transformed processor ticks, consumption, repair, trade, relay, sanctuary and containment)");
    }
    @SuppressWarnings("unchecked")
    private static Runnable nativeFuelData() throws ReflectiveOperationException {
        Class<?> dataMaps;
        try { dataMaps = Class.forName("net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps"); }
        catch (ClassNotFoundException fabric) { return () -> {}; }
        Object fuelType = dataMaps.getField("FURNACE_FUELS").get(null);
        var field = Class.forName("net.neoforged.neoforge.registries.BaseMappedRegistry").getDeclaredField("dataMaps");
        field.setAccessible(true);
        var maps = (Map<Object, Object>)field.get(BuiltInRegistries.ITEM);
        Object previous = maps.get(fuelType);
        try (var stream = dataMaps.getResourceAsStream("/data/neoforge/data_maps/item/furnace_fuels.json")) {
            if (stream == null) throw new AssertionError("Missing native NeoForge fuel data");
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var codec = (com.mojang.serialization.Codec<?>)Class.forName("net.neoforged.neoforge.registries.datamaps.builtin.FurnaceFuel").getField("CODEC").get(null);
            Object coal = codec.parse(JsonOps.INSTANCE, json.getAsJsonObject("values").get("minecraft:coal")).getOrThrow();
            var entries = previous == null ? new HashMap<Object, Object>() : new HashMap<>((Map<Object, Object>)previous);
            entries.put(BuiltInRegistries.ITEM.getResourceKey(Items.COAL).orElseThrow(), coal);
            maps.put(fuelType, entries);
        } catch (java.io.IOException error) { throw new AssertionError(error); }
        return () -> { if (previous == null) maps.remove(fuelType); else maps.put(fuelType, previous); };
    }
    private static void enable(ProjectileNativeInterceptionTest.Fixture f, ServerPlayer player, ResourceLocation... ids) {
        var data = f.saved.getPlayerData(player.getUUID());
        data.setTier(com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT);
        SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
        data.clearAllSkillsForAdmin();
        data.grantAllSkillsForAdmin(Arrays.stream(ids).map(SkillRegistry::require).toList());
        for (var id : ids) {
            var group = SkillRegistry.require(id).choiceGroup();
            if (group != null) data.setLoadoutSelection(group, id);
            else if (SkillRegistry.require(id).activationPolicy() == SkillActivationPolicy.TOGGLE) data.setLoadoutSelection(id, id);
        }
        SkillEffectRuntime.refresh(player);
        for (var id : ids) check(SkillEffectRuntime.context(player).isEffective(id), "Native chain prerequisite active: " + id);
    }
    private static void resources(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var registries = new ArrayList<Registry<?>>();
        RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).registries().forEach(e -> registries.add(e.value()));
        f.level.memoryRegistries.registries().filter(e -> !e.key().equals(Registries.ENCHANTMENT)).forEach(e -> registries.add(e.value()));
        var enchantments = new MappedRegistry<net.minecraft.world.item.enchantment.Enchantment>(Registries.ENCHANTMENT, Lifecycle.stable());
        net.minecraft.data.registries.VanillaRegistries.createLookup().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .forEach(h -> enchantments.register(h.key(), h.value(), RegistrationInfo.BUILT_IN));
        enchantments.freeze(); registries.add(enchantments);
        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        f.level.memoryBiome = lookup.lookupOrThrow(Registries.BIOME).getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
        var loot = new MappedRegistry<LootTable>(Registries.LOOT_TABLE, Lifecycle.stable());
        for (String path : List.of("blocks/potatoes", "gameplay/fishing", "gameplay/fishing/fish", "gameplay/fishing/junk", "gameplay/fishing/treasure")) {
            var key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace(path));
            loot.register(key, LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, lookup),
                    vanillaJson("loot_table/" + path)).getOrThrow(), RegistrationInfo.BUILT_IN);
        }
        loot.freeze(); registries.add(loot);
        f.level.memoryRegistries = new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
        var recipe = Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, f.level.memoryRegistries),
                vanillaJson("recipe/baked_potato")).getOrThrow();
        f.level.memoryRecipes = new RecipeManager(f.level.memoryRegistries);
        f.level.memoryRecipes.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.withDefaultNamespace("baked_potato"), recipe)));
        var managers = ProjectileNativeInterceptionTest.instance(ReloadableServerResources.class);
        ProjectileNativeInterceptionTest.set(ReloadableServerResources.class, managers, "fullRegistryHolder", new ReloadableServerRegistries.Holder(f.level.memoryRegistries.freeze()));
        ProjectileNativeInterceptionTest.set(ReloadableServerResources.class, managers, "recipes", f.level.memoryRecipes);
        var ctor = Class.forName("net.minecraft.server.MinecraftServer$ReloadableResources").getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        ProjectileNativeInterceptionTest.set(MinecraftServer.class, f.server, "resources", ctor.newInstance(null, managers));
    }
    private static com.google.gson.JsonElement vanillaJson(String path) {
        try (var stream = NativeResourceChainsTest.class.getResourceAsStream("/data/minecraft/" + path + ".json")) {
            if (stream == null) {
                // NeoForge module isolation does not expose Minecraft's data through this module's classloader.
                // The opt-in runner supplies its resolved Minecraft dependency, never a copied recipe/loot model.
                String jar = System.getProperty("essence.native.vanillaDataJar");
                if (jar == null) throw new AssertionError("Missing native data: " + path);
                try (var archive = new java.util.zip.ZipFile(jar)) {
                    var entry = archive.getEntry("data/minecraft/" + path + ".json");
                    if (entry == null) throw new AssertionError("Missing native jar data: " + path);
                    try (var reader = new InputStreamReader(archive.getInputStream(entry), StandardCharsets.UTF_8)) {
                        return JsonParser.parseReader(reader);
                    }
                }
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
    private static void harvestCookEat(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        enable(f, f.player, SkillIds.HUNGER_WARD, SkillIds.METABOLIC_CONVERSION, SkillIds.METABOLIC_MENDING, SkillIds.INDUSTRIOUS_PRESENCE);
        var crop = new BlockPos(1, 0, 0);
        var mature = ((CropBlock)Blocks.POTATOES).getStateForAge(7);
        f.level.memoryBlocks.put(crop, mature);
        var hoe = com.mistaboom.essence_ascendance.item.AscendanceItems.ASCENDANCE_HOE.get().getDefaultInstance();
        EquipmentTierData.setTier(hoe, EquipmentTier.TRANSCENDENT);
        f.saved.getPlayerData(f.player.getUUID()).setInvested(com.mistaboom.essence_ascendance.stat.EssenceStats.CROP_YIELD, 100_000);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
        check(PlayerAttributedBlockHarvestService.cropYieldPercent(f.player) > 0, "Harvest uses the actual held tool's invested crop benefit");
        var drops = Block.getDrops(mature, f.level, crop, null, f.player, hoe);
        var harvest = PlayerAttributedBlockHarvestService.lastCropHarvest(f.player).orElseThrow();
        check(harvest.bonusPercent() > 0 && harvest.eligibleBaseUnits() > 0, "Transformed native loot applies crop yield to this exact player harvest");
        PlayerAttributedBlockHarvestService.forget(f.player);
        Block.getDrops(((CropBlock)Blocks.POTATOES).getStateForAge(0), f.level, crop, null, f.player, hoe);
        check(PlayerAttributedBlockHarvestService.lastCropHarvest(f.player).isEmpty(), "Immature native crop loot cannot earn mature harvest credit");
        int potatoes = drops.stream().filter(s -> s.is(Items.POTATO)).mapToInt(ItemStack::getCount).sum();
        check(potatoes > 0, "Native mature crop loot supplies real food input");
        var pos = new BlockPos(2, 0, 0);
        var state = Blocks.FURNACE.defaultBlockState();
        f.level.memoryBlocks.put(pos, state);
        var furnace = new FurnaceBlockEntity(pos, state); furnace.setLevel(f.level);
        furnace.setItem(0, new ItemStack(Items.POTATO, potatoes)); furnace.setItem(1, new ItemStack(Items.COAL));
        var ticker = boundTicker(f, furnace);
        int elapsed = 0;
        while (furnace.getItem(2).isEmpty() && elapsed < 200) { f.level.tick++; ticker.tick(); elapsed++; }
        check(furnace.getItem(2).is(Items.BAKED_POTATO) && elapsed < 200,
                "Transformed native processor ticker cooks harvested input faster than vanilla recipe time: " + furnace.saveWithoutMetadata(f.level.memoryRegistries));
        check(furnace.getItem(0).getCount() == potatoes - 1 && furnace.getItem(1).isEmpty(),
                "Accelerated native recipe still consumes exactly one input and native fuel");
        var meal = furnace.removeItem(2, 1);
        consumeAndRepair(f, meal, "harvest -> furnace -> meal");
        check(furnace.getItem(2).isEmpty(), "Eating withdrawn output cannot leave a duplicate in the processor");
        var ally = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(0, 0, 1), "processor_ally");
        enable(f, ally, SkillIds.INDUSTRIOUS_PRESENCE);
        f.level.tick++;
        var plan = ProcessingAccelerationService.plan(furnace);
        check(plan.active() && plan.multiplier() == SkillEffectRuntime.context(f.player).settings().utility().industriousPresence().processingSpeedMultiplier(),
                "Overlapping equal auras choose one native processing multiplier");
        check(!ProcessingAccelerationService.plan(furnace).active(), "Same processor cannot spend its acceleration twice in one world tick");
        enable(f, ally);
        enable(f, f.player, SkillIds.HUNGER_WARD, SkillIds.METABOLIC_CONVERSION, SkillIds.METABOLIC_MENDING);
        var progress = AbstractFurnaceBlockEntity.class.getDeclaredField("cookingProgress"); progress.setAccessible(true);
        int before = progress.getInt(furnace);
        for (int i = 0; i < 20; i++) { f.level.tick++; ticker.tick(); }
        check(progress.getInt(furnace) == before + 20, "Removing the final aura restores exactly one native processing tick per world tick");
    }
    private static TickingBlockEntity boundTicker(ProjectileNativeInterceptionTest.Fixture f, FurnaceBlockEntity furnace) throws ReflectiveOperationException {
        var chunk = ProjectileNativeInterceptionTest.instance(MemoryChunk.class); chunk.memoryLevel = f.level;
        ProjectileNativeInterceptionTest.set(LevelChunk.class, chunk, "level", f.level);
        var ctor = Class.forName("net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity").getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        BlockEntityTicker<FurnaceBlockEntity> tick = AbstractFurnaceBlockEntity::serverTick;
        return (TickingBlockEntity)ctor.newInstance(chunk, furnace, tick);
    }
    private static final class MemoryChunk extends LevelChunk {
        ProjectileNativeInterceptionTest.MemoryLevel memoryLevel;
        private MemoryChunk() { super(null, new ChunkPos(0, 0)); throw new AssertionError("Storage allocated without world constructors"); }
        @Override public BlockState getBlockState(BlockPos pos) { return memoryLevel.getBlockState(pos); }
        @Override public FullChunkStatus getFullStatus() { return FullChunkStatus.ENTITY_TICKING; }
    }
    private static void consumeAndRepair(ProjectileNativeInterceptionTest.Fixture f, ItemStack meal, String chain) {
        var tool = new ItemStack(Items.IRON_PICKAXE); tool.setDamageValue(100);
        f.player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        f.player.setHealth(5); f.player.getFoodData().setFoodLevel(20); f.player.getFoodData().setSaturation(0);
        int count = meal.getCount();
        int nutrition = meal.get(DataComponents.FOOD).nutrition();
        meal.finishUsingItem(f.level, f.player);
        double expected = nutrition * SkillEffectRuntime.context(f.player).settings().vitality().damage().metabolicConversion().healthPerNutrition();
        check(meal.getCount() == count - 1, chain + ": native consumption spends exactly one output");
        near(f.player.getHealth() - 5, expected, chain + ": native healing matches generated conversion");
        check(tool.getDamageValue() < 100, chain + ": the same consumed meal also repairs native durability");
    }
    private static void fishingEat(ProjectileNativeInterceptionTest.Fixture f) {
        ItemStack fish = ItemStack.EMPTY;
        for (int i = 0; i < 64 && fish.isEmpty(); i++) {
            var loot = FishingLootService.roll(f.player, f.player, f.player.position(), new ItemStack(Items.FISHING_ROD), 0);
            fish = loot.stream().filter(s -> s.is(Items.COD) || s.is(Items.SALMON)).findFirst().orElse(ItemStack.EMPTY);
        }
        check(!fish.isEmpty(), "Loaded vanilla fishing tables produce edible native catch");
        var original = fish.copy();
        int prior = f.player.getInventory().countItem(fish.getItem());
        check(FishingLootService.giveOrDrop(f.player, List.of(fish)) == fish.getCount(), "Catch delivery reports real inventory insertion");
        check(f.player.getInventory().countItem(fish.getItem()) == prior + fish.getCount() && ItemStack.matches(fish, original),
                "Catch inserted once without mutating loot evidence");
        int slot = f.player.getInventory().findSlotMatchingItem(fish);
        var meal = f.player.getInventory().removeItem(slot, 1);
        consumeAndRepair(f, meal, "fishing loot -> inventory -> meal");
        check(f.player.getInventory().countItem(fish.getItem()) == prior + fish.getCount() - 1, "Consumed catch is removed from inventory once");
    }
    private static void tradeRestock(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        enable(f, f.player, SkillIds.VILLAGE_PATRON, SkillIds.HUNGER_WARD, SkillIds.METABOLIC_CONVERSION, SkillIds.METABOLIC_MENDING);
        var villager = new Villager(EntityType.VILLAGER, f.level); villager.setPos(1, 0, 0); f.level.entities.add(villager);
        var offer = new MerchantOffer(new ItemCost(Items.EMERALD, 8), new ItemStack(Items.BREAD, 3), 1, 1, 0);
        villager.getOffers().clear(); villager.getOffers().add(offer);
        var priceMethod = Villager.class.getDeclaredMethod("updateSpecialPrices", net.minecraft.world.entity.player.Player.class);
        priceMethod.setAccessible(true); priceMethod.invoke(villager, f.player);
        int price = offer.getCostA().getCount();
        check(price >= 1 && price < 8, "Patron discount improves native offer without making payment free");
        var payment = new ItemStack(Items.EMERALD, 8);
        check(offer.take(payment, ItemStack.EMPTY), "Native offer accepts discounted payment");
        near(payment.getCount(), 8 - price, "Native trade spends its displayed discounted price");
        var meal = offer.assemble(); offer.increaseUses();
        check(offer.isOutOfStock(), "Successful native trade exhausts its finite stock");
        consumeAndRepair(f, meal, "paid trade -> meal");
        int interval = SkillEffectRuntime.context(f.player).settings().utility().villagePatron().restockIntervalTicks();
        for (int i = 0; i <= interval; i++) { f.level.tick++; VillagePatronService.tick(SkillEffectRuntime.context(f.player)); }
        check(!offer.isOutOfStock(), "Continuous nearby Patron presence restores exhausted native stock after the required interval");
        check(payment.getCount() == 8 - price, "Restocking cannot refund completed trade payment");
        f.level.entities.remove(villager);
    }
    private static void potionRelay(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var ally = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(1, 0, 0), "relay_ally");
        var stranger = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(1, 0, 1), "relay_stranger");
        var team = f.level.scoreboard.addPlayerTeam("resource_chain_allies");
        f.level.scoreboard.addPlayerToTeam(f.player.getScoreboardName(), team); f.level.scoreboard.addPlayerToTeam(ally.getScoreboardName(), team);
        enable(f, f.player, SkillIds.ALCHEMICAL_AMPLIFICATION, SkillIds.POTION_RELAY);
        enable(f, ally, SkillIds.ALCHEMICAL_AMPLIFICATION, SkillIds.POTION_RELAY);
        var potion = net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.REGENERATION);
        int originalDuration = potion.get(DataComponents.POTION_CONTENTS).getAllEffects().iterator().next().getDuration();
        var bottle = potion.finishUsingItem(f.level, f.player);
        check(potion.isEmpty() && bottle.is(Items.GLASS_BOTTLE), "Completed native potion spends one item and returns its bottle");
        int ownerDuration = f.player.getEffect(MobEffects.REGENERATION).getDuration();
        int relayDuration = (int)Math.floor(ownerDuration * SkillEffectRuntime.context(f.player).settings().utility().potionRelay().durationFraction());
        check(ownerDuration > originalDuration, "Amplification applies before the relay takes its duration fraction");
        check(ally.getEffect(MobEffects.REGENERATION).getDuration() == relayDuration, "Allied recipient receives exactly one derived native effect");
        check(!stranger.hasEffect(MobEffects.REGENERATION), "Nearby non-ally cannot borrow relay benefit");
        check(UtilityPotionService.relaySnapshot(SkillEffectRuntime.context(ally)) == null, "Recipient with Relay cannot recursively relay the same potion");
        ally.removeEffect(MobEffects.REGENERATION); f.player.removeEffect(MobEffects.REGENERATION);
        check(UtilityPotionService.amplificationSnapshot(SkillEffectRuntime.context(f.player)) == null, "Native removal clears amplified live-state information");
        enable(f, ally); enable(f, stranger);
    }
    private static void sanctuary(ProjectileNativeInterceptionTest.Fixture f) {
        enable(f, f.player, SkillIds.WAYLIGHT, SkillIds.SANCTUARY);
        check(UtilitySanctuaryService.suppressesNaturalMonsterSpawn(f.level, f.player.blockPosition()), "Effective Sanctuary protects its actual nearby position");
        var mob = new net.minecraft.world.entity.monster.Zombie(EntityType.ZOMBIE, f.level); mob.setPos(1, 0, 0); f.level.entities.add(mob);
        mob.setTarget(f.player);
        var context = SkillEffectRuntime.context(f.player); UtilitySanctuaryService.tick(context);
        f.level.tick += context.settings().utility().sanctuary().disengageDelayTicks();
        UtilitySanctuaryService.tick(SkillEffectRuntime.context(f.player));
        check(mob.getTarget() == null, "Sustained Sanctuary presence disengages a native hostile target");
        mob.setTarget(f.player);
        check(mob.getTarget() == null, "Transformed native target hook prevents immediate reacquisition");
        enable(f, f.player, SkillIds.WAYLIGHT);
        check(!UtilitySanctuaryService.suppressesNaturalMonsterSpawn(f.level, f.player.blockPosition()), "Removing Sanctuary immediately removes spawn protection");
        mob.setTarget(f.player);
        check(mob.getTarget() == f.player, "Removed Sanctuary cannot leak pacification into native targeting");
        f.level.entities.remove(mob);
    }
    private static void containment(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        enable(f, f.player, SkillIds.CONTAINMENT_FIELD);
        f.player.setHealth(20); f.player.invulnerableTime = 0; f.player.setDeltaMovement(Vec3.ZERO);
        var victim = f.player(NativeGuardOutcomeTest.NativePlayer.class, new Vec3(0, 0, 1), "unprotected_blast_target");
        var item = new net.minecraft.world.entity.item.ItemEntity(f.level, 1, 0, 0, new ItemStack(Items.POTATO));
        var priorEntities = new ArrayList<>(f.level.entities);
        f.level.entities.clear(); f.level.entities.addAll(List.of(f.player, victim, item));
        try {
            var blast = explosion(f); blast.getToBlow().add(new BlockPos(2, 0, 0)); blast.explode();
            near(f.player.getHealth(), 20, "Containment intercepts actual native explosion damage to its owner");
            check(f.player.getDeltaMovement().lengthSqr() == 0 && !blast.getHitPlayers().containsKey(f.player),
                    "Containment suppresses both native owner displacement and its client impulse packet");
            near(victim.getHealth(), 19, "Unprotected creature still takes native explosion damage inside containment");
            check(!item.isRemoved() && item.getItem().getCount() == 1, "Contained native blast preserves dropped resource items");
            check(blast.getToBlow().isEmpty(), "Containment clears the native terrain destruction list");
            check(ExplosionContainmentService.snapshot(SkillEffectRuntime.context(f.player)).containedThisTick() == 1,
                    "One actual contained blast creates one owner event");
            enable(f, f.player); f.player.invulnerableTime = 0;
            var uncontained = explosion(f); uncontained.getToBlow().add(new BlockPos(2, 0, 0)); uncontained.explode();
            near(f.player.getHealth(), 19, "Removing Containment immediately restores ordinary native owner damage");
            check(!uncontained.getToBlow().isEmpty(), "Removed field no longer clears terrain outcomes");
        } finally { f.level.entities.clear(); f.level.entities.addAll(priorEntities); }
    }
    private static Explosion explosion(ProjectileNativeInterceptionTest.Fixture f) {
        var calculator = new ExplosionDamageCalculator() {
            @Override public float getEntityDamageAmount(Explosion explosion, net.minecraft.world.entity.Entity entity) {
                return entity instanceof net.minecraft.world.entity.item.ItemEntity ? 20 : 1;
            }
            @Override public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos,
                    BlockState block, net.minecraft.world.level.material.FluidState fluid) { return Optional.of(100F); }
        };
        return new Explosion(f.level, null, f.player.damageSources().explosion(null, null), calculator,
                0, 0, -1, 2, false, Explosion.BlockInteraction.DESTROY,
                net.minecraft.core.particles.ParticleTypes.EXPLOSION, net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE);
    }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-5, message + ": " + actual + " != " + expected); }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
