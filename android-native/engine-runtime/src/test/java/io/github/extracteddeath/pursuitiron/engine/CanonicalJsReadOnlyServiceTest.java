package io.github.extracteddeath.pursuitiron.engine;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

/** Transport-only JVM tests; real sandbox lifecycle requires an Android device. */
public final class CanonicalJsReadOnlyServiceTest {
    private static final String VALID =
            "{\"protocolVersion\":1,\"id\":\"case-1\",\"method\":\"progressionPreview\"," +
            "\"params\":{\"exerciseId\":\"back_squat\"}}";

    private static void rejects(String json) {
        assertThrows(Exception.class,
                () -> CanonicalJsReadOnlyService.checkedPreviewRequest(json));
    }

    @Test public void canonicalLookupRejectsWritesDuplicatesAndInvalidPayloads() throws Exception {
        final String lookup =
                "{\"protocolVersion\":1,\"id\":\"catalog-1\",\"method\":\"exerciseLookup\"," +
                "\"params\":{\"ids\":[\"bb-bench\",\"inc-db-press\"]}}";
        JSONObject request = CanonicalJsReadOnlyService.checkedExerciseLookupRequest(lookup);
        assertEquals("exerciseLookup", request.getString("method"));
        assertEquals(2, request.getJSONObject("params").getJSONArray("ids").length());
        assertThrows(Exception.class,
                () -> CanonicalJsReadOnlyService.checkedExerciseLookupRequest(
                        lookup.replace("exerciseLookup","deleteHistory")));
        assertThrows(Exception.class,
                () -> CanonicalJsReadOnlyService.checkedExerciseLookupRequest(
                        lookup.replace("inc-db-press","bb-bench")));
        assertThrows(Exception.class,
                () -> CanonicalJsReadOnlyService.checkedExerciseLookupRequest(
                        lookup.replace("bb-bench","not valid!")));
        assertThrows(Exception.class,
                () -> CanonicalJsReadOnlyService.checkedExerciseLookupRequest(
                        lookup.replace("\"params\":{\"ids\":","\"params\":{\"history\":1,\"ids\":")));
        assertThrows(Exception.class,
                () -> CanonicalJsReadOnlyService.checkedExerciseLookupRequest(
                        lookup.replace("\"protocolVersion\":1","\"protocolVersion\":2")));
    }


    @Test public void syntheticOutlineAcceptsOnlyFixedProfilesWithoutPrivateData() throws Exception {
        final String valid =
                "{\"protocolVersion\":1,\"id\":\"outline-1\",\"method\":\"workoutOutline\"," +
                "\"params\":{\"split\":\"full_body\",\"dayIndex\":0}}";
        JSONObject request = CanonicalJsReadOnlyService.checkedSyntheticOutlineRequest(valid);
        assertEquals("full_body",request.getJSONObject("params").getString("split"));
        for (String invalid : new String[] {
                valid.replace("workoutOutline","deleteHistory"),
                valid.replace("\"dayIndex\":0","\"dayIndex\":3"),
                valid.replace("\"dayIndex\":0","\"dayIndex\":0.5"),
                valid.replace("full_body","custom"),
                valid.replace("\"split\":\"full_body\"","\"goal\":\"strength\",\"split\":\"full_body\""),
                valid.replace("\"protocolVersion\":1","\"protocolVersion\":2"),
                valid.replace("\"id\":\"outline-1\"","\"id\":\"\"")
        }) {
            assertThrows(Exception.class,
                    () -> CanonicalJsReadOnlyService.checkedSyntheticOutlineRequest(invalid));
        }
    }

    @Test public void progressionOnlyAndBoundedTransport() throws Exception {
        JSONObject request = CanonicalJsReadOnlyService.checkedPreviewRequest(VALID);
        assertEquals("progressionPreview", request.getString("method"));
        assertEquals("case-1", request.getString("id"));
        rejects(VALID.replace("progressionPreview","deleteHistory"));
        rejects(VALID.replace("progressionPreview","generateProgram"));
        rejects(VALID.replace("\"protocolVersion\":1","\"protocolVersion\":2"));
        rejects(VALID.replace("\"id\":\"case-1\"","\"id\":\"\""));
        rejects(VALID.replace("\"params\":{","\"params\":["));
        rejects("not json");
        rejects("x".repeat(17000));
    }
}
