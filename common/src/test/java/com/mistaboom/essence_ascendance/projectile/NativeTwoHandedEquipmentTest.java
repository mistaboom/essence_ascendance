package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import java.util.*;

/** Real transformed mutations/packets/native protection. Uses the existing no-world fixture. */
public final class NativeTwoHandedEquipmentTest {
    private static int checks;
    public static void run() throws ReflectiveOperationException {
        var f = new ProjectileNativeInterceptionTest.Fixture(PolicyPlayer.class);
        NativeGuardOutcomeTest.registry(f);
        var p = (PolicyPlayer) f.player;
        var oldTags = new HashMap<TagKey<Item>, List<Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(t -> oldTags.put(t.getFirst(), t.getSecond().stream().toList()));
        var tags = new HashMap<>(oldTags);
        tags.put(TwoHandedEquipmentPolicy.PASSIVE_OFFHAND, List.of(Items.TOTEM_OF_UNDYING.builtInRegistryHolder(),
                Items.SHIELD.builtInRegistryHolder(), Items.IRON_SWORD.builtInRegistryHolder(), Items.BOW.builtInRegistryHolder(),
                Items.APPLE.builtInRegistryHolder(), Items.FISHING_ROD.builtInRegistryHolder()));
        BuiltInRegistries.ITEM.bindTags(tags);
        TwoHandedEquipmentPolicy.rulesChanged();
        try {
            disconnectedPlayers(f);
            var glaive = new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, glaive);
            var totem = new ItemStack(Items.TOTEM_OF_UNDYING);
            p.setItemInHand(InteractionHand.OFF_HAND, totem);
            check(valid(p), "Passive tag resolves immediately after native equipment setter");
            var d = TwoHandedEquipmentService.decision(p);
            check(d.twoHanded() && !d.offhandUse() && !d.offhandVisible(), "Three independent policy decisions");
            var packet = new com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload(
                    p.level().dimension().location(), p.getId(), p.getUUID(), d);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), f.level.registryAccess());
            try {
                com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.CODEC.encode(buffer, packet);
                check(packet.equals(com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.CODEC.decode(buffer)),
                        "Server decision payload preserves identity, separate flags and revisions");
            } finally { buffer.release(); }
            long before = TwoHandedEquipmentPolicy.classifications();
            int damage = glaive.getDamageValue();
            for (int i = 0; i < 100; i++) {
                check(TwoHandedEquipmentService.validateAttack(p), "Cached compatible validation");
                check(WeaponActionRuntime.attempt(p, false)==(i==0), "Only the first ready swipe press accepts; repeats never queue");
                p.attack(p);
            }
            check(before == TwoHandedEquipmentPolicy.classifications(), "Repeated attack and active-action checks never classify");
            check(glaive.getDamageValue() == damage, "Rejected actions do not spend durability");
            for (Item item : List.of(Items.SHIELD, Items.IRON_SWORD, Items.BOW, Items.FISHING_ROD, Items.APPLE,
                    Items.IRON_AXE, Items.TRIDENT, AscendanceItems.ASCENDANCE_CASTER.get(), Items.STICK)) {
                var stack = new ItemStack(item);
                p.setItemInHand(InteractionHand.OFF_HAND, stack);
                check(TwoHandedEquipmentService.decision(p).status() == TwoHandedEquipmentService.Status.INCOMPATIBLE,
                        "Combat/active roles override passive tag; unknown defaults closed: " + item);
                check(p.getOffhandItem() == stack, "Native equipment query returns the actual incompatible stack");
            }
            p.messages = 0;
            for (int i = 0; i < 10; i++) { WeaponActionRuntime.attempt(p, false); WeaponActionRuntime.attempt(p, true); }
            check(p.messages == 1 && p.messageKey.equals("message.essence_ascendance.incompatible_offhand"), "Both rejected actions share throttled localized message");
            p.setItemInHand(InteractionHand.OFF_HAND, totem);
            check(valid(p), "Shield-to-passive change is valid without a tick");
            var modifiers = ItemAttributeModifiers.builder().add(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(net.minecraft.resources.ResourceLocation.withDefaultNamespace("policy_test"), 2,
                            AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND).build();
            before = TwoHandedEquipmentPolicy.classifications();
            totem.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
            check(!valid(p) && TwoHandedEquipmentPolicy.classifications() == before + 1, "Relevant in-place attributes refresh immediately");
            totem.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            check(valid(p), "Attribute removal refreshes immediately");
            before = TwoHandedEquipmentPolicy.classifications();
            TwoHandedEquipmentService.enterCombat();
            try {
                totem.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers);
                check(TwoHandedEquipmentPolicy.classifications() == before
                        && TwoHandedEquipmentService.decision(p).status() == TwoHandedEquipmentService.Status.PENDING,
                        "Relevant mutation during combat stays pending without classification");
            } finally { TwoHandedEquipmentService.exitCombat(); }
            TwoHandedEquipmentService.reconcile(p);
            check(!valid(p), "Combat-time dirty state refreshes outside combat");
            totem.remove(DataComponents.ATTRIBUTE_MODIFIERS);
            before = TwoHandedEquipmentPolicy.classifications();
            for (int i = 0; i < 50; i++) {
                glaive.setDamageValue(i);
                totem.setCount(2 + i);
                totem.set(DataComponents.CUSTOM_NAME, Component.literal("Name " + i));
                CustomData.update(DataComponents.CUSTOM_DATA, totem, tag -> tag.putInt("irrelevant_tint", 123));
            }
            check(before == TwoHandedEquipmentPolicy.classifications() && valid(p), "Durability, nonzero count, name and unrelated custom data do not reclassify");
            p.setItemInHand(InteractionHand.OFF_HAND, totem.copy());
            check(before == TwoHandedEquipmentPolicy.classifications(), "Harmless copied-stack replacement keeps semantic answer");
            totem = p.getOffhandItem(); totem.setCount(1);
            var patch = DataComponentPatch.builder().set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers).build();
            totem.applyComponents(patch);
            check(!valid(p), "Bulk component patch refreshes completed semantics");
            totem.applyComponentsAndValidate(DataComponentPatch.builder().remove(DataComponents.ATTRIBUTE_MODIFIERS).build());
            check(valid(p), "Validated patch observes final accepted state");

            // Reload the actual bound tags, exercising generation refresh without any equipment mutation.
            tags.put(TwoHandedEquipmentPolicy.PASSIVE_OFFHAND, List.of());
            BuiltInRegistries.ITEM.bindTags(tags); TwoHandedEquipmentPolicy.rulesChanged();
            check(!valid(p), "Rule/tag generation refreshes cached compatibility");
            tags.put(TwoHandedEquipmentPolicy.PASSIVE_OFFHAND, List.of(Items.TOTEM_OF_UNDYING.builtInRegistryHolder()));
            BuiltInRegistries.ITEM.bindTags(tags); TwoHandedEquipmentPolicy.rulesChanged();
            check(valid(p), "Passive membership restored without a tick");

            EquipmentStatProviderRegistry.register(new EquipmentStatProvider() {
                private boolean flag(ItemStack s, String name) {
                    var data = s.get(DataComponents.CUSTOM_DATA);
                    return s.is(Items.STICK) && data != null && data.copyTag().getBoolean(name);
                }
                public EquipmentStatProfile evaluate(ItemStack s) {
                    return flag(s, "policy_combat") ? new EquipmentStatProfile(Map.of(EquipmentActivationType.HELD,
                            Map.of(EssenceStats.MELEE_DAMAGE.id(), 1.0))) : EquipmentStatProfile.none();
                }
                public boolean passiveOffhand(ItemStack s) { return flag(s, "policy_passive"); }
                public Set<DataComponentType<?>> equipmentPolicyComponents() { return Set.of(DataComponents.CUSTOM_DATA); }
                public Object equipmentPolicyKey(ItemStack s) {
                    return s.is(Items.STICK) ? List.of(flag(s, "policy_passive"), flag(s, "policy_combat")) : null;
                }
            });
            var charm = new ItemStack(Items.STICK);
            p.setItemInHand(InteractionHand.OFF_HAND, charm);
            check(!valid(p), "Provider requires explicit approval");
            CustomData.update(DataComponents.CUSTOM_DATA, charm, t -> t.putBoolean("policy_passive", true));
            check(valid(p), "Provider component-sensitive passive approval refreshes immediately");
            before = TwoHandedEquipmentPolicy.classifications();
            CustomData.update(DataComponents.CUSTOM_DATA, charm, t -> t.putInt("unrelated_color", 9));
            check(before == TwoHandedEquipmentPolicy.classifications(), "Provider semantic projection ignores unrelated custom fields");
            CustomData.update(DataComponents.CUSTOM_DATA, charm, t -> t.putBoolean("policy_combat", true));
            check(!valid(p), "Provider combat role overrides its passive approval");
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));

            // Native menu pickup/number-key swap and drag leave all inventory operations ordinary.
            var shield = new ItemStack(Items.SHIELD);
            p.getInventory().setItem(1, shield);
            before = TwoHandedEquipmentPolicy.classifications();
            f.listener.handleContainerClick(new ServerboundContainerClickPacket(p.inventoryMenu.containerId,
                    p.inventoryMenu.getStateId(), 45, 1, ClickType.SWAP, ItemStack.EMPTY,
                    new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));
            check(p.getOffhandItem() == shield && !valid(p), "Number-key passive-to-shield packet resolves at completion");
            check(TwoHandedEquipmentPolicy.classifications() == before + 1, "Multi-write native menu swap classifies only its completed hands");
            p.inventoryMenu.clicked(45, 1, ClickType.SWAP, p);
            check(p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && valid(p), "Same-tick reverse menu swap resolves at completion");
            p.inventoryMenu.clicked(45, 0, ClickType.PICKUP, p);
            check(p.getOffhandItem().isEmpty() && valid(p) && p.inventoryMenu.getCarried().is(Items.TOTEM_OF_UNDYING), "Menu pickup unchanged");
            p.inventoryMenu.clicked(45, 0, ClickType.PICKUP, p);
            check(valid(p) && p.inventoryMenu.getCarried().isEmpty(), "Menu placement unchanged");
            p.inventoryMenu.clicked(45, 0, ClickType.PICKUP, p);
            p.inventoryMenu.clicked(-999, 0, ClickType.QUICK_CRAFT, p);
            p.inventoryMenu.clicked(45, 1, ClickType.QUICK_CRAFT, p);
            p.inventoryMenu.clicked(-999, 2, ClickType.QUICK_CRAFT, p);
            check(p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) && valid(p)
                    && p.inventoryMenu.getCarried().isEmpty(), "Native drag into offhand remains unchanged");
            for (int i = 2; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            f.listener.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN));
            check(p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) && p.getOffhandItem() == glaive,
                    "F swap works with a full inventory and glaive may reside in offhand");
            check(glaive.use(f.level, p, InteractionHand.OFF_HAND).getResult() == net.minecraft.world.InteractionResult.FAIL,
                    "A glaive residing in offhand cannot attack");
            check(valid(p) && TwoHandedEquipmentService.decision(p).offhandUse(), "Offhand glaive imposes no two-hand main policy");
            f.listener.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN));
            check(p.getMainHandItem() == glaive && valid(p), "F reverse swap immediately restores selected policy");
            check(java.util.stream.IntStream.range(2, 36).allMatch(i -> p.getInventory().getItem(i).is(Items.COBBLESTONE)
                    && p.getInventory().getItem(i).getCount() == 64), "Full-inventory F swaps preserve every other stack");
            p.getInventory().setItem(1, shield);
            f.listener.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(1));
            check(!TwoHandedEquipmentService.decision(p).twoHanded(), "Selected hotbar packet completes cache refresh");
            f.listener.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(0));
            check(TwoHandedEquipmentService.decision(p).twoHanded() && valid(p), "Hotbar switch back is immediate");
            ProjectileNativeInterceptionTest.set(net.minecraft.server.level.ServerPlayerGameMode.class, p.gameMode,
                    "gameModeForPlayer", GameType.CREATIVE);
            f.listener.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(45, shield));
            check(!valid(p) && p.getOffhandItem().is(Items.SHIELD), "Creative slot mutation resolves before return");
            f.listener.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(45, new ItemStack(Items.TOTEM_OF_UNDYING)));
            check(valid(p), "Creative reverse mutation is immediate");
            ProjectileNativeInterceptionTest.set(net.minecraft.server.level.ServerPlayerGameMode.class, p.gameMode,
                    "gameModeForPlayer", GameType.SURVIVAL);

            // Native combat/offhand packets are consumed; main-hand interactions retain native priority.
            p.setItemInHand(InteractionHand.OFF_HAND, shield);
            f.level.tick += 21; p.messages = 0;
            f.listener.handleAnimate(new ServerboundSwingPacket(InteractionHand.OFF_HAND));
            f.listener.handleUseItem(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, 1, 0, 0));
            check(p.messages == 0, "Offhand packets are suppressed without becoming main-hand glaive attempts");
            float readiness = p.getAttackStrengthScale(0);
            f.listener.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            check(p.getAttackStrengthScale(0) == readiness, "Rejected native animation cannot reset cooldown or trigger swing skills");
            f.listener.handleUseItem(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, 1, 0, 0));
            check(!p.isUsingItem(), "Rejected stab cannot fall through to shield use");
            var far = new BlockPos(100000, 80, 100000);
            f.listener.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(far), Direction.UP, far, false), 2));
            f.listener.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    far, Direction.UP, 3));
            check(p.getOffhandItem() == shield && glaive.getDamageValue() == 49, "Consumed use/mining packets leave equipment unchanged");
            interactions(f);
            p.setItemInHand(InteractionHand.MAIN_HAND, glaive);

            // Held/lowered reflection, active guard, and ordinary restoration use the same policy.
            var ownShield = new ItemStack(AscendanceItems.ASCENDANCE_SHIELD.get());
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setItemInHand(InteractionHand.OFF_HAND, ownShield);
            check(EquipmentShieldService.heldContext(p) != null && EquipmentShieldService.canGuard(p, ownShield), "Ordinary held shield control");
            p.startUsingItem(InteractionHand.OFF_HAND);
            check(p.isUsingItem(), "Native shield raises with one-handed main");
            p.setItemInHand(InteractionHand.MAIN_HAND, glaive);
            check(!p.isUsingItem(), "Selecting two-handed equipment stops active offhand use");
            p.startUsingItem(InteractionHand.OFF_HAND);
            check(!p.isUsingItem() && !EquipmentShieldService.canGuard(p, ownShield), "Native active-use setter cannot bypass policy");
            check(EquipmentShieldService.heldContext(p) == null, "Lowered shield context is suppressed");
            p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(AscendanceItems.ASCENDANCE_CHESTPLATE.get()));
            var armor = p.getItemBySlot(EquipmentSlot.CHEST); EquipmentTierData.setTier(armor, EquipmentTier.TRANSCENDENT);
            var data = f.saved.getPlayerData(p.getUUID());
            data.setTier(com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT);
            data.setInvested(EssenceStats.DAMAGE_REFLECTION, 100);
            double armorReflection = EquipmentDamageService.armorReflectionPercent(p);
            check(armorReflection > 0 && EquipmentDamageService.evaluateStats(p).damageReflectionPercent() == armorReflection,
                    "Armor reflection remains while held shield contribution is suppressed");
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(EquipmentShieldService.heldContext(p) != null
                    && EquipmentDamageService.evaluateStats(p).damageReflectionPercent() > armorReflection,
                    "Switching away restores ordinary lowered shield reflection");

            // Deliberately bypass a supported setter: stale reads fail pending without classification/message.
            p.setItemInHand(InteractionHand.MAIN_HAND, glaive);
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            p.getInventory().offhand.set(0, shield);
            before = TwoHandedEquipmentPolicy.classifications(); int messages = p.messages;
            check(TwoHandedEquipmentService.decision(p).status() == TwoHandedEquipmentService.Status.PENDING
                    && !TwoHandedEquipmentService.validateAttack(p), "Unexpected unobserved mutation is pending");
            check(before == TwoHandedEquipmentPolicy.classifications() && p.messages == messages, "Pending combat does not classify or claim incompatibility");
            TwoHandedEquipmentService.reconcile(p);
            check(!valid(p), "Ordinary reconciliation refreshes pending state");

            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            before = TwoHandedEquipmentPolicy.classifications();
            // Invoke the native death-protection method, including actual hand lookup, shrink, health/effects and entity event.
            var protection = LivingEntity.class.getDeclaredMethod("checkTotemDeathProtection", net.minecraft.world.damagesource.DamageSource.class);
            protection.setAccessible(true);
            TwoHandedEquipmentService.enterCombat();
            try { check((boolean) protection.invoke(p, p.damageSources().generic()), "Native totem death protection succeeds with glaive selected"); }
            finally { TwoHandedEquipmentService.exitCombat(); }
            check(p.getOffhandItem().isEmpty() && p.getHealth() == 1 && p.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE),
                    "Native totem consumption, survival and effects preserved");
            check(valid(p) && before == TwoHandedEquipmentPolicy.classifications(), "Compatible totem-to-empty stays valid without combat classification");
            check(f.level.totemActivations == 1, "Native totem activation event is preserved");
            p.removeAllEffects(); p.setAbsorptionAmount(0); p.setHealth(20); p.invulnerableTime = 0;
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            before = TwoHandedEquipmentPolicy.classifications();
            check(p.hurt(p.damageSources().generic(), 1000) && p.getHealth() == 1 && p.getOffhandItem().isEmpty(),
                    "Actual lethal Player.hurt reaches native totem survival with two-handed main");
            check(valid(p) && f.level.totemActivations == 2 && before == TwoHandedEquipmentPolicy.classifications(),
                    "Complete lethal damage path preserves activation and cached valid-to-empty transition");
            if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) ClientInput.run();
            System.out.println("NativeTwoHandedEquipmentTest: " + checks + " cache, mutation, input, shield and native totem checks PASS");
        } finally {
            BuiltInRegistries.ITEM.bindTags(oldTags); TwoHandedEquipmentPolicy.rulesChanged();
            for (var player : f.onlinePlayers) TwoHandedEquipmentService.forget(player);
            f.close();
        }
    }
    private static boolean valid(ServerPlayer p) { return TwoHandedEquipmentService.decision(p).mayAttack(); }
    @SuppressWarnings("unchecked")
    private static void disconnectedPlayers(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        Class<? extends ServerPlayer> playerType = PolicyPlayer.class;
        try { playerType = (Class<? extends ServerPlayer>) Class.forName("net.neoforged.neoforge.common.util.FakePlayer"); }
        catch (ClassNotFoundException fabric) { }
        var simulated = f.player(playerType, Vec3.ZERO, "supplementaries_startup_probe");
        var handler = simulated.connection;
        var transportField = net.minecraft.server.network.ServerCommonPacketListenerImpl.class.getDeclaredField("connection");
        transportField.setAccessible(true);
        var previousTransport = transportField.get(handler);
        var socketless = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        try {
            ProjectileNativeInterceptionTest.set(net.minecraft.server.network.ServerCommonPacketListenerImpl.class, handler, "connection", socketless);
            check(!handler.isAcceptingMessages(), "A fake-player listener can exist without a network channel");
            // Supplementaries discovers faucet/cauldron behavior by equipping its fake player during server startup.
            simulated.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
            simulated.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get()));
            simulated.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            check(!valid(simulated), "Socketless equipment mutations still enforce two-handed combat rules");
            simulated.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            check(valid(simulated), "Socketless fake-player equipment changes reconcile without network negotiation");
            com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.track(f.player, simulated);
            com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.untrack(f.player, simulated);
            var send = com.mistaboom.essence_ascendance.network.WeaponActionStatePayload.class.getDeclaredMethod("send",
                    ServerPlayer.class, ServerPlayer.class, WeaponActionRuntime.Snapshot.class);
            send.setAccessible(true);
            send.invoke(null, f.player, simulated, null);
            check(!socketless.isConnected(), "Policy and action observer updates leave fake connections untouched");
            simulated.connection = null;
            simulated.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            check(valid(simulated), "A player without a listener also reconciles safely");
        } finally {
            simulated.connection = handler;
            ProjectileNativeInterceptionTest.set(net.minecraft.server.network.ServerCommonPacketListenerImpl.class, handler, "connection", previousTransport);
        }
    }
    private static void interactions(ProjectileNativeInterceptionTest.Fixture f) throws ReflectiveOperationException {
        var p = (PolicyPlayer) f.player;
        f.level.memoryBlocks = new HashMap<>();
        var pos = new BlockPos(0, 0, 1);
        f.level.memoryBlocks.put(pos, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
        for (var item : List.of(AscendanceItems.ASCENDANCE_GLAIVE.get(), AscendanceItems.ASCENDANCE_FISHING_ROD.get())) {
            WeaponActionRuntime.clear();
            var stack = new ItemStack(item);
            p.setItemInHand(InteractionHand.MAIN_HAND, stack);
            int opened = p.openedMenus;
            f.listener.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), 4));
            check(p.openedMenus == opened + 1 && p.fishing == null && WeaponActionRuntime.snapshot(p) == null,
                    "Native block menu opens before a weapon action: " + item);
            f.listener.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            check(WeaponActionRuntime.snapshot(p) == null, "Right-click interaction animation cannot start a glaive swipe");
            var frame = new net.minecraft.world.entity.decoration.ItemFrame(f.level, new BlockPos(0, 1, 1), Direction.NORTH);
            f.level.entities.add(frame);
            f.listener.handleInteract(ServerboundInteractPacket.createInteractionPacket(frame, false, InteractionHand.MAIN_HAND));
            check(!frame.isRemoved() && frame.getItem().is(item) && p.getMainHandItem().isEmpty(),
                    "Native item frame accepts the weapon without an attack: " + item);
            check(p.fishing == null && WeaponActionRuntime.snapshot(p) == null, "Frame insertion cannot cast or stab");
        }
    }
    private static final class ClientInput {
    private static void run() throws ReflectiveOperationException {
        var client = net.minecraft.client.Minecraft.getInstance();
        var previous = client.player;
        var previousLevel = client.level;
        var previousMode = client.gameMode;
        var previousHit = client.hitResult;
        var previousRenderer = client.gameRenderer;
        var local = ProjectileNativeInterceptionTest.instance(net.minecraft.client.player.LocalPlayer.class);
        var mode = ProjectileNativeInterceptionTest.instance(InputGameMode.class);
        var inventory = new net.minecraft.world.entity.player.Inventory(local);
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.player.Player.class, local, "inventory", inventory);
        inventory.items.set(0, new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get()));
        inventory.offhand.set(0, new ItemStack(Items.SHIELD));
        try {
            client.player = local;
            client.level = ProjectileNativeInterceptionTest.instance(InputLevel.class);
            client.gameMode = mode;
            var renderer = ProjectileNativeInterceptionTest.instance(net.minecraft.client.renderer.GameRenderer.class);
            ProjectileNativeInterceptionTest.set(net.minecraft.client.renderer.GameRenderer.class, renderer, "itemInHandRenderer",
                    ProjectileNativeInterceptionTest.instance(net.minecraft.client.renderer.ItemInHandRenderer.class));
            ProjectileNativeInterceptionTest.set(net.minecraft.client.Minecraft.class, client, "gameRenderer", renderer);
            var use = net.minecraft.client.Minecraft.class.getDeclaredMethod("startUseItem"); use.setAccessible(true);
            var attack = net.minecraft.client.Minecraft.class.getDeclaredMethod("startAttack"); attack.setAccessible(true);
            var mine = net.minecraft.client.Minecraft.class.getDeclaredMethod("continueAttack", boolean.class); mine.setAccessible(true);
            for (var item : List.of(AscendanceItems.ASCENDANCE_GLAIVE.get(), AscendanceItems.ASCENDANCE_FISHING_ROD.get())) {
                inventory.items.set(0, new ItemStack(item));
                client.hitResult = new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false);
                mode.blockResult = net.minecraft.world.InteractionResult.CONSUME;
                int blocks = mode.blockUses, casts = mode.itemUses;
                use.invoke(client);
                check(mode.blockUses == blocks + 1 && mode.itemUses == casts, "Client block interaction precedes weapon fallback: " + item);
                var frame = ProjectileNativeInterceptionTest.instance(net.minecraft.world.entity.decoration.ItemFrame.class);
                ProjectileNativeInterceptionTest.set(Entity.class, frame, "blockPosition", BlockPos.ZERO);
                client.hitResult = new EntityHitResult(frame, Vec3.ZERO);
                int entities = mode.entityUses;
                use.invoke(client);
                check(mode.entityUses == entities + 1 && mode.itemUses == casts, "Client item-frame interaction precedes weapon fallback: " + item);
            }
            inventory.items.set(0, new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get()));
            client.hitResult = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
            use.invoke(client);
            int blockUses = mode.blockUses;
            client.hitResult = new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false);
            use.invoke(client);
            check(mode.blockUses == blockUses && mode.itemUses == 0, "Glaive air stab latches the press without native use or offhand fallback");
            inventory.items.set(0, new ItemStack(AscendanceItems.ASCENDANCE_FISHING_ROD.get()));
            client.hitResult = BlockHitResult.miss(Vec3.ZERO, Direction.UP, BlockPos.ZERO);
            use.invoke(client);
            check(mode.itemUses == 1, "Rod air use reaches the native cast/reel fallback exactly once");
            client.hitResult = new BlockHitResult(Vec3.ZERO, Direction.UP, BlockPos.ZERO, false);
            use.invoke(client);
            check(mode.itemUses == 1 && mode.blockUses == blockUses, "Rod cast/reel latches the press before repeated block or item use");
            inventory.items.set(0, new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get()));
            check(!(boolean)attack.invoke(client), "Actual client attack press remains owned by the weapon");
            mine.invoke(client, true); check(inventory.offhand.getFirst().is(Items.SHIELD), "Actual client mining input consumed without moving offhand");
            var decision = new TwoHandedEquipmentService.Decision(TwoHandedEquipmentService.Status.INCOMPATIBLE,
                    true, true, false, false, 9, TwoHandedEquipmentPolicy.generation());
            com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.accept(local, decision);
            check(!TwoHandedEquipmentService.permitsOffhandUse(local)
                    && !com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.clientDecision(local).offhandVisible(),
                    "Client use/presentation consume the shared server decision");
            local.startUsingItem(InteractionHand.OFF_HAND);
            check(inventory.offhand.getFirst().is(Items.SHIELD), "Native client active-use setter is suppressed by shared decision");
        } finally {
            client.player = previous; client.level = previousLevel; client.gameMode = previousMode; client.hitResult = previousHit;
            ProjectileNativeInterceptionTest.set(net.minecraft.client.Minecraft.class, client, "gameRenderer", previousRenderer);
            com.mistaboom.essence_ascendance.network.TwoHandedPolicyPayload.clearClient();
        }
    }
    private static final class InputGameMode extends net.minecraft.client.multiplayer.MultiPlayerGameMode {
        int blockUses, entityUses, itemUses;
        net.minecraft.world.InteractionResult blockResult;
        private InputGameMode() { super(null, null); throw new AssertionError("Fixture only"); }
        @Override public boolean isDestroying() { return false; }
        @Override public void stopDestroyBlock() { }
        @Override public net.minecraft.world.InteractionResult useItemOn(net.minecraft.client.player.LocalPlayer player,
                InteractionHand hand, BlockHitResult hit) { blockUses++; return blockResult; }
        @Override public net.minecraft.world.InteractionResult interactAt(net.minecraft.world.entity.player.Player player,
                Entity entity, EntityHitResult hit, InteractionHand hand) { return net.minecraft.world.InteractionResult.PASS; }
        @Override public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player,
                Entity entity, InteractionHand hand) { entityUses++; return net.minecraft.world.InteractionResult.CONSUME; }
        @Override public net.minecraft.world.InteractionResult useItem(net.minecraft.world.entity.player.Player player,
                InteractionHand hand) { itemUses++; return net.minecraft.world.InteractionResult.CONSUME; }
    }
    private static final class InputLevel extends net.minecraft.client.multiplayer.ClientLevel {
        private InputLevel() { super(null, null, null, null, 0, 0, null, null, false, 0); throw new AssertionError("Fixture only"); }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS; }
        @Override public net.minecraft.world.level.border.WorldBorder getWorldBorder() { return new net.minecraft.world.level.border.WorldBorder(); }
    }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    static final class PolicyPlayer extends ServerPlayer {
        int messages, openedMenus; String messageKey;
        private PolicyPlayer() { super(null, null, null, null); throw new AssertionError("Fixture only"); }
        @Override public void displayClientMessage(Component message, boolean actionBar) {
            messages++; messageKey = message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents c ? c.getKey() : message.getString();
        }
        @Override public void awardStat(Stat<?> statistic, int amount) { }
        @Override public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider) {
            openedMenus++; return java.util.OptionalInt.of(openedMenus);
        }
        @Override public void onEnterCombat() { }
        @Override public void onLeaveCombat() { }
        @Override public void indicateDamage(double x, double z) { }
    }
}
