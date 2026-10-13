package com.mistaboom.essence_ascendance.network;

import com.mistaboom.essence_ascendance.EssenceAscendance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Bounded presentation, with an opaque execution receipt. No inventories or hidden financial shares. */
public record TeamInterfacePayload(int menuId, UUID receipt, int view, int page, int pages,
                                   List<Text> lines, List<Control> controls, int selection, long selectionRevision) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 6, MAX_LINES = 48, MAX_TEXT = 192;
    public record Text(String key, List<String> arguments) {
        public Text { arguments = List.copyOf(arguments);
            if (key.length() > MAX_TEXT || arguments.size() > 4 || arguments.stream().anyMatch(s -> s.length() > MAX_TEXT))
                throw new IllegalArgumentException("Interface text exceeds bounds"); }
        public static Text of(String key, String... arguments) { return new Text("gui.essence_ascendance.team." + key, List.of(arguments)); }
    }
    public record Control(Text label, Text warning, boolean confirmation, boolean input) {}
    public TeamInterfacePayload {
        lines = List.copyOf(lines); controls = List.copyOf(controls);
        if (lines.size() > MAX_LINES || controls.size() > PAGE_SIZE || view < 0 || view > 3 || pages < 1 || page < 0 || page >= pages
                || !com.mistaboom.essence_ascendance.crucible.CrucibleChannelSelection.valid(selection) || selectionRevision < 0)
            throw new IllegalArgumentException("Invalid interface bounds");
    }
    public static final Type<TeamInterfacePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(EssenceAscendance.MOD_ID, "team_interface"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamInterfacePayload> CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeVarInt(p.menuId); b.writeUUID(p.receipt); b.writeVarInt(p.view); b.writeVarInt(p.page); b.writeVarInt(p.pages);
                b.writeVarInt(p.lines.size()); p.lines.forEach(t -> writeText(b, t));
                b.writeVarInt(p.controls.size()); p.controls.forEach(c -> { writeText(b, c.label); writeText(b, c.warning); b.writeBoolean(c.confirmation); b.writeBoolean(c.input); });
                b.writeVarInt(p.selection); b.writeVarLong(p.selectionRevision);
            }, b -> {
                int id = b.readVarInt(); UUID receipt = b.readUUID(); int view = b.readVarInt(), page = b.readVarInt(), pages = b.readVarInt();
                List<Text> lines = new ArrayList<>(); for (int n = count(b, MAX_LINES); n > 0; n--) lines.add(readText(b));
                List<Control> controls = new ArrayList<>(); for (int n = count(b, PAGE_SIZE); n > 0; n--) controls.add(new Control(readText(b), readText(b), b.readBoolean(), b.readBoolean()));
                return new TeamInterfacePayload(id, receipt, view, page, pages, lines, controls, b.readVarInt(), b.readVarLong());
            });
    private static int count(RegistryFriendlyByteBuf b, int maximum) { int n = b.readVarInt(); if (n < 0 || n > maximum) throw new IllegalArgumentException("Oversized interface list"); return n; }
    private static void writeText(RegistryFriendlyByteBuf b, Text text) { b.writeUtf(text.key, MAX_TEXT); b.writeVarInt(text.arguments.size()); text.arguments.forEach(s -> b.writeUtf(s, MAX_TEXT)); }
    private static Text readText(RegistryFriendlyByteBuf b) { String key = b.readUtf(MAX_TEXT); List<String> args = new ArrayList<>(); for (int n = count(b, 4); n > 0; n--) args.add(b.readUtf(MAX_TEXT)); return new Text(key, args); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
