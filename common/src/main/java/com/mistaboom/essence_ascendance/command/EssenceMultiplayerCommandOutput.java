package com.mistaboom.essence_ascendance.command;

import com.mistaboom.essence_ascendance.balance.engine.ProviderReadiness;
import com.mistaboom.essence_ascendance.data.EssenceSavedData;
import com.mistaboom.essence_ascendance.machine.MachineOwnerDisplay;
import com.mistaboom.essence_ascendance.team.PoolId;
import com.mistaboom.essence_ascendance.text.EssenceText;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

/** Shared presentation only; identity and authorization remain in the membership services. */
final class EssenceMultiplayerCommandOutput {
    private EssenceMultiplayerCommandOutput() { }

    static MutableComponent text(String key, Object... args) {
        return EssenceText.command("multiplayer." + key, args);
    }

    static void line(CommandSourceStack source, String key, Component value) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(text(key), value));
    }

    static void line(CommandSourceStack source, String key, String value) {
        line(source, key, EssenceCommandUtil.value(value));
    }

    static Component player(CommandSourceStack source, UUID id) {
        if (id == null) return EssenceCommandUtil.muted(text("none"));
        String name = MachineOwnerDisplay.playerName(source.getServer(), id);
        if (name.equals("Unknown player")) name = id.toString();
        return EssenceCommandUtil.value(name).withStyle(style -> style.withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT, Component.literal(id.toString()))));
    }

    static Component pool(CommandSourceStack source, PoolId pool) {
        if (pool == null) return EssenceCommandUtil.warn(text("unbound"));
        Component label = pool.kind() == PoolId.Kind.TEAM
                ? Component.literal(MachineOwnerDisplay.teamName(source.getServer(), pool))
                : text("personal_pool", player(source, EssenceSavedData.get(source.getServer()).personalOwner(pool)));
        return EssenceCommandUtil.value(label).withStyle(style -> style.withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT, Component.literal(pool.authority().displayName() + "\n" + pool.id()))));
    }

    static Component readiness(ProviderReadiness readiness) {
        Component label = text("readiness." + readiness.status().name().toLowerCase(Locale.ROOT));
        return switch (readiness.status()) {
            case AVAILABLE -> EssenceCommandUtil.good(label);
            case NOT_READY, PARTIALLY_SUPPORTED -> EssenceCommandUtil.warn(label);
            case ABSENT, DISABLED -> EssenceCommandUtil.muted(label);
            default -> EssenceCommandUtil.bad(label);
        };
    }

    /** Preserve fractional shares and aggregates larger than long/double can represent. */
    static String amount(BigInteger units) {
        DecimalFormat format = new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(new BigDecimal(units, 6));
    }
}
