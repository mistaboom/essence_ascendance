package com.mistaboom.essence_ascendance.client.audio;

import com.mistaboom.essence_ascendance.command.AudioCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent.ClientCommandSourceStack;
import net.minecraft.network.chat.Component;

import static dev.architectury.event.events.client.ClientCommandRegistrationEvent.*;

/** Device-local previews under the ordinary /essence command domain. */
public final class AudioDevelopment {
    private AudioDevelopment() { }

    static void init() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, registry) -> register(dispatcher));
    }

    public static void register(CommandDispatcher<ClientCommandSourceStack> dispatcher) {
        // The exact audio literal owns local actions. Every other branch must produce UnknownCommand:
        // Fabric consumes UnknownArgument after a matched root, while both loaders forward UnknownCommand.
        dispatcher.register(literal("essence").then(audio())
                .then(argument("server_command", StringArgumentType.greedyString()).executes(context -> {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
                })));
    }

    /** Merge the fully built local subtree into completion only; loaders still own command execution.
     * Fabric's nested merge can otherwise populate a detached copy when the server already owns essence. */
    @SuppressWarnings("unchecked")
    public static void installCompletions(CommandDispatcher<?> dispatcher) {
        ((CommandDispatcher<ClientCommandSourceStack>) dispatcher).register(literal("essence").then(audio()));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<ClientCommandSourceStack> audio() {
        return literal("audio")
                .executes(context -> AudioCommands.localHelp(message -> send(context.getSource(), message)))
                .then(literal("help").executes(context -> AudioCommands.localHelp(message -> send(context.getSource(), message))))
                .then(literal("audition").then(argument("semantic",StringArgumentType.string())
                        .suggests((context, builder) -> AudioCommands.suggestSemantics(builder))
                        .executes(context -> {
                            String semantic = AudioCommands.semantic(StringArgumentType.getString(context, "semantic"));
                            AudioRuntime.audition(semantic);
                            send(context.getSource(), AudioCommands.response("requested", semantic));
                            return 1;
                        })))
                .then(literal("start").then(argument("profile",StringArgumentType.string())
                        .suggests((context, builder) -> AudioCommands.suggestProfiles(builder))
                        .executes(context -> {
                            String profile = AudioCommands.profile(StringArgumentType.getString(context, "profile"));
                            AudioRuntime.auditionStart(profile);
                            send(context.getSource(), AudioCommands.response("started", profile));
                            return 1;
                        })))
                .then(literal("stop").executes(context -> {
                    AudioRuntime.auditionStop();
                    send(context.getSource(), AudioCommands.response("stopped"));
                    return 1;
                }));
    }

    private static void send(ClientCommandSourceStack source, Component message) {
        source.arch$sendSuccess(() -> message, false);
    }
}
