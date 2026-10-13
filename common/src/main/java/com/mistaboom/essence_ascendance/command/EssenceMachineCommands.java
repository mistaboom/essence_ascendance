package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.crucible.EssenceCrucibleBlockEntity;
import com.mistaboom.essence_ascendance.infuser.EssenceInfuserBlockEntity;
import com.mistaboom.essence_ascendance.machine.*;
import com.mistaboom.essence_ascendance.network.ServerMenuAccess;
import com.mistaboom.essence_ascendance.pylon.EssencePylonBlockEntity;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static com.mistaboom.essence_ascendance.command.EssenceMultiplayerCommandOutput.*;

/** Small authoritative control surface for minimal development sessions; stage 8 can call the same operations. */
final class EssenceMachineCommands {
    private EssenceMachineCommands() { }
    @FunctionalInterface private interface Operation {
        boolean run(ServerPlayer player, BoundMachine machine, CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        var position = Commands.argument("machine", BlockPosArgument.blockPos());
        for (boolean enabled : new boolean[]{true, false}) position.then(Commands.literal(enabled ? "start" : "stop")
                .executes(c -> execute(c, (p, m, ctx) -> {
                    if (m instanceof EssenceCrucibleBlockEntity crucible) return crucible.setProcessingEnabled(p, enabled);
                    if (m instanceof EssenceInfuserBlockEntity infuser) {
                        if (!infuser.canPlayerConfigure(p)) return false;
                        infuser.setProcessingEnabled(p, enabled); return infuser.processingEnabled() == enabled;
                    }
                    return false;
                })));
        var link = Commands.literal("link");
        for (MachineLink.Mode mode : new MachineLink.Mode[]{MachineLink.Mode.AUTOMATIC, MachineLink.Mode.DISCONNECTED})
            link.then(Commands.literal(mode.name().toLowerCase(java.util.Locale.ROOT)).executes(c -> execute(c, (p, m, ctx) -> setLink(p, m, mode, null))));
        link.then(Commands.literal("locked").then(Commands.argument("source", BlockPosArgument.blockPos())
                .executes(c -> execute(c, (p, m, ctx) -> setLink(p, m, MachineLink.Mode.LOCKED,
                        new MachineLink.Source(p.serverLevel().dimension().location(), BlockPosArgument.getBlockPos(ctx, "source")))))));
        position.then(link);
        var recovery = Commands.literal("recover-item");
        for (int slot : new int[]{EssenceInfuserBlockEntity.INPUT_SLOT, EssenceInfuserBlockEntity.OUTPUT_SLOT})
            recovery.then(Commands.literal(slot == EssenceInfuserBlockEntity.INPUT_SLOT ? "input" : "output")
                    .executes(c -> execute(c, (p, m, ctx) -> m instanceof EssenceInfuserBlockEntity infuser && infuser.recoverWorkpiece(p, slot))));
        position.then(recovery);
        position.then(Commands.literal("recover").requires(s -> s.hasPermission(2))
                .then(Commands.argument("recipient", UuidArgument.uuid()).executes(c -> execute(c,
                        (p, m, ctx) -> m.recover(p, UuidArgument.getUuid(ctx, "recipient"))))));
        position.then(Commands.literal("status").executes(c -> execute(c, (p, m, ctx) -> {
            if (!m.authorize(p, MachineAction.USE) && !p.hasPermissions(2)) return false;
            var source = ctx.getSource();
            var b = m.machineBinding();
            line(source, "pool", pool(source, b.pool()));
            if (b.pool() != null) line(source, "authority", b.pool().authority().displayName());
            line(source, "machine.placer", player(source, b.placer()));
            line(source, "machine.personal_owner", player(source, b.personalOwner()));
            line(source, "machine.operator", player(source, b.operator()));
            line(source, "machine.recipient", player(source, b.recipient()));
            line(source, "machine.automation_recipient", player(source, b.automationRecipient()));
            line(source, "machine.paused", b.paused() ? EssenceCommandUtil.warn(text("yes")) : EssenceCommandUtil.good(text("no")));
            if (m instanceof EssenceInfuserBlockEntity infuser) showLink(source, infuser.linkMode(), infuser.linkState(), infuser.linkedSource());
            if (m instanceof EssencePylonBlockEntity pylon) showLink(source, pylon.linkMode(), pylon.linkState(), pylon.linkedSource());
            if (m instanceof EssenceCrucibleBlockEntity crucible) {
                line(source, "machine.stored", amount(crucible.storedUnitsTotal()));
                line(source, "machine.fill_limit", String.format(java.util.Locale.ROOT, "%,d", crucible.localFillLimit()));
            }
            return true;
        })));
        return Commands.literal("machine").then(position);
    }
    private static void showLink(CommandSourceStack source, MachineLink.Mode mode, MachineLink.State state, MachineLink.Source linked) {
        line(source, "machine.link_mode", text("machine.link_mode." + mode.name().toLowerCase(java.util.Locale.ROOT)));
        line(source, "machine.link_status", text("machine.link_state." + state.name().toLowerCase(java.util.Locale.ROOT)));
        line(source, "machine.link_source", linked == null ? text("none")
                : Component.literal(linked.dimension() + " [" + linked.pos().getX() + ", " + linked.pos().getY() + ", " + linked.pos().getZ() + "]"));
    }
    private static boolean setLink(ServerPlayer player, BoundMachine machine, MachineLink.Mode mode, MachineLink.Source source) {
        if (machine instanceof EssenceInfuserBlockEntity infuser) return infuser.setLink(player, mode, source);
        if (machine instanceof EssencePylonBlockEntity pylon) return pylon.setLink(player, mode, source);
        return false;
    }
    private static int execute(CommandContext<CommandSourceStack> context, Operation operation) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        var pos = BlockPosArgument.getBlockPos(context, "machine");
        EssenceCommandUtil.send(context.getSource(), EssenceCommandUtil.title(text("machine.title")));
        line(context.getSource(), "machine.position", pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        if (!player.serverLevel().hasChunkAt(pos) || !(player.serverLevel().getBlockEntity(pos) instanceof BoundMachine machine)
                || !ServerMenuAccess.canReach(player, (net.minecraft.world.level.block.entity.BlockEntity) machine)) {
            EssenceCommandUtil.fail(context.getSource(), text("machine.unreachable")); return 0;
        }
        try {
            if (operation.run(player, machine, context)) {
                if (!context.getNodes().getLast().getNode().getName().equals("status"))
                    EssenceCommandUtil.send(context.getSource(), EssenceCommandUtil.good(text("machine.updated")));
                return 1;
            }
            EssenceCommandUtil.fail(context.getSource(), text("machine.denied")); return 0;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException failure) {
            EssenceCommandUtil.fail(context.getSource(), failure.getMessage()); return 0;
        }
    }
}
