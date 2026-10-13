package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.team.*;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import java.util.UUID;

import static com.mistaboom.essence_ascendance.command.EssenceMultiplayerCommandOutput.*;

/** Native membership command surface; external providers own their membership interface. */
final class EssenceTeamCommands {
    private EssenceTeamCommands() { }
    private interface Operation { Component run(NativeMembershipService service, UUID actor, CommandContext<CommandSourceStack> context); }
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        var root = Commands.literal("team").executes(EssenceTeamCommands::status);
        root.then(Commands.literal("interface").executes(c -> {
            com.mistaboom.essence_ascendance.network.TeamInterfaceNetworkService.interfaceGuidance(c.getSource().getPlayerOrException());
            return 1;
        }));
        root.then(Commands.literal("status").executes(EssenceTeamCommands::status));
        root.then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.string())
                .executes(c -> execute(c, (s, a, ctx) -> text("team.created", s.name(s.create(a, StringArgumentType.getString(ctx, "name"))))))));
        root.then(Commands.literal("request").then(Commands.argument("team", UuidArgument.uuid())
                .then(Commands.literal("share-stored").executes(c -> execute(c, (s, a, ctx) -> {
                    var r = s.request(a, new PoolId(MembershipAuthority.NATIVE, PoolId.Kind.TEAM, UuidArgument.getUuid(ctx, "team")), true);
                    showRequest(ctx.getSource(), a, r);
                    return text("team.request_sent");
                })))));
        root.then(Commands.literal("requests").executes(c -> execute(c, (s, a, ctx) -> {
            var requests = s.requests(a);
            EssenceCommandUtil.send(ctx.getSource(), EssenceCommandUtil.section(text("team.requests", requests.size())));
            if (requests.isEmpty()) EssenceCommandUtil.send(ctx.getSource(), EssenceCommandUtil.muted(text("team.no_requests")));
            requests.forEach(request -> showRequest(ctx.getSource(), a, request));
            return null;
        })));
        for (String action : new String[]{"cancel", "approve", "decline"}) {
            var request = Commands.argument("request", UuidArgument.uuid())
                    .executes(c -> execute(c, (s, a, ctx) -> {
                        UUID token = UuidArgument.getUuid(ctx, "request");
                        if (action.equals("cancel")) s.cancel(a, token); else s.decide(a, token, action.equals("approve"));
                        return text("team." + action);
                    }));
            if (action.equals("approve")) request.then(Commands.argument("name", StringArgumentType.string())
                    .executes(c -> execute(c, (s, a, ctx) -> {
                        s.decide(a, UuidArgument.getUuid(ctx, "request"), true, StringArgumentType.getString(ctx, "name"));
                        return text("team.approve");
                    })));
            root.then(Commands.literal(action).then(request));
        }
        root.then(Commands.literal("leave").executes(c -> execute(c, (s, a, ctx) -> { s.leave(a); return text("team.leave"); })));
        for (String action : new String[]{"remove", "transfer"})
            root.then(Commands.literal(action).then(Commands.argument("member", UuidArgument.uuid())
                    .executes(c -> execute(c, (s, a, ctx) -> {
                        UUID member = UuidArgument.getUuid(ctx, "member");
                        if (action.equals("remove")) s.remove(a, member); else s.transfer(a, member);
                        return text("team." + action, player(ctx.getSource(), member));
                    }))));
        root.then(Commands.literal("disband").executes(c -> execute(c, (s, a, ctx) -> { s.disband(a); return text("team.disband"); })));
        return root;
    }

    private static int execute(CommandContext<CommandSourceStack> context, Operation operation) throws CommandSyntaxException {
        var source = context.getSource(); UUID actor = source.getPlayerOrException().getUUID();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("team.title")));
        try {
            Component result = operation.run(EssenceSavedData.get(source.getServer()).nativeMembership(), actor, context);
            if (result != null) EssenceCommandUtil.send(source, EssenceCommandUtil.good(result));
            return 1;
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException failure) {
            EssenceCommandUtil.fail(source, failure.getMessage()); return 0;
        }
    }

    private static void showRequest(CommandSourceStack source, UUID actor, NativeMembershipService.Request request) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section(text("team.request")));
        line(source, "request_id", request.token().toString());
        line(source, "applicant", player(source, request.applicant()));
        line(source, "destination", pool(source, request.target()));
        EssenceCommandUtil.send(source, EssenceCommandUtil.warn(text("team.sharing")));
        if (request.applicant().equals(actor)) {
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence team cancel " + request.token(), text("team.cancel_help")));
        } else {
            String suffix = request.target().kind() == PoolId.Kind.SOLO ? " <name>" : "";
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence team approve " + request.token() + suffix,
                    text(suffix.isEmpty() ? "team.approve_help" : "team.approve_named_help")));
            EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence team decline " + request.token(), text("team.decline_help")));
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var source = context.getSource(); UUID actor = source.getPlayerOrException().getUUID();
        var saved = EssenceSavedData.get(source.getServer()); var resolved = saved.resolvePool(actor);
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(text("team.title")));
        line(source, "authority", saved.membershipAuthority().displayName());
        line(source, "status", readiness(resolved.readiness()));
        if (resolved.snapshot().isPresent()) {
            var snapshot = resolved.snapshot().orElseThrow();
            line(source, "pool", pool(source, snapshot.pool()));
            EssenceCommandUtil.send(source, EssenceCommandUtil.section(text("team.members", snapshot.members().size())));
            for (UUID member : snapshot.members()) {
                line(source, snapshot.allows(member, PoolCapability.MANAGE) ? "manager" : "member", player(source, member));
            }
            var permissions = Component.empty();
            for (PoolCapability capability : PoolCapability.values()) {
                if (!snapshot.allows(actor, capability)) continue;
                if (!permissions.getSiblings().isEmpty()) permissions.append(Component.literal(", "));
                permissions.append(text("capability." + capability.name().toLowerCase(java.util.Locale.ROOT)));
            }
            line(source, "your_access", permissions.getSiblings().isEmpty() ? text("none") : permissions);
            EssenceCommandUtil.send(source, EssenceCommandUtil.section(text("team.stored")));
            var amounts = saved.reservoirs().read(actor);
            for (var essence : EssenceRegistry.values()) {
                EssenceCommandUtil.send(source, EssenceCommandUtil.line(EssenceText.essence(essence), amount(amounts.units(essence.id()))));
            }
        } else line(source, "details", EssenceCommandUtil.warn(resolved.readiness().detail()));
        if (saved.membershipAuthority() != MembershipAuthority.NATIVE)
            EssenceCommandUtil.send(source, EssenceCommandUtil.muted(text("team.provider_guidance", saved.membershipAuthority().displayName())));
        else EssenceCommandUtil.send(source, EssenceCommandUtil.command("/essence team interface", text("team.interface_help")));
        return 1;
    }
}
