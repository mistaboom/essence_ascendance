package com.mistaboom.essence_ascendance.mixin;

import com.mistaboom.essence_ascendance.client.ore.LatentOreSprites;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(SpriteLoader.class)
abstract class LatentOreSpriteLoaderMixin {
    @Shadow @Final private ResourceLocation location;
    @Unique private ResourceManager essence$resources;

    @Inject(method = "loadAndStitch(Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/resources/ResourceLocation;ILjava/util/concurrent/Executor;Ljava/util/Collection;)Ljava/util/concurrent/CompletableFuture;", at = @At("HEAD"))
    private void essence$captureResources(ResourceManager resources, ResourceLocation atlas, int mip, Executor executor,
            Collection<MetadataSectionSerializer<?>> sections, CallbackInfoReturnable<CompletableFuture<SpriteLoader.Preparations>> cir) {
        essence$resources = resources;
    }

    @ModifyVariable(method = "stitch", at = @At("HEAD"), argsOnly = true)
    private List<SpriteContents> essence$compositeBeforeStitch(List<SpriteContents> sprites) {
        return TextureAtlas.LOCATION_BLOCKS.equals(location) && essence$resources != null
                ? LatentOreSprites.prepare(essence$resources, sprites) : sprites;
    }
}
