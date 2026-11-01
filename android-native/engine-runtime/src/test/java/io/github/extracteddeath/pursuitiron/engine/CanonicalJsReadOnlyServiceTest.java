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
