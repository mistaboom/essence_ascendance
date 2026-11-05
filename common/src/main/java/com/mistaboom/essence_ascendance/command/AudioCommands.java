package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.audio.AudioCatalog;
import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.network.FeedbackDispatch;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Shared audio command identifiers and presentation; no client classes load on a dedicated server. */
public final class AudioCommands {
    private static final DynamicCommandExceptionType UNKNOWN_CUE = new DynamicCommandExceptionType(
            value -> EssenceCommandUtil.bad(EssenceText.command("audio.error.unknown_cue", value)));
    private static final DynamicCommandExceptionType UNKNOWN_PROFILE = new DynamicCommandExceptionType(
            value -> EssenceCommandUtil.bad(EssenceText.command("audio.error.unknown_profile", value)));
    private static final DynamicCommandExceptionType CONTEXT_CUE = new DynamicCommandExceptionType(
            value -> EssenceCommandUtil.bad(EssenceText.command("audio.error.context_cue", value)));
    private static final DynamicCommandExceptionType LOCAL_CUE = new DynamicCommandExceptionType(
            value -> EssenceCommandUtil.bad(EssenceText.command("audio.error.local_cue", value)));

    private AudioCommands() { }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("audio")
                .executes(context -> serverHelp(context.getSource()))
                .then(Commands.literal("help").executes(context -> serverHelp(context.getSource())))
                .then(Commands.literal("play")
                        .then(Commands.argument("semantic", StringArgumentType.string())
                                .suggests((context, builder) -> suggestSemantics(builder, false))
                                .executes(context -> play(context.getSource(), StringArgumentType.getString(context, "semantic")))));
    }

    private static int serverHelp(CommandSourceStack source) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("audio.server.title")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence debug audio play <semantic>", EssenceText.command("audio.server.play")));
        EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence audio", EssenceText.command("audio.help.client_only")));
        return 1;
    }

    private static int play(CommandSourceStack source, String input) throws CommandSyntaxException {
        String semantic = semantic(input);
        var cue = AudioCatalog.get().recipe(semantic);
        if (cue.audience().scope() == AudioCatalog.Scope.UI) throw LOCAL_CUE.create(input);
        var player = source.getPlayerOrException();
        if (cue.audience().scope() == AudioCatalog.Scope.WORLD)
            FeedbackDispatch.world(player.serverLevel(), semantic, player.position());
        else FeedbackDispatch.player(player, semantic);
        EssenceCommandUtil.send(source, response("requested", semantic));
        return 1;
    }

    public static int localHelp(Consumer<Component> send) {
        send.accept(EssenceCommandUtil.title(EssenceText.command("audio.help.title")));
        send.accept(EssenceCommandUtil.command("/essence audio audition <semantic>", EssenceText.command("audio.help.audition")));
        send.accept(EssenceCommandUtil.command("/essence audio start <profile>", EssenceText.command("audio.help.start")));
        send.accept(EssenceCommandUtil.command("/essence audio stop", EssenceText.command("audio.help.stop")));
        send.accept(EssenceCommandUtil.muted(EssenceText.command("audio.help.client_only")));
        send.accept(EssenceCommandUtil.muted(EssenceText.command("help.tab_completion")));
        return 1;
    }

    public static Component response(String key, Object... arguments) {
        return EssenceCommandUtil.good(EssenceText.command("audio." + key, arguments));
    }

    /** The same namespace/default-ID rules as the established /essence registry arguments. */
    public static String semantic(String input) throws CommandSyntaxException {
        var resolved = EssenceCommandUtil.resolveResourceId(input);
        String id = resolved.toString();
        var catalog = AudioCatalog.get();
        if (catalog.recipe(id) == null) {
            String shortId = resolved.getNamespace().equals(EssenceAscendance.MOD_ID) ? resolved.getPath() : id;
            if (catalog.recipeIds().contains(id) || catalog.recipeIds().contains(shortId)) throw CONTEXT_CUE.create(input);
            throw UNKNOWN_CUE.create(input);
        }
        return id;
    }

    public static String profile(String input) throws CommandSyntaxException {
        var id = EssenceCommandUtil.resolveResourceId(input);
        String key = id.getNamespace().equals(EssenceAscendance.MOD_ID) ? id.getPath() : id.toString();
        if (AudioCatalog.get().profile(key) == null) throw UNKNOWN_PROFILE.create(input);
        return key;
    }

    public static CompletableFuture<Suggestions> suggestSemantics(SuggestionsBuilder builder) {
        return suggestSemantics(builder, true);
    }

    private static CompletableFuture<Suggestions> suggestSemantics(SuggestionsBuilder builder, boolean local) {
        var catalog = AudioCatalog.get();
        return EssenceCommandUtil.suggestRegistryIds(builder, catalog.recipeIds().stream()
                .filter(id -> { var cue = catalog.recipe(id); return cue != null && (local || cue.audience().scope() != AudioCatalog.Scope.UI); })
                .map(AudioCommands::catalogId).toList());
    }

    public static CompletableFuture<Suggestions> suggestProfiles(SuggestionsBuilder builder) {
        return EssenceCommandUtil.suggestRegistryIds(builder, AudioCatalog.get().profileIds().stream()
                .map(AudioCommands::catalogId).toList());
    }

    private static ResourceLocation catalogId(String id) {
        return EssenceCommandUtil.parseId(id);
    }
}
