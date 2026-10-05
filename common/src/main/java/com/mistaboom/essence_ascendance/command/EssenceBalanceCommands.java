package com.mistaboom.essence_ascendance.command;

import com.google.gson.JsonElement;
import com.mistaboom.essence_ascendance.balance.generated.BalanceDocument;
import com.mistaboom.essence_ascendance.balance.generated.BalanceProfileStore;
import com.mistaboom.essence_ascendance.balance.generated.GeneratedBalanceService;
import com.mistaboom.essence_ascendance.mapping.ItemEssenceMappingManager;
import com.mistaboom.essence_ascendance.essence.EssenceRegistry;
import com.mistaboom.essence_ascendance.skill.SkillRegistry;
import com.mistaboom.essence_ascendance.stat.EssenceStatRegistry;
import com.mistaboom.essence_ascendance.text.EssenceText;
import com.mistaboom.essence_ascendance.visual.AscendancePalette;
import com.mistaboom.essence_ascendance.tier.AscendanceTierRegistry;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Explanations read the installed snapshot. Only rebuild invokes providers or solvers. */
final class EssenceBalanceCommands {
    private EssenceBalanceCommands() { }
    static LiteralArgumentBuilder<CommandSourceStack> build() {
        var root = Commands.literal("balance").requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                .executes(context -> showHelp(context.getSource(), false))
                .then(Commands.literal("help").executes(context -> showHelp(context.getSource(), false)))
                .then(Commands.literal("summary").executes(context -> guarded(context.getSource(), () -> summary(context.getSource()))))
                .then(Commands.literal("validate").executes(context -> guarded(context.getSource(), () -> validate(context.getSource()))))
                .then(Commands.literal("cost").then(Commands.argument("path", StringArgumentType.string())
                        .executes(context -> guarded(context.getSource(), () -> cost(context.getSource(), StringArgumentType.getString(context,"path"))))))
                .then(Commands.literal("bonus").then(Commands.argument("id", StringArgumentType.string())
                        .suggests(EssenceCommandUtil::suggestStats)
                        .executes(context -> guarded(context.getSource(), () -> stat(context.getSource(), StringArgumentType.getString(context,"id"))))))
                .then(Commands.literal("skill").then(Commands.argument("id", StringArgumentType.string())
                        .suggests(EssenceCommandUtil::suggestSkills)
                        .executes(context -> guarded(context.getSource(), () -> skill(context.getSource(), StringArgumentType.getString(context,"id"),1)))
                        .then(Commands.argument("rank",IntegerArgumentType.integer(1,1000))
                                .executes(context -> guarded(context.getSource(), () -> skill(context.getSource(),StringArgumentType.getString(context,"id"),IntegerArgumentType.getInteger(context,"rank")))))));
        root.then(Commands.literal("item")
                    .executes(context -> guarded(context.getSource(), () -> explain(context.getSource(),
                            BuiltInRegistries.ITEM.getKey(context.getSource().getPlayerOrException().getMainHandItem().getItem()).toString())))
                    .then(Commands.argument("id",StringArgumentType.string())
                            .executes(context -> guarded(context.getSource(), () -> explain(context.getSource(),StringArgumentType.getString(context,"id"))))));
        return root;
    }

    static LiteralArgumentBuilder<CommandSourceStack> admin() {
        return Commands.literal("balance")
                .requires(source -> source.hasPermission(EssenceCommandUtil.ADMIN_PERMISSION))
                .executes(context -> showHelp(context.getSource(), true))
                .then(Commands.literal("help").executes(context -> showHelp(context.getSource(), true)))
                .then(Commands.literal("rebuild").executes(context -> rebuild(context.getSource())))
                .then(Commands.literal("export").executes(context -> guarded(context.getSource(), () -> {
                    GeneratedBalanceService.export();
                    tell(context.getSource(), "exported", GeneratedBalanceService.directory().resolve("reports").toString());
                    EssenceCommandUtil.send(context.getSource(), EssenceCommandUtil.line("README_REPORTS.txt",
                            EssenceText.command("balance.help.report_guide")));
                    return 1;
                })));
    }

    private static int showHelp(CommandSourceStack source, boolean administration) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command(
                administration ? "balance.help.admin_title" : "debug.balance.title")));
        if (administration) {
            help(source, "/essence admin balance rebuild", "rebuild");
            help(source, "/essence admin balance export", "export");
        } else {
            help(source, "/essence debug balance summary", "summary");
            help(source, "/essence debug balance validate", "validate");
            help(source, "/essence debug balance item [id]", "item");
            help(source, "/essence debug balance bonus <bonus>", "bonus");
            help(source, "/essence debug balance skill <skill> [rank]", "skill");
            help(source, "/essence debug balance cost <path>", "cost");
        }
        return 1;
    }

    private static void help(CommandSourceStack source, String syntax, String key) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.command(syntax, EssenceText.command("balance.help." + key)));
    }
    private static int summary(CommandSourceStack source) {
        var profile = GeneratedBalanceService.active();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.command("debug.balance.title")));
        line(source,"Profile",profile.document().integrity().substring(0,16)+" / "+BalanceDocument.GENERATOR);
        line(source,"Resources / equipment / enemies",profile.economy().resources().size()+" / "+profile.evidence().equipment().size()+" / "+profile.evidence().enemies().size());
        line(source,"Curves",profile.runtime().config().statMaxBonuses().size()+" stats; "+profile.runtime().skillCurves().size()+" skills");
        line(source,"Conservation",profile.economy().invariants().size()+" paths passed; "+profile.economy().solverPasses()+" bounded solver passes");
        line(source,"Last load",GeneratedBalanceService.lastLoadMillis()+" ms");
        return 1;
    }
    private static int validate(CommandSourceStack source) throws Exception {
        tell(source,"validating");
        try (var operation = com.mistaboom.essence_ascendance.balance.generated.BalancePerformance.begin(
                "explicit_profile_validation", "debug_balance_validate")) {
        try {
        var disk=BalanceProfileStore.read(GeneratedBalanceService.profilePath());
        GeneratedBalanceService.decode(disk);
        if(!disk.integrity().equals(GeneratedBalanceService.active().document().integrity()))
            throw new IllegalArgumentException("Saved profile differs from active snapshot; use /essence admin mappings reload or /essence admin balance rebuild");
        tell(source,"valid");
        operation.complete("validated_matches_active");
        return 1;
        } catch (Exception | Error error) {
            operation.fail(error);
            throw error;
        }
        }
    }
    private static int rebuild(CommandSourceStack source) {
        tell(source,"rebuilding");
        var result=ItemEssenceMappingManager.rebuild();
        if(!result.successful()) {
            EssenceCommandUtil.fail(source, EssenceText.command("balance.rejected",String.join("; ",result.errors())));return 0;
        }
        tell(source,"rebuilt",result.activeMappingCount());return 1;
    }
    private static int explain(CommandSourceStack source,String id) {
        var active=GeneratedBalanceService.active();
        var resource=active.evidence().resources().get(id);
        if(resource==null)throw new IllegalArgumentException("No saved evidence for "+id);
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Balance: " + id));
        section(source, "Acquisition");
        line(source,"Stage",resource.stage().toString());
        line(source,"Reachable",EssenceCommandUtil.status(resource.reachable(), "YES", "NO"));
        line(source,"Confidence",EssenceCommandUtil.formatProgress(resource.confidence()));
        line(source,"Supply",resource.availability()+" / "+resource.automation());
        var economic=active.economy().resources().get(id);
        if(economic!=null) {
            section(source, "Essence yield");
            line(source,"Economic value",number(economic.economicValue().amount()));
            line(source,"Dissolution total",number(economic.dissolutionYield().amount())+" Essence");
            economic.routedYields().forEach((essence, amount) -> {
                ResourceLocation essenceId = ResourceLocation.parse(essence);
                Component label = EssenceRegistry.get(essenceId).<Component>map(EssenceText::essenceShort)
                        .orElseGet(() -> Component.literal(essence));
                EssenceCommandUtil.send(source, EssenceCommandUtil.line(label,
                        Component.literal(number(amount)).withStyle(style -> style.withColor(AscendancePalette.categoryRgb(essenceId)))));
            });
        }
        section(source, "Evidence");
        resource.sources().stream().limit(4).forEach(entry->line(source,"Source",entry.kind()+" / "+entry.stage()+" / "+entry.reason()));
        active.evidence().equipment().stream().filter(entry->entry.itemId().equals(id))
                .limit(4).forEach(entry -> {
                    section(source, "Equipment: " + entry.slot());
                    line(source,"Reference", EssenceCommandUtil.status(entry.included(), "INCLUDED", "EXCLUDED"));
                    entry.axes().forEach((axis, amount) -> line(source, axis.toString().toLowerCase(Locale.ROOT).replace('_',' '), number(amount)));
                    EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  " + entry.reason()));
                });
        active.evidence().facts().stream().filter(fact->fact.subjectId().equals(id)).sorted(java.util.Comparator.comparingInt((com.mistaboom.essence_ascendance.balance.engine.EvidenceFact fact)->fact.priority()).reversed())
                .limit(5).forEach(fact -> {
                    line(source,fact.origin()+" / "+fact.provider(),fact.property()+" = "+fact.value());
                    EssenceCommandUtil.send(source, EssenceCommandUtil.muted("  " + fact.reason()));
                });
        resource.warnings().stream().limit(3).forEach(warning->line(source,"Warning",EssenceCommandUtil.warn(warning)));
        tell(source,"details");return 1;
    }
    private static int stat(CommandSourceStack source,String id) {
        var stat=EssenceStatRegistry.get(EssenceCommandUtil.parseId(id)).orElseThrow(()->new IllegalArgumentException("Unknown stat "+id));
        var config=GeneratedBalanceService.active().runtime().config();
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(EssenceText.stat(stat)));
        line(source,"ID",stat.id().toString());
        line(source,"Total maximum bonus",EssenceCommandUtil.formatBonusComponent(stat, config.statMaxBonus(stat)));
        var track = java.util.Objects.requireNonNull(config.balanceProfile().bonusTrack(stat.id()), "Missing resolved Bonus; rebuild balance");
        line(source,"Investment exponent",number(track.investmentExponent()));
        line(source,"Purchase style",track.purchaseStyle().name());
        line(source,"Applicability",track.applicability().name());
        section(source,"Tier limits");
        for(var point:track.checkpoints()) {
            var tier = AscendanceTierRegistry.get(point.tierId()).orElseThrow();
            line(source,EssenceText.ascendanceTier(tier).getString(),
                    fields("Cumulative / segment", EssenceCommandUtil.format(point.cumulativeCap()) + " / "
                                    + EssenceCommandUtil.format(point.segmentCost()),
                            "effect", EssenceCommandUtil.formatBonus(stat, track.maximumEffect() * point.effectFraction())));
        }
        return 1;
    }
    private static int skill(CommandSourceStack source,String id,int rank) {
        var definition=SkillRegistry.get(EssenceCommandUtil.parseId(id)).orElseThrow(()->new IllegalArgumentException("Unknown skill "+id));
        var curve=GeneratedBalanceService.active().runtime().skillCurves().get(definition.id().toString());
        if(rank>curve.ranks().size())throw new IllegalArgumentException("Rank exceeds generated projection "+curve.ranks().size());
        var point=curve.ranks().get(rank-1);
        EssenceCommandUtil.send(source, EssenceCommandUtil.title(Component.translatable(definition.nameTranslationKey())));
        line(source,"ID",definition.id().toString());
        line(source,"Rank",fields("Selected", Integer.toString(rank), "maximum purchasable", Integer.toString(curve.maximumRank())));
        line(source,"Cost",EssenceCommandUtil.format(point.cost()));
        line(source,"Power versus rank 1",EssenceCommandUtil.formatStrength(point.powerMultiplier()));
        line(source,"Tier",definition.requiredTierId().toString());
        tell(source,"details");return 1;
    }
    private static int cost(CommandSourceStack source,String pointer) {
        if(!pointer.startsWith("/runtime/"))throw new IllegalArgumentException("Use a quoted /runtime/... path from generated_balance.json.gz or reports/runtime_parameters.csv");
        JsonElement value=GeneratedBalanceService.active().document().section("runtime");
        for(String token:pointer.substring("/runtime/".length()).split("/")) {
            String key=token.replace("~1","/").replace("~0","~");
            if(value.isJsonObject())value=value.getAsJsonObject().get(key);
            else if(value.isJsonArray())value=value.getAsJsonArray().get(Integer.parseInt(key));
            else throw new IllegalArgumentException("Path ends before "+key);
            if(value==null)throw new IllegalArgumentException("Unknown runtime path "+pointer);
        }
        if(!value.isJsonPrimitive())throw new IllegalArgumentException("Choose a scalar value path; full structures are in generated JSON");
        EssenceCommandUtil.send(source, EssenceCommandUtil.title("Generated balance value"));
        line(source,"Path",EssenceCommandUtil.muted(pointer));
        line(source,"Value",value.getAsString());return 1;
    }
    private static int guarded(CommandSourceStack source,Action action) {
        try {return action.run();}catch(Exception error){EssenceCommandUtil.fail(source, EssenceText.command("balance.error",error.getMessage()));return 0;}
    }
    private interface Action {int run()throws Exception;}
    private static void section(CommandSourceStack source, String name) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.section(name));
    }
    private static void line(CommandSourceStack source,String key,String value) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(key, value));
    }
    private static void line(CommandSourceStack source,String key,Component value) {
        EssenceCommandUtil.send(source, EssenceCommandUtil.line(key, value));
    }
    private static MutableComponent fields(String firstLabel, String firstValue, String secondLabel, String secondValue) {
        return Component.literal(firstLabel + " ").withStyle(ChatFormatting.GRAY)
                .append(EssenceCommandUtil.value(firstValue))
                .append(Component.literal("  |  " + secondLabel + " ").withStyle(ChatFormatting.GRAY))
                .append(EssenceCommandUtil.value(secondValue));
    }
    private static String number(double value) {
        return new DecimalFormat("#,##0.######", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(value);
    }
    private static void tell(CommandSourceStack source,String key,Object... args) {
        ChatFormatting color = switch (key) {
            case "valid", "rebuilt", "exported" -> ChatFormatting.GREEN;
            case "rebuilding", "validating" -> ChatFormatting.YELLOW;
            case "details" -> ChatFormatting.DARK_GRAY;
            default -> ChatFormatting.WHITE;
        };
        EssenceCommandUtil.send(source, EssenceText.command("balance."+key,args).withStyle(color));
    }
}
