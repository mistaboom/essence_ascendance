package com.mistaboom.essence_ascendance.balance.config;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;

/** Stable named identifiers in resolved runtime data and saved generation inputs. */
public final class ResourceLocationJsonAdapter implements JsonSerializer<ResourceLocation>, JsonDeserializer<ResourceLocation> {
    @Override
    public JsonElement serialize(ResourceLocation value, Type type, JsonSerializationContext context) {
        return new JsonPrimitive(value.toString());
    }

    @Override
    public ResourceLocation deserialize(JsonElement value, Type type, JsonDeserializationContext context) {
        ResourceLocation result = ResourceLocation.tryParse(value.getAsString());
        if (result == null) throw new JsonParseException("Invalid resource identifier: " + value);
        return result;
    }
}
