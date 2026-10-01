package me.char321.sfadvancements.core.criteria.progress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonIOException;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/** Overlay currently managed progress without deleting unresolved identities or opaque saved fields. */
final class RetainedProgressDocument {
    private static final Gson GSON = new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    private RetainedProgressDocument() {}

    static JsonObject merge(JsonObject original, JsonObject updates) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(updates, "updates");
        JsonObject result = original.deepCopy();
        for (Map.Entry<String, JsonElement> entry : updates.entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                throw new IllegalArgumentException("Managed advancement progress must be an object");
            }
            JsonObject update = entry.getValue().getAsJsonObject();
            JsonObject merged = objectOrEmpty(result.get(entry.getKey()));
            for (Map.Entry<String, JsonElement> field : update.entrySet()) {
                if (field.getKey().equals("criteria") && field.getValue().isJsonObject()) {
                    JsonObject criteria = objectOrEmpty(merged.get("criteria"));
                    for (Map.Entry<String, JsonElement> criterion : field.getValue().getAsJsonObject().entrySet()) {
                        criteria.add(criterion.getKey(), criterion.getValue().deepCopy());
                    }
                    merged.add("criteria", criteria);
                } else {
                    merged.add(field.getKey(), field.getValue().deepCopy());
                }
            }
            result.add(entry.getKey(), merged);
        }
        return result;
    }

    static void write(JsonObject document, JsonWriter writer) throws IOException {
        try {
            GSON.toJson(document, writer);
        } catch (JsonIOException failure) {
            if (failure.getCause() instanceof IOException cause) {
                throw cause;
            }
            throw failure;
        }
    }

    private static JsonObject objectOrEmpty(JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }
}
