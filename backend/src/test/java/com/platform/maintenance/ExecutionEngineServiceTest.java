package com.platform.maintenance;

import com.platform.maintenance.service.ExecutionEngineService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ExecutionEngineServiceTest {

    private final ExecutionEngineService executionEngine = new ExecutionEngineService();

    @Test
    void testSecretRedaction() {
        String logWithSecret1 = "Cloning with token: ghp_1234567890abcdef1234567890abcdef1234 to workspace";
        String redacted1 = executionEngine.redactSecrets(logWithSecret1);
        assertFalse(redacted1.contains("ghp_1234567890abcdef1234567890abcdef1234"));
        assertTrue(redacted1.contains("[REDACTED_SECRET]"));

        String logWithSecret2 = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz";
        String redacted2 = executionEngine.redactSecrets(logWithSecret2);
        assertFalse(redacted2.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz"));
        assertTrue(redacted2.contains("[REDACTED_SECRET]"));

        String logWithSecret3 = "Database config: password=SuperSecretPassword123! and host=localhost";
        String redacted3 = executionEngine.redactSecrets(logWithSecret3);
        assertFalse(redacted3.contains("SuperSecretPassword123!"));
        assertTrue(redacted3.contains("[REDACTED_SECRET]"));
    }
}
