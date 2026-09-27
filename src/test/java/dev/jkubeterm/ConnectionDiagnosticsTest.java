package dev.jkubeterm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionDiagnosticsTest {
    @TempDir Path temp;

    private KubeconfigLoader.ContextRef missingFileRef() {
        return new KubeconfigLoader.ContextRef(temp.resolve("nope-config"), "ctx");
    }

    @Test void missingFileFailsFirstCheck() {
        List<ConnectionDiagnostics.Check> checks = ConnectionDiagnostics.runChecks(missingFileRef());
        assertFalse(checks.isEmpty());
        assertFalse(checks.getFirst().ok());
        assertTrue(checks.getFirst().fix().contains("Config"));
    }

    @Test void unresolvableHostFailsEndpointCheck() throws Exception {
        Path file = temp.resolve("config");
        Files.writeString(file, "apiVersion: v1\nkind: Config\nclusters: []\n");
        var ref = new KubeconfigLoader.ContextRef(file, "ctx");
        List<ConnectionDiagnostics.Check> checks = ConnectionDiagnostics.runChecks(ref);
        assertTrue(checks.size() >= 2);
    }

    @Test void causeHintsClassify() {
        assertTrue(ConnectionDiagnostics.causeHint(new RuntimeException("PKIX path building failed")).contains("TLS"));
        assertTrue(ConnectionDiagnostics.causeHint(new RuntimeException("Unauthorized 401")).contains("401"));
        assertTrue(ConnectionDiagnostics.causeHint(new RuntimeException("Forbidden 403")).contains("RBAC"));
        assertTrue(ConnectionDiagnostics.causeHint(new RuntimeException("Connection refused")).contains("not reachable"));
    }

    @Test void describeIncludesDiagnosis() {
        String report = ConnectionDiagnostics.describe(missingFileRef(), new RuntimeException("boom"));
        assertTrue(report.contains("failed"));
        assertTrue(report.contains("Diagnosis"));
    }

    @Test void diagnoseAllOkMessageOrFirstFail() {
        String report = ConnectionDiagnostics.diagnose(missingFileRef());
        assertTrue(report.contains("[FAIL]"));
        assertTrue(report.contains("FIX:"));
    }
}
