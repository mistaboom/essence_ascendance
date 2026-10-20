package com.mistaboom.essence_ascendance.fixture;

import com.mistaboom.essence_ascendance.skill.effect.GuardMobilityController;

import com.mistaboom.essence_ascendance.equipment.EquipmentShieldService;
import com.mistaboom.essence_ascendance.equipment.FracturedEquipmentData;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Client-loader initialization fixture: creative cache validity, fullscreen hooks, sprint, native jump and step collision; no world. */
public final class GuardMovementNativeTest {
    private static int checks;
    private static Object allocator;
    private static Method allocate;
    private GuardMovementNativeTest() { }
    public static void runAndExit() {
        if (!Boolean.getBoolean("essence.guard.movementNativeTest")) throw new IllegalStateException("Explicit fixture flag required");
        try { run(); }
        catch (Throwable failure) { failure.printStackTrace(); System.exit(1); }
        System.exit(0);
    }
    public static void run() throws Exception {
        var field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true);
        allocator = field.get(null); allocate = allocator.getClass().getMethod("allocateInstance", Class.class);
        creativeCatalogInvalidation();
        optionalJeiCatalogOrder();
        var screenMethods = Arrays.stream(net.minecraft.client.gui.screens.Screen.class.getDeclaredMethods())
                .map(Method::getName).toList();
        for (String hook : List.of("composedWidgets", "composedRenderables", "finishComposedWidgets", "composedNarration"))
            check(screenMethods.stream().anyMatch(name -> name.contains(hook)),
                    "Actual native client Screen has the shared fullscreen widget hook: " + hook);
        if ("neoforge".equals(System.getProperty("essence.guard.fixtureLoader")))
            check(screenMethods.stream().anyMatch(name -> name.contains("composedEventWidgets")),
                    "Actual NeoForge Screen has the native initialization-event widget hook");
        check(Arrays.stream(Entity.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("guardedContact")), "Common native movement wrapper transformed");
        check(Arrays.stream(LivingEntity.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("guardedStep")), "Common native step return transformed");
        check(Arrays.stream(net.minecraft.client.renderer.GameRenderer.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("counterattackReach")),
                "Actual native client targeting renderer has the armed Riposte reach wrapper");
        Method sprint = LocalPlayer.class.getDeclaredMethod("canStartSprinting"); sprint.setAccessible(true);
        Method slowdown = Arrays.stream(LocalPlayer.class.getDeclaredMethods()).filter(m -> m.getName().contains("shieldSlowdown")).findFirst().orElseThrow(); slowdown.setAccessible(true);
        Method collide = Entity.class.getDeclaredMethod("collide", Vec3.class); collide.setAccessible(true);
        MemoryLevel level = instance(MemoryLevel.class);
        level.shapes = List.of(Shapes.create(new AABB(-2, -1, -2, 2, 0, 3)), Shapes.create(new AABB(-1, 0, .5, 1, 1, 2)));
        level.border = new WorldBorder();
        set(Level.class, level, "isClientSide", true); set(Level.class, level, "dimension", Level.OVERWORLD);
        FixturePlayer player = instance(FixturePlayer.class);
        set(Player.class, player, "inventory", new net.minecraft.world.entity.player.Inventory(player));
        player.food = new FoodData(); player.abilities = new Abilities(); player.cooldowns = new ItemCooldowns();
        player.input = new Input(); player.alive = true; player.using = true; player.grounded = true; player.readyTicks = 8;
        player.hand = InteractionHand.OFF_HAND;
        set(Entity.class, player, "uuid", new UUID(1, 42)); set(Entity.class, player, "level", level);
        set(Entity.class, player, "type", EntityType.PLAYER); set(Entity.class, player, "position", Vec3.ZERO);
        set(Entity.class, player, "deltaMovement", Vec3.ZERO); set(Entity.class, player, "bb", new AABB(-.3, 0, -.3, .3, 1.8, .3));
        set(LivingEntity.class, player, "attributes", new AttributeMap(Player.createAttributes().build()));
        player.shield = AscendanceItems.ASCENDANCE_SHIELD.get().getDefaultInstance(); player.held = player.shield;
        syncHandPolicy(player, true);
        sync(player, true, .85, 1);
        player.input.forwardImpulse = .88F;
        check(EquipmentShieldService.isGuarding(player), "Functional offhand shield is natively ready");
        check((boolean) sprint.invoke(player), "Actual transformed LocalPlayer can begin sprint during ready guard");
        syncHandPolicy(player, false);
        check(!EquipmentShieldService.isGuarding(player) && !(boolean) sprint.invoke(player),
                "Server offhand prohibition revokes shield guard and native guarded sprint");
        syncHandPolicy(player, true);
        if ("neoforge".equals(System.getProperty("essence.guard.fixtureLoader"))) {
            Method sprintKey = Arrays.stream(LocalPlayer.class.getDeclaredMethods()).filter(m -> m.getName().contains("guardedSprintKey")).findFirst().orElseThrow();
            sprintKey.setAccessible(true);
            check(!(boolean) sprintKey.invoke(player, true), "NeoForge's additional native sprint-key gate is transformed and permits active guard");
            sync(player, false, .85, 1);
            check((boolean) sprintKey.invoke(player, true), "NeoForge's additional key gate retains native item-use rejection when skill is inactive");
            sync(player, true, .85, 1);
        }
        for (double removal : new double[]{0, .1, .5, .85, 1}) {
            sync(player, true, removal, 1);
            player.input.forwardImpulse = (float) slowdown.invoke(player, .2F);
            check((boolean) sprint.invoke(player), "Native sprint input threshold survives configured guard multiplier " + removal);
        }
        sync(player, true, .85, 1); player.input.forwardImpulse = .88F;
        player.sprinting = true;
        check(Math.abs((float) slowdown.invoke(player, .2F) - .88F) < .00001 && player.sprinting, "Slowdown hook maintains already guarded sprint");
        player.jumpFromGround();
        check(player.using && player.getDeltaMovement().y > .4 && player.getDeltaMovement().z > .19, "Native sprint jump keeps guard and normal forward boost");
        check(Math.abs(player.exhaustion - .2F) < .00001, "Native guarded sprint jump still charges normal exhaustion");
        player.sprinting = false; player.setDeltaMovement(Vec3.ZERO); player.exhaustion = 0;
        player.jumpFromGround();
        check(player.using && player.getDeltaMovement().y > .4 && player.getDeltaMovement().z == 0 && player.exhaustion > 0,
                "Ordinary guarded jump retains vertical impulse, guard and native exhaustion");
        check(player.maxUpStep() == 1, "Native step query resolves active-only allowance");
        Vec3 stepped = (Vec3) collide.invoke(player, new Vec3(0, -.08, .8));
        check(stepped.z > .79 && stepped.y > .99, "Actual native collision traverses a one-block short obstacle while guarding");
        level.shapes = List.of(Shapes.create(new AABB(-2, -1, -2, 2, 0, 3)), Shapes.create(new AABB(-1, 0, .5, 1, 2, 2)));
        Vec3 wall = (Vec3) collide.invoke(player, new Vec3(0, -.08, .8));
        check(wall.z < .21 && wall.y <= 0, "Same native collision rejects a tall wall without climbing");
        level.shapes = List.of(Shapes.create(new AABB(-2, -1, -2, 2, 0, 3)), Shapes.create(new AABB(-1, 0, .5, 1, 1, 2)),
                Shapes.create(new AABB(-2, 2, -2, 2, 3, 3)));
        check(((Vec3) collide.invoke(player, new Vec3(0, -.08, .8))).z < .21, "Low ceiling prevents unsafe step traversal");
        player.using = false;
        check(close(player.maxUpStep(), .6) && close((float) slowdown.invoke(player, .2F), .2), "Lowering guard immediately restores ordinary step/input");
        player.using = true; player.readyTicks = 4;
        check(!(boolean) sprint.invoke(player) && !GuardMobilityController.active(player), "Before real raise-delay boundary no mobile guard");
        player.readyTicks = 5;
        check((boolean) sprint.invoke(player), "Exact native ready tick permits sprint");
        for (InteractionHand hand : InteractionHand.values()) {
            player.hand = hand; check((boolean) sprint.invoke(player), "Both functional held hands permit native guard movement");
        }
        player.held = player.shield.copy(); check(!(boolean) sprint.invoke(player), "Distinct swapped stack invalidates active movement immediately"); player.held = player.shield;
        player.cooldowns.addCooldown(player.shield.getItem(), 20); check(!(boolean) sprint.invoke(player), "Native shield cooldown stops guard movement"); player.cooldowns = new ItemCooldowns();
        player.food.setFoodLevel(6); check(!(boolean) sprint.invoke(player), "Native exact hunger boundary rejects sprint");
        player.food.setFoodLevel(7); check((boolean) sprint.invoke(player), "Food above native boundary permits sprint");
        player.blind = true; check(!(boolean) sprint.invoke(player), "Native blindness restriction remains"); player.blind = false;
        player.input.forwardImpulse = .3F * .88F; check(!(boolean) sprint.invoke(player), "Crouch-scaled native input cannot meet sprint threshold"); player.input.forwardImpulse = .88F;
        player.crouch = true; check(!(boolean) sprint.invoke(player) && close(player.maxUpStep(), .6), "Crouch rejects sprint and extra step"); player.crouch = false;
        player.riding = true; check(!(boolean) sprint.invoke(player) && close(player.maxUpStep(), .6), "Riding cannot gain guard movement"); player.riding = false;
        player.water = true; check(close(player.maxUpStep(), .6), "Water retains native step behavior"); player.water = false;
        player.ladder = true; check(close(player.maxUpStep(), .6), "Ladder retains native movement"); player.ladder = false;
        player.levitating = true; check(close(player.maxUpStep(), .6), "Levitation cannot become step climbing"); player.levitating = false;
        sync(player, false, .85, 1); check(!(boolean) sprint.invoke(player), "Synced effective skill loss immediately rejects sprint"); sync(player, true, .85, 1);
        player.alive = false; check(!(boolean) sprint.invoke(player), "Death clears functional movement permission"); player.alive = true;
        FracturedEquipmentData.markFractured(player.shield); check(!(boolean) sprint.invoke(player), "Fractured shield cannot grant movement");
        player.shield = Items.SHIELD.getDefaultInstance(); player.held = player.shield; sync(player, true, .85, 1);
        check(!(boolean) sprint.invoke(player), "Unregistered vanilla shield cannot inherit Ascendance guard skill contract");
        System.out.println("Guard movement native hooks passed: " + checks + " (transformed LocalPlayer sprint/slowdown, native jump and terrain-step collision; no world)");
    }
    /** If installed in this isolated fixture only, exercise JEI's actual transformed element constructor. */
    private static void optionalJeiCatalogOrder() throws Exception {
        Class<?> element;
        try { element = Class.forName("mezz.jei.gui.ingredients.ListElement"); }
        catch (ClassNotFoundException absent) {
            if (Boolean.getBoolean("essence.guard.requireJei"))
                throw new AssertionError("Explicit JEI fixture jar was not loaded", absent);
            System.out.println("Optional JEI catalog constructor probe: SKIP (JEI not installed)");
            return;
        }
        Class<?> typed = Class.forName("mezz.jei.api.ingredients.ITypedIngredient");
        var constructor = element.getConstructor(typed, int.class);
        Method createdIndex = element.getMethod("getCreatedIndex");
        Method sortedIndex = element.getMethod("getSortedIndex");
        var order = com.mistaboom.essence_ascendance.compat.jei.JeiCatalogOrder.INSTANCE;
        order.clear();
        try {
            java.util.function.Function<ItemStack, Object> ingredient = stack -> java.lang.reflect.Proxy.newProxyInstance(
                    typed.getClassLoader(), new Class<?>[]{typed}, (proxy, method, arguments) -> switch (method.getName()) {
                        case "getItemStack" -> java.util.Optional.of(stack);
                        case "getIngredient" -> stack;
                        case "toString" -> "Isolated JEI catalog ingredient";
                        default -> throw new UnsupportedOperationException(method.toString());
                    });
            Object first = constructor.newInstance(ingredient.apply(com.mistaboom.essence_ascendance.ore.LatentOreHost.stack(
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())), 10);
            order.runtimeAvailable();
            Object late = constructor.newInstance(ingredient.apply(com.mistaboom.essence_ascendance.ore.LatentOreHost.stack(
                    net.minecraft.world.level.block.Blocks.TUFF.defaultBlockState())), 90000);
            Object unrelated = constructor.newInstance(ingredient.apply(new ItemStack(Items.COAL)), 90001);
            check((int) createdIndex.invoke(first) == 10 && (int) createdIndex.invoke(late) == 10,
                    "Actual transformed JEI element anchors a late own subtype to the initial family");
            check((int) createdIndex.invoke(unrelated) == 90001, "Actual JEI hook preserves unrelated ingredients");
            check((int) sortedIndex.invoke(late) == 90000,
                    "JEI retains authority over its sorted index and configured sorting stages");
            var filter = Class.forName("mezz.jei.gui.ingredients.IngredientFilter");
            check(Arrays.stream(filter.getDeclaredMethods()).anyMatch(method -> method.getName().contains("sortNewVariants")),
                    "Actual JEI filter has the native sort invalidation hook");
            Object nativeFilter = allocate.invoke(allocator, filter);
            set(filter, nativeFilter, "listeners", List.of());
            var search = Class.forName("mezz.jei.gui.search.IElementSearch");
            Object searchIndex = java.lang.reflect.Proxy.newProxyInstance(search.getClassLoader(), new Class<?>[]{search},
                    (proxy, method, arguments) -> { throw new AssertionError("Catalog sort invalidation must not rebuild the search index: " + method); });
            set(filter, nativeFilter, "elementSearch", searchIndex);
            var additions = filter.getMethod("onIngredientsAdded",
                    Class.forName("mezz.jei.api.ingredients.IIngredientHelper"), java.util.Collection.class);
            additions.invoke(nativeFilter, null, List.of());
            var sortDirty = filter.getDeclaredField("sortIndexesDirty"); sortDirty.setAccessible(true);
            var searchDirty = filter.getDeclaredField("searchIndexDirty"); searchDirty.setAccessible(true);
            var elementSearch = filter.getDeclaredField("elementSearch"); elementSearch.setAccessible(true);
            check(sortDirty.getBoolean(nativeFilter), "Actual ingredient-addition hook schedules native sorting for the late family");
            check(!searchDirty.getBoolean(nativeFilter) && elementSearch.get(nativeFilter) == searchIndex,
                    "Catalog ordering neither dirties nor rebuilds the native search index");
            check(!order.takePendingSort(), "Actual ingredient-addition hook consumes the constructor's pending re-sort");
            sortDirty.setBoolean(nativeFilter, false);
            additions.invoke(nativeFilter, null, List.of());
            check(!sortDirty.getBoolean(nativeFilter), "Another empty ingredient event does not repeat the native re-sort");
            var info = Class.forName("mezz.jei.gui.ingredients.ListElementInfo");
            var infoElement = info.getDeclaredField("element"); infoElement.setAccessible(true);
            var comparators = Class.forName("mezz.jei.gui.ingredients.IngredientSorterComparators");
            var creative = comparators.getDeclaredMethod("getCreativeMenuComparator"); creative.setAccessible(true);
            @SuppressWarnings("unchecked") var comparator = (java.util.Comparator<Object>) creative.invoke(null);
            int index = 100000;
            for (var item : AscendanceItems.tieredCatalogItems()) {
                var stacks = new java.util.ArrayList<>(com.mistaboom.essence_ascendance.equipment.EquipmentCatalogStacks.allTiers(item));
                java.util.Collections.reverse(stacks);
                var infos = new java.util.ArrayList<Object>();
                for (var stack : stacks) {
                    Object entry = allocate.invoke(allocator, info);
                    infoElement.set(entry, constructor.newInstance(ingredient.apply(stack), index++));
                    check(entry instanceof com.mistaboom.essence_ascendance.compat.jei.JeiCatalogOrder.IngredientAccess,
                            "Actual JEI list info exposes its existing ingredient through the optional bridge");
                    infos.add(entry);
                }
                infos.sort(comparator);
                for (int tier = 0; tier < infos.size(); tier++) {
                    var stack = ((com.mistaboom.essence_ascendance.compat.jei.JeiCatalogOrder.IngredientAccess) infos.get(tier))
                            .essenceAscendance$catalogStack();
                    check(com.mistaboom.essence_ascendance.equipment.EquipmentTierData.tier(stack).ordinal() == tier,
                            "Actual transformed JEI creative comparator orders every equipment/armor family by tier");
                }
            }
            System.out.println("Optional JEI catalog order probe: PASS (actual transformed constructors, ingredient-addition sort invalidation, list info and native creative comparator; every equipment/armor family, no full index or world)");
        } finally { order.clear(); }
    }

    private static void creativeCatalogInvalidation() {
        var flags = net.minecraft.world.flag.FeatureFlags.VANILLA_SET;
        var holders = net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty());
        var globalCache = new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(flags, false, holders);
        var tabCache = new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(flags, false, holders);
        check(!globalCache.needsUpdate(flags, false, holders) && !tabCache.needsUpdate(flags, false, holders),
                "Unchanged native creative parameters reuse both global and per-tab caches");
        com.mistaboom.essence_ascendance.item.CreativeVariantRegistry.invalidate();
        check(globalCache.needsUpdate(flags, false, holders) && tabCache.needsUpdate(flags, false, holders),
                "Actual transformed native parameters invalidate every cached catalog without changing world inputs");
        var current = new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(flags, false, holders);
        check(!current.needsUpdate(flags, false, holders), "Rebuilt parameters acknowledge the current catalog once");
        check(current.needsUpdate(flags, true, holders), "Native creative permission invalidation remains intact");
        check(current.needsUpdate(net.minecraft.world.flag.FeatureFlagSet.of(), false, holders),
                "Native creative feature invalidation remains intact");
        check(current.needsUpdate(flags, false,
                net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty())),
                "Native creative registry invalidation remains intact");
        com.mistaboom.essence_ascendance.item.CreativeVariantRegistry.invalidate();
        check(current.needsUpdate(flags, false, holders), "Disconnect/catalog replacement invalidates a later cache too");
    }

    private static void syncHandPolicy(FixturePlayer player, boolean offhandUse) {
        TwoHandedPolicyPayload.accept(player,
                new TwoHandedEquipmentService.Decision(
                        offhandUse ? TwoHandedEquipmentService.Status.COMPATIBLE
                                : TwoHandedEquipmentService.Status.INCOMPATIBLE,
                        !offhandUse, !offhandUse, offhandUse, offhandUse, 1, 1));
    }
    private static void sync(FixturePlayer player, boolean enabled, double removal, double step) {
        var data = new CompoundTag(); data.putUUID("holder", player.getUUID()); data.putBoolean("enabled", enabled);
        data.putDouble("removal", removal); data.putDouble("step", step);
        CustomData.update(DataComponents.CUSTOM_DATA, player.shield, tag -> tag.put("essence_ascendance_guard_mobility", data));
    }
    /** Only environmental reads and cosmetic bookkeeping are fixtures. Native methods under test are inherited. */
    private static final class FixturePlayer extends LocalPlayer {
        ItemStack shield, held; InteractionHand hand; FoodData food; Abilities abilities; ItemCooldowns cooldowns;
        boolean alive, using, sprinting, grounded, blind, crouch, riding, water, ladder, levitating;
        int readyTicks; float exhaustion;
        private FixturePlayer() { super(null, null, null, null, null, false, false); throw new AssertionError("No client player/world constructor"); }
        @Override public boolean isAlive() { return alive; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isCreative() { return false; }
        @Override public boolean isSleeping() { return false; }
        @Override public boolean isUsingItem() { return using; }
        @Override public ItemStack getUseItem() { return using ? shield : ItemStack.EMPTY; }
        @Override public InteractionHand getUsedItemHand() { return hand; }
        @Override public ItemStack getItemInHand(InteractionHand hand) { return hand == this.hand ? held : ItemStack.EMPTY; }
        @Override public ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
            return switch (slot) {
                case MAINHAND -> getItemInHand(InteractionHand.MAIN_HAND);
                case OFFHAND -> getItemInHand(InteractionHand.OFF_HAND);
                default -> super.getItemBySlot(slot);
            };
        }
        @Override public int getTicksUsingItem() { return readyTicks; }
        @Override public ItemCooldowns getCooldowns() { return cooldowns; }
        @Override public FoodData getFoodData() { return food; }
        @Override public Abilities getAbilities() { return abilities; }
        @Override public boolean isSprinting() { return sprinting; }
        @Override public void setSprinting(boolean value) { sprinting = value; }
        @Override public boolean isShiftKeyDown() { return crouch; }
        @Override public boolean isPassenger() { return riding; }
        @Override public boolean isFallFlying() { return false; }
        @Override public boolean isUnderWater() { return water; }
        @Override public boolean isInWater() { return water; }
        @Override public boolean isInLava() { return false; }
        @Override public boolean onClimbable() { return ladder; }
        @Override public boolean onGround() { return grounded; }
        @Override public boolean hasEffect(Holder<MobEffect> effect) { return effect == MobEffects.BLINDNESS && blind || effect == MobEffects.LEVITATION && levitating; }
        @Override protected float getJumpPower() { return .42F; }
        @Override public void awardStat(ResourceLocation stat) { }
        @Override public void causeFoodExhaustion(float amount) { exhaustion += amount; }
    }
    private static final class MemoryLevel extends ServerLevel {
        List<VoxelShape> shapes; WorldBorder border;
        private MemoryLevel() { super(null, null, null, null, Level.OVERWORLD, null, null, false, 0, List.of(), false, null); throw new AssertionError("No world constructor"); }
        @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB bounds) { return List.of(); }
        @Override public Iterable<VoxelShape> getBlockCollisions(Entity entity, AABB bounds) { return shapes; }
        @Override public WorldBorder getWorldBorder() { return border; }
    }
    private static <T> T instance(Class<T> type) throws Exception { return type.cast(allocate.invoke(allocator, type)); }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
    private static boolean close(double a, double b) { return Math.abs(a - b) < .00001; }
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
}
