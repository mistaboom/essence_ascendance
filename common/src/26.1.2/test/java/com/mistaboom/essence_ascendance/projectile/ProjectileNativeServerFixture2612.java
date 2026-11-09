package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.equipment.EquipmentDamageService;
import com.mistaboom.essence_ascendance.equipment.EquipmentTier;
import com.mistaboom.essence_ascendance.equipment.EquipmentTierData;
import com.mistaboom.essence_ascendance.equipment.GlaiveTuning;
import com.mistaboom.essence_ascendance.equipment.TwoHandedEquipmentService;
import com.mistaboom.essence_ascendance.equipment.WeaponActionDamage;
import com.mistaboom.essence_ascendance.equipment.WeaponActionRuntime;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.lifecycle.PlayerRuntimeLifecycleService;
import com.mistaboom.essence_ascendance.movement.FlightAbilityService;
import com.mistaboom.essence_ascendance.movement.FlightAbilityState;
import com.mistaboom.essence_ascendance.movement.MovementAbilityInput;
import com.mistaboom.essence_ascendance.skill.SkillGroups;
import com.mistaboom.essence_ascendance.skill.SkillActivationPolicy;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.skill.effect.MobilityFlightEffects;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRegistry;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.utility.ExplosionContainmentService;
import com.mistaboom.essence_ascendance.visual.EntityPresentationBridge2612;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registry-bound native arrow/attachment probe for the real 26.1.2 server lifecycle. */
public final class ProjectileNativeServerFixture2612 {
    private static int checks;

    private ProjectileNativeServerFixture2612() { }

    public static void run(ServerLevel level) throws Exception {
        var magicBolt = ProjectileContent.MAGIC_BOLT.get();
        check(BuiltInRegistries.ENTITY_TYPE.getKey(magicBolt).equals(
                Identifier.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "magic_bolt")),
                "Magic bolt retains its saved entity ID");
        check(magicBolt.canSerialize(), "Magic bolt remains saveable");

        var gravity = Entity.class.getDeclaredMethod("applyGravity");
        gravity.setAccessible(true);
        var correction = Arrays.stream(AbstractArrow.class.getDeclaredMethods())
                .filter(method -> method.getName().contains("slowNativePhysics"))
                .findFirst().orElseThrow();
        correction.setAccessible(true);
        for (AbstractArrow arrow : new AbstractArrow[]{new Arrow(EntityType.ARROW, level),
                new SpectralArrow(EntityType.SPECTRAL_ARROW, level)}) {
            var access = (ProjectileStateAccess) arrow;
            check(access.essenceAscendance$flightScale() == 1,
                    "Real loader arrow attachment starts at ordinary flight speed");
            access.essenceAscendance$flightScale(.125F);
            check(access.essenceAscendance$flightScale() == .125F
                            && EntityPresentationBridge2612.get(arrow).arrowScale() == .125F,
                    "Native arrow presentation attachment stores server-authored flight scale");
            access.essenceAscendance$state(new ProjectileState(ProjectileSource.RANGED_PHYSICAL,
                    ProjectilePath.NONE, new UUID(1, 1), new UUID(0, 0),
                    Identifier.parse("minecraft:overworld"), 0,
                    ProjectileBalanceSettings.defaults(), 0));
            var control = ProjectileControlService.state(arrow);
            control.dragFactor = .125;
            Vec3 input = new Vec3(.2, .015, 0);
            arrow.setDeltaMovement(input);
            control.physicsInput = input;
            arrow.setDeltaMovement(input.scale(.99F));
            gravity.invoke(arrow);
            check(Math.abs(arrow.getDeltaMovement().y - (input.y * .99F - .05)) < 1e-9,
                    "Native arrow gravity still applies its original full-tick fall");
            correction.invoke(arrow, new CallbackInfo("tick", false));
            Vec3 expected = input.scale(Math.pow(.99F, .125)).add(0, -.05 * .125 * .125, 0);
            check(arrow.getDeltaMovement().distanceTo(expected) < 1e-9 && control.physicsInput == null,
                    "Transformed native arrow physics applies server flight scale");
            access.essenceAscendance$flightScale(1);
            check(access.essenceAscendance$flightScale() == 1,
                    "Native attachment releases its slowed flight scale");
        }
        nativeAdminSkillCommands(level);
        rapidGlaive(level);
        nativeWings(level);
        nativeExplosion(level);
        System.out.println("ProjectileNativeServerFixture2612: " + checks
                + " registry-bound native arrow, attachment, physics, admin skill commands, glaive, wings, rocket, boost and explosion checks PASS");
    }

    /** Registered commands must activate self-selected toggles as well as their prerequisite branches. */
    private static void nativeAdminSkillCommands(ServerLevel level) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "AdminSkillProbe");
        var client = ClientInformation.createDefault();
        var player = new ServerPlayer(level.getServer(), level, profile, client);
        var channel = connectPlayer(player, profile, client);
        var data = EssenceSavedData.get(level.getServer()).getPlayerData(player.getUUID());
        var messages = new ArrayList<Component>();
        var output = new CommandSource() {
            @Override public void sendSystemMessage(Component message) { messages.add(message); }
            @Override public boolean acceptsSuccess() { return true; }
            @Override public boolean acceptsFailure() { return true; }
            @Override public boolean shouldInformAdmins() { return false; }
        };
        var source = player.createCommandSourceStack().withPermission(PermissionSet.ALL_PERMISSIONS).withSource(output);
        var dispatcher = level.getServer().getCommands().getDispatcher();
        try {
            data.setTier(AscendanceTiers.TRANSCENDENT);
            SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
            // Sanctuary's only prerequisite is deliberately owned but unselected. The actual
            // activation command must select Waylight and the independent Sanctuary toggle.
            data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.WAYLIGHT)));
            SkillEffectRuntime.refresh(player);
            for (var id : List.of(SkillIds.SANCTUARY, SkillIds.INDUSTRIOUS_PRESENCE, SkillIds.CONTAINMENT_FIELD)) {
                check(SkillRegistry.require(id).activationPolicy() == SkillActivationPolicy.TOGGLE,
                        "Native admin activation probe covers a self-selected toggle: " + id);
                messages.clear();
                check(dispatcher.execute("essence admin player skills grant \"" + id + "\"", source) == 1
                                && data.ownsSkill(id),
                        "Registered admin grant commits the requested toggle: " + id);
                check(messages.stream().anyMatch(message -> message.getContents() instanceof TranslatableContents text
                                && text.getKey().equals("command.essence_ascendance.admin.skills.granted")
                                && text.getArgs().length == 2 && Integer.valueOf(1).equals(text.getArgs()[0])
                                && id.toString().equals(text.getArgs()[1])),
                        "Native grant feedback safely carries its count and normalized identifier: " + id);
                check(data.getLoadoutSelection(id).isEmpty() && !SkillEffectRuntime.context(player).isEffective(id),
                        "Granting ownership alone leaves the toggle inactive: " + id);
                String activate = "essence admin player skills activate \"" + id + "\"";
                check(dispatcher.execute(activate, source) == 1
                                && data.getLoadoutSelection(id).filter(id::equals).isPresent()
                                && SkillEffectRuntime.context(player).isEffective(id),
                        "Registered admin activation immediately selects and enables the toggle: " + id);
                if (id.equals(SkillIds.SANCTUARY)) {
                    check(data.getLoadoutSelection(SkillGroups.UTILITY_SENSE_FOCUS).filter(SkillIds.WAYLIGHT::equals).isPresent()
                                    && SkillEffectRuntime.context(player).isEffective(SkillIds.WAYLIGHT),
                            "Sanctuary activation also selects its owned Waylight prerequisite branch");
                }
                var selections = Map.copyOf(data.getLoadoutSelections());
                var ownership = Map.copyOf(data.getOwnedSkills());
                check(dispatcher.execute(activate, source) == 1
                                && selections.equals(data.getLoadoutSelections())
                                && ownership.equals(data.getOwnedSkills())
                                && SkillEffectRuntime.context(player).isEffective(id),
                        "Repeated admin activation is idempotent and never switches the toggle off: " + id);
            }
            var selections = Map.copyOf(data.getLoadoutSelections());
            var ownership = Map.copyOf(data.getOwnedSkills());
            messages.clear();
            check(dispatcher.execute("essence admin player skills grant \"essence_ascendance:fixture_missing_skill\"", source) == 0
                            && selections.equals(data.getLoadoutSelections()) && ownership.equals(data.getOwnedSkills()),
                    "Unknown native skill grant fails without changing committed ownership or selections");
            check(messages.size() == 2, "Unknown skill grant sends player context followed by one failure");
            // Vanilla sendFailure wraps the supplied translation in a red empty-root component.
            var failureMessage = messages.getLast();
            check(failureMessage.getContents().equals(Component.empty().getContents())
                            && failureMessage.getSiblings().size() == 1
                            && Component.empty().withStyle(ChatFormatting.RED).getStyle().getColor()
                                    .equals(failureMessage.getStyle().getColor()),
                    "Unknown skill rejection uses the actual native red failure wrapper");
            check(failureMessage.getSiblings().getFirst().getContents() instanceof TranslatableContents text
                            && text.getKey().equals("command.essence_ascendance.error.unknown_skill")
                            && text.getArgs().length == 1
                            && "essence_ascendance:fixture_missing_skill".equals(text.getArgs()[0]),
                    "Native unknown-skill feedback safely carries the rejected identifier");
        } finally {
            data.clearAllSkillsForAdmin();
            PlayerRuntimeLifecycleService.forget(player);
            channel.finishAndReleaseAll();
        }
    }

    /** Real transformed glide validation and vanilla rockets, without a rendered-client simulation. */
    @SuppressWarnings("unchecked")
    private static void nativeWings(ServerLevel level) throws ReflectiveOperationException {
        var profile = new GameProfile(UUID.randomUUID(), "WingsProbe");
        var client = ClientInformation.createDefault();
        var player = new ServerPlayer(level.getServer(), level, profile, client);
        var channel = connectPlayer(player, profile, client);
        var data = EssenceSavedData.get(level.getServer()).getPlayerData(player.getUUID());
        var nativeGlide = LivingEntity.class.getDeclaredMethod("updateFallFlying");
        var flightTicks = LivingEntity.class.getDeclaredField("fallFlyTicks");
        nativeGlide.setAccessible(true);
        flightTicks.setAccessible(true);
        var managerField = ServerLevel.class.getDeclaredField("entityManager");
        var visibilityField = PersistentEntitySectionManager.class.getDeclaredField("chunkVisibility");
        managerField.setAccessible(true);
        visibilityField.setAccessible(true);
        var manager = (PersistentEntitySectionManager<Entity>) managerField.get(level);
        var visibility = (Map<Long, Visibility>) visibilityField.get(manager);
        var chunk = new ChunkPos(0, 0);
        var priorVisibility = visibility.getOrDefault(chunk.pack(), Visibility.HIDDEN);
        long originalTime = level.getGameTime();
        FireworkRocketEntity rocket = null;
        try {
            level.getChunk(0, 0);
            manager.updateChunkStatus(chunk, Visibility.TICKING);
            player.setPos(8, 280, 8);
            player.setOnGround(false);
            player.setYRot(0);
            player.setXRot(0);
            player.setDeltaMovement(new Vec3(0, -.1, .2));
            check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "Wings probe has no physical Elytra");
            data.setTier(AscendanceTiers.TRANSCENDENT);
            SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
            data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.IMPACT_CONTROL),
                    SkillRegistry.require(SkillIds.FATIGUE_FLIGHT), SkillRegistry.require(SkillIds.ESSENCE_WINGS),
                    SkillRegistry.require(SkillIds.VECTOR_BOOST)));
            data.setLoadoutSelection(SkillGroups.MOBILITY_FLIGHT_REPLACEMENT, SkillIds.ESSENCE_WINGS);
            SkillEffectRuntime.refresh(player);
            check(SkillEffectRuntime.context(player).isEffective(SkillIds.ESSENCE_WINGS)
                            && SkillEffectRuntime.context(player).isEffective(SkillIds.VECTOR_BOOST),
                    "Real committed loadout enables Wings and Vector Boost");
            var wingsHandler = SkillEffectRegistry.get(SkillIds.ESSENCE_WINGS);
            var boostHandler = SkillEffectRegistry.get(SkillIds.VECTOR_BOOST);
            var hold = new MovementAbilityInput(MovementAbilityInput.ENABLED | MovementAbilityInput.JUMP);
            FlightAbilityService.input(player, hold);
            level.getServer().getWorldData().overworldData().setGameTime(originalTime + MovementAbilityInput.HEARTBEAT_TICKS);
            FlightAbilityService.input(player, hold);
            wingsHandler.tick(SkillEffectRuntime.context(player));
            check(MobilityFlightEffects.ownedWings(SkillEffectRuntime.context(player)),
                    "Held ordinary Jump at the apex starts one owned native glide");
            FlightAbilityState wings = SkillEffectRuntime.context(player).existingState(SkillIds.ESSENCE_WINGS);
            long started = wings.wingsStartedTick();
            FlightAbilityService.input(player, new MovementAbilityInput(MovementAbilityInput.ENABLED));
            // Exercise both early equipment rejection and the native periodic durability/event boundaries.
            for (int tick = 0; tick < 60; tick++) {
                level.getServer().getWorldData().overworldData().setGameTime(started + tick + 1);
                if (tick % MovementAbilityInput.HEARTBEAT_TICKS == 0)
                    FlightAbilityService.input(player, new MovementAbilityInput(MovementAbilityInput.ENABLED));
                flightTicks.setInt(player, tick);
                nativeGlide.invoke(player);
                check(player.isFallFlying(), "Native MC26 upkeep must not drop equipment-free Wings at tick " + tick);
                wingsHandler.tick(SkillEffectRuntime.context(player));
                check(wings.wingsActive() && wings.wingsStartedTick() == started
                                && wingsHandler.hudEntries(SkillEffectRuntime.context(player)).getFirst().active(),
                        "One glide session and its HUD survive without restart/flicker at tick " + tick);
            }
            player.setDeltaMovement(new Vec3(0, -.1, .2));
            FlightAbilityService.input(player, hold);
            var beforeBoost = player.getDeltaMovement();
            boostHandler.tick(SkillEffectRuntime.context(player));
            FlightAbilityState boost = SkillEffectRuntime.context(player).existingState(SkillIds.VECTOR_BOOST);
            check(boost.boostCharge() == 0 && player.getDeltaMovement().z > beforeBoost.z,
                    "A fresh ordinary Jump during sustained Wings actually boosts velocity and spends charge");
            FlightAbilityService.input(player, hold);
            var afterBoost = player.getDeltaMovement();
            boostHandler.tick(SkillEffectRuntime.context(player));
            check(player.getDeltaMovement().equals(afterBoost), "Held Jump cannot replay the same boost");

            var fireworks = new ItemStack(Items.FIREWORK_ROCKET, 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, fireworks);
            Items.FIREWORK_ROCKET.use(level, player, InteractionHand.MAIN_HAND);
            check(fireworks.getCount() == 2, "Vanilla rocket use accepts the same native Wings glide and consumes one rocket");
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof FireworkRocketEntity candidate && candidate.distanceToSqr(player) < 4) {
                    rocket = candidate;
                    break;
                }
            }
            check(rocket != null, "Actual vanilla item use spawns an attached rocket in the native world");
            player.setDeltaMovement(new Vec3(0, -.1, .2));
            for (int tick = 0; tick < 6; tick++) {
                nativeGlide.invoke(player);
                wingsHandler.tick(SkillEffectRuntime.context(player));
                double beforeRocket = player.getDeltaMovement().z;
                rocket.tick();
                check(player.isFallFlying() && player.getDeltaMovement().z > beforeRocket,
                        "Vanilla attached rocket keeps adding forward thrust during Wings, tick " + tick);
            }
            player.stopFallFlying();
            wingsHandler.tick(SkillEffectRuntime.context(player));
            check(!wings.wingsActive() && !wingsHandler.hudEntries(SkillEffectRuntime.context(player)).getFirst().active(),
                    "A native glide stop removes owned flight and the HUD immediately");
            // Merely owning the skill must not grant arbitrary native glide state without a session.
            player.startFallFlying();
            nativeGlide.invoke(player);
            check(!player.isFallFlying(), "MC26 rejects an unowned empty-chest glide instead of inventing a flight lease");
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            player.startFallFlying();
            flightTicks.setInt(player, 19);
            nativeGlide.invoke(player);
            check(player.isFallFlying() && player.getItemBySlot(EquipmentSlot.CHEST).getDamageValue() == 1,
                    "Ordinary equipped Elytra still follows vanilla glide validation and durability");
        } finally {
            if (rocket != null) rocket.discard();
            data.clearAllSkillsForAdmin();
            SkillEffectRuntime.forget(player);
            manager.updateChunkStatus(chunk, priorVisibility);
            level.getServer().getWorldData().overworldData().setGameTime(originalTime);
            channel.finishAndReleaseAll();
        }
    }

    @SuppressWarnings("unchecked")
    private static void nativeExplosion(ServerLevel level) throws ReflectiveOperationException {
        // Loading this class forces Mixin to resolve every explosion selector, including
        // NeoForge's hurtEntities() bridge and its hurtEntities(List) implementation.
        var methods = Arrays.stream(ServerExplosion.class.getDeclaredMethods()).map(method -> method.getName()).toList();
        for (String hook : List.of("explosionGuardHit", "explosionGuardImpulse", "explosionGuardPacket",
                "beginContainment", "containedTerrain", "containedDamage", "containedKnockback", "finishContainment")) {
            check(methods.stream().anyMatch(name -> name.contains(hook)),
                    "Real ServerExplosion contains transformed " + hook + " hook");
        }
        var playerList = level.getServer().getPlayerList();
        check(playerList.getPlayers().isEmpty(), "Explosion regression runs only in its isolated fixture server");
        // The public view is unmodifiable on NeoForge. Register this fixture-only probe in
        // the native indexes without invoking the unrelated login/handshake lifecycle.
        var playersField = PlayerList.class.getDeclaredField("players");
        var byIdField = PlayerList.class.getDeclaredField("playersByUUID");
        playersField.setAccessible(true);
        byIdField.setAccessible(true);
        var online = (List<ServerPlayer>) playersField.get(playerList);
        var byId = (Map<UUID, ServerPlayer>) byIdField.get(playerList);
        // ServerStarted precedes ordinary entity-section visibility updates. Temporarily
        // expose the fixture chunk to native spatial queries, then restore its exact state.
        var fixtureChunk = new ChunkPos(0, 0);
        level.getChunk(0, 0);
        var managerField = ServerLevel.class.getDeclaredField("entityManager");
        var visibilityField = PersistentEntitySectionManager.class.getDeclaredField("chunkVisibility");
        managerField.setAccessible(true);
        visibilityField.setAccessible(true);
        var entityManager = (PersistentEntitySectionManager<Entity>) managerField.get(level);
        var visibility = (Map<Long, Visibility>) visibilityField.get(entityManager);
        var priorVisibility = visibility.getOrDefault(fixtureChunk.pack(), Visibility.HIDDEN);
        var profile = new GameProfile(UUID.randomUUID(), "ExplosionProbe");
        var client = ClientInformation.createDefault();
        var player = new ServerPlayer(level.getServer(), level, profile, client);
        var channel = connectPlayer(player, profile, client);
        var data = EssenceSavedData.get(level.getServer()).getPlayerData(player.getUUID());
        Throwable failure = null;
        try {
            // Existing fixture chunks only; KEEP and no fire prohibit terrain changes.
            entityManager.updateChunkStatus(fixtureChunk, Visibility.TICKING);
            player.setPos(1, 300, 1);
            player.setInvulnerable(false);
            player.getAbilities().invulnerable = false;
            level.addNewPlayer(player);
            online.add(player);
            byId.put(player.getUUID(), player);
            check(playerList.getPlayers().contains(player) && playerList.getPlayer(player.getUUID()) == player,
                    "Native player indexes expose the disposable explosion probe");
            player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
            check(player.connection.hasClientLoaded()
                            && !player.isInvulnerableTo(level, player.damageSources().explosion(null, null)),
                    "Native client-loaded acknowledgement removes initial player damage protection");
            check(level.getEntities((Entity) null, player.getBoundingBox().inflate(1)).contains(player),
                    "Native spatial lookup includes the disposable explosion probe");
            check(!player.isCreative() && !player.isSpectator(), "Native explosion probe is a survival player");
            Vec3 center = new Vec3(-1, player.getEyeY(), 1);
            Vec3 before = new Vec3(.1, -.05, .02);

            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            player.setDeltaMovement(before);
            float health = player.getHealth();
            var ordinary = explosion(level, player, center);
            ordinary.explode();
            Vec3 packet = ordinary.getHitPlayers().get(player);
            check(player.getHealth() > 0 && player.getHealth() < health,
                    "Unprotected native explosion executes actual player damage: health " + health
                            + " -> " + player.getHealth() + ", packet impulse=" + packet);
            check(packet != null && packet.lengthSqr() > 0,
                    "Unprotected native explosion creates a nonzero player packet impulse");
            check(packet.distanceToSqr(player.getDeltaMovement().subtract(before)) < 1e-10,
                    "Native explosion push and player packet retain the same velocity delta");

            data.setTier(AscendanceTiers.TRANSCENDENT);
            SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
            data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.CONTAINMENT_FIELD)));
            var skill = SkillRegistry.require(SkillIds.CONTAINMENT_FIELD);
            if (skill.choiceGroup() != null) data.setLoadoutSelection(skill.choiceGroup(), skill.id());
            else if (skill.activationPolicy() == SkillActivationPolicy.TOGGLE) data.setLoadoutSelection(skill.id(), skill.id());
            SkillEffectRuntime.refresh(player);
            check(SkillEffectRuntime.context(player).isEffective(SkillIds.CONTAINMENT_FIELD),
                    "Real committed skill graph enables Containment Field");
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            player.setDeltaMovement(before);
            health = player.getHealth();
            int priorContained = ExplosionContainmentService.snapshot(SkillEffectRuntime.context(player)).containedThisTick();
            var contained = explosion(level, player, center);
            check(contained.explode() == 0, "Containment clears the actual native exploded-position result");
            check(player.getHealth() == health && player.getDeltaMovement().distanceToSqr(before) < 1e-10,
                    "Containment suppresses native owner damage and push");
            check(!contained.getHitPlayers().containsKey(player), "Containment removes the owner's native packet impulse");
            check(ExplosionContainmentService.snapshot(SkillEffectRuntime.context(player)).containedThisTick() == priorContained + 1,
                    "One native contained explosion records exactly one field event");

            data.clearAllSkillsForAdmin();
            SkillEffectRuntime.refresh(player);
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            player.setDeltaMovement(before);
            health = player.getHealth();
            var restored = explosion(level, player, center);
            restored.explode();
            check(player.getHealth() < health && restored.getHitPlayers().containsKey(player)
                            && player.getDeltaMovement().distanceToSqr(before) > 0,
                    "Removing Containment immediately restores native damage, push and packet impulse");
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            cleanupAfter(failure,
                    data::clearAllSkillsForAdmin,
                    () -> SkillEffectRuntime.forget(player),
                    () -> EquipmentDamageService.forget(player),
                    () -> online.remove(player),
                    () -> byId.remove(player.getUUID(), player),
                    () -> level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED),
                    () -> entityManager.updateChunkStatus(fixtureChunk, priorVisibility),
                    channel::finishAndReleaseAll);
        }
    }

    private static void cleanupAfter(Throwable originalFailure, Runnable... actions) {
        Throwable failure = originalFailure;
        for (var action : actions) {
            try {
                action.run();
            } catch (RuntimeException | Error exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (originalFailure == null && failure != null) {
            throw new AssertionError("Explosion fixture cleanup failed", failure);
        }
    }

    private static ServerExplosion explosion(ServerLevel level, ServerPlayer player, Vec3 center) {
        return new ServerExplosion(level, null, player.damageSources().explosion(null, null),
                new ExplosionDamageCalculator(), center, 2, false, Explosion.BlockInteraction.KEEP);
    }

    private static EmbeddedChannel connectPlayer(ServerPlayer player, GameProfile profile, ClientInformation client) {
        var transport = new Connection(PacketFlow.SERVERBOUND);
        new ServerGamePacketListenerImpl(player.level().getServer(), transport, player,
                new CommonListenerCookie(profile, 0, client, false));
        var channel = new EmbeddedChannel(transport);
        channel.close().syncUninterruptibly();
        return channel;
    }

    private static void rapidGlaive(ServerLevel level) {
        var profile = new GameProfile(UUID.randomUUID(), "GlaiveRestartProbe");
        var client = ClientInformation.createDefault();
        var player = new ServerPlayer(level.getServer(), level, profile, client);
        var channel = connectPlayer(player, profile, client);
        check(player.connection != null && !channel.isOpen()
                        && !player.connection.isAcceptingMessages(),
                "Native 26 fixture player has a real closed in-memory packet channel");
        var stack = new ItemStack(AscendanceItems.ASCENDANCE_GLAIVE.get());
        EquipmentTierData.setTier(stack, EquipmentTier.LATENT);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        TwoHandedEquipmentService.refresh(player);
        try {
            check(TwoHandedEquipmentService.decision(player).actionWeapon(),
                    "Native 26 player recognizes the registered Glaive action policy");
            check(WeaponActionRuntime.attempt(player, false), "Native 26 swipe accepts");
            var swipe = WeaponActionRuntime.snapshot(player);
            float early = WeaponActionDamage.snapshot(player).readiness();
            check(early < GlaiveTuning.STRONG_HIT_THRESHOLD
                            && Math.abs(early - swipe.readiness(WeaponActionRuntime.now(player) + .5)) < 1e-6,
                    "Native 26 readiness mixin reports the prior action's partial strength");
            check(WeaponActionRuntime.attempt(player, true),
                    "Native 26 stab restarts while the swipe strength meter is low");
            var stab = WeaponActionRuntime.snapshot(player);
            check(stab.actionId() > swipe.actionId() && stab.mode() == WeaponActionRuntime.Mode.STAB
                            && player.getAttackStrengthScale(0) == 0,
                    "Native 26 replacement has a fresh identity and resets combat strength");
            long revision = TwoHandedEquipmentService.decision(player).revision();
            long sentAt = level.getGameTime();
            check(WeaponActionRuntime.input(player, false, 1, sentAt, revision)
                            && WeaponActionRuntime.snapshot(player).actionId() > stab.actionId(),
                    "Native 26 custom input can restart the opposite mode in the same tick");
            var next = WeaponActionRuntime.snapshot(player);
            check(!WeaponActionRuntime.input(player, true, 1, sentAt, revision)
                            && !WeaponActionRuntime.input(player, true, 2, sentAt - 1, revision),
                    "Native 26 duplicate and stale custom inputs cannot replay actions");
            check(WeaponActionRuntime.snapshot(player).actionId() == next.actionId(),
                    "Rejected native 26 inputs leave the current action intact");
        } finally {
            WeaponActionRuntime.clear();
            TwoHandedEquipmentService.forget(player);
            channel.finishAndReleaseAll();
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
