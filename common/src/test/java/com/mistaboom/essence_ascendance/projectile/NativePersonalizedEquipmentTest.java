package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.equipment.personalized.PersonalizedEquipmentService;
import com.mistaboom.essence_ascendance.gathering.*;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.network.PreciseMobMotionPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
//? if >=26.1.2
/*import net.minecraft.world.entity.monster.zombie.Zombie;*/
//? if <26.1.2
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Real owner-held activation, native mob despawn and loader-transformed motion tracking. */
public final class NativePersonalizedEquipmentTest {
    //? if >=26.1.2
    /*private static ServerEntity.Synchronizer trackingSynchronizer(List<Packet<?>> packets) {*/
        //? if >=26.1.2
        /*return new ServerEntity.Synchronizer() {*/
            //? if >=26.1.2
            /*@Override public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) { packets.add(packet); }*/
            //? if >=26.1.2
            /*@Override public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) { packets.add(packet); }*/
            //? if >=26.1.2
            /*@Override public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, java.util.function.Predicate<net.minecraft.server.level.ServerPlayer> filter) { packets.add(packet); }*/
        //? if >=26.1.2
        /*};*/
    //? if >=26.1.2
    /*}*/
    private static int checks;
    public static void run(ProjectileNativeInterceptionTest.Fixture f) throws Exception {
        inventoryOverlay(f);
        var vance = f.player(NativeGuardOutcomeTest.NativePlayer.class, Vec3.ZERO, "Vance");
        UUID previousId = vance.getUUID();
        vance.setUUID(UUID.fromString("422890f3-c52e-4cdd-ad9c-a18c17b469fa"));
        f.players.remove(previousId); f.players.put(vance.getUUID(), vance);
        ProjectileNativeInterceptionTest.set(net.minecraft.world.entity.player.Player.class, vance, "gameProfile",
                new com.mojang.authlib.GameProfile(vance.getUUID(), "Vance"));
        f.saved.getPlayerData(vance.getUUID()).setTier(com.mistaboom.essence_ascendance.tier.AscendanceTiers.TRANSCENDENT);
        ProjectileNativeInterceptionTest.set(Entity.class, vance, "fluidOnEyes", new HashSet<>());
        ProjectileNativeInterceptionTest.set(net.minecraft.server.level.ServerPlayer.class, vance, "requestedViewDistance", 16);
        var rod = new ItemStack(AscendanceItems.ASCENDANCE_FISHING_ROD.get());
        vance.setItemInHand(InteractionHand.MAIN_HAND, rod);
        for (var tier : EquipmentTier.values()) {
            EquipmentTierData.setTier(rod, tier);
            check(PersonalizedEquipmentService.active(vance, rod).isPresent() == (tier == EquipmentTier.TRANSCENDENT),
                    "Only Vance's top-tier held rod activates");
        }
        var unchanged = rod.copy();
        check(PersonalizedEquipmentService.active(f.player, rod).isEmpty(), "Other viewers have no gift profile");
        check(PersonalizedEquipmentService.active(vance, rod.copy()).isEmpty(), "Detached JEI/creative/preview copies cannot activate");
        var catalog = EquipmentCatalogStacks.allTiers(rod.getItem());
        check(catalog.size() == 6 && catalog.stream().noneMatch(stack -> PersonalizedEquipmentService.active(vance, stack).isPresent()),
                "Catalog contains only the six ordinary tiers, even for Vance");
        for (var item : List.of(AscendanceItems.ASCENDANCE_SWORD.get(), AscendanceItems.ASCENDANCE_SHIELD.get(), Items.FISHING_ROD)) {
            var stack = new ItemStack(item);
            if (EquipmentTierData.isAscendanceEquipment(stack)) EquipmentTierData.setTier(stack, EquipmentTier.TRANSCENDENT);
            vance.setItemInHand(InteractionHand.MAIN_HAND, stack);
            check(PersonalizedEquipmentService.active(vance, stack).isEmpty(), "Gift is specific to its equipment type");
        }
        vance.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(PersonalizedEquipmentService.active(vance, rod).isEmpty(), "Unequipping deactivates the gift");
        vance.setItemInHand(InteractionHand.OFF_HAND, rod);
        check(PersonalizedEquipmentService.active(vance, rod).isPresent(), "Actual offhand is supported");
        vance.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); vance.setItemInHand(InteractionHand.MAIN_HAND, rod);
        FracturedEquipmentData.markFractured(rod);
        check(PersonalizedEquipmentService.active(vance, rod).isEmpty(), "Fractured equipment cannot activate perks or gift appearance");
        FracturedEquipmentData.clearFractured(rod);
        rod.set(DataComponents.CUSTOM_NAME, Component.literal("Anvil Name"));
        check(PersonalizedEquipmentService.active(vance, rod).isPresent(), "Anvil names cannot change authorization");
        rod.remove(DataComponents.CUSTOM_NAME);
        check(ItemStack.isSameItemSameComponents(rod, unchanged), "Gift activation never rewrites item components");
        var mob = new Zombie(EntityType.ZOMBIE, f.level); mob.setPos(0, 0, 6); f.level.entities.add(mob);
        check(PersonalizedRodLaunch.multiplier(vance, rod, mob) == 10
                && PersonalizedRodLaunch.multiplier(vance, rod, f.player) == 1,
                "Extra opening force applies to mobs, never players");
        check(PersonalizedRodLaunch.multiplier(f.player, rod, mob) == 1, "Another player gets ordinary force");
        var dropped = new net.minecraft.world.entity.item.ItemEntity(f.level, 0, 0, 6, new ItemStack(Items.STONE));
        check(PersonalizedRodLaunch.multiplier(vance, rod, dropped) == 1, "Objects retain ordinary hauling");
        int oldView = f.server.getPlayerList().getViewDistance(), oldSimulation = f.server.getPlayerList().getSimulationDistance();
        ProjectileNativeInterceptionTest.set(PlayerList.class, f.server.getPlayerList(), "viewDistance", 10);
        ProjectileNativeInterceptionTest.set(PlayerList.class, f.server.getPlayerList(), "simulationDistance", 10);
        f.level.memoryBlocks.clear();
        try {
            vance.setYRot(0); vance.setXRot(-90);
            rod.getItem().use(f.level, vance, InteractionHand.MAIN_HAND);
            var hook = vance.fishing; var access = (RodHookAccess) hook;
            var context = EquipmentFishingRodService.context(vance, rod);
            Vec3 toward = vance.getBoundingBox().getCenter().subtract(mob.getBoundingBox().getCenter());
            var smallView = PersonalizedRodLaunch.force(vance, rod, mob, toward, Vec3.ZERO, context, 0,
                    EssenceConfigManager.get().fishingRodBalance(), 0, .5);
            var fullView = PersonalizedRodLaunch.force(vance, rod, mob, toward, Vec3.ZERO, context, 0,
                    EssenceConfigManager.get().fishingRodBalance(), 0, 2);
            check(smallView.length() < fullView.length() * .5, "Short client entity visibility reduces launch before rendering would cull the mob");
            if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
                var holders = List.of(f.player, vance);
                check(com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders, rod, 0) == 0xFFFF1493
                        && com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders, rod, 1) == 0xFF7FFFD4,
                        "Every observer resolves the eligible holder's pink and aquamarine rod");
                check(com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders, rod, 2) == 0xFFFFFFFF,
                        "Nochange string/details retain their authored colors");
                check(com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.active(holders, rod).orElseThrow().name().equals("Vance's Yeet Stick"),
                        "Every observer resolves the eligible holder's custom name");
                check(com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders, rod.copy(), 0)
                        == EquipmentTierVisuals.itemTint(rod, 0), "Catalog copies retain ordinary colors for Vance too");
                var customTexture = com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.hookTexture(hook);
                check(customTexture != null && customTexture.getPath().contains("vance_yeet_stick"),
                        "Personal hook texture resolves its actual owner for every observer");
                vance.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                check(com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders, rod, 0)
                        == EquipmentTierVisuals.itemTint(rod, 0)
                        && com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.hookTexture(hook) == null,
                        "Dropping or unequipping restores the normal rod and hook for all observers");
                f.player.setItemInHand(InteractionHand.MAIN_HAND, rod);
                check(com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.active(holders, rod).isEmpty()
                        && com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(holders, rod, 0)
                        == EquipmentTierVisuals.itemTint(rod, 0) && PersonalizedRodLaunch.multiplier(f.player, rod, mob) == 1,
                        "Another holder receives ordinary appearance and ordinary mob force");
                f.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); vance.setItemInHand(InteractionHand.MAIN_HAND, rod);
                check(Arrays.stream(ItemStack.class.getDeclaredMethods()).anyMatch(m -> m.getName().contains("personalizedName")),
                        "Native owner-only item name hook transforms");
                var packet = new PreciseMobMotionPayload(f.level.dimension().location(), mob.getId(), mob.getUUID(), new Vec3(10, 6, -8));
                com.mistaboom.essence_ascendance.client.PreciseMobMotionClient.apply(mob, packet);
                check(mob.getDeltaMovement().equals(packet.velocity()), "Client accepts the server's full launch velocity");
                com.mistaboom.essence_ascendance.client.PreciseMobMotionClient.apply(mob,
                        new PreciseMobMotionPayload(packet.dimension(), mob.getId(), UUID.randomUUID(), Vec3.ZERO));
                check(mob.getDeltaMovement().equals(packet.velocity()), "Reused entity IDs cannot apply stale motion to a different mob");
                com.mistaboom.essence_ascendance.client.PreciseMobMotionClient.apply(mob,
                        new PreciseMobMotionPayload(net.minecraft.resources.ResourceLocation.parse("minecraft:the_nether"), mob.getId(), mob.getUUID(), Vec3.ZERO));
                check(mob.getDeltaMovement().equals(packet.velocity()), "Motion from another dimension is ignored");
                mob.setDeltaMovement(Vec3.ZERO);
            }
            vance.releaseUsingItem(); access.essenceAscendance$attachEntity(mob);
            rod.getItem().use(f.level, vance, InteractionHand.MAIN_HAND);
            mob.setNoActionTime(700);
            f.level.tick++; AscendanceFishingService.heldInput(vance, hook.getId(), true, 2);
            AscendanceFishingService.tickAttachment(hook, access);
            Vec3 kick = mob.getDeltaMovement();
            check(kick.y > 5 && kick.length() > EssenceConfigManager.get().fishingRodBalance().tier(EquipmentTier.TRANSCENDENT).reelSpeed() * 3,
                    "Real server opening launch is satisfyingly stronger than normal reeling");
            check(access.essenceAscendance$rodState().poweredTicks == 1 && !mob.isPersistenceRequired(),
                    "Gift uses the normal press lifecycle without making mobs permanent");
            check(mob.getNoActionTime() == 0, "A real hook-set resets idle despawn time without granting persistence");
            AscendanceFishingService.tickAttachment(hook, access);
            check(mob.getDeltaMovement().equals(kick), "Repeated tick cannot duplicate the gift impulse");
            vance.releaseUsingItem(); check(hook.isRemoved() && mob.getDeltaMovement().equals(kick), "Release retains the entire launch impulse");
            List<Packet<?>> packets = new ArrayList<>();
            //? if >=26.1.2
            /*var tracker = new ServerEntity(f.level, mob, 3, true, trackingSynchronizer(packets));*/
            //? if <26.1.2
            var tracker = new ServerEntity(f.level, mob, 3, true, packets::add);
            tracker.sendChanges();
            check(packets.getLast() instanceof ClientboundCustomPayloadPacket packet
                    && packet.payload().type().id().equals(PreciseMobMotionPayload.TYPE.id()),
                    "Actual tracker sends precise motion after clamped native updates");
            var payload = ((ClientboundCustomPayloadPacket) packets.getLast()).payload();
            byte[] bytes = (byte[]) payload.getClass().getMethod("payload").invoke(payload);
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(bytes), f.level.registryAccess());
            try {
                var decoded = PreciseMobMotionPayload.CODEC.decode(buffer);
                check(decoded.velocity().equals(kick) && decoded.entityUuid().equals(mob.getUUID()), "Native outgoing packet retains full velocity and mob identity");
            } finally { buffer.release(); }
            check(packets.stream().anyMatch(packet -> packet instanceof ClientboundSetEntityMotionPacket velocity
                    //? if >=26.1.2
                    /*&& Math.abs(velocity.movement().y - 3.9) < 1e-8 && velocity.movement().y < kick.y),*/
                    //? if <26.1.2
                    && Math.abs(velocity.getYa() - 3.9) < 1e-8 && velocity.getYa() < kick.y),
                    "Test exercises the real native velocity representation limit");
            double maximum = 0;
            for (int tick = 0; tick < 320; tick++) {
                Vec3 velocity = mob.getDeltaMovement(), position = mob.position().add(velocity);
                if (position.y < 0) { position = new Vec3(position.x, 0, position.z); velocity = new Vec3(velocity.x, 0, velocity.z); }
                mob.setPos(position);
                mob.setDeltaMovement(new Vec3(velocity.x * .91, (velocity.y - .08) * .98, velocity.z * .91));
                mob.checkDespawn(); maximum = Math.max(maximum, mob.distanceTo(vance));
                check(!mob.isRemoved(), "Released ballistic launch remains inside the real native despawn distance");
            }
            check(maximum > 100 && maximum <= 115.2001, "Launch approaches the despawn boundary with a real margin");
            System.out.println("Personalized released launch: peak distance=" + maximum + ", opening velocity=" + kick);
            mob.setDeltaMovement(new Vec3(0, 2, 0));
            check(PreciseMobMotion.update(mob) != null && PreciseMobMotion.update(mob) == null,
                    "Precision tracking retires once native packets can represent motion");
            mob.setDeltaMovement(new Vec3(10, 0, 0)); PreciseMobMotion.launched(mob);
            mob.setDeltaMovement(new Vec3(Double.NaN, 0, 0));
            check(PreciseMobMotion.update(mob) == null, "Invalid external motion cannot produce a precise packet or crash tracking");
            mob.setDeltaMovement(Vec3.ZERO);
            closeCapture(f, vance, rod);
            // This same eligible rod uses the unmodified native block-grapple force path.
            var anchor = new net.minecraft.core.BlockPos(0, 4, 8);
            f.level.memoryBlocks.put(anchor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            vance.setDeltaMovement(Vec3.ZERO);
            rod.getItem().use(f.level, vance, InteractionHand.MAIN_HAND); hook = vance.fishing; access = (RodHookAccess) hook;
            vance.releaseUsingItem();
            access.essenceAscendance$attachBlock(new net.minecraft.world.phys.BlockHitResult(new Vec3(.5, 4.5, 8), net.minecraft.core.Direction.NORTH, anchor, false));
            rod.getItem().use(f.level, vance, InteractionHand.MAIN_HAND);
            Vec3 playerBefore = com.mistaboom.essence_ascendance.movement.PlayerMotionTracker.velocity(vance);
            Vec3 ordinaryGrapple = RodPullMath.anchorForce(access.essenceAscendance$rodState().anchor.subtract(vance.getEyePosition()),
                    playerBefore, vance.getLookAngle(), context.baseline(), 0, EssenceConfigManager.get().fishingRodBalance(),
                    vance.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.GRAVITY));
            f.level.tick++; AscendanceFishingService.heldInput(vance, hook.getId(), true, 2);
            AscendanceFishingService.tickAttachment(hook, access);
            check(vance.getDeltaMovement().distanceTo(playerBefore.add(ordinaryGrapple)) < 1e-8,
                    "Vance's block grapple uses exactly the ordinary generated force");
            vance.releaseUsingItem();
            var unmodified = new Zombie(EntityType.ZOMBIE, f.level); unmodified.setPos(-300, 0, 0); unmodified.checkDespawn();
            check(unmodified.isRemoved(), "Ordinary distance despawning still works");
        } finally {
            AscendanceFishingService.forget(vance); vance.stopUsingItem();
            ProjectileNativeInterceptionTest.set(PlayerList.class, f.server.getPlayerList(), "viewDistance", oldView);
            ProjectileNativeInterceptionTest.set(PlayerList.class, f.server.getPlayerList(), "simulationDistance", oldSimulation);
        }
        System.out.println("NativePersonalizedEquipmentTest: " + checks + " owner privacy, catalogs, native launch, tracking and despawn checks PASS");
    }
    private static void inventoryOverlay(ProjectileNativeInterceptionTest.Fixture f) throws Exception {
        for (var profile : PersonalizedEquipmentService.definitions()) {
            var owner = f.player(NativeGuardOutcomeTest.NativePlayer.class, Vec3.ZERO, "gift_inventory_owner");
            owner.setUUID(profile.playerId());
            var stack = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.ResourceLocation.parse(profile.itemId())));
            EquipmentTierData.setTier(stack, EquipmentTier.TRANSCENDENT);
            var unchanged = stack.copy();
            owner.getInventory().setItem(0, stack);
            for (int selected : new int[]{0, 1, 8}) {
                //? if >=26.1.2
                /*owner.getInventory().setSelectedSlot(selected);*/
                //? if <26.1.2
                owner.getInventory().selected = selected;
                check(PersonalizedEquipmentService.active(owner, stack).orElseThrow().equals(profile),
                        "Shared overlay survives hotbar selection: " + profile.id());
                verifyInventoryTint(owner, stack, profile.baseRgb(), profile.accentRgb());
            }
            owner.getInventory().setItem(0, ItemStack.EMPTY);
            owner.getInventory().setItem(20, stack);
            check(PersonalizedEquipmentService.active(owner, stack).isPresent(), "Main inventory retains the gift");
            verifyInventoryTint(owner, stack, profile.baseRgb(), profile.accentRgb());
            owner.getInventory().setItem(20, ItemStack.EMPTY);
            owner.containerMenu.setCarried(stack);
            check(PersonalizedEquipmentService.active(owner, stack).isPresent(), "Menu cursor retains its owner's gift");
            verifyInventoryTint(owner, stack, profile.baseRgb(), profile.accentRgb());
            owner.containerMenu.setCarried(ItemStack.EMPTY);
            var dropped = new net.minecraft.world.entity.item.ItemEntity(f.level, 0, 0, 0, stack);
            check(PersonalizedEquipmentService.active(owner, dropped.getItem()).isEmpty(), "Dropped gift immediately reverts");
            verifyInventoryTint(owner, dropped.getItem(), EquipmentTierVisuals.primaryRgb(EquipmentTier.TRANSCENDENT),
                    EquipmentTierVisuals.armorAccentRgb(EquipmentTier.TRANSCENDENT));
            f.player.getInventory().setItem(20, stack);
            check(PersonalizedEquipmentService.active(owner, stack).isEmpty()
                    && PersonalizedEquipmentService.active(f.player, stack).isEmpty(), "Transferred gift immediately reverts");
            f.player.getInventory().setItem(20, ItemStack.EMPTY);
            owner.getInventory().setItem(20, stack);
            check(PersonalizedEquipmentService.active(owner, stack).isPresent()
                    && PersonalizedEquipmentService.active(owner, stack.copy()).isEmpty(), "Pickup restores only the actual inventory stack");
            check(ItemStack.isSameItemSameComponents(stack, unchanged), "Inventory overlay never stores custom colors on the item");
            owner.getInventory().setItem(20, ItemStack.EMPTY);
        }
    }
    private static void verifyInventoryTint(net.minecraft.world.entity.player.Player owner, ItemStack stack, int base, int accent) {
        if (dev.architectury.platform.Platform.getEnvironment() != dev.architectury.utils.Env.CLIENT) return;
        check(com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(List.of(owner), stack, 0) == (0xFF000000 | base)
                && com.mistaboom.essence_ascendance.client.PersonalizedEquipmentClient.itemTint(List.of(owner), stack, 1) == (0xFF000000 | accent),
                "Client inventory base and accent follow the shared possession rule");
    }
    private static void closeCapture(ProjectileNativeInterceptionTest.Fixture f, net.minecraft.server.level.ServerPlayer owner, ItemStack rod) {
        for (double distance : new double[]{1, 2}) {
            var target = new Zombie(EntityType.ZOMBIE, f.level); target.setPos(0, 0, distance); f.level.entities.add(target);
            rod.getItem().use(f.level, owner, InteractionHand.MAIN_HAND);
            var hook = owner.fishing; var access = (RodHookAccess) hook;
            owner.releaseUsingItem(); access.essenceAscendance$attachEntity(target);
            rod.getItem().use(f.level, owner, InteractionHand.MAIN_HAND);
            RodPullMath.OpeningFrame frame = null;
            double minimum = distance;
            for (int tick = 0; tick < 45; tick++) {
                f.level.tick++; AscendanceFishingService.heldInput(owner, hook.getId(), true, 2);
                AscendanceFishingService.tickAttachment(hook, access);
                check(!hook.isRemoved(), "A close held launch remains a valid native attachment");
                if (frame == null) frame = access.essenceAscendance$rodState().openingFrame;
                check(frame != null && frame == access.essenceAscendance$rodState().openingFrame,
                        "Opening direction is captured once, rather than swinging around a moving target");
                Vec3 velocity = target.getDeltaMovement(), position = target.position().add(velocity);
                if (position.y < 0) { position = new Vec3(position.x, 0, position.z); velocity = new Vec3(velocity.x, 0, velocity.z); }
                target.setPos(position); target.setDeltaMovement(new Vec3(velocity.x * .91, (velocity.y - .08) * .98, velocity.z * .91));
                minimum = Math.min(minimum, target.getZ()); target.checkDespawn();
                check(!target.isRemoved(), "Close held launch obeys native mob despawn rules");
            }
            check(minimum >= .6, "Close zombie never crosses the holder or enters the body collision gap");
            Vec3 retained = target.getDeltaMovement(); owner.releaseUsingItem();
            check(hook.isRemoved() && target.getDeltaMovement().equals(retained), "Close capture adds no release braking");
            System.out.println("Native close launch: starting distance=" + distance + ", minimum forward distance=" + minimum);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
