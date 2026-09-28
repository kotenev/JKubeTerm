package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

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
    private static io.fabric8.kubernetes.api.model.PodSpec bareSpec(String name) {
        return new PodBuilder().withNewMetadata().withName(name).endMetadata()
            .withNewSpec().addNewContainer().withName("c").withImage("nginx").endContainer().endSpec().build().getSpec();
    }

    @Test void barePodTriggersCoreFindings() {
        var pod = new PodBuilder().withNewMetadata().withName("p").withNamespace("demo").endMetadata()
            .withNewSpec().addNewContainer().withName("c").withImage("nginx").endContainer().endSpec().build();
        var checks = ClusterAdvisor.advise(pod).stream().map(ClusterAdvisor.Finding::check).toList();
        assertTrue(checks.contains("readinessProbe"));
        assertTrue(checks.contains("resources"));
        assertTrue(checks.contains("runAsNonRoot"));
        assertTrue(checks.contains("image-tag"));
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
        assertTrue(ClusterAdvisor.advise(deploy).stream().anyMatch(f -> f.check().equals("replicas")));
    }

    @Test void serviceWithoutSelectorWarns() {
        var svc = new ServiceBuilder().withNewMetadata().withName("s").endMetadata()
            .withNewSpec().withType("ClusterIP").endSpec().build();
        assertTrue(ClusterAdvisor.advise(svc).stream().anyMatch(f -> f.check().equals("selector")));
    }

    @Test void ingressWithoutTlsWarns() {
        var ing = new IngressBuilder().withNewMetadata().withName("i").endMetadata()
            .withNewSpec().endSpec().build();
        assertTrue(ClusterAdvisor.advise(ing).stream().anyMatch(f -> f.check().equals("tls")));
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
        assertTrue(ClusterAdvisor.advise(cron).stream().anyMatch(f -> f.check().equals("suspend")));
    }

    @Test void explainHoodMentionsNodeAndServiceAccount() {
        var pod = new PodBuilder().withNewMetadata().withName("p").endMetadata()
            .withNewSpec().withNodeName("minikube").withServiceAccountName("web-sa").withRestartPolicy("Always")
            .addNewContainer().withName("c").withImage("i").endContainer().endSpec().build();
        String hood = ClusterAdvisor.explainHood(pod);
        assertTrue(hood.contains("minikube") && hood.contains("web-sa"));
    }

    @Test void kindsWithoutChecksReturnEmpty() {
        var cm = new ConfigMapBuilder().withNewMetadata().withName("c").endMetadata().build();
        assertTrue(ClusterAdvisor.advise(cm).isEmpty());
    }
}
