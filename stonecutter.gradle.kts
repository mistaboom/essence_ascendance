plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.17-SNAPSHOT" apply false
    id("dev.architectury.loom-no-remap") version "1.17-SNAPSHOT" apply false
    id("architectury-plugin") version "3.5-SNAPSHOT" apply false
    id("com.gradleup.shadow") version "9.4.3" apply false
}
stonecutter active "1.21.1" /* [SC] DO NOT EDIT */
// Keep the version axis in shared source; loader adapters stay in their own branches.
stonecutter parameters {
    constants { match(node.branch.id, "common", "fabric", "neoforge") }
    // Minecraft renamed this core value type in 1.21.11. Project the authored
    // 1.21.1 spelling into official 26.1.2 sources without duplicating code.
    replacements.regex(current.parsed >= "26.1.2") {
        replace("\\bResourceLocation\\b" to "Identifier", "\\bIdentifier\\b" to "ResourceLocation")
    }
    replacements.regex(current.parsed >= "26.1.2") {
        replace("\\.serverLevel\\(\\)" to ".level()", "(?!)" to ".serverLevel()")
    }
    replacements.regex(current.parsed >= "26.1.2") {
        replace("\\.isClientSide\\b(?!\\()" to ".isClientSide()", "(?!)" to ".isClientSide")
    }
    replacements.regex(current.parsed >= "26.1.2") {
        replace("\\b(player|serverPlayer|viewer|administrator|defender|owner|p)\\.server\\b" to "$1.level().getServer()",
                "(?!)" to "$1.server")
    }
    replacements.regex(current.parsed >= "26.1.2") {
        replace("(?<!NbtVersionCompat)\\.getCompound\\(" to ".getCompoundOrEmpty(", "(?!)" to ".getCompound(")
    }
    // Registry.get now returns an optional Holder; these callers need the value.
    replacements.regex(current.parsed >= "26.1.2") {
        replace("BuiltInRegistries\\.(ITEM|BLOCK|RECIPE_TYPE)\\.get\\(" to "BuiltInRegistries.$1.getValue(", "(?!)" to "NO_REVERSE")
    }
    replacements.regex(current.parsed >= "26.1.2") {
        replace("\\.dimension\\(\\)\\.location\\(\\)" to ".dimension().identifier()", "(?!)" to ".dimension().location()")
        replace("\\.key\\(\\)\\.location\\(\\)" to ".key().identifier()", "(?!)" to ".key().location()")
        replace("\\b(key|k|type|dimension|tab)\\.location\\(\\)" to "$1.identifier()", "(?!)" to "$1.location()")
    }
    replacements.regex(current.parsed >= "26.1.2") {
        replace("\\.getInt\\(([^(),]+)\\)" to ".getIntOr($1, 0)", "(?!)" to ".getInt($1)")
        replace("\\.getLong\\(([^(),]+)\\)" to ".getLongOr($1, 0L)", "(?!)" to ".getLong($1)")
        replace("\\.getDouble\\(([^(),]+)\\)" to ".getDoubleOr($1, 0.0)", "(?!)" to ".getDouble($1)")
        replace("\\.getFloat\\(([^(),]+)\\)" to ".getFloatOr($1, 0.0F)", "(?!)" to ".getFloat($1)")
        replace("\\.getString\\(([^(),]+)\\)" to ".getStringOr($1, \"\")", "(?!)" to ".getString($1)")
        // java.lang.Boolean.getBoolean is a JVM property read, not an NBT accessor.
        replace("(?<!Boolean)\\.getBoolean\\(([^(),]+)\\)" to ".getBooleanOr($1, false)", "(?!)" to ".getBoolean($1)")
    }
    replacements.regex(current.parsed >= "26.1.2") {
        val nbt = "com.mistaboom.essence_ascendance.data.NbtVersionCompat"
        replace("\\.getAllKeys\\(\\)" to ".keySet()", "(?!)" to ".getAllKeys()")
        replace("\\b((?!NbtVersionCompat\\b)[A-Za-z_][A-Za-z0-9_]*)\\.contains\\(([^,()]+),\\s*(Tag\\.TAG_[A-Z_]+|net\\.minecraft\\.nbt\\.Tag\\.TAG_[A-Z_]+)\\)" to
                "$nbt.contains($1, $2, $3)", "(?!)" to "NO_REVERSE")
        replace("\\b((?!NbtVersionCompat\\b)[A-Za-z_][A-Za-z0-9_]*)\\.getList\\(([^,()]+),\\s*(Tag\\.TAG_[A-Z_]+|net\\.minecraft\\.nbt\\.Tag\\.TAG_[A-Z_]+)\\)" to
                "$nbt.getList($1, $2, $3)", "(?!)" to "NO_REVERSE")
        replace("\\b((?!NbtVersionCompat\\b)[A-Za-z_][A-Za-z0-9_]*)\\.(putUUID|getUUID|hasUUID)\\(([^()]+)\\)" to
                "$nbt.$2($1, $3)", "(?!)" to "NO_REVERSE")
    }
    replacements.regex(current.parsed >= "26.1.2") {
        replace("\\bClickType\\b" to "ContainerInput", "\\bContainerInput\\b" to "ClickType")
        replace("\\.readResourceLocation\\(\\)" to ".readIdentifier()", "(?!)" to ".readResourceLocation()")
        replace("\\.writeResourceLocation\\(" to ".writeIdentifier(", "(?!)" to ".writeResourceLocation(")
        replace("\\.getGameProfile\\(\\)\\.getName\\(\\)" to ".nameAndId().name()", "(?!)" to ".getGameProfile().getName()")
        replace("\\blevel\\.random\\b" to "level.getRandom()", "(?!)" to "level.random")
    }
}
tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds every configured Stonecutter node."
    dependsOn(stonecutter.tasks.named("build").map { it.values })
}
apply(from = "build.gradle")
