package com.mistaboom.essence_ascendance.valuation;

import com.google.gson.JsonObject;
import com.mistaboom.essence_ascendance.balance.economy.ProductionGraph;
import com.mistaboom.essence_ascendance.balance.generated.BalancePerformance;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.*;

/** One immutable view of published items, tags and recipes. No quest, script, loot or world sampling. */
public final class GenerationDataSnapshot implements AutoCloseable {
    private MinecraftServer server;
    private final GenerationEpoch epoch;
    private List<Item> items;
    private List<RecipeHolder<?>> recipes;
    private Map<String,String> mods;
    private Map<String,List<String>> tags;
    private ProductionGraph production;
    private boolean readOnly;
    private final Map<String,Long> unsupported = new TreeMap<>();
    private final List<String> limitations = new ArrayList<>();
    private JsonObject routing;
    private GenerationDataSnapshot(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Generation requires the server thread");
        this.server=server;
        epoch=new GenerationEpoch(server,server.getResourceManager(),server.getRecipeManager(),server.registryAccess(),server.reloadableRegistries());
        items=BuiltInRegistries.ITEM.stream().sorted(Comparator.comparing(i->BuiltInRegistries.ITEM.getKey(i).toString())).toList();
        recipes=server.getRecipeManager().getRecipes().stream().sorted(Comparator.comparing(r->r.id().toString())).toList();
        var installed=new TreeMap<String,String>();
        Platform.getMods().forEach(m->installed.put(m.getModId(),m.getVersion()));
        mods=Collections.unmodifiableMap(installed);
    }
    public static GenerationDataSnapshot capture(MinecraftServer server) {
        BalancePerformance.increment("generation_snapshot_captures");
        return new GenerationDataSnapshot(server);
    }
    public void requireCurrent(MinecraftServer expected) {
        if (server==null || server!=expected || !expected.isSameThread()) throw new IllegalStateException("Generation snapshot is not current");
        epoch.require(expected,expected.getResourceManager(),expected.getRecipeManager(),expected.registryAccess(),expected.reloadableRegistries());
    }
    private void requireOpen(){ if(server==null)throw new IllegalStateException("Generation snapshot released");requireCurrent(server); }
    private void requireWritable(){requireOpen();if(readOnly)throw new IllegalStateException("Provider cannot mutate shared generation inputs");}
    public <T>T readOnlyIntegration(java.util.function.Supplier<T> action){requireOpen();boolean prior=readOnly;readOnly=true;try{return action.get();}finally{readOnly=prior;}}
    public MinecraftServer server(){requireOpen();return server;}
    public List<Item> items(){requireOpen();return items;}
    public List<RecipeHolder<?>> recipes(){requireOpen();return recipes;}
    public String installedVersion(String id){requireOpen();return mods.get(id);}
    public ProductionGraph production(){requireOpen();if(production==null)production=EffectiveProduction.collect(this,server.registryAccess());return production;}
    public void production(ProductionGraph graph){requireWritable();production=Objects.requireNonNull(graph);}
    public List<String> itemTag(ResourceLocation id){return itemTags().getOrDefault(id.toString(),List.of());}
    public Map<String,List<String>> itemTags(){
        requireOpen();if(tags==null){
            var collected=new TreeMap<String,List<String>>();
            items.forEach(i->i.builtInRegistryHolder().tags().forEach(t->collected.computeIfAbsent(t.location().toString(),k->new ArrayList<>()).add(BuiltInRegistries.ITEM.getKey(i).toString())));
            collected.replaceAll((k,v)->v.stream().sorted().distinct().toList());tags=Collections.unmodifiableMap(collected);
        }return tags;
    }
    void unsupportedRecipe(String family,String reason){unsupported.merge(family+": "+reason,1L,Long::sum);}
    void addLimitations(List<String> reasons){requireWritable();limitations.addAll(reasons);}
    public List<String> limitations(){requireOpen();return limitations.stream().distinct().sorted().toList();}
    public void recordRoutingDiagnostics(List<ProceduralValuationResult> values){
        requireWritable();routing=new JsonObject();routing.addProperty("items",values.size());
        routing.addProperty("policy","Generic item properties and bounded recipe estimates; no acquisition or throughput inference");
    }
    public JsonObject diagnostics(){
        requireOpen();var result=new JsonObject();var gson=new com.google.gson.Gson();
        result.addProperty("engine","simple-items-recipes-v1");result.addProperty("items",items.size());result.addProperty("effectiveRecipes",recipes.size());
        result.add("mods",gson.toJsonTree(mods));result.add("unsupportedRecipes",gson.toJsonTree(unsupported));result.add("limitations",gson.toJsonTree(limitations()));
        if(routing!=null)result.add("routing",routing.deepCopy());
        if(production!=null)result.addProperty("normalizedProcesses",production.processes().size());
        return result;
    }
    @Override public void close(){server=null;items=List.of();recipes=List.of();mods=Map.of();tags=null;production=null;routing=null;}
}
