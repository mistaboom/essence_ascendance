package com.mistaboom.essence_ascendance.neoforge.mixin;

import com.google.gson.JsonElement;
import com.mistaboom.essence_ascendance.balance.generated.StableKubeRecipeIds;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Optional exact-version repair of KubeJS's insertion-order-sensitive fallback auto IDs. */
@Pseudo
@Mixin(targets = "dev.latvian.mods.kubejs.recipe.KubeRecipe", remap = false)
public abstract class KubeRecipeGeneratedIdMixin {
    @ModifyArg(method = "getOrCreateId()Lnet/minecraft/resources/ResourceLocation;",
            at = @At(value = "INVOKE", target = "Ldev/latvian/mods/kubejs/plugin/builtin/wrapper/StringUtilsWrapper;getUniqueId(Lcom/google/gson/JsonElement;)Ljava/lang/String;", remap = false),
            index = 0, require = 0, remap = false)
    private JsonElement essenceAscendance$stableFallbackInput(JsonElement json) {
        return StableKubeRecipeIds.fallbackInput(this, json, essenceAscendance$modVersion("kubejs"),
                essenceAscendance$modVersion("modern_industrialization"));
    }

    @Unique
    private static String essenceAscendance$modVersion(String id) {
        return ModList.get().getModContainerById(id).map(container -> container.getModInfo().getVersion().toString()).orElse("");
    }
}
