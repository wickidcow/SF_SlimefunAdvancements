package me.char321.sfadvancements.core.criteria.progress;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Actual Gson documents through the production merge/writer; gameplay definitions are tested separately. */
class RetainedProgressDocumentTest {
    private static final String KNOWN = "compatfixture:known";
    private static final String ABSENT = "compatfixture:absent";

    @Test
    void emptyManagedViewDoesNotDeleteUnresolvedAdvancementProgress() {
        var original = parse("{\"compatfixture:absent\":{\"done\":true,\"criteria\":{\"retired\":74}}}");
        assertEquals(original, RetainedProgressDocument.merge(original, new JsonObject()));
    }

    @Test
    void updatingKnownCriteriaKeepsUnresolvedSiblingsAndOtherAdvancements() {
        var original = parse("{\"compatfixture:known\":{\"done\":false,\"criteria\":{\"keep\":12,\"retired\":74}},"
                + "\"compatfixture:absent\":{\"done\":true,\"criteria\":{\"legacy\":37}}}");
        var actual = RetainedProgressDocument.merge(original, update(KNOWN, "keep", 13, false));
        assertEquals(13, actual.getAsJsonObject(KNOWN).getAsJsonObject("criteria").get("keep").getAsInt());
        assertEquals(74, actual.getAsJsonObject(KNOWN).getAsJsonObject("criteria").get("retired").getAsInt());
        assertEquals(original.get(ABSENT), actual.get(ABSENT));
    }

    @Test
    void explicitKnownRevocationAndZeroRemainAuthoritative() {
        var original = update(KNOWN, "keep", 100, true);
        var actual = RetainedProgressDocument.merge(original, update(KNOWN, "keep", 0, false));
        assertFalse(actual.getAsJsonObject(KNOWN).get("done").getAsBoolean());
        assertEquals(0, actual.getAsJsonObject(KNOWN).getAsJsonObject("criteria").get("keep").getAsInt());
    }

    @Test
    void existingFutureFieldsNestedObjectsAndExplicitNullsSurviveTheRealWriter() throws Exception {
        var original = parse("{\"compatfixture:known\":{\"done\":false,\"criteria\":{\"keep\":12,\"future\":null},"
                + "\"opaque\":{\"values\":[null,true,\"<tag&>\"],\"large\":9223372036854775807}},\"unknown-root\":null}");
        var actual = roundTrip(RetainedProgressDocument.merge(original, update(KNOWN, "keep", 13, false)));
        assertEquals(original.getAsJsonObject(KNOWN).get("opaque"), actual.getAsJsonObject(KNOWN).get("opaque"));
        assertTrue(actual.has("unknown-root"));
        assertEquals(JsonNull.INSTANCE, actual.get("unknown-root"));
        assertEquals(JsonNull.INSTANCE, actual.getAsJsonObject(KNOWN).getAsJsonObject("criteria").get("future"));
        assertEquals(Long.MAX_VALUE, actual.getAsJsonObject(KNOWN).getAsJsonObject("opaque").get("large").getAsLong());
    }

    @Test
    void originalAndManagedInputAreNotMutatedOrAliased() {
        var original = update(KNOWN, "retired", 74, false);
        var updates = update(KNOWN, "keep", 13, false);
        var originalBefore = original.deepCopy();
        var updatesBefore = updates.deepCopy();
        var result = RetainedProgressDocument.merge(original, updates);
        result.getAsJsonObject(KNOWN).getAsJsonObject("criteria").addProperty("retired", 0);
        result.getAsJsonObject(KNOWN).getAsJsonObject("criteria").addProperty("keep", 0);
        assertEquals(originalBefore, original);
        assertEquals(updatesBefore, updates);
    }

    @Test
    void subsequentInputMutationsDoNotAlterTheMergedSnapshot() {
        var original = update(KNOWN, "retired", 74, false);
        var updates = update(KNOWN, "keep", 13, false);
        var result = RetainedProgressDocument.merge(original, updates);
        var snapshot = result.deepCopy();
        original.getAsJsonObject(KNOWN).getAsJsonObject("criteria").addProperty("retired", 0);
        updates.getAsJsonObject(KNOWN).getAsJsonObject("criteria").addProperty("keep", 0);
        assertEquals(snapshot, result);
    }

    @Test
    void keysRemainLiteralAndAreNotNormalized() {
        var original = new JsonObject();
        original.addProperty("old.namespace:missing/path.with.dots", "opaque");
        original.addProperty("Not a valid advancement key", "retain");
        var actual = RetainedProgressDocument.merge(original, update(KNOWN, "literal.dotted:id", 17, false));
        assertEquals("opaque", actual.get("old.namespace:missing/path.with.dots").getAsString());
        assertEquals("retain", actual.get("Not a valid advancement key").getAsString());
        assertEquals(17, actual.getAsJsonObject(KNOWN).getAsJsonObject("criteria").get("literal.dotted:id").getAsInt());
    }

    @Test
    void returningDefinitionResumesSavedCountAndKeepsNewUpdates() {
        var disk = update(ABSENT, "legacy", 37, false);
        disk = RetainedProgressDocument.merge(disk, new JsonObject());
        disk = RetainedProgressDocument.merge(disk, update(KNOWN, "keep", 12, false));
        var returning = update(ABSENT, "legacy", disk.getAsJsonObject(ABSENT).getAsJsonObject("criteria").get("legacy").getAsInt() + 1, false);
        disk = RetainedProgressDocument.merge(disk, returning);
        assertEquals(38, disk.getAsJsonObject(ABSENT).getAsJsonObject("criteria").get("legacy").getAsInt());
        assertEquals(12, disk.getAsJsonObject(KNOWN).getAsJsonObject("criteria").get("keep").getAsInt());
    }

    @Test
    void malformedUnmanagedRecordsAreOpaqueButKnownUpdatesReplaceManagedFields() {
        var original = parse("{\"opaque\":[1,null,\"retain\"],\"compatfixture:known\":{\"future\":true,\"criteria\":\"old-invalid-type\"}}");
        var actual = RetainedProgressDocument.merge(original, update(KNOWN, "keep", 12, false));
        assertEquals(original.get("opaque"), actual.get("opaque"));
        assertTrue(actual.getAsJsonObject(KNOWN).get("future").getAsBoolean());
        assertEquals(12, actual.getAsJsonObject(KNOWN).getAsJsonObject("criteria").get("keep").getAsInt());
    }

    @Test
    void rejectsNonObjectManagedUpdatesWithoutChangingInputs() {
        var original = update(KNOWN, "keep", 12, false);
        var updates = parse("{\"compatfixture:known\":null}");
        var before = original.deepCopy();
        assertThrows(IllegalArgumentException.class, () -> RetainedProgressDocument.merge(original, updates));
        assertEquals(before, original);
        assertThrows(NullPointerException.class, () -> RetainedProgressDocument.merge(null, updates));
        assertThrows(NullPointerException.class, () -> RetainedProgressDocument.merge(original, null));
    }

    @Test
    void repeatedSavesAreIdempotentAndKeepLargeOpaqueNumbers() throws Exception {
        var disk = parse("{\"compatfixture:known\":{\"done\":false,\"criteria\":{\"retired\":9223372036854775807}},\"future\":-9223372036854775808}");
        var updates = update(KNOWN, "keep", Integer.MAX_VALUE, false);
        var expected = RetainedProgressDocument.merge(disk, updates);
        for (int cycle = 0; cycle < 10; cycle++) {
            disk = roundTrip(RetainedProgressDocument.merge(disk, updates));
            assertEquals(expected, disk);
        }
    }

    @Test
    void preservesTheExistingCheckedIoFailureContract() throws Exception {
        IOException expected = new IOException("Expected writer failure");
        Writer failing = new Writer() {
            @Override public void write(char[] chars, int offset, int length) throws IOException { throw expected; }
            @Override public void flush() {}
            @Override public void close() {}
        };
        var writer = new JsonWriter(failing);
        try {
            IOException actual = assertThrows(IOException.class,
                    () -> RetainedProgressDocument.write(update(KNOWN, "keep", 12, false), writer));
            assertSame(expected, actual);
        } finally {
            try {
                writer.close();
            } catch (IOException incompleteDocument) {
                // The intentionally interrupted writer can also reject closing its partial document.
            }
        }
    }

    @Test
    void generatedManagedResultsMatchTheOriginalWriterWhenNothingIsUnresolved() throws Exception {
        Random random = new Random(713421L);
        for (int cycle = 0; cycle < 2000; cycle++) {
            var updates = new JsonObject();
            for (int item = 0; item < 8; item++) {
                String key = "fixture:item_" + item;
                updates.add(key, update(key, "counter", random.nextInt(), random.nextBoolean()).get(key));
            }
            assertEquals(updates, roundTrip(RetainedProgressDocument.merge(new JsonObject(), updates)));
        }
    }

    @Test
    void everyUnknownSiblingSurvivesMixedDefinitionChanges() throws Exception {
        var original = new JsonObject();
        for (int item = 0; item < 1000; item++) {
            String key = "fixture:item_" + item;
            original.add(key, update(key, "retired", item, false).get(key));
        }
        var updates = update("fixture:item_7", "active", 99, true);
        var result = roundTrip(RetainedProgressDocument.merge(original, updates));
        assertEquals(1000, result.size());
        for (int item = 0; item < 1000; item++) {
            var criteria = result.getAsJsonObject("fixture:item_" + item).getAsJsonObject("criteria");
            assertEquals(item, criteria.get("retired").getAsInt());
        }
        assertEquals(99, result.getAsJsonObject("fixture:item_7").getAsJsonObject("criteria").get("active").getAsInt());
    }

    private static JsonObject update(String key, String criterion, int count, boolean done) {
        var result = new JsonObject();
        var progress = new JsonObject();
        progress.addProperty("done", done);
        var criteria = new JsonObject();
        criteria.addProperty(criterion, count);
        progress.add("criteria", criteria);
        result.add(key, progress);
        return result;
    }

    private static JsonObject parse(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static JsonObject roundTrip(JsonObject value) throws Exception {
        var text = new StringWriter();
        try (var writer = new JsonWriter(text)) {
            RetainedProgressDocument.write(value, writer);
        }
        return parse(text.toString());
    }
}
