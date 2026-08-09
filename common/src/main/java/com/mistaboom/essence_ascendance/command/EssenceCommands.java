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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

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
                         */
                        .then(
                                Commands.literal("invest")
                                        .then(
                                                Commands.argument(
                                                                "stat",
                                                                StringArgumentType.word()
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


    private static String format(long value) {
        return String.format("%,d", value);
    }
}