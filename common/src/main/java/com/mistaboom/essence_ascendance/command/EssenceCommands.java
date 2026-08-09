package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.data.PlayerEssenceData;
import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.stat.StatDefinition;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

public final class EssenceCommands {

    private static final DynamicCommandExceptionType UNKNOWN_ESSENCE =
            new DynamicCommandExceptionType(
                    value -> Component.literal(
                            "Unknown Essence type: " + value
                    )
            );

    private static final DynamicCommandExceptionType UNKNOWN_STAT =
            new DynamicCommandExceptionType(
                    value -> Component.literal(
                            "Unknown stat: " + value
                    )
            );

    private EssenceCommands() {
    }


    /*
     * ============================================================
     * COMMAND REGISTRATION
     * ============================================================
     */

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher
    ) {
        dispatcher.register(
                Commands.literal("essence")

                        /*
                         * /essence balance
                         * /essence balance <essence>
                         */
                        .then(
                                Commands.literal("balance")
                                        .executes(context ->
                                                showAllBalances(
                                                        context.getSource()
                                                )
                                        )
                                        .then(
                                                Commands.argument(
                                                                "essence",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(
                                                                EssenceCommands::suggestEssences
                                                        )
                                                        .executes(context ->
                                                                showBalance(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "essence"
                                                                        )
                                                                )
                                                        )
                                        )
                        )

                        /*
                         * /essence give <essence> <amount>
                         *
                         * Admin/testing command.
                         */
                        .then(
                                Commands.literal("give")
                                        .requires(source ->
                                                source.hasPermission(2)
                                        )
                                        .then(
                                                Commands.argument(
                                                                "essence",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(
                                                                EssenceCommands::suggestEssences
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "amount",
                                                                                LongArgumentType.longArg(1)
                                                                        )
                                                                        .executes(context ->
                                                                                giveEssence(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "essence"
                                                                                        ),
                                                                                        LongArgumentType.getLong(
                                                                                                context,
                                                                                                "amount"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        /*
                         * /essence invest <stat> <amount>
                         *
                         * Normal progression operation.
                         * Removes available Essence and invests it into a stat.
                         */
                        .then(
                                Commands.literal("invest")
                                        .then(
                                                Commands.argument(
                                                                "stat",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(
                                                                EssenceCommands::suggestStats
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "amount",
                                                                                LongArgumentType.longArg(1)
                                                                        )
                                                                        .executes(context ->
                                                                                invest(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "stat"
                                                                                        ),
                                                                                        LongArgumentType.getLong(
                                                                                                context,
                                                                                                "amount"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        /*
                         * /essence get <stat>
                         */
                        .then(
                                Commands.literal("get")
                                        .then(
                                                Commands.argument(
                                                                "stat",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(
                                                                EssenceCommands::suggestStats
                                                        )
                                                        .executes(context ->
                                                                showStat(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(
                                                                                context,
                                                                                "stat"
                                                                        )
                                                                )
                                                        )
                                        )
                        )

                        /*
                         * /essence set <essence> <amount>
                         *
                         * Admin/testing command.
                         * Directly sets an available Essence balance.
                         */
                        .then(
                                Commands.literal("set")
                                        .requires(source ->
                                                source.hasPermission(2)
                                        )
                                        .then(
                                                Commands.argument(
                                                                "essence",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(
                                                                EssenceCommands::suggestEssences
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "amount",
                                                                                LongArgumentType.longArg(0)
                                                                        )
                                                                        .executes(context ->
                                                                                setEssence(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "essence"
                                                                                        ),
                                                                                        LongArgumentType.getLong(
                                                                                                context,
                                                                                                "amount"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        /*
                         * /essence setstat <stat> <amount>
                         *
                         * Admin/testing command.
                         * Directly sets invested Essence without consuming
                         * an available Essence balance.
                         */
                        .then(
                                Commands.literal("setstat")
                                        .requires(source ->
                                                source.hasPermission(2)
                                        )
                                        .then(
                                                Commands.argument(
                                                                "stat",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(
                                                                EssenceCommands::suggestStats
                                                        )
                                                        .then(
                                                                Commands.argument(
                                                                                "amount",
                                                                                LongArgumentType.longArg(0)
                                                                        )
                                                                        .executes(context ->
                                                                                setStat(
                                                                                        context.getSource(),
                                                                                        StringArgumentType.getString(
                                                                                                context,
                                                                                                "stat"
                                                                                        ),
                                                                                        LongArgumentType.getLong(
                                                                                                context,
                                                                                                "amount"
                                                                                        )
                                                                                )
                                                                        )
                                                        )
                                        )
                        )

                        /*
                         * /essence stats
                         *
                         * Lists every registered stat and its invested Essence.
                         */
                        .then(
                                Commands.literal("stats")
                                        .executes(context ->
                                                showAllStats(
                                                        context.getSource()
                                                )
                                        )
                        )

                        /*
                         * /essence reset
                         *
                         * Admin/testing command.
                         * Clears all available and invested Essence
                         * for the executing player.
                         */
                        .then(
                                Commands.literal("reset")
                                        .requires(source ->
                                                source.hasPermission(2)
                                        )
                                        .executes(context ->
                                                resetAll(
                                                        context.getSource()
                                                )
                                        )
                        )
        );
    }


    /*
     * ============================================================
     * BALANCES
     * ============================================================
     */

    private static int showAllBalances(
            CommandSourceStack source
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        EssenceSavedData savedData =
                EssenceSavedData.get(
                        source.getServer()
                );

        PlayerEssenceData playerData =
                savedData.getPlayerData(
                        player.getUUID()
                );

        source.sendSuccess(
                () -> Component.literal(
                        "Essence balances:"
                ),
                false
        );

        for (EssenceDefinition essence :
                EssenceRegistry.values()) {

            long balance =
                    playerData.getAvailable(essence);

            source.sendSuccess(
                    () -> Component.literal(
                            "  "
                                    + essence.displayName()
                                    + ": "
                                    + format(balance)
                    ),
                    false
            );
        }

        return 1;
    }


    private static int showBalance(
            CommandSourceStack source,
            String essenceName
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        EssenceDefinition essence =
                resolveEssence(essenceName);

        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(source.getServer())
                        .getPlayerData(player.getUUID());

        long balance =
                playerData.getAvailable(essence);

        source.sendSuccess(
                () -> Component.literal(
                        essence.displayName()
                                + ": "
                                + format(balance)
                ),
                false
        );

        return 1;
    }


    /*
     * ============================================================
     * GIVE
     * ============================================================
     */

    private static int giveEssence(
            CommandSourceStack source,
            String essenceName,
            long amount
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        EssenceDefinition essence =
                resolveEssence(essenceName);

        long updated =
                EssenceSavedData
                        .get(source.getServer())
                        .addEssence(
                                player.getUUID(),
                                essence,
                                amount
                        );

        source.sendSuccess(
                () -> Component.literal(
                        "Added "
                                + format(amount)
                                + " "
                                + essence.displayName()
                                + ". New balance: "
                                + format(updated)
                ),
                false
        );

        return 1;
    }


    /*
     * ============================================================
     * SET AVAILABLE ESSENCE
     * ============================================================
     */

    private static int setEssence(
            CommandSourceStack source,
            String essenceName,
            long amount
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        EssenceDefinition essence =
                resolveEssence(essenceName);

        EssenceSavedData
                .get(source.getServer())
                .setEssence(
                        player.getUUID(),
                        essence,
                        amount
                );

        source.sendSuccess(
                () -> Component.literal(
                        "Set "
                                + essence.displayName()
                                + " to "
                                + format(amount)
                ),
                false
        );

        return 1;
    }


    /*
     * ============================================================
     * INVEST
     * ============================================================
     */

    private static int invest(
            CommandSourceStack source,
            String statName,
            long amount
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        StatDefinition stat =
                resolveStat(statName);

        EssenceSavedData savedData =
                EssenceSavedData.get(
                        source.getServer()
                );

        PlayerEssenceData playerData =
                savedData.getPlayerData(
                        player.getUUID()
                );

        EssenceDefinition requiredEssence =
                stat.essenceType();

        long available =
                playerData.getAvailable(
                        requiredEssence
                );

        if (available < amount) {
            source.sendFailure(
                    Component.literal(
                            "Not enough "
                                    + requiredEssence.displayName()
                                    + ". Required: "
                                    + format(amount)
                                    + ", available: "
                                    + format(available)
                    )
            );

            return 0;
        }

        boolean success =
                savedData.invest(
                        player.getUUID(),
                        stat,
                        amount
                );

        if (!success) {
            source.sendFailure(
                    Component.literal(
                            "Unable to invest Essence."
                    )
            );

            return 0;
        }

        long invested =
                playerData.getInvested(stat);

        long remaining =
                playerData.getAvailable(
                        requiredEssence
                );

        source.sendSuccess(
                () -> Component.literal(
                        "Invested "
                                + format(amount)
                                + " "
                                + requiredEssence.displayName()
                                + " into "
                                + stat.displayName()
                                + ". Total invested: "
                                + format(invested)
                                + ". Remaining balance: "
                                + format(remaining)
                ),
                false
        );

        return 1;
    }


    /*
     * ============================================================
     * SET INVESTED ESSENCE
     * ============================================================
     */

    private static int setStat(
            CommandSourceStack source,
            String statName,
            long amount
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        StatDefinition stat =
                resolveStat(statName);

        EssenceSavedData
                .get(source.getServer())
                .setInvested(
                        player.getUUID(),
                        stat,
                        amount
                );

        source.sendSuccess(
                () -> Component.literal(
                        "Set "
                                + stat.displayName()
                                + " invested Essence to "
                                + format(amount)
                ),
                false
        );

        return 1;
    }


    /*
     * ============================================================
     * STAT INFO
     * ============================================================
     */

    private static int showStat(
            CommandSourceStack source,
            String statName
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        StatDefinition stat =
                resolveStat(statName);

        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(source.getServer())
                        .getPlayerData(player.getUUID());

        long invested =
                playerData.getInvested(stat);

        source.sendSuccess(
                () -> Component.literal(
                        stat.displayName()
                                + ": "
                                + format(invested)
                                + " invested "
                                + stat.essenceType().displayName()
                ),
                false
        );

        return 1;
    }


    private static int showAllStats(
            CommandSourceStack source
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        PlayerEssenceData playerData =
                EssenceSavedData
                        .get(source.getServer())
                        .getPlayerData(player.getUUID());

        source.sendSuccess(
                () -> Component.literal(
                        "Essence Ascendance stats:"
                ),
                false
        );

        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            long invested =
                    playerData.getInvested(stat);

            source.sendSuccess(
                    () -> Component.literal(
                            "  "
                                    + stat.displayName()
                                    + ": "
                                    + format(invested)
                    ),
                    false
            );
        }

        return 1;
    }


    /*
     * ============================================================
     * RESET
     * ============================================================
     */

    private static int resetAll(
            CommandSourceStack source
    ) throws CommandSyntaxException {

        ServerPlayer player =
                source.getPlayerOrException();

        EssenceSavedData
                .get(source.getServer())
                .clearAll(
                        player.getUUID()
                );

        source.sendSuccess(
                () -> Component.literal(
                        "Reset all Essence balances and stat investments."
                ),
                false
        );

        return 1;
    }


    /*
     * ============================================================
     * LOOKUP
     * ============================================================
     */

    private static EssenceDefinition resolveEssence(
            String input
    ) throws CommandSyntaxException {

        ResourceLocation id =
                parseId(input);

        return EssenceRegistry
                .get(id)
                .orElseThrow(
                        () -> UNKNOWN_ESSENCE.create(input)
                );
    }


    private static StatDefinition resolveStat(
            String input
    ) throws CommandSyntaxException {

        ResourceLocation id =
                parseId(input);

        return EssenceStatRegistry
                .get(id)
                .orElseThrow(
                        () -> UNKNOWN_STAT.create(input)
                );
    }


    private static ResourceLocation parseId(
            String input
    ) throws CommandSyntaxException {

        String fullId =
                input.contains(":")
                        ? input
                        : EssenceAscendance.MOD_ID
                        + ":"
                        + input;

        ResourceLocation id =
                ResourceLocation.tryParse(fullId);

        if (id == null) {
            throw UNKNOWN_STAT.create(input);
        }

        return id;
    }


    /*
     * ============================================================
     * COMMAND SUGGESTIONS
     * ============================================================
     */

    private static CompletableFuture<Suggestions> suggestEssences(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining =
                builder.getRemainingLowerCase();

        for (EssenceDefinition essence :
                EssenceRegistry.values()) {

            String name =
                    essence.id().getPath();

            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }

        return builder.buildFuture();
    }


    private static CompletableFuture<Suggestions> suggestStats(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        String remaining =
                builder.getRemainingLowerCase();

        for (StatDefinition stat :
                EssenceStatRegistry.values()) {

            String name =
                    stat.id().getPath();

            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }

        return builder.buildFuture();
    }


    /*
     * ============================================================
     * FORMATTING
     * ============================================================
     */

    private static String format(long value) {
        return String.format("%,d", value);
    }
}