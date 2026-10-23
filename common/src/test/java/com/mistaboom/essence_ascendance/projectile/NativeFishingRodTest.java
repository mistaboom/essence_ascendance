package com.mistaboom.essence_ascendance.projectile;

import com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition;
import com.mistaboom.essence_ascendance.config.EssenceConfigManager;
import com.mistaboom.essence_ascendance.equipment.*;
import com.mistaboom.essence_ascendance.gathering.*;
import com.mistaboom.essence_ascendance.infuser.EquipmentInfusionRecipe;
import com.mistaboom.essence_ascendance.item.AscendanceItems;
import com.mistaboom.essence_ascendance.movement.TraversalService;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.stat.EssenceStats;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import java.util.*;

/** Runs against real loader-transformed bobbers, registered rods and native active-use/durability paths. */
public final class NativeFishingRodTest {
    private static int checks;
    public static void run() throws Exception {
        var previous = EssenceConfigManager.serverRuntime();
        EssenceConfigManager.install(RuntimeBalanceDefinition.bootstrap());
        var f = new ProjectileNativeInterceptionTest.Fixture(NativeGuardOutcomeTest.NativePlayer.class);
        NativeGuardOutcomeTest.registry(f); f.level.memoryBlocks = new HashMap<>();
        var rod = new ItemStack(AscendanceItems.ASCENDANCE_FISHING_ROD.get());
        var player = f.player;
        ProjectileNativeInterceptionTest.set(Entity.class, player, "fluidOnEyes", new HashSet<>());
        var saved = f.saved.getPlayerData(player.getUUID());
        saved.setInvested(EssenceStats.FISHING_SPEED, EssenceConfigManager.get().balanceProfile().bonusTrack(EssenceStats.FISHING_SPEED.id()).checkpoints().getLast().cumulativeCap());
        try {
            infusionTransactions(f);
            for (var tier : EquipmentTier.values()) {
                EquipmentTierData.setTier(rod, tier);
                check(rod.getMaxDamage() == EssenceConfigManager.get().fishingRodBalance().tier(tier).durability(), "Every native max damage is generated");
                var recipe = EquipmentInfusionRecipe.forWorkpiece(rod);
                check(recipe.isPresent() == (tier.next()!=null), "Every equipment upgrade route is connected");
                if (recipe.isPresent()) check(recipe.get().totalRequired() == EssenceConfigManager.get().infuserBalance().equipmentUpgrade(tier.next().serializedName()).totalEssenceRequired(), "Native upgrade cost matches shared generated requirement");
            }
            player.setItemInHand(InteractionHand.OFF_HAND, rod);
            var result = rod.getItem().use(f.level, player, InteractionHand.OFF_HAND);
            //? if >=26.1.2
            /*check(result.consumesAction() && player.fishing instanceof RodHookAccess && player.isUsingItem(), "Real item use casts a native hook and latches the press");*/
            //? if <26.1.2
            check(result.getResult().consumesAction() && player.fishing instanceof RodHookAccess && player.isUsingItem(), "Real item use casts a native hook and latches the press");
            FishingHook hook = player.fishing; RodHookAccess access = (RodHookAccess) hook;
            check(access.essenceAscendance$rodHand()==2 && AscendanceFishingService.valid(player,hook), "Actual offhand cast identity stays valid");
            check(hook.getDeltaMovement().distanceTo(player.getLookAngle().scale(EquipmentFishingRodService.context(player,rod).castSpeed())) < 1e-8, "Native hook uses deterministic generated speed");
            var updates=hook.getEntityData().packDirty(); check(updates!=null && updates.size()>=2, "Cast hand/tier travel through native entity synchronization");
            ((GatheringFishingHookAccess)hook).essenceAscendance$setNibble(10);
            check(hook.retrieve(rod.copy())==0 && !hook.isRemoved(), "A different stack cannot retrieve a genuine native bite");
            check(hook.retrieve(rod)==0 && !hook.isRemoved(), "Initial held cast cannot retrieve before its release");
            ((GatheringFishingHookAccess)hook).essenceAscendance$setNibble(0);
            double range=EquipmentFishingRodService.context(player,rod).baseline().range();
            hook.setPos(player.position().add(0,0,range-1));
            check(AscendanceFishingService.valid(player,hook) && hook.shouldRenderAtSqrDistance((range-1)*(range-1)), "Generated long range survives owner and native render checks");
            hook.setPos(player.position().add(0,0,range+1));
            check(!AscendanceFishingService.valid(player,hook), "Generated range exhaustion invalidates the hook");
            hook.setPos(player.getEyePosition());
            player.releaseUsingItem(); check(access.essenceAscendance$rodState().armed && !hook.isRemoved(), "Initial native release preserves and arms hook");
            BlockPos anchor=new BlockPos(0,4,8); f.level.memoryBlocks.put(anchor, Blocks.STONE.defaultBlockState());
            access.essenceAscendance$attachBlock(new BlockHitResult(new Vec3(.5,4.5,8),Direction.NORTH,anchor,false));
            check(access.essenceAscendance$attachment()==1, "Native solid impact creates a persistent attachment");
            check(access.essenceAscendance$anchorPosition().distanceTo(hook.position())<1e-6, "Native attachment data retains the precise block hit point");
            rod.getItem().use(f.level,player,InteractionHand.OFF_HAND);
            check(access.essenceAscendance$rodState().pulling, "Subsequent native press starts powered pull");
            Vec3 before=player.getDeltaMovement();
            float originalYaw=player.getYRot();player.setYRot(90);
            for(int i=0;i<45;i++){
                f.level.tick++; AscendanceFishingService.heldInput(player,hook.getId(),true); AscendanceFishingService.tickAttachment(hook,access);
                if(i==0){
                    Vec3 kick=player.getDeltaMovement().subtract(before);
                    check(kick.x<0 && Math.abs(kick.x)>Math.abs(kick.z),"Native player grapple responds strongly to sideways opening aim");
                    Vec3 once=player.getDeltaMovement();AscendanceFishingService.tickAttachment(hook,access);
                    check(player.getDeltaMovement().equals(once) && access.essenceAscendance$rodState().poweredTicks==1,"Repeated attachment tick cannot duplicate or restart the opening burst");
                }
            }
            check(!hook.isRemoved() && player.getDeltaMovement().lengthSqr()>before.lengthSqr(), "Pull continues beyond force ramp");
            check(rod.getDamageValue()==2, "Continuous native wear charged once per interval");
            Vec3 momentum=player.getDeltaMovement(); player.releaseUsingItem();
            check(hook.isRemoved() && player.getDeltaMovement().equals(momentum) && player.fishing==null, "Native release detaches and preserves earned momentum");
            // Entity hauling uses the same actual rod/hand and ordinary owner/team/PvP control rules.
            player.setDeltaMovement(Vec3.ZERO); player.fishing=null;
            rod.getItem().use(f.level,player,InteractionHand.OFF_HAND); hook=player.fishing; access=(RodHookAccess)hook; player.releaseUsingItem();
            var target=f.player(NativeGuardOutcomeTest.NativePlayer.class,new Vec3(0,0,6),"hauled_target");
            ProjectileNativeInterceptionTest.set(Entity.class,target,"fluidOnEyes",new HashSet<>());
            check(AscendanceFishingService.movable(player,target), "Native living/PvP target accepts control");
            access.essenceAscendance$attachEntity(target); rod.getItem().use(f.level,player,InteractionHand.OFF_HAND);
            f.level.tick++; AscendanceFishingService.tickAttachment(hook,access);
            check(target.getDeltaMovement().dot(player.position().subtract(target.position()))>0, "Real target receives inward force without direct damage");
            check(target.getDeltaMovement().x<0 && Math.abs(target.getDeltaMovement().x)>Math.abs(target.getDeltaMovement().z),
                    "Native target opening burst follows the owner's sideways look");
            float health=target.getHealth(); Vec3 hauled=target.getDeltaMovement(); player.releaseUsingItem();
            check(target.getDeltaMovement().equals(hauled) && target.getHealth()==health, "Target release retains momentum and adds no hook damage");
            player.setYRot(originalYaw);
            // Switching the exact cast stack invalidates the native owner-held test, even with another rod.
            rod.getItem().use(f.level,player,InteractionHand.OFF_HAND); hook=player.fishing;
            player.setItemInHand(InteractionHand.OFF_HAND,rod.copy());
            check(!AscendanceFishingService.valid(player,hook), "Rod swap cannot reuse stale identity/tier/bonuses");
            AscendanceFishingService.forget(player); check(hook.isRemoved(), "Lifecycle cleanup removes native hook");
            player.stopUsingItem(); player.setItemInHand(InteractionHand.OFF_HAND,rod);
            // Existing artifact fracture is exercised by the real native damage method.
            rod.setDamageValue(rod.getMaxDamage()-1); rod.hurtAndBreak(10,player,EquipmentSlot.OFFHAND);
            check(!rod.isEmpty() && FracturedEquipmentData.isFractured(rod) && !EquipmentFishingRodService.functional(rod), "Native breakage retains a Fractured rod");
            //? if >=26.1.2
            /*check(rod.getItem().use(f.level,player,InteractionHand.OFF_HAND)==net.minecraft.world.InteractionResult.FAIL, "Fractured rod cannot cast");*/
            //? if <26.1.2
            check(rod.getItem().use(f.level,player,InteractionHand.OFF_HAND).getResult()==net.minecraft.world.InteractionResult.FAIL, "Fractured rod cannot cast");
            // Real shared traversal query recognizes ordinary and Ascendance rods in either hand.
            saved.grantAllSkillsForAdmin(List.of(SkillRegistry.require(SkillIds.TERRAIN_FREEDOM),SkillRegistry.require(SkillIds.AQUATIC_BODY),SkillRegistry.require(SkillIds.SURFACE_TENSION)));
            com.mistaboom.essence_ascendance.skill.CommittedSkillService.invalidate(player);
            player.setSprinting(false); player.setShiftKeyDown(false);
            check(TraversalService.surfaceActive(player), "Effective Surface Tension supports held Ascendance rod without sprinting");
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.FISHING_ROD)); check(TraversalService.surfaceActive(player), "Vanilla offhand rod supports standing");
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY); check(!TraversalService.surfaceActive(player), "Removing rod restores sprint requirement");
            player.setSprinting(true); check(TraversalService.surfaceActive(player), "Sprinting still supports exposed liquid");
            player.setShiftKeyDown(true); check(!TraversalService.surfaceActive(player), "Native Shift overrides support");
            fluidBuoyancy(f);
            normalFishing(f);
            attachmentTransitions(f);
            creativeCatalog();
            NativePersonalizedEquipmentTest.run(f);
            if (dev.architectury.platform.Platform.getEnvironment() == dev.architectury.utils.Env.CLIENT) {
                for (var entry : Map.of("net.minecraft.client.Minecraft","rodPress", "net.minecraft.client.renderer.entity.FishingHookRenderer","castHand", "net.minecraft.client.player.LocalPlayer","rodHeartbeat").entrySet())
                    check(Arrays.stream(Class.forName(entry.getKey()).getDeclaredMethods()).anyMatch(m->m.getName().contains(entry.getValue())), "Native client rod input/rendering hook transforms: "+entry.getKey());
            }
        } finally { f.close(); if(previous==null)EssenceConfigManager.reset();else EssenceConfigManager.install(previous); }
        System.out.println("NativeFishingRodTest: "+checks+" actual registered equipment, native cast/use/release, attachment, server force, durability and traversal checks PASS");
    }
    private static void attachmentTransitions(ProjectileNativeInterceptionTest.Fixture f) throws Exception {
        ProjectileNativeInterceptionTest.set(net.minecraft.world.level.Level.class,f.level,"dimensionTypeRegistration",
                net.minecraft.data.registries.VanillaRegistries.createLookup().lookupOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE)
                        .getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD));
        var oldTags=new HashMap<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>,List<Holder<net.minecraft.world.level.material.Fluid>>>();
        oldTags.putAll(com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.snapshot(BuiltInRegistries.FLUID));
        var tags=new HashMap<>(oldTags);tags.put(net.minecraft.tags.FluidTags.WATER,List.of(Fluids.WATER.builtInRegistryHolder(),Fluids.FLOWING_WATER.builtInRegistryHolder()));com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.bind(BuiltInRegistries.FLUID, tags);
        var rod=new ItemStack(AscendanceItems.ASCENDANCE_FISHING_ROD.get());EquipmentTierData.setTier(rod,EquipmentTier.TRANSCENDENT);
        var player=f.player;player.stopUsingItem();player.setItemInHand(InteractionHand.MAIN_HAND,rod);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        f.level.entities.clear();f.level.memoryBlocks.clear();
        f.level.nativeBlockClip=true;
        try {
            // A fast downward cast crosses the whole water block and reaches its floor in one tick.
            // Starting inside water with zero velocity does not exercise this real cast failure.
            f.level.memoryBlocks.put(BlockPos.ZERO,Blocks.WATER.defaultBlockState());
            f.level.memoryBlocks.put(new BlockPos(0,-1,0),Blocks.STONE.defaultBlockState());
            rod.getItem().use(f.level,player,InteractionHand.MAIN_HAND);var sweptHook=player.fishing;
            sweptHook.setPos(.5,2,.5);sweptHook.setDeltaMovement(new Vec3(0,-4,0));sweptHook.tick();
            var sweptAccess=(RodHookAccess)sweptHook;
            check(sweptAccess.essenceAscendance$attachment()==0 && sweptAccess.essenceAscendance$rodState().waterFishing
                    && sweptHook.getY()>0 && sweptHook.getY()<1,"Fast cast enters water before the shallow pool floor");
            check(sweptHook.getDeltaMovement().equals(Vec3.ZERO),"Water absorbs flight momentum before native bobbing");
            player.releaseUsingItem();sweptHook.retrieve(rod);player.stopUsingItem();
            f.level.memoryBlocks.clear();
            f.level.memoryBlocks.put(BlockPos.ZERO,Blocks.WATER.defaultBlockState());
            rod.getItem().use(f.level,player,InteractionHand.MAIN_HAND);var hook=player.fishing;hook.setPos(.5,.5,.5);hook.setDeltaMovement(Vec3.ZERO);hook.tick();
            var access=(RodHookAccess)hook;
            check(access.essenceAscendance$attachment()==0 && access.essenceAscendance$rodState().waterFishing,"Actual native water landing enters ordinary fishing");
            player.releaseUsingItem();check(hook.retrieve(rod)==0 && hook.isRemoved(),"Waiting water cast remains safely click-retractable");player.stopUsingItem();
            var target=f.player(NativeGuardOutcomeTest.NativePlayer.class,new Vec3(.5,.1,2),"water-target");
            f.level.memoryBlocks.put(target.blockPosition(),Blocks.WATER.defaultBlockState());
            rod.getItem().use(f.level,player,InteractionHand.MAIN_HAND);hook=player.fishing;hook.setPos(.5,.5,.5);hook.setDeltaMovement(new Vec3(0,0,3));hook.tick();access=(RodHookAccess)hook;
            check(access.essenceAscendance$attachment()==2 && hook.getHookedIn()==target && !access.essenceAscendance$rodState().waterFishing,"Swept native entity collision takes precedence over water");
            AscendanceFishingService.forget(player);player.stopUsingItem();f.level.entities.clear();f.level.memoryBlocks.clear();
            f.level.memoryBlocks.put(new BlockPos(0,0,2),Blocks.STONE.defaultBlockState());
            rod.getItem().use(f.level,player,InteractionHand.MAIN_HAND);hook=player.fishing;hook.setPos(.5,.5,.5);hook.setDeltaMovement(new Vec3(0,0,3));hook.tick();access=(RodHookAccess)hook;
            check(access.essenceAscendance$attachment()==1 && !hook.isRemoved(),"Swept native solid collision attaches at its impact point");
            AscendanceFishingService.forget(player);player.stopUsingItem();
        } finally {com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.bind(BuiltInRegistries.FLUID, oldTags);f.level.memoryBlocks.clear();f.level.nativeBlockClip=false;}
    }
    private static void creativeCatalog() {
        var combat=new ArrayList<ItemStack>();
        com.mistaboom.essence_ascendance.item.CreativeVariantRegistry.append(CreativeModeTabs.COMBAT,(stack,visibility)->combat.add(stack));
        var shieldTiers=combat.stream().filter(stack->stack.is(AscendanceItems.ASCENDANCE_SHIELD.get()))
                .map(EquipmentTierData::tier).toList();
        check(shieldTiers.equals(List.of(EquipmentTier.values())),"Creative combat catalog includes each shield tier once in order");
        var catalog=AscendanceItems.tieredCatalogItems();
        check(catalog.contains(AscendanceItems.ASCENDANCE_SHIELD.get()) && new HashSet<>(catalog).size()==catalog.size(),
                "Shared JEI catalog includes the shield without duplicate rods");
        var ingredients=new ArrayList<ItemStack>();
        com.mistaboom.essence_ascendance.item.CreativeVariantRegistry.append(CreativeModeTabs.INGREDIENTS,(stack,visibility)->ingredients.add(stack));
        var content=com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent.ASCENDANCE_MATRIX.get();
        check(ingredients.getFirst().is(content),"Matrix starts the material group");
        var expected=List.of("raw_latent_ore","latent_nugget","latent_ingot","latent_block");
        check(ingredients.subList(1,5).stream().map(stack->BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath()).toList().equals(expected),
                "Raw materials and Latent forms precede Essentium in requested order");
        check(ingredients.stream().noneMatch(stack->stack.is(com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent.RAW_LATENT_ORE_BLOCK_ITEM.get())),
                "Raw Latent Block follows vanilla raw blocks outside Ingredients");
        var natural=new ArrayList<ItemStack>();
        com.mistaboom.essence_ascendance.item.CreativeVariantRegistry.append(CreativeModeTabs.NATURAL_BLOCKS,(stack,visibility)->natural.add(stack));
        check(natural.stream().noneMatch(stack->stack.is(com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent.RAW_LATENT_ORE.get())),
                "Raw Latent ore item is absent from natural-block variants");
        check(natural.stream().filter(stack->stack.is(com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent.RAW_LATENT_ORE_BLOCK_ITEM.get())).count()==1,
                "Raw Latent Block appears exactly once in Natural Blocks");
    }
    private static void normalFishing(ProjectileNativeInterceptionTest.Fixture f) throws Exception {
        NativeResourceChainsTest.resources(f);
        var rod=new ItemStack(AscendanceItems.ASCENDANCE_FISHING_ROD.get());EquipmentTierData.setTier(rod,EquipmentTier.TRANSCENDENT);
        var player=f.player; player.setShiftKeyDown(false);player.stopUsingItem();player.setItemInHand(InteractionHand.OFF_HAND,rod);
        rod.getItem().use(f.level,player,InteractionHand.OFF_HAND);var hook=player.fishing;player.releaseUsingItem();
        int before=f.level.entities.size();
        check(hook.retrieve(rod)==0 && hook.isRemoved() && f.level.entities.size()==before,"Native missed retrieval has no fishing loot or XP");
        rod.getItem().use(f.level,player,InteractionHand.OFF_HAND);hook=player.fishing;player.releaseUsingItem();
        ((GatheringFishingHookAccess)hook).essenceAscendance$setNibble(10);
        java.lang.reflect.Field loaderLoot=null;Object previousLoaderLoot=null;
        try {
            try {
                loaderLoot=Class.forName("net.neoforged.neoforge.common.NeoForgeEventHandler").getDeclaredField("INSTANCE");loaderLoot.setAccessible(true);previousLoaderLoot=loaderLoot.get(null);
                if(previousLoaderLoot==null)loaderLoot.set(null,Class.forName("net.neoforged.neoforge.common.loot.LootModifierManager").getConstructor().newInstance());
            } catch(ClassNotFoundException fabric) { }
            check(hook.retrieve(rod)>0 && hook.isRemoved(),"Ordinary click retrieves a native water bite");
            check(f.level.entities.stream().anyMatch(e->e instanceof net.minecraft.world.entity.item.ItemEntity)
                    && f.level.entities.stream().anyMatch(e->e instanceof net.minecraft.world.entity.ExperienceOrb),"Genuine native loot tables spawn a catch and fishing XP");
        } finally {if(loaderLoot!=null)loaderLoot.set(null,previousLoaderLoot);}
    }
    @SuppressWarnings("unchecked")
    private static void fluidBuoyancy(ProjectileNativeInterceptionTest.Fixture f) throws Exception {
        var oldTags=new HashMap<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>,List<Holder<net.minecraft.world.level.material.Fluid>>>();
        oldTags.putAll(com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.snapshot(BuiltInRegistries.FLUID));
        var tags=new HashMap<>(oldTags);
        tags.put(TraversalService.SURFACE_FLUIDS,List.of(Fluids.WATER.builtInRegistryHolder(),Fluids.FLOWING_WATER.builtInRegistryHolder(),Fluids.LAVA.builtInRegistryHolder(),Fluids.FLOWING_LAVA.builtInRegistryHolder()));
        tags.put(net.minecraft.tags.FluidTags.WATER,List.of(Fluids.WATER.builtInRegistryHolder(),Fluids.FLOWING_WATER.builtInRegistryHolder()));
        tags.put(net.minecraft.tags.FluidTags.LAVA,List.of(Fluids.LAVA.builtInRegistryHolder(),Fluids.FLOWING_LAVA.builtInRegistryHolder()));
        com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.bind(BuiltInRegistries.FLUID, tags);
        var player=f.player; player.setShiftKeyDown(false); player.setSprinting(false);
        player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.FISHING_ROD));
        var updateEyes=Entity.class.getDeclaredMethod("updateFluidOnEyes");updateEyes.setAccessible(true);
        try {
            for(var block:List.of(Blocks.WATER,Blocks.LAVA)) {
                f.level.memoryBlocks.put(BlockPos.ZERO,block.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL,3));
                player.setPos(.5,.1,.5);player.setDeltaMovement(Vec3.ZERO);updateEyes.invoke(player);
                check(TraversalService.surfaceBuoyancyEligible(player),"Real flowing "+block+" height admits head-above-liquid buoyancy");
                TraversalService.tickSurfaceBuoyancy(player);
                check(player.getDeltaMovement().y>0 && player.getDeltaMovement().y<=EssenceConfigManager.get().fishingRodBalance().buoyancySpeed(),"Real buoyancy is gentle and profile-bounded");
                player.setShiftKeyDown(true);player.setDeltaMovement(Vec3.ZERO);TraversalService.tickSurfaceBuoyancy(player);
                check(player.getDeltaMovement().equals(Vec3.ZERO),"Shift immediately overrides native buoyancy");player.setShiftKeyDown(false);
                var eyePos=BlockPos.containing(player.getX(),player.getEyeY(),player.getZ());
                f.level.memoryBlocks.put(eyePos,block.defaultBlockState());updateEyes.invoke(player);
                check(!TraversalService.surfaceBuoyancyEligible(player),"Native submerged-eye fluid prevents ascent");
                f.level.memoryBlocks.remove(eyePos);updateEyes.invoke(player);
            }
            player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
            check(!TraversalService.surfaceBuoyancyEligible(player),"Rod removal stops native buoyancy");
            f.level.memoryBlocks.clear();
        } finally {com.mistaboom.essence_ascendance.verification.RegistryTagTestCompat.bind(BuiltInRegistries.FLUID, oldTags);}
    }
    static void infusionTransactions(ProjectileNativeInterceptionTest.Fixture f) throws Exception {
        int beforeChecks = checks;
        var type=com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity.class;
        var machine=new com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity(new BlockPos(6,0,0),com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent.ESSENCE_INFUSER.get().defaultBlockState());
        machine.setLevel(f.level);
        f.level.memoryMachines = new HashMap<>(); f.level.memoryMachines.put(machine.getBlockPos(), machine);
        machine.bindOnPlacement(f.player);
        var source = new com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity(new BlockPos(5,0,0), com.mistaboom.essence_ascendance.crucible.EssenceCrucibleContent.ESSENCE_CRUCIBLE.get().defaultBlockState());
        source.setLevel(f.level); f.level.memoryMachines.put(source.getBlockPos(), source); source.bindOnPlacement(f.player);
        machine.setLink(f.player, com.mistaboom.essence_ascendance.machine.MachineLink.Mode.LOCKED,
                new com.mistaboom.essence_ascendance.machine.MachineLink.Source(f.level.dimension().location(), source.getBlockPos()));
        machine.machineBinding().start(f.saved, f.player.getUUID(), f.player.getUUID());
        var installedFocus = new ItemStack(com.mistaboom.essence_ascendance.pylon.EssencePylonContent.ESSENCE_FOCUS.get());
        com.mistaboom.essence_ascendance.pylon.EssenceFocusData.setTier(installedFocus, com.mistaboom.essence_ascendance.pylon.EssenceFocusTier.values()[com.mistaboom.essence_ascendance.pylon.EssenceFocusTier.values().length-1]);
        machine.setItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("FOCUS_SLOT"), null), installedFocus);
        var grantField = type.getDeclaredField("grantRemaining"); grantField.setAccessible(true);
        var transfer=type.getDeclaredMethod("transferEquipmentEssence",net.minecraft.server.level.ServerLevel.class,EquipmentInfusionRecipe.class,long.class); transfer.setAccessible(true);
        var complete=type.getDeclaredMethod("completeEquipmentInfusion",EquipmentInfusionRecipe.class); complete.setAccessible(true);
        var work=new ItemStack(AscendanceItems.ASCENDANCE_FISHING_ROD.get()); EquipmentFishingRodService.refreshNativeDurability(work);
        for(var tier:EquipmentTier.values()) {
            check(EquipmentTierData.tier(work)==tier && work.getMaxDamage()==EssenceConfigManager.get().fishingRodBalance().tier(tier).durability(),"Evolving rod reaches actual generated tier "+tier);
            var recipe=EquipmentInfusionRecipe.forWorkpiece(work).orElse(null); if(recipe==null)break;
            machine.setItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("INPUT_SLOT"), null),work);
            machine.setProcessingEnabled(f.player, true);
            machine.setItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("OUTPUT_SLOT"), null),ItemStack.EMPTY);
            machine.setItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("COMPONENT_SLOT"), null),new ItemStack(com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent.ASCENDANCE_MATRIX.get(),recipe.matrixCount()+1));
            for(var entry:recipe.requirements().entrySet()) f.saved.addCrucibleStored(f.player.getUUID(),com.mistaboom.essence_ascendance.essence.EssenceRegistry.get(entry.getKey()).orElseThrow(),entry.getValue());
            grantField.setLong(machine, recipe.totalRequired());
            long first=(long)transfer.invoke(machine,f.level,recipe,recipe.totalRequired()-1);
            work=machine.getItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("INPUT_SLOT"), null));
            check(first==recipe.totalRequired()-1 && SoulboundEquipmentData.isSoulbound(work),"Real streamed transfer reserves exact Essence and binds owner");
            complete.invoke(machine,recipe);
            check(EquipmentTierData.tier(work)==tier && machine.getItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("COMPONENT_SLOT"), null)).getCount()==recipe.matrixCount()+1,"Incomplete infusion changes neither tier nor matrices");
            check((long)transfer.invoke(machine,f.level,recipe,1L)==1,"Final missing Essence is withdrawn once"); complete.invoke(machine,recipe);
            work=machine.getItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("OUTPUT_SLOT"), null));
            check(EquipmentTierData.tier(work)==recipe.targetTier() && com.mistaboom.essence_ascendance.infuser.EquipmentInfusionData.read(work).isEmpty(),"Native transaction publishes completed tier and clears only upgrade progress");
            check(machine.getItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("COMPONENT_SLOT"), null)).getCount()==1 && SoulboundEquipmentData.read(work).orElseThrow().ownerId().equals(f.player.getUUID()),"Matrices consumed once; original Soulbound identity retained");
            for(var id:recipe.requirements().keySet())check(f.saved.getPlayerData(f.player.getUUID()).getCrucibleStored(com.mistaboom.essence_ascendance.essence.EssenceRegistry.get(id).orElseThrow())==0,"Exact category balance settled");
        }
        // Exercise the production Infuser participant, including failure after a partial inventory/job mutation.
        var pay=type.getDeclaredMethod("payWithInventory",com.mistaboom.essence_ascendance.data.EssenceSavedData.class,Map.class,Runnable.class);
        pay.setAccessible(true);
        Map<net.minecraft.resources.ResourceLocation,Long> costs=new LinkedHashMap<>();
        for(var essence:com.mistaboom.essence_ascendance.essence.EssenceTypes.ORDERED) {
            f.saved.addCrucibleStored(f.player.getUUID(),essence,3);
            costs.put(essence.id(),3L);
        }
        var itemsField=type.getDeclaredField("items");itemsField.setAccessible(true);
        machine.setItem(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(type.getField("INPUT_SLOT"), null), new ItemStack(com.mistaboom.essence_ascendance.pylon.EssencePylonContent.ESSENCE_FOCUS.get()));
        machine.setProcessingEnabled(f.player, true);
        @SuppressWarnings("unchecked") var inventory=(List<ItemStack>)itemsField.get(machine);
        var beforeInventory=inventory.stream().map(ItemStack::copy).toList();
        var beforeMoney=f.saved.save(new net.minecraft.nbt.CompoundTag(),null);
        var ticksField=type.getDeclaredField("processingTicks");ticksField.setAccessible(true);ticksField.setInt(machine,17);
        Runnable fail=() -> {
            inventory.set(0,new ItemStack(Items.DIRT,2));
            try { ticksField.setInt(machine,999); } catch(ReflectiveOperationException failure) { throw new RuntimeException(failure); }
            throw new IllegalStateException("Intentional stage-2 Infuser rollback fixture");
        };
        grantField.setLong(machine, 100L);
        check(!(boolean)pay.invoke(machine,f.saved,costs,fail),"Production Infuser rejects failed inventory mutation");
        for(int i=0;i<inventory.size();i++)check(ItemStack.matches(inventory.get(i),beforeInventory.get(i)),"Production rollback restores exact native stack/components");
        check(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(ticksField, machine)==17 && f.saved.save(new net.minecraft.nbt.CompoundTag(),null).equals(beforeMoney),"Production rollback preserves timer and all six monetary records/revisions");
        costs.put(com.mistaboom.essence_ascendance.essence.EssenceTypes.ORDERED.getLast().id(),4L);
        check(!(boolean)pay.invoke(machine,f.saved,costs,(Runnable)() -> { throw new AssertionError("Unfunded operation touched inventory"); }),"Production six-type validation precedes inventory");
        f.saved.reservoirs().pause(new com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness(
                com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness.Status.NOT_READY,"fixture","Roster temporarily unavailable"));
        com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity.serverTick(f.level,machine.getBlockPos(),machine.getBlockState(),machine);
        check(com.mistaboom.essence_ascendance.verification.ReflectiveFieldTestCompat.readInt(ticksField, machine)==17 && f.saved.save(new net.minecraft.nbt.CompoundTag(),null).equals(beforeMoney),
                "Unavailable authoritative roster pauses native machine without timer or monetary mutation");
        com.mistaboom.essence_ascendance.team.MembershipPublication.publish(f.saved, com.mistaboom.essence_ascendance.team.NativeMembershipService.READY,
                f.saved.reservoirs().settledTeams().stream().map(com.mistaboom.essence_ascendance.team.MembershipRoster::from).toList());
        var provider=net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var loaded=com.mistaboom.essence_ascendance.data.ItemStackNbtCompat.parse(provider,
                com.mistaboom.essence_ascendance.data.ItemStackNbtCompat.save(work, provider)).orElseThrow();
        check(EquipmentTierData.tier(loaded)==EquipmentTier.TRANSCENDENT && SoulboundEquipmentData.read(loaded).equals(SoulboundEquipmentData.read(work)),"Native ItemStack save/load preserves completed rod and ownership");
        FracturedEquipmentData.markFractured(work); work.setDamageValue(work.getMaxDamage()-1);
        var repair=com.mistaboom.essence_ascendance.infuser.RepairInfusionRecipe.forWorkpiece(work).orElseThrow();
        check(repair.essenceRequired()==(long)work.getMaxDamage()*EssenceConfigManager.get().infuserBalance().repair().essencePerDurability(),"Fractured repair uses actual generated durability/cost");
        var repaired=repair.createOutput(new com.mistaboom.essence_ascendance.infuser.EssenceInfuserRecipeContext(work,com.mistaboom.essence_ascendance.essence.EssenceTypes.GATHERING,null,null,com.mistaboom.essence_ascendance.infuser.EssenceInfuserBalance.profile((com.mistaboom.essence_ascendance.pylon.EssenceFocusTier)null)));
        check(EquipmentFishingRodService.functional(repaired) && repaired.getDamageValue()==0 && SoulboundEquipmentData.read(repaired).equals(SoulboundEquipmentData.read(work)),"Repair restores full rod while preserving tier and ownership");
        System.out.println("Native Infuser streamed-payment/rollback fixture: " + (checks - beforeChecks) + " assertions passed");
    }
    private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
}
