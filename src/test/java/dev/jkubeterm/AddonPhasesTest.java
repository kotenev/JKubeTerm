package dev.jkubeterm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AddonPhasesTest {
    @Test void phaseKeywords() {
        assertEquals(AddonPhases.Phase.PULL, AddonPhases.phaseOf("Pulling image ingress-nginx/controller"));
        assertEquals(AddonPhases.Phase.VERIFY, AddonPhases.phaseOf("* Verifying Kubernetes version"));
        assertEquals(AddonPhases.Phase.ENABLE, AddonPhases.phaseOf("Enabling ingress addon"));
        assertEquals(AddonPhases.Phase.ENABLE, AddonPhases.phaseOf("Creating ingress-nginx namespace"));
        assertEquals(AddonPhases.Phase.IDLE, AddonPhases.phaseOf("just some banner text"));
        assertEquals(AddonPhases.Phase.IDLE, AddonPhases.phaseOf(null));
    }
    @Test void fractionIsMonotonicAcrossTranscript() {
        String t1 = "$ minikube addons enable ingress\nPulling image...\n";
        String t2 = t1 + "* Verifying Kubernetes version\n";
        String t3 = t2 + "Enabling ingress addon\n";
        double f1 = AddonPhases.fractionOf("Pulling image...", t1);
        double f2 = AddonPhases.fractionOf("* Verifying Kubernetes version", t2);
        double f3 = AddonPhases.fractionOf("Enabling ingress addon", t3);
        assertTrue(f1 < f2 && f2 < f3, f1 + " < " + f2 + " < " + f3);
        assertTrue(f3 <= 0.97);
    }
    @Test void etaFormats() {
        long started = System.nanoTime() - 30_000_000_000L;
        String eta = AddonPhases.eta(0.5, started, 600);
        assertTrue(eta.contains("elapsed 30s"), eta);
        assertTrue(eta.contains("left"), eta);
        assertEquals("done in 30s", AddonPhases.eta(1.0, started, 600));
        assertTrue(AddonPhases.eta(0.0, started, 600).contains("estimating"));
    }
}
