package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClusterVisualsTest {
    @Test void everyKindHasIconExplainAndValidColor() {
        for (ResourceKind kind : ResourceKind.values()) {
            assertFalse(ClusterVisuals.svgPath(kind).isBlank(), kind.name());
            assertTrue(ClusterVisuals.validHex(ClusterVisuals.colorHex(kind)), kind.name());
            assertFalse(ClusterVisuals.explain(kind).isBlank(), kind.name());
        }
    }

    @Test void iconsAreDistinctPerKind() {
        long distinct = java.util.Arrays.stream(ResourceKind.values()).map(ClusterVisuals::svgPath).distinct().count();
        assertEquals(ResourceKind.values().length, distinct);
    }

    @Test void explainKindFallsBackForUnknown() {
        assertFalse(ClusterVisuals.explainKind("Pod").isBlank());
        assertFalse(ClusterVisuals.explainKind("Secret").isBlank());
        assertFalse(ClusterVisuals.explainKind(null).isBlank());
    }
}

class ClusterAdvisorTest {
    @Test void barePodTriggersCoreFindings() {
        var pod = new PodBuilder().withNewMetadata().withName("p").withNamespace("demo").endMetadata()
            .withNewSpec().addNewContainer().withName("c").withImage("nginx").endContainer().endSpec().build();
        var checks = ClusterAdvisor.advise(pod).stream().map(ClusterAdvisor.Finding::check).toList();
        assertTrue(checks.contains("readiness-probe"));
        assertTrue(checks.contains("resource-requests"));
        assertTrue(checks.contains("run-as-non-root"));
        assertTrue(checks.contains("pinned-image"));
    }

    @Test void compliantPodIsQuiet() {
        var httpGet = new io.fabric8.kubernetes.api.model.HTTPGetActionBuilder()
            .withPath("/").withPort(new io.fabric8.kubernetes.api.model.IntOrString(80)).build();
        var probe = new io.fabric8.kubernetes.api.model.ProbeBuilder().withHttpGet(httpGet).build();
        var pod = new PodBuilder().withNewMetadata().withName("p").endMetadata()
            .withNewSpec().addNewContainer().withName("c").withImage("nginx:1.27")
            .withImagePullPolicy("IfNotPresent")
            .withReadinessProbe(probe).withLivenessProbe(probe)
            .withNewResources().addToRequests("cpu", new io.fabric8.kubernetes.api.model.Quantity("50m")).addToLimits("cpu", new io.fabric8.kubernetes.api.model.Quantity("200m")).endResources()
            .withNewSecurityContext().withRunAsNonRoot(true).withAllowPrivilegeEscalation(false).endSecurityContext()
            .endContainer().endSpec().build();
        List<String> severes = ClusterAdvisor.advise(pod).stream()
            .filter(f -> f.severity() != ClusterAdvisor.Severity.INFO).map(ClusterAdvisor.Finding::check).toList();
        assertTrue(severes.isEmpty(), String.valueOf(severes));
    }

    @Test void singleReplicaDeploymentWarns() {
        var deploy = new DeploymentBuilder().withNewMetadata().withName("web").endMetadata()
            .withNewSpec().withReplicas(1).endSpec().build();
        assertTrue(ClusterAdvisor.advise(deploy).stream().anyMatch(f -> f.check().equals("replica-count")));
    }

    @Test void serviceWithoutSelectorWarns() {
        var svc = new ServiceBuilder().withNewMetadata().withName("s").endMetadata()
            .withNewSpec().withType("ClusterIP").endSpec().build();
        assertTrue(ClusterAdvisor.advise(svc).stream().anyMatch(f -> f.check().equals("service-selector")));
    }

    @Test void ingressWithoutTlsWarns() {
        var ing = new IngressBuilder().withNewMetadata().withName("i").endMetadata()
            .withNewSpec().endSpec().build();
        assertTrue(ClusterAdvisor.advise(ing).stream().anyMatch(f -> f.check().equals("ingress-tls")));
    }

    @Test void hostNetworkIsCritical() {
        var pod = new PodBuilder().withNewMetadata().withName("p").endMetadata()
            .withNewSpec().withHostNetwork(true)
            .addNewContainer().withName("c").withImage("nginx:1.27").endContainer().endSpec().build();
        assertTrue(ClusterAdvisor.advise(pod).stream().anyMatch(f -> f.severity() == ClusterAdvisor.Severity.CRITICAL));
    }

    @Test void suspendedCronJobIsInfo() {
        var cron = new io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder()
            .withNewMetadata().withName("r").endMetadata()
            .withNewSpec().withSchedule("0 * * * *").withSuspend(true).endSpec().build();
        assertTrue(ClusterAdvisor.advise(cron).stream().anyMatch(f -> f.check().equals("cron-suspend")));
    }

    @Test void explainHoodMentionsNodeAndServiceAccount() {
        var pod = new PodBuilder().withNewMetadata().withName("p").endMetadata()
            .withNewSpec().withNodeName("minikube").withServiceAccountName("web-sa").withRestartPolicy("Always")
            .addNewContainer().withName("c").withImage("i").endContainer().endSpec().build();
        String hood = ClusterAdvisor.explainHood(pod);
        assertTrue(hood.contains("minikube") && hood.contains("web-sa"));
    }

    @Test void docsUrlPerKind() {
        assertTrue(ClusterVisuals.docsUrl("Pod").contains("/pods/"));
        assertTrue(ClusterVisuals.docsUrl("Ingress").contains("/ingress/"));
        assertTrue(ClusterVisuals.docsUrl(null).contains("kubernetes.io"));
    }

    @Test void registryBackfillAddsPassingPractices() {
        var cm = new ConfigMapBuilder().withNewMetadata().withName("c").withNamespace("demo").endMetadata()
            .withData(java.util.Map.of("key", "value")).withImmutable(true).build();
        var findings = ClusterAdvisor.advise(cm);
        assertTrue(findings.stream().anyMatch(f -> f.check().equals("config-no-secrets")),
            "backfill must list config-no-secrets as passing INFO");
        assertTrue(findings.stream().noneMatch(f -> f.severity() != ClusterAdvisor.Severity.INFO));
    }

    @Test void registryOverrideChangesFix() {
        var custom = new PracticeRegistry.Practice("pinned-image", java.util.Set.of("Pod"),
            ClusterAdvisor.Severity.WARN, "Custom", java.util.List.of("https://example.com"), "why", "custom-fix", false);
        var pod = new PodBuilder().withNewMetadata().withName("p").endMetadata()
            .withNewSpec().addNewContainer().withName("c").withImage("nginx:latest").endContainer().endSpec().build();
        var findings = ClusterAdvisor.advise(pod, java.util.List.of(custom));
        assertTrue(findings.stream().anyMatch(f -> f.check().equals("pinned-image") && f.fix().equals("custom-fix")));
    }

    @Test void versionGatedHighlights() {
        PracticeRegistry.reload();
        var sts = new io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder()
            .withNewMetadata().withName("db").endMetadata()
            .withNewSpec().addNewVolumeClaimTemplate()
            .withNewMetadata().withName("data").endMetadata()
            .withNewSpec().withAccessModes("ReadWriteOnce")
            .withNewResources().addToRequests("storage", new io.fabric8.kubernetes.api.model.Quantity("1Gi")).endResources()
            .endSpec().endVolumeClaimTemplate().endSpec().build();
        assertTrue(ClusterAdvisor.advise(sts, PracticeRegistry.cached(), "v1.37.0").stream()
            .anyMatch(f -> f.check().equals("stateful-retention") && f.message().contains("1.37")));
        assertTrue(ClusterAdvisor.advise(sts, PracticeRegistry.cached(), "v1.30.0").stream()
            .filter(f -> f.check().equals("stateful-retention"))
            .allMatch(f -> !f.message().contains("Server is Kubernetes")));
        var job = new io.fabric8.kubernetes.api.model.batch.v1.JobBuilder()
            .withNewMetadata().withName("m").endMetadata()
            .withNewSpec().withNewTemplate().withNewSpec().withRestartPolicy("Never")
            .addNewContainer().withName("c").withImage("busybox:1.36").endContainer().endSpec().endTemplate().endSpec().build();
        assertTrue(ClusterAdvisor.advise(job, PracticeRegistry.cached(), "v1.36.1").stream()
            .anyMatch(f -> f.check().equals("job-success-policy")));
        assertEquals(37, ClusterAdvisor.parseVersion("v1.37.0")[1]);
        assertNull(ClusterAdvisor.parseVersion("not-a-version"));
        assertNull(ClusterAdvisor.parseVersion(null));
    }
}
class PracticeRegistryTest {
    @Test void parseBundledWiki() {
        var bundled = PracticeRegistry.loadBundled();
        assertTrue(bundled.size() >= 200, "bundled wiki must be rich, got " + bundled.size());
        var probe = PracticeRegistry.byId(bundled, "pod-readiness-gates");
        assertNotNull(probe);
        assertTrue(probe.docs().stream().anyMatch(u -> u.contains("kubernetes.io")));
        assertFalse(probe.why().isBlank());
    }

    @Test void userOverrideWinsById() throws Exception {
        var dir = java.nio.file.Files.createTempDirectory("jkubeterm-wiki");
        var file = dir.resolve("practices.md");
        var custom = new PracticeRegistry.Practice("pinned-image", java.util.Set.of("Pod"),
            ClusterAdvisor.Severity.CRITICAL, "Custom title", java.util.List.of("https://example.com/x"),
            "custom why", "custom fix", false);
        PracticeRegistry.saveUserFile(file, java.util.List.of(custom));
        var reparsed = PracticeRegistry.parse(java.nio.file.Files.readString(file));
        assertEquals(1, reparsed.size());
        assertEquals("custom fix", reparsed.getFirst().fix());
        assertEquals("https://example.com/x", reparsed.getFirst().docs().getFirst());
        assertEquals(ClusterAdvisor.Severity.CRITICAL, reparsed.getFirst().severity());
    }

    @Test void highlightsParse() {
        var highlights = PracticeRegistry.highlights();
        assertFalse(highlights.isEmpty());
        assertFalse(highlights.getFirst().version().isBlank());
        assertFalse(highlights.getFirst().docs().isEmpty());
    }

    @Test void everyKindHasRichCoverage() {
        var all = PracticeRegistry.loadBundled();
        for (String kind : java.util.List.of("Pod", "Deployment", "StatefulSet", "DaemonSet",
                "Service", "ConfigMap", "Job", "CronJob", "Ingress", "PersistentVolumeClaim",
                "Event", "Node", "Namespace", "PersistentVolume")) {
            long count = PracticeRegistry.forKind(all, kind).size();
            assertTrue(count >= 15, kind + " has only " + count + " practices");
        }
    }

    @Test void forKindFilters() {
        var all = PracticeRegistry.loadBundled();
        assertTrue(PracticeRegistry.forKind(all, "Pod").stream().anyMatch(p -> p.id().equals("pod-init-containers")));
        assertTrue(PracticeRegistry.forKind(all, "Ingress").stream().noneMatch(p -> p.id().equals("pod-init-containers")));
        assertTrue(PracticeRegistry.forKind(all, "ConfigMap").stream().anyMatch(p -> p.id().equals("cm-size-limits")));
    }
}
