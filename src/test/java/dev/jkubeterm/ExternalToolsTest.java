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
}
