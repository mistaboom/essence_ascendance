package com.mistaboom.essence_ascendance.text;

import com.mistaboom.essence_ascendance.essence.EssenceDefinition;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Native translation argument and codec contracts shared by both Minecraft versions. */
public final class EssenceTextTest {
    private static int checks;

    private EssenceTextTest() {}

    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        allArgumentFamilies();
        skillCommandMessages();
        fallbackMessages();
        new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out))
                .println("EssenceTextTest: " + checks + " native argument and codec checks PASS");
    }

    private static void allArgumentFamilies() {
        List<Family> families = List.of(
                new Family("gui", EssenceText::gui),
                new Family("tooltip", EssenceText::tooltip),
                new Family("command", EssenceText::command),
                new Family("jei", EssenceText::jei),
                new Family("term", EssenceText::term),
                new Family("guide", EssenceText::guide),
                new Family("literal", (path, args) -> EssenceText.literalOrTranslated(
                        "literal.essence_ascendance." + path, "Unused argument-free fallback", args))
        );
        for (Family family : families) {
            ResourceLocation id = ResourceLocation.parse("essence_ascendance:vector_boost");
            MutableComponent label = Component.literal("Vector ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("Boost").withStyle(ChatFormatting.BOLD, ChatFormatting.AQUA));
            Object diagnostic = new Object() {
                @Override public String toString() { return "diagnostic detail"; }
            };
            Object[] supplied = {id, label, 7, 2.5D, true, "ready", diagnostic, null};
            Object[] original = supplied.clone();
            Component message = family.factory.create("argument_contract", supplied);
            String key = family.prefix + ".essence_ascendance.argument_contract";
            TranslatableContents contents = translated(message, key);
            Object[] normalized = contents.getArgs();
            check(normalized.length == supplied.length, key + " preserves argument count");
            check(normalized[0] instanceof String && normalized[0].equals(id.toString()),
                    key + " converts resource identifiers to supported display text");
            check(normalized[1] == label, key + " retains the styled component object");
            for (int i = 2; i <= 5; i++) {
                check(normalized[i] == original[i], key + " retains scalar argument " + i);
            }
            check(normalized[6].equals("diagnostic detail") && normalized[7].equals("null"),
                    key + " normalizes diagnostic and null arguments through vanilla");
            for (int i = 0; i < supplied.length; i++) {
                check(supplied[i] == original[i], key + " does not mutate caller argument " + i);
                check(supported(normalized[i]), key + " contains only codec-supported argument " + i);
            }
            supplied[0] = "changed after construction";
            check(contents.getArgs()[0].equals(id.toString()), key + " owns its normalized argument array");

            TranslatableContents decoded = translated(roundTrip(message), key);
            Object[] restored = decoded.getArgs();
            check(restored.length == normalized.length && restored[0].equals(id.toString()),
                    key + " identifier survives native component serialization");
            check(restored[1] instanceof Component, key + " nested label stays a component on the wire");
            Component decodedLabel = (Component) restored[1];
            check(decodedLabel.getString().equals("Vector Boost")
                            && decodedLabel.getStyle().equals(label.getStyle())
                            && decodedLabel.getSiblings().size() == 1
                            && decodedLabel.getSiblings().getFirst().getStyle().equals(label.getSiblings().getFirst().getStyle()),
                    key + " nested text and independent parent/child styles survive the codec");
            check(restored[2] instanceof Number whole && whole.intValue() == 7
                            && restored[3] instanceof Number fraction && fraction.doubleValue() == 2.5D
                            && Boolean.TRUE.equals(restored[4]) && restored[5].equals("ready"),
                    key + " scalar values survive the codec without becoming display strings");
        }
    }

    private static void skillCommandMessages() {
        ResourceLocation id = ResourceLocation.parse("essence_ascendance:essence_wings");
        TranslatableContents granted = translated(roundTrip(EssenceText.command("admin.skills.granted", 1, id)),
                "command.essence_ascendance.admin.skills.granted");
        check(granted.getArgs()[0] instanceof Number count && count.intValue() == 1
                        && granted.getArgs()[1].equals(id.toString()),
                "Successful skill grants send numeric count and printable skill identifier");
        TranslatableContents unknown = translated(roundTrip(EssenceText.command("error.unknown_skill", id)),
                "command.essence_ascendance.error.unknown_skill");
        check(unknown.getArgs().length == 1 && unknown.getArgs()[0].equals(id.toString()),
                "Unknown-skill errors safely send the rejected identifier");
    }

    private static void fallbackMessages() {
        String missing = "test.essence_ascendance.external_name_without_translation";
        for (Object[] args : new Object[][] {new Object[0], null}) {
            Component message = EssenceText.literalOrTranslated(missing, "External pack name", args);
            TranslatableContents contents = translated(message, missing);
            check(contents.getArgs().length == 0 && "External pack name".equals(contents.getFallback()),
                    "Argument-free and null-array callers preserve explicit fallback text");
            Component restored = roundTrip(message);
            check(restored.getString().equals("External pack name")
                            && "External pack name".equals(translated(restored, missing).getFallback()),
                    "Missing translations retain their visible fallback after serialization");
        }
        TranslatableContents formatted = translated(EssenceText.literalOrTranslated(
                "command.essence_ascendance.error.unknown_skill", "Unused fallback", ResourceLocation.parse("example:missing")),
                "command.essence_ascendance.error.unknown_skill");
        check(formatted.getFallback() == null && formatted.getArgs()[0].equals("example:missing"),
                "Formatted catalog keys keep their existing no-fallback behavior");
        EssenceDefinition external = new EssenceDefinition(ResourceLocation.parse("example_pack:custom"), "Custom Essence");
        Component fullName = roundTrip(EssenceText.essence(external));
        Component shortName = roundTrip(EssenceText.essenceShort(external));
        check(translated(fullName, "essence.example_pack.custom").getFallback().equals("Custom Essence")
                        && fullName.getString().equals("Custom Essence"),
                "Config-defined names preserve their fallback without translation arguments");
        check(translated(shortName, "essence_short.example_pack.custom").getFallback().equals("Custom")
                        && shortName.getString().equals("Custom"),
                "Config-defined short names preserve their trimmed fallback");
    }

    private static Component roundTrip(Component component) {
        var encoded = ComponentSerialization.CODEC.encodeStart(JsonOps.INSTANCE, component).getOrThrow();
        return ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }

    private static TranslatableContents translated(Component component, String expectedKey) {
        check(component.getContents() instanceof TranslatableContents, expectedKey + " stays translatable");
        var contents = (TranslatableContents) component.getContents();
        check(contents.getKey().equals(expectedKey), "Translation key stays " + expectedKey);
        return contents;
    }

    private static boolean supported(Object argument) {
        return argument instanceof Component || argument instanceof String
                || argument instanceof Number || argument instanceof Boolean;
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    @FunctionalInterface
    private interface Factory {
        MutableComponent create(String path, Object... args);
    }

    private record Family(String prefix, Factory factory) {}
}
