package com.mistaboom.essence_ascendance.achievement;

import com.mistaboom.essence_ascendance.attunement.AttunementGameplay;
import com.mistaboom.essence_ascendance.achievementcontrol.HarvestRemovalCancellation;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.gathering.NaturalOreDropService;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserContent;
import com.mistaboom.essence_ascendance.ore.LatentOreBlockEntity;
import com.mistaboom.essence_ascendance.ore.LatentOreHost;
import com.mistaboom.essence_ascendance.skill.*;
import com.mistaboom.essence_ascendance.skill.effect.SkillEffectRuntime;
import com.mistaboom.essence_ascendance.tier.AscendanceTiers;
import com.mistaboom.essence_ascendance.verification.BlockEntityTestNbtCompat;
import com.mistaboom.essence_ascendance.worldgen.PrimarySubstrateDiscovery;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Actual native successful mining, generated Latent Ore, persisted origin and Nature's Boon rewards in a disposable world. */
public final class GatheringAchievementNativeFixture {
    private static int checks;
    private static final List<Integer> IDS = List.of(122,126,182);
    private GatheringAchievementNativeFixture() { }
    public static void run(ServerLevel level, ServerPlayer observer) throws Exception {
        checks=0;var p=player(level,"AchGathering");online(p,true);
        try {
            p.setPos(150,110,150);level.getChunkAt(p.blockPosition());
            for(int id:IDS){check(advancement(p,id)!=null,"Native gathering resource present "+id);revoke(p,id);}
            momentum(level,p);naturalOre(level,p);boon(level,p);
            for(int id:IDS){check(done(p,id),"Actual native gathering completion "+id);check(!done(observer,id),"Nearby observer never receives another actor's gathering achievement "+id);}
            p.getAdvancements().save();
            for(int id:IDS)revoke(p,id);
            p.getAdvancements().reload(level.getServer().getAdvancements());
            for(int id:IDS)check(done(p,id),"Native advancement JSON restores gathering completion "+id);
            System.out.println("GatheringAchievementNativeFixture: "+checks+" assertions passed; actual destroyBlock rhythm, natural WorldGenRegion Latent Ore origin and native save/reload, underwater Aquatic Body harvest, actual Nature's Boon item spawns in all three canonical dimensions including default End Latent Ore; controlled skill/time/terrain fixtures");
        } finally {SkillEffectRuntime.reset(p);online(p,false);p.discard();}
    }
    private static void momentum(ServerLevel level,ServerPlayer p) {
        enable(p,SkillIds.MINING_MOMENTUM);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));
        var pos=new BlockPos(151,110,150);var tuning=SkillEffectRuntime.context(p).settings().gathering().miningMomentum();
        int toMaximum=0;double amount=0;
        while(amount<1){amount=Math.clamp(amount+1.0/tuning.buildBreaks(),0,1);toMaximum++;}
        for(int i=0;i<toMaximum+19;i++)mine(level,p,pos,Blocks.STONE.defaultBlockState());
        check(!done(p,122),"Native break reaching maximum is excluded and 19 continuation blocks do not award");
        p.gameMode.destroyBlock(pos);
        check(!done(p,122),"Native AIR no-op cannot become the twentieth continuation block regardless of native return convention");
        level.setBlock(pos,Blocks.COMMAND_BLOCK.defaultBlockState(),Block.UPDATE_ALL);
        check(!p.gameMode.destroyBlock(pos)&&level.getBlockState(pos).is(Blocks.COMMAND_BLOCK)&&!done(p,122),
                "Native permission-rejected command-block break neither removes the block nor advances mining continuation");
        level.setBlock(pos,Blocks.STONE.defaultBlockState(),Block.UPDATE_ALL);
        canceledRemoval(p,pos);
        check(level.getBlockState(pos).is(Blocks.STONE)&&!done(p,122),
                "Actual native removal failure despite outer true cannot become the twentieth momentum block");
        mine(level,p,pos,Blocks.STONE.defaultBlockState());check(done(p,122),"Twentieth actual native capped continuation earns Diggy Diggy Flow");
        revoke(p,122);mine(level,p,pos,Blocks.STONE.defaultBlockState());check(!done(p,122),"Unchanged saturated rhythm cannot replay revoke");
        SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        for(int i=0;i<toMaximum+10;i++)mine(level,p,pos,Blocks.STONE.defaultBlockState());
        advance(level,tuning.chainTimeoutTicks()+1L);SkillEffectRuntime.tick(p);
        for(int i=0;i<toMaximum+19;i++)mine(level,p,pos,Blocks.STONE.defaultBlockState());
        check(!done(p,122),"Native skill timeout resets continuation rather than merging old ten blocks");
        mine(level,p,pos,Blocks.STONE.defaultBlockState());check(done(p,122),"Fresh actual 20-block continuation re-earns after timeout");
        revoke(p,122);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);p.setGameMode(GameType.CREATIVE);
        for(int i=0;i<toMaximum;i++)mine(level,p,pos,Blocks.STONE.defaultBlockState());
        p.setGameMode(GameType.SURVIVAL);for(int i=0;i<20;i++)mine(level,p,pos,Blocks.STONE.defaultBlockState());
        check(!done(p,122),"Creative-origin maximum cannot become a survival continuation");
        SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        for(int i=0;i<toMaximum+20;i++)mine(level,p,pos,Blocks.STONE.defaultBlockState());
        check(done(p,122),"Fresh wholly survival rhythm can earn normally after creative challenge rejection");
    }
    private static void naturalOre(ServerLevel overworld,ServerPlayer p) {
        var nether=Objects.requireNonNull(overworld.getServer().getLevel(Level.NETHER));
        var invalidated=findGeneratedVanillaOre(nether);
        var unchanged=nether.getBlockState(invalidated);
        nether.setBlock(invalidated,unchanged,Block.UPDATE_ALL);
        check(!com.mistaboom.essence_ascendance.gathering.GeneratedOreObservations.matches(nether.getServer(),nether.dimension().location().toString(),invalidated,unchanged),
                "Actual same-state Level mutation invalidates old generated-origin proof even when the block is unchanged");
        move(p,nether,invalidated.getX()+2.5,invalidated.getY(),invalidated.getZ()+.5);enable(p,SkillIds.AQUATIC_BODY);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));water(nether,p);
        check(p.isUnderWater()&&p.gameMode.destroyBlock(invalidated)&&!done(p,126),
                "Actual underwater harvest cannot inherit generation proof after a same-state administrative mutation");
        var vanilla=findGeneratedVanillaOre(nether);
        move(p,nether,vanilla.getX()+2.5,vanilla.getY(),vanilla.getZ()+.5);enable(p,SkillIds.AQUATIC_BODY);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));water(nether,p);
        check(p.isUnderWater(),"Native fluid-eye state confirms vanilla natural-ore actor is underwater");
        check(p.gameMode.destroyBlock(vanilla)&&done(p,126),"Actual positively observed vanilla worldgen ore qualifies Under Pressure");
        revoke(p,126);
        var end=Objects.requireNonNull(overworld.getServer().getLevel(Level.END));
        check(PrimarySubstrateDiscovery.selection(end).enabled(),"Default End Latent Ore worldgen is enabled");
        persistentOriginInvalidation(end,p);
        var ore=findGeneratedOre(end);var pos=ore.getBlockPos();
        String dimension=end.dimension().location().toString();
        check(ore.hasGeneratedOrigin(dimension,pos),"Actual End WorldGenRegion feature supplies positive generated origin");
        var saved=ore.saveWithFullMetadata(end.registryAccess());
        BlockEntityTestNbtCompat.load(ore,saved,end.registryAccess());
        check(ore.hasGeneratedOrigin(dimension,pos),"Native ore save/load preserves positive generated origin");
        var moved=new LatentOreBlockEntity(pos.above(),ore.getBlockState());BlockEntityTestNbtCompat.load(moved,saved,end.registryAccess());
        check(!moved.hasGeneratedOrigin(dimension,pos.above()),"Native copied block-entity NBT cannot authorize another position");
        var returned=new LatentOreBlockEntity(pos,ore.getBlockState());
        BlockEntityTestNbtCompat.load(returned,moved.saveWithFullMetadata(end.registryAccess()),end.registryAccess());
        check(!returned.hasGeneratedOrigin(dimension,pos),"Detached native save/load A-to-B-to-A cannot revive copied proof");
        move(p,end,pos.getX()+2.5,pos.getY(),pos.getZ()+.5);enable(p,SkillIds.AQUATIC_BODY);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));
        water(end,p);check(p.isUnderWater(),"Native fluid-eye state confirms underwater mining actor");
        canceledRemoval(p,pos);
        check(end.getBlockState(pos).is(EssenceInfuserContent.LATENT_ORE.get())&&!done(p,126),
                "Generated ore with positive provenance cannot earn from canceled native removal despite outer true");
        var placed=pos.offset(1,0,0);end.setBlock(placed,Blocks.DIAMOND_ORE.defaultBlockState(),Block.UPDATE_ALL);
        check(p.gameMode.destroyBlock(placed)&&!done(p,126),"Player/command placed ore identity alone never earns Under Pressure");
        var unknown=pos.offset(-1,0,0);end.setBlock(unknown,EssenceInfuserContent.LATENT_ORE.get().defaultBlockState(),Block.UPDATE_ALL);
        var placedOre=(LatentOreBlockEntity)end.getBlockEntity(unknown);placedOre.setHost(Blocks.END_STONE.defaultBlockState());
        check(!placedOre.hasGeneratedOrigin(dimension,unknown),"New admin/player Latent Ore has host identity without generation proof");
        check(p.gameMode.destroyBlock(unknown)&&!done(p,126),"Placed Latent Ore also fails closed while underwater with effective skill");
        // A denied Level.setBlock attempt may conservatively invalidate provenance before the
        // fixture's cancellation hook runs. Injector ordering must not make the later positive
        // depend on whether that canceled block retained evidence. Use another genuinely generated
        // block, retaining all failed-removal and unchanged-block assertions above.
        ore=findGeneratedOre(end,pos);pos=ore.getBlockPos();
        move(p,end,pos.getX()+2.5,pos.getY(),pos.getZ()+.5);enable(p,SkillIds.AQUATIC_BODY);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));water(end,p);
        check(ore.hasGeneratedOrigin(dimension,pos),"Fresh positive harvest retains genuine native origin after fixture setup");
        check(p.gameMode.destroyBlock(pos),"Fresh generated ore native removal actually succeeds");
        check(done(p,126),"Actual generated ore harvested underwater with effective Aquatic Body earns");
        var silk=LatentOreHost.stack(Blocks.END_STONE.defaultBlockState());
        end.setBlock(pos,EssenceInfuserContent.LATENT_ORE.get().defaultBlockState(),Block.UPDATE_ALL);
        var block=EssenceInfuserContent.LATENT_ORE.get();block.setPlacedBy(end,pos,block.defaultBlockState(),p,silk);
        check(!((LatentOreBlockEntity)end.getBlockEntity(pos)).hasGeneratedOrigin(dimension,pos),"Replaced/picked-up ore never carries worldgen proof through its ordinary item");
    }
    private static void persistentOriginInvalidation(ServerLevel end,ServerPlayer p) {
        String dimension=end.dimension().location().toString();
        var ore=findGeneratedOre(end);var pos=ore.getBlockPos();
        end.setBlock(pos,ore.getBlockState(),Block.UPDATE_ALL);
        check(!ore.hasGeneratedOrigin(dimension,pos),"Actual same-state Level write invalidates persistent Latent Ore origin");
        BlockEntityTestNbtCompat.load(ore,ore.saveWithFullMetadata(end.registryAccess()),end.registryAccess());
        check(!ore.hasGeneratedOrigin(dimension,pos),"Same-state invalidation survives native block-entity save/load");
        move(p,end,pos.getX()+2.5,pos.getY(),pos.getZ()+.5);enable(p,SkillIds.AQUATIC_BODY);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));water(end,p);
        check(p.gameMode.destroyBlock(pos)&&!done(p,126),"Underwater harvest of admin-mutated Latent Ore cannot claim its old natural origin");

        ore=findGeneratedOre(end);pos=ore.getBlockPos();var copy=pos.offset(2,2,2);
        end.getChunkAt(copy);
        cloneOre(end,pos,copy);
        check(end.getBlockEntity(copy) instanceof LatentOreBlockEntity copied&&!copied.hasGeneratedOrigin(dimension,copy),
                "Actual native clone at another address immediately discards copied Latent Ore proof");
        cloneOre(end,copy,pos);
        check(end.getBlockEntity(pos) instanceof LatentOreBlockEntity returned&&!returned.hasGeneratedOrigin(dimension,pos),
                "Actual native clone A-to-B-to-A cannot revive original worldgen proof");
        move(p,end,pos.getX()+2.5,pos.getY(),pos.getZ()+.5);enable(p,SkillIds.AQUATIC_BODY);
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));water(end,p);
        check(p.gameMode.destroyBlock(pos)&&!done(p,126),"Underwater harvest after native clone round trip produces no natural-origin award");
        end.setBlock(copy,Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
    }
    private static void cloneOre(ServerLevel level,BlockPos from,BlockPos to) {
        String source=from.getX()+" "+from.getY()+" "+from.getZ();
        String destination=to.getX()+" "+to.getY()+" "+to.getZ();
        level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack().withLevel(level),
                "clone "+source+" "+source+" "+destination+" replace force");
    }
    private static BlockPos findGeneratedVanillaOre(ServerLevel level) {
        int start;
        try {
            var sequence=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("gathering-fixture-next-chunk.txt");
            start=java.nio.file.Files.exists(sequence)?Integer.parseInt(java.nio.file.Files.readString(sequence).trim()):200;
            java.nio.file.Files.writeString(sequence,Integer.toString(start+16));
        } catch(java.io.IOException failure) {throw new IllegalStateException("Cannot allocate fresh disposable ore-generation fixture chunks",failure);}
        for(int cx=start;cx<start+3;cx++)for(int cz=start;cz<start+3;cz++) {
            var chunk=level.getChunk(cx,cz);
            //? if >=26.1.2
            /*int minimum=level.getMinY(),maximum=level.getMaxY();*/
            //? if <26.1.2
            int minimum=level.getMinBuildHeight(),maximum=level.getMaxBuildHeight()-1;
            for(int y=minimum;y<=maximum;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
                var position=new BlockPos((cx<<4)+x,y,(cz<<4)+z);var state=chunk.getBlockState(position);
                if(state.getBlock() instanceof com.mistaboom.essence_ascendance.ore.LatentOreBlock)continue;
                if(com.mistaboom.essence_ascendance.gathering.GeneratedOreObservations.matches(level.getServer(),level.dimension().location().toString(),position,state))return position;
            }
        }
        throw new AssertionError("Actual newly generated Nether chunks produced no positively observed vanilla ore");
    }
    private static LatentOreBlockEntity findGeneratedOre(ServerLevel end) {
        return findGeneratedOre(end,null);
    }
    private static LatentOreBlockEntity findGeneratedOre(ServerLevel end,BlockPos excluded) {
        for(int radius=0;radius<5;radius++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=radius)continue;
            var chunk=end.getChunk(x,z);
            for(var pos:List.copyOf(chunk.getBlockEntitiesPos()))if(!pos.equals(excluded)&&chunk.getBlockEntity(pos) instanceof LatentOreBlockEntity ore
                    && ore.hasGeneratedOrigin(end.dimension().location().toString(),pos))return ore;
        }
        throw new AssertionError("Default End generated no positively proven Latent Ore in the bounded 9x9 chunk search");
    }
    private static void boon(ServerLevel overworld,ServerPlayer p) {
        enable(p,SkillIds.NATURES_BOON);p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.NETHERITE_PICKAXE));
        p.getRandom().setSeed(0x182A7L);
        var end=Objects.requireNonNull(overworld.getServer().getLevel(Level.END));
        move(p,end,150,110,150);
        check(NaturalOreDropService.candidateCount(p)==1,"Default End Nature's Boon has its real Latent Ore candidate");
        var originalRuntime=com.mistaboom.essence_ascendance.config.EssenceConfigManager.serverRuntime();
        try {
            var guaranteed=originalRuntime.toJson();guaranteed.getAsJsonObject("effects").getAsJsonObject("gathering").getAsJsonObject("naturesBoon").addProperty("dropChance",1.0);
            // A temporary one-state curve at the native probability ceiling keeps the published
            // rank-one mirror exact and avoids pretending that further improvements exist above 100%.
            var curve=guaranteed.getAsJsonObject("skillCurves").getAsJsonObject(SkillIds.NATURES_BOON.toString());
            var first=curve.getAsJsonArray("ranks").get(0).getAsJsonObject().deepCopy();
            first.getAsJsonObject("parameters").addProperty("gathering/naturesBoon/dropChance",1.0);
            var ranks=new com.google.gson.JsonArray();ranks.add(first);curve.add("ranks",ranks);curve.addProperty("maximumRank",1);
            com.mistaboom.essence_ascendance.config.EssenceConfigManager.install(com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition.fromJson(guaranteed));
            SkillEffectRuntime.refresh(p);
            check(SkillEffectRuntime.context(p).settings().gathering().naturesBoon().dropChance()==1,
                    "Disposable canceled-harvest probe uses actual effective guaranteed Nature's Boon chance");
            var denied=new BlockPos(151,110,150);end.setBlock(denied,Blocks.END_STONE.defaultBlockState(),Block.UPDATE_ALL);int before=mask(p);
            canceledRemoval(p,denied);
            check(end.getBlockState(denied).is(Blocks.END_STONE)&&mask(p)==before&&!done(p,182),
                    "Canceled actual native substrate removal cannot produce a Nature's Boon receipt even with guaranteed configured drop chance");
        } finally {
            com.mistaboom.essence_ascendance.config.EssenceConfigManager.install(originalRuntime);SkillEffectRuntime.refresh(p);
        }
        try {
            var disabled=originalRuntime.toJson();disabled.getAsJsonObject("worldgen").getAsJsonObject("end").addProperty("enabled",false);
            com.mistaboom.essence_ascendance.config.EssenceConfigManager.install(com.mistaboom.essence_ascendance.balance.runtime.RuntimeBalanceDefinition.fromJson(disabled));
            PrimarySubstrateDiscovery.clear();
            check(NaturalOreDropService.candidateCount(p)==0,"A real in-memory disabled-End configuration removes the default End ore reward pool");
            check(NaturalOreDropService.dropRandomOreReceipt(p,new BlockPos(151,110,150),Blocks.END_STONE.defaultBlockState(),p.getMainHandItem())==null&&!done(p,182),
                    "An unavailable configured End pool cannot invent a Nature's Boon reward or completion");
        } finally {
            com.mistaboom.essence_ascendance.config.EssenceConfigManager.install(originalRuntime);PrimarySubstrateDiscovery.clear();SkillEffectRuntime.refresh(p);
        }
        // A nonempty native loot roll with block-drop delivery disabled is not a committed reward.
        tileDrops(end,false);
        try {
            var missed=NaturalOreDropService.dropRandomOreReceipt(p,new BlockPos(151,110,150),Blocks.END_STONE.defaultBlockState(),p.getMainHandItem());
            check(missed==null&&!done(p,182),"Missing native bonus delivery does not fabricate a received ore");
        } finally { tileDrops(end,true); }
        try(var receipt=com.mistaboom.essence_ascendance.gameplay.CommittedItemDrops.beginBlockDrops(end)) {
            var unrelated=new net.minecraft.world.entity.item.ItemEntity(end,151.5,110.5,150.5,new ItemStack(Items.DIAMOND));
            check(end.addFreshEntity(unrelated)&&receipt.items()==0,"An unrelated successful native item spawn cannot substitute for a bonus ore receipt");
            receipt.expectBlockDrop();Block.popResource(end,new BlockPos(151,110,150),new ItemStack(Items.DIAMOND));
            check(receipt.items()==1,"Only the exact directly armed native block-drop UUID supplies receipt credit");
            unrelated.discard();
        }
        for(var dimension:List.of(Level.OVERWORLD,Level.NETHER,Level.END)) {
            var level=Objects.requireNonNull(overworld.getServer().getLevel(dimension));move(p,level,150,110,150);SkillEffectRuntime.refresh(p);
            check(SkillEffectRuntime.context(p).isEffective(SkillIds.NATURES_BOON),"Nature's Boon remains effective in actual target dimension "+dimension);
            var substrate=dimension.equals(Level.OVERWORLD)?Blocks.STONE:dimension.equals(Level.NETHER)?Blocks.NETHERRACK:Blocks.END_STONE;
            var pos=new BlockPos(151,110,150);int before=mask(p),attempt=0;
            while(mask(p)==before&&attempt++<4096)mine(level,p,pos,substrate.defaultBlockState());
            check(mask(p)!=before,"Actual successful native Nature's Boon spawn supplies dimension receipt "+dimension+" within "+attempt+" controlled substrate harvests");
            if(!dimension.equals(Level.END))check(!done(p,182),"Two dimensions cannot substitute for all three actual bonuses");
        }
        check(done(p,182),"Actual Nature's Boon native loot in Overworld Nether and default-enabled End earns");
    }
    private static int mask(ServerPlayer p) {
        var root=EssenceSavedData.get(p.server).getPlayerData(p.getUUID()).achievementProgress().save();
        return com.mistaboom.essence_ascendance.data.NbtVersionCompat.getInt(com.mistaboom.essence_ascendance.data.NbtVersionCompat.getCompound(com.mistaboom.essence_ascendance.data.NbtVersionCompat.getCompound(root,"entries"),"a182"),"mask");
    }
    private static void mine(ServerLevel level,ServerPlayer p,BlockPos pos,net.minecraft.world.level.block.state.BlockState state) {
        advance(level,1);
        level.setBlock(pos,state,Block.UPDATE_ALL);check(p.gameMode.destroyBlock(pos),"Native destroyBlock succeeds");
    }
    private static void canceledRemoval(ServerPlayer player,BlockPos position) {
        HarvestRemovalCancellation.deny(player.serverLevel(),position);
        try {
            check(player.gameMode.destroyBlock(position),"Native destroyBlock preserves its true outer return after actual removal was denied");
            check(HarvestRemovalCancellation.intercepted()>0,"Disposable fixture really intercepted the native block mutation, after permission checks");
        } finally {HarvestRemovalCancellation.allow();}
    }
    private static void water(ServerLevel level,ServerPlayer p) {
        var feet=p.blockPosition();
        // NeoForge's native fluid scan deliberately skips a body whose one-block-expanded box touches
        // an unloaded chunk. The ore may be at a freshly generated chunk edge, so load the real neighbors.
        for(int x:new int[]{-2,2})for(int z:new int[]{-2,2})level.getChunkAt(feet.offset(x,0,z));
        for(int y=0;y<3;y++)level.setBlock(feet.above(y),Blocks.WATER.defaultBlockState(),Block.UPDATE_ALL);
        p.baseTick();p.baseTick();
        var eyes=BlockPos.containing(p.getX(),p.getEyeY(),p.getZ());
        check(p.isUnderWater(),"Native submerged fixture after real fluid scans: position="+p.position()+", eyes="+eyes
                +", feetFluid="+level.getFluidState(feet)+", eyeFluid="+level.getFluidState(eyes)
                +", inWater="+p.isInWater()+", eyeInWater="+p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER));
    }
    private static void enable(ServerPlayer p,ResourceLocation... skills) {
        var data=EssenceSavedData.get(p.server).getPlayerData(p.getUUID());data.setTier(AscendanceTiers.TRANSCENDENT);SkillRegistry.referencedPermanentMilestoneIds().forEach(data::completeMilestone);data.clearAllSkillsForAdmin();
        Set<ResourceLocation> required=new LinkedHashSet<>(Arrays.asList(skills));var queue=new ArrayDeque<ResourceLocation>(required);
        while(!queue.isEmpty()){var definition=SkillRegistry.require(queue.remove());for(var prerequisite:definition.prerequisiteRanks(definition.maximumRank()).keySet())if(required.add(prerequisite))queue.add(prerequisite);}
        var ordered=SkillRegistry.topologicalOrder(required);data.grantAllSkillsForAdmin(ordered);
        for(var definition:ordered){var id=definition.id();if(definition.choiceGroup()!=null)data.setLoadoutSelection(definition.choiceGroup(),id);else if(definition.activationPolicy()==SkillActivationPolicy.TOGGLE)data.setLoadoutSelection(id,id);}
        p.setGameMode(GameType.SURVIVAL);SkillEffectRuntime.reset(p);SkillEffectRuntime.refresh(p);
        for(var id:skills)check(SkillEffectRuntime.context(p).isEffective(id),"Fixture gathering skill effective "+id);
    }
    private static ServerPlayer player(ServerLevel level,String name) {
        var profile=new GameProfile(UUID.randomUUID(),name);var client=ClientInformation.createDefault();var p=new ServerPlayer(level.getServer(),level,profile,client);
        var transport=new Connection(PacketFlow.SERVERBOUND);new ServerGamePacketListenerImpl(level.getServer(),transport,p,new CommonListenerCookie(profile,0,client,false));
        new EmbeddedChannel(transport).close().syncUninterruptibly();return p;
    }
    @SuppressWarnings("unchecked") private static void online(ServerPlayer p,boolean add)throws Exception {
        var list=p.server.getPlayerList();var players=PlayerList.class.getDeclaredField("players");var ids=PlayerList.class.getDeclaredField("playersByUUID");players.setAccessible(true);ids.setAccessible(true);
        var online=(List<ServerPlayer>)players.get(list);var byId=(Map<UUID,ServerPlayer>)ids.get(list);
        if(add){online.add(p);byId.put(p.getUUID(),p);}else{online.remove(p);byId.remove(p.getUUID());}
    }
    private static void move(ServerPlayer p,ServerLevel level,double x,double y,double z) {
        level.getChunkAt(BlockPos.containing(x,y,z));
        //? if >=26.1.2
        /*p.teleportTo(level,x,y,z,Set.of(),0,0,false);*/
        //? if <26.1.2
        p.teleportTo(level,x,y,z,Set.of(),0,0);
        p.setDeltaMovement(Vec3.ZERO);
    }
    private static void advance(ServerLevel level,long ticks){level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+ticks);}
    private static void tileDrops(ServerLevel level,boolean enabled) {
        //? if >=26.1.2
        /*level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.BLOCK_DROPS,enabled,level.getServer());*/
        //? if <26.1.2
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOBLOCKDROPS).set(enabled,level.getServer());
    }
    private static net.minecraft.advancements.AdvancementHolder advancement(ServerPlayer p,int id){return p.server.getAdvancements().get(AchievementService.resource(id));}
    private static boolean done(ServerPlayer p,int id){return p.getAdvancements().getOrStartProgress(advancement(p,id)).isDone();}
    private static void revoke(ServerPlayer p,int id){p.getAdvancements().revoke(advancement(p,id),"achieved");}
    private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
}
