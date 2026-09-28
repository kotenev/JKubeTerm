package dev.jkubeterm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExternalToolsTest {
    @Test void minikubeArgvWithProfile() {
        var argv = ExternalTools.minikube("minikube", "addons", "enable", "ingress");
        assertEquals(java.util.List.of("minikube", "-p", "minikube", "addons", "enable", "ingress"), argv);
    }
    @Test void minikubeArgvWithoutProfile() {
        assertEquals(java.util.List.of("minikube", "addons", "list"), ExternalTools.minikube(null, "addons", "list"));
        assertEquals(java.util.List.of("minikube", "addons", "list"), ExternalTools.minikube("  ", "addons", "list"));
    }
    @Test void runStreamingCollectsLines() throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("jkube");
        var file = dir.resolve("config");
        java.nio.file.Files.writeString(file, "apiVersion: v1\nkind: Config\n");
        var ctx = new KubeconfigLoader.ContextRef(file, "ctx");
        String out = ExternalTools.runStreaming(ctx, java.util.List.of("echo", "hello-addon"), 10,
            line -> {}, () -> false);
        assertTrue(out.contains("hello-addon"), out);
    }
    @Test void runStreamingCancelled() {
        var dir = assertDoesNotThrow(() -> java.nio.file.Files.createTempDirectory("jkube2"));
        var file = dir.resolve("config");
        assertDoesNotThrow(() -> java.nio.file.Files.writeString(file, "apiVersion: v1\nkind: Config\n"));
        var ctx = new KubeconfigLoader.ContextRef(file, "ctx");
        // /bin/sleep produces no lines; cancel via timeout path instead of infinite sleep portability issues:
        // use a quick command but pre-cancelled supplier
        var ex = assertThrows(java.io.IOException.class, () ->
            ExternalTools.runStreaming(ctx, java.util.List.of("echo", "x"), 10, line -> {}, () -> true));
        assertTrue(ex.getMessage().contains("Cancelled"), ex.getMessage());
    }
}
