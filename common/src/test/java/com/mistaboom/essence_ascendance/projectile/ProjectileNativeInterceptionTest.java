package com.mistaboom.essence_ascendance.projectile;

import com.google.common.collect.ImmutableList;
import com.mistaboom.essence_ascendance.config.ProjectileBalanceSettings;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.skill.CommittedSkillService;
import com.mistaboom.essence_ascendance.skill.SkillIds;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Loader-only regression through the transformed packet handler and the real ServerPlayer.swing override.
 * Only world storage, entity enumeration, AIR clipping and cosmetic output are in-memory fixtures.
 * No world constructor, chunk, disk storage or network connection is opened.
 */
public final class ProjectileNativeInterceptionTest {
    private static int assertions;
    private static Object allocator;
    private static Method allocate;

    private ProjectileNativeInterceptionTest() { }

    public static void run() {
        if (!Boolean.getBoolean("essence.projectile.nativeHookTest")) throw new IllegalStateException("Explicit native hook test flag required");
        try {
            var field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            field.setAccessible(true);
            allocator = field.get(null);
            allocate = allocator.getClass().getMethod("allocateInstance", Class.class);
            for (var arrowType : List.of(Arrow.class, SpectralArrow.class)) {
                var fixture = new Fixture();
                AbstractArrow shot = fixture.arrow(arrowType);
                check(ProjectileOwnership.resolve(shot, fixture.player).hostile(), "Ownerless native damaging arrow is eligible");
                check(fixture.player.getAttackStrengthScale(0) == 1, "Native attack readiness starts fully charged");
                fixture.packet(InteractionHand.MAIN_HAND);
                check(shot.isRemoved(), arrowType.getSimpleName() + " is destroyed by the actual ready main-hand packet");
                check(fixture.player.getAttackStrengthScale(0) == 0, "Native ServerPlayer.swing still resets readiness after interception");

                shot = fixture.arrow(arrowType);
                fixture.advance(1, 0);
                fixture.packet(InteractionHand.MAIN_HAND);
                check(!shot.isRemoved(), "An unready packet cannot intercept");
                fixture.advance(1, 100);
                fixture.packet(InteractionHand.OFF_HAND);
                check(!shot.isRemoved(), "Off-hand animation cannot intercept");
                fixture.advance(1, 100);
                fixture.packet(InteractionHand.MAIN_HAND);
                check(shot.isRemoved(), "The next fully charged main-hand packet intercepts the same surviving shot");

                fixture.enableTheft();
                ServerPlayer source = fixture.player(new Vec3(0, 0, 4), "source");
                shot = fixture.arrow(arrowType);
                ProjectileOwnership.transferNative(shot, source);
                var sourceLife = fixture.saved.getPlayerData(source.getUUID()).projectileLife();
                ((ProjectileStateAccess) shot).essenceAscendance$state(new ProjectileState(
                        ProjectileSource.RANGED_PHYSICAL, ProjectilePath.NONE, source.getUUID(), sourceLife,
                        Level.OVERWORLD.location(), fixture.level.tick, ProjectileBalanceSettings.defaults(), 0));
                shot.pickup = AbstractArrow.Pickup.DISALLOWED;
                var control = ProjectileControlService.state(shot);
                control.dragFactor = 0.125;
                ((ProjectileStateAccess) shot).essenceAscendance$flightScale(0.125F);
                fixture.advance(1, 100);
                fixture.packet(InteractionHand.MAIN_HAND);
                var redirected = ProjectileRuntime.state(shot);
                check(!shot.isRemoved() && shot.getOwner() == fixture.player, "Theft transfers the existing native arrow to the defender");
                check(redirected != null && redirected.redirected && source.getUUID().equals(redirected.target),
                        "Theft acquires the responsible source as a real homing target");
                check(shot.getDeltaMovement().z > 3 && shot.getDeltaMovement().dot(new Vec3(0, 0, -1)) < 0,
                        "The packet reverses incoming flight with a useful native velocity");
                check(control.dragFactor == 1 && ((ProjectileStateAccess) shot).essenceAscendance$flightScale() == 1,
                        "Theft releases synchronized drag physics");
                check(shot.pickup == AbstractArrow.Pickup.DISALLOWED, "Changing native owner preserves noncollectable ammunition");
                fixture.close();
            }
            System.out.println("Projectile native interception passed: " + assertions
                    + " (actual handleAnimate, ServerPlayer.swing, committed skills, Arrow/SpectralArrow destruction and theft; no world)");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Native interception fixture initialization failed", failure);
        }
    }

    static final class Fixture {
        final DedicatedServer server;
        final MemoryLevel level;
        final EssenceSavedData saved = new EssenceSavedData();
        final Map<UUID, ServerPlayer> players = new HashMap<>();
        final List<ServerPlayer> onlinePlayers = new ArrayList<>();
        final List<io.netty.channel.embedded.EmbeddedChannel> networkChannels = new ArrayList<>();
        final ServerPlayer player;
        final ServerGamePacketListenerImpl listener;

        Fixture() throws ReflectiveOperationException {
            this(ServerPlayer.class);
        }

        Fixture(Class<? extends ServerPlayer> playerClass) throws ReflectiveOperationException {
            server = instance(DedicatedServer.class);
            set(MinecraftServer.class, server, "serverThread", Thread.currentThread());
            set(MinecraftServer.class, server, "pvp", true);
            level = instance(MemoryLevel.class);
            level.entities = new ArrayList<>();
            set(ServerLevel.class, level, "players", onlinePlayers);
            level.movementSupport = List.of();
            level.tick = 100;
            level.memoryServer = server;
            level.scoreboard = new ServerScoreboard(server);
            level.storage = instance(DimensionDataStorage.class);
            set(DimensionDataStorage.class, level.storage, "cache", new HashMap<>(Map.of("essence_ascendance_players", saved)));
            set(Level.class, level, "dimension", Level.OVERWORLD);
            set(Level.class, level, "random", RandomSource.create(0));
            set(Level.class, level, "threadSafeRandom", RandomSource.create(0));
            set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
            var list = instance(DedicatedPlayerList.class);
            set(PlayerList.class, list, "playersByUUID", players);
            set(PlayerList.class, list, "players", onlinePlayers);
            try {
                // NeoForge exposes the constructor-created read-only view from getPlayers().
                set(PlayerList.class, list, "playersView", java.util.Collections.unmodifiableList(onlinePlayers));
            } catch (NoSuchFieldException vanillaList) {
                // Vanilla/Fabric returns the original list directly.
            }
            set(MinecraftServer.class, server, "playerList", list);
            player = player(playerClass, Vec3.ZERO, "defender");
            PlayerEssenceData data = saved.getPlayerData(player.getUUID());
            data.setTier(AscendanceTiers.TRANSCENDENT);
            data.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.PROJECTILE_DRAG_FIELD), SkillRegistry.require(SkillIds.INTERCEPTOR)));
            // Keep unrelated configured providers out of this in-memory fixture; completion still uses real saved receipts and evaluation.
            SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);
            check(CommittedSkillService.effectiveIds(player).containsAll(List.of(SkillIds.PROJECTILE_DRAG_FIELD, SkillIds.INTERCEPTOR)),
                    "The production committed evaluator enables Interceptor and its prerequisite");
            listener = player.connection;
        }

        ServerPlayer player(Vec3 position, String name) throws ReflectiveOperationException {
            return player(ServerPlayer.class, position, name);
        }

        <T extends ServerPlayer> T player(Class<T> playerClass, Vec3 position, String name) throws ReflectiveOperationException {
            T entity = instance(playerClass);
            initializeEntity(entity, EntityType.PLAYER, level, position, new AABB(position.x - 0.3, position.y,
                    position.z - 0.3, position.x + 0.3, position.y + 1.8, position.z + 0.3));
            set(Entity.class, entity, "eyeHeight", 1.62F);
            set(ServerPlayer.class, entity, "server", server);
            set(Player.class, entity, "abilities", new Abilities());
            set(Player.class, entity, "inventory", new Inventory(entity));
            set(Player.class, entity, "cooldowns", new net.minecraft.world.item.ItemCooldowns());
            set(Player.class, entity, "foodData", new net.minecraft.world.food.FoodData());
            set(LivingEntity.class, entity, "useItem", net.minecraft.world.item.ItemStack.EMPTY);
            set(Player.class, entity, "gameProfile", new GameProfile(entity.getUUID(), name));
            try {
                set(Player.class,entity,"prefixes",new ArrayList<>());
                set(Player.class,entity,"suffixes",new ArrayList<>());
            } catch (NoSuchFieldException ignored) { }
            set(LivingEntity.class, entity, "attributes", new AttributeMap(Player.createAttributes().build()));
            set(LivingEntity.class, entity, "activeEffects", new HashMap<>());
            // The initialization-only NeoForge fixture skips native constructors, including its
            // transient damage bookkeeping stack. Fabric has no corresponding field.
            try { set(LivingEntity.class,entity,"damageContainers",new java.util.Stack<>()); }
            catch (NoSuchFieldException ignored) { }
            set(LivingEntity.class, entity, "attackStrengthTicker", 100);
            // An animation already in progress skips only the cosmetic tracker broadcast. The native override still resets the ticker.
            set(LivingEntity.class, entity, "swinging", true);
            set(LivingEntity.class, entity, "swingTime", 0);
            defineData(entity, Player.class);
            entity.setHealth(entity.getMaxHealth());
            set(LivingEntity.class, entity, "combatTracker", new net.minecraft.world.damagesource.CombatTracker(entity));
            set(LivingEntity.class, entity, "lastClimbablePos", java.util.Optional.empty());
            set(LivingEntity.class, entity, "walkAnimation", new net.minecraft.world.entity.WalkAnimationState());
            var gameMode = new ServerPlayerGameMode(entity);
            set(ServerPlayerGameMode.class, gameMode, "gameModeForPlayer", GameType.SURVIVAL);
            set(ServerPlayer.class, entity, "gameMode", gameMode);
            players.put(entity.getUUID(), entity);
            onlinePlayers.add(entity);
            level.entities.add(entity);
            // Every fixture combat participant can now enter the shared accepted-combat runtime.
            // Keep unavailable world advancement providers outside this in-memory boundary using real captured receipts.
            SkillRegistry.referencedPermanentMilestoneIds().forEach(saved.getPlayerData(entity.getUUID())::completeMilestone);
            initializeNetworkBoundary(entity);
            return entity;
        }

        /** Real detached connection, with no negotiated custom channels and no socket/world lifecycle. */
        private void initializeNetworkBoundary(ServerPlayer entity) throws ReflectiveOperationException {
            var handler = instance(ServerGamePacketListenerImpl.class);
            initializeNetworkBoundary(entity, handler);
        }

        void initializeNetworkBoundary(ServerPlayer entity, ServerGamePacketListenerImpl handler) throws ReflectiveOperationException {
            set(ServerGamePacketListenerImpl.class, handler, "player", entity);
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            var channel = new io.netty.channel.embedded.EmbeddedChannel();
            networkChannels.add(channel);
            set(net.minecraft.network.Connection.class, connection, "channel", channel);
            set(net.minecraft.server.network.ServerCommonPacketListenerImpl.class, handler, "connection", connection);
            set(net.minecraft.server.network.ServerCommonPacketListenerImpl.class, handler, "server", server);
            entity.connection = handler;
            try {
                // Fabric's addon is normally created by the listener constructor, which this fixture deliberately skips.
                // Use its real constructor and empty negotiated-channel state, not a replacement networking API.
                var addonType = Class.forName("net.fabricmc.fabric.impl.networking.server.ServerPlayNetworkAddon");
                Object addon = addonType.getConstructor(ServerGamePacketListenerImpl.class,
                        net.minecraft.network.Connection.class, MinecraftServer.class).newInstance(handler, connection, server);
                var field = java.util.Arrays.stream(ServerGamePacketListenerImpl.class.getDeclaredFields())
                        .filter(f -> f.getType() == addonType).findFirst().orElseThrow();
                field.setAccessible(true); field.set(handler, addon);
            } catch (ClassNotFoundException notFabric) {
                // NeoForge owns negotiation on the native connection rather than a Fabric addon.
            }
        }

        AbstractArrow arrow(Class<? extends AbstractArrow> type) throws ReflectiveOperationException {
            AbstractArrow arrow = instance(type);
            Vec3 position = player.getEyePosition().add(0, 0, 1.5);
            initializeEntity(arrow, type == Arrow.class ? EntityType.ARROW : EntityType.SPECTRAL_ARROW, level,
                    position, new AABB(position.x - 0.25, position.y, position.z - 0.25,
                            position.x + 0.25, position.y + 0.5, position.z + 0.25));
            defineData(arrow, type == Arrow.class ? Arrow.class : AbstractArrow.class);
            arrow.setDeltaMovement(0, 0, -0.2);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            level.entities.add(arrow);
            return arrow;
        }

        void packet(InteractionHand hand) {
            listener.handleAnimate(new ServerboundSwingPacket(hand));
        }

        void advance(int ticks, int readinessTicks) throws ReflectiveOperationException {
            level.tick += ticks;
            set(LivingEntity.class, player, "attackStrengthTicker", readinessTicks);
        }

        void enableTheft() {
            saved.getPlayerData(player.getUUID()).grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.TRAJECTORY_THEFT)));
            check(CommittedSkillService.effectiveIds(player).contains(SkillIds.TRAJECTORY_THEFT), "Production evaluator enables Theft after its real purchase");
        }

        void close() {
            networkChannels.forEach(io.netty.channel.embedded.EmbeddedChannel::finishAndReleaseAll);
            CommittedSkillService.forget(player);
            ProjectileControlService.forget(player);
            SkillEffectRuntime.forget(player);
            com.mistaboom.essence_ascendance.equipment.EquipmentDamageService.forgetSkillInput(player);
        }
    }

    /** A storage boundary only: every intercepted entity, native input method and gameplay decision stays real. */
    static final class MemoryLevel extends ServerLevel {
        List<Entity> entities;
        long tick;
        MinecraftServer memoryServer;
        DimensionDataStorage storage;
        ServerScoreboard scoreboard;
        boolean occluded;
        List<net.minecraft.world.phys.shapes.VoxelShape> movementSupport;
        net.minecraft.core.RegistryAccess memoryRegistries;
        net.minecraft.world.damagesource.DamageSources memoryDamageSources;
        net.minecraft.world.level.GameRules memoryGameRules;
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> memoryBlocks;
        net.minecraft.world.item.crafting.RecipeManager memoryRecipes;
        Holder<net.minecraft.world.level.biome.Biome> memoryBiome;

        private MemoryLevel() {
            super(null, null, null, null, Level.OVERWORLD, null, null, false, 0, List.of(), false, null);
            throw new AssertionError("World constructors must never run in this fixture");
        }

        @Override public MinecraftServer getServer() { return memoryServer; }
        @Override public Iterable<Entity> getAllEntities() { return entities; }
        @Override public long getGameTime() { return tick; }
        @Override public long getDayTime() { return tick; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public ServerScoreboard getScoreboard() { return scoreboard; }
        @Override public net.minecraft.world.Difficulty getDifficulty() { return net.minecraft.world.Difficulty.NORMAL; }
        @Override public net.minecraft.world.DifficultyInstance getCurrentDifficultyAt(BlockPos position) {
            // Native zombie melee consults local difficulty even with no fire transfer; no chunk is opened.
            return new net.minecraft.world.DifficultyInstance(getDifficulty(), 0, 0, 0);
        }
        @Override public net.minecraft.world.level.GameRules getGameRules() {
            return memoryGameRules == null ? new net.minecraft.world.level.GameRules() : memoryGameRules;
        }
        @Override public net.minecraft.world.flag.FeatureFlagSet enabledFeatures() { return net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS; }
        @Override public void broadcastEntityEvent(Entity entity, byte event) { }
        @Override public void broadcastDamageEvent(Entity entity, net.minecraft.world.damagesource.DamageSource source) { }
        @Override public net.minecraft.world.level.block.state.BlockState getBlockState(BlockPos position) {
            return memoryBlocks == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : memoryBlocks.getOrDefault(position, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        }
        @Override public boolean hasChunkAt(BlockPos position) { return true; }
        @Override public boolean areEntitiesLoaded(long chunk) { return true; }
        @Override public boolean setBlock(BlockPos position, net.minecraft.world.level.block.state.BlockState state, int flags, int depth) {
            if (memoryBlocks == null) throw new AssertionError("Block storage was not installed for this fixture");
            memoryBlocks.put(position.immutable(), state); return true;
        }
        @Override public void blockEntityChanged(BlockPos position) { }
        @Override public void updateNeighbourForOutputSignal(BlockPos position, net.minecraft.world.level.block.Block block) { }
        @Override public net.minecraft.util.profiling.ProfilerFiller getProfiler() { return net.minecraft.util.profiling.InactiveProfiler.INSTANCE; }
        @Override public net.minecraft.world.item.crafting.RecipeManager getRecipeManager() {
            return memoryRecipes == null ? super.getRecipeManager() : memoryRecipes;
        }
        @Override public RandomSource getRandomSequence(net.minecraft.resources.ResourceLocation sequence) { return getRandom(); }
        @Override public Holder<net.minecraft.world.level.biome.Biome> getBiome(BlockPos position) {
            return memoryBiome == null ? super.getBiome(position) : memoryBiome;
        }
        @Override public Iterable<net.minecraft.world.phys.shapes.VoxelShape> getBlockCollisions(Entity entity, AABB bounds) {
            return movementSupport.stream().filter(shape -> shape.bounds().intersects(bounds)).toList();
        }
        @Override public net.minecraft.core.RegistryAccess registryAccess() {
            return memoryRegistries == null ? super.registryAccess() : memoryRegistries;
        }
        @Override public net.minecraft.world.damagesource.DamageSources damageSources() {
            return memoryDamageSources == null ? super.damageSources() : memoryDamageSources;
        }
        @Override public void gameEvent(Holder<net.minecraft.world.level.gameevent.GameEvent> event, Vec3 position,
                                        net.minecraft.world.level.gameevent.GameEvent.Context context) { }
        @Override public net.minecraft.world.level.border.WorldBorder getWorldBorder() {
            return new net.minecraft.world.level.border.WorldBorder();
        }
        @Override public Entity getEntity(int id) { return entities.stream().filter(entity -> entity.getId() == id).findFirst().orElse(null); }
        @Override public Entity getEntity(UUID id) { return entities.stream().filter(entity -> entity.getUUID().equals(id)).findFirst().orElse(null); }
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos position) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState().getFluidState();
        }
        @Override public boolean isInWorldBounds(BlockPos position) { return true; }
        @Override public List<Entity> getEntities(Entity except, AABB box, Predicate<? super Entity> predicate) {
            return entities.stream().filter(entity -> entity != except && !entity.isRemoved()
                    && entity.getBoundingBox().intersects(box) && predicate.test(entity)).toList();
        }
        @Override public <T extends Entity> List<T> getEntities(EntityTypeTest<Entity, T> type, AABB box, Predicate<? super T> predicate) {
            List<T> result = new ArrayList<>();
            for (Entity entity : entities) {
                T candidate = type.tryCast(entity);
                if (candidate != null && !candidate.isRemoved() && candidate.getBoundingBox().intersects(box) && predicate.test(candidate)) result.add(candidate);
            }
            return result;
        }
        @Override public <T extends Entity> void getEntities(EntityTypeTest<Entity,T> type, AABB box,
                Predicate<? super T> predicate, List<? super T> result, int limit) {
            for (T entity : getEntities(type,box,predicate)) {
                if (result.size() >= limit) break;
                result.add(entity);
            }
        }
        @Override public BlockHitResult clip(ClipContext context) {
            if (occluded) return new BlockHitResult(context.getFrom().lerp(context.getTo(), .5), Direction.NORTH,
                    BlockPos.containing(context.getFrom().lerp(context.getTo(), .5)), false);
            return BlockHitResult.miss(context.getTo(), Direction.getNearest(context.getTo().subtract(context.getFrom())), BlockPos.containing(context.getTo()));
        }
        @Override public <T extends ParticleOptions> int sendParticles(T particle, double x, double y, double z, int count,
                double dx, double dy, double dz, double speed) { return 0; }
        @Override public void playSeededSound(Player player, double x, double y, double z, Holder<SoundEvent> sound,
                SoundSource source, float volume, float pitch, long seed) { }
        @Override public void playSeededSound(Player player, Entity entity, Holder<SoundEvent> sound,
                SoundSource source, float volume, float pitch, long seed) { }
    }

    static void initializeEntity(Entity entity, EntityType<?> type, Level level, Vec3 position, AABB bounds)
            throws ReflectiveOperationException {
        set(Entity.class, entity, "level", level);
        set(Entity.class, entity, "type", type);
        set(Entity.class, entity, "dimensions", type.getDimensions());
        set(Entity.class, entity, "uuid", UUID.randomUUID());
        set(Entity.class, entity, "stringUUID", entity.getUUID().toString());
        set(Entity.class, entity, "id", entity.getUUID().hashCode() & Integer.MAX_VALUE);
        set(Entity.class, entity, "position", position);
        set(Entity.class, entity, "blockPosition", BlockPos.containing(position));
        set(Entity.class, entity, "chunkPosition", new net.minecraft.world.level.ChunkPos(BlockPos.containing(position)));
        set(Entity.class, entity, "bb", bounds);
        set(Entity.class, entity, "deltaMovement", Vec3.ZERO);
        set(Entity.class, entity, "passengers", ImmutableList.of());
        set(Entity.class, entity, "levelCallback", EntityInLevelCallback.NULL);
        set(Entity.class, entity, "random", RandomSource.create(0));
        set(Entity.class, entity, "fluidHeight", new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<>());
        try { set(Entity.class,entity,"forgeFluidTypeHeight",new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<>()); }
        catch (NoSuchFieldException ignored) { }
        entity.xo = position.x; entity.yo = position.y; entity.zo = position.z;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void defineData(Entity entity, Class<?> nativeClass) throws ReflectiveOperationException {
        var builder = new SynchedEntityData.Builder(entity);
        var defaults = Map.<String, Object>of("DATA_SHARED_FLAGS_ID", (byte) 0, "DATA_AIR_SUPPLY_ID", 300,
                "DATA_CUSTOM_NAME", Optional.empty(), "DATA_CUSTOM_NAME_VISIBLE", false, "DATA_SILENT", false,
                "DATA_NO_GRAVITY", false, "DATA_POSE", Pose.STANDING, "DATA_TICKS_FROZEN", 0);
        for (var entry : defaults.entrySet()) {
            var field = Entity.class.getDeclaredField(entry.getKey()); field.setAccessible(true);
            builder.define((EntityDataAccessor) field.get(null), entry.getValue());
        }
        Method define = nativeClass.getDeclaredMethod("defineSynchedData", SynchedEntityData.Builder.class);
        define.setAccessible(true); define.invoke(entity, builder);
        set(Entity.class, entity, "entityData", builder.build());
    }

    static <T> T instance(Class<T> type) throws ReflectiveOperationException {
        return type.cast(allocate.invoke(allocator, type));
    }
    static void set(Class<?> owner, Object target, String name, Object value) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
