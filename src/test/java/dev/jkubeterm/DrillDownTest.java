package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DrillDownTest {
    @Test void podTargetsNodeEventsServicesConfig() {
        var pod = new PodBuilder().withNewMetadata().withName("web-0").withNamespace("demo")
            .withLabels(Map.of("app", "web")).endMetadata()
            .withNewSpec().withNodeName("minikube")
            .addNewContainer().withName("c").withImage("nginx:1.27").endContainer()
            .addToVolumes(new io.fabric8.kubernetes.api.model.VolumeBuilder().withName("cfg")
                .withNewConfigMap().withName("demo-config").endConfigMap().build())
            .endSpec().build();
        var titles = DrillDown.targets(pod).stream().map(DrillDown.Target::title).toList();
        assertTrue(titles.stream().anyMatch(t -> t.contains("minikube")), "node");
        assertTrue(titles.stream().anyMatch(t -> t.contains("Events")), "events");
        assertTrue(titles.stream().anyMatch(t -> t.contains("Services selecting")), "services");
        assertTrue(titles.stream().anyMatch(t -> t.contains("ConfigMap")), "configmap");
    }

    @Test void deploymentTargetsPodsBySelector() {
        var deploy = new DeploymentBuilder().withNewMetadata().withName("web").withNamespace("demo").endMetadata()
            .withNewSpec().withNewSelector().withMatchLabels(Map.of("app", "web")).endSelector().endSpec().build();
        var target = DrillDown.targets(deploy).stream()
            .filter(t -> t.mode() == DrillDown.Mode.BY_LABELS).findFirst().orElseThrow();
        assertEquals(ResourceKind.PODS, target.kind());
        assertEquals(Map.of("app", "web"), target.matchLabels());
    }

    @Test void serviceTargetsPodsAndBackends() {
        var svc = new ServiceBuilder().withNewMetadata().withName("web").withNamespace("demo").endMetadata()
            .withNewSpec().withSelector(Map.of("app", "web")).endSpec().build();
        var modes = DrillDown.targets(svc).stream().map(DrillDown.Target::mode).toList();
        assertTrue(modes.contains(DrillDown.Mode.BY_LABELS));
        assertTrue(modes.contains(DrillDown.Mode.BACKEND));
    }

    @Test void ingressTargetsBackendServices() {
        var ing = new IngressBuilder().withNewMetadata().withName("demo").withNamespace("demo").endMetadata()
            .withNewSpec().addNewRule().withHost("h").withNewHttp().addNewPath().withPath("/").withPathType("Prefix")
            .withNewBackend().withNewService().withName("web").withNewPort().withNumber(80).endPort().endService().endBackend()
            .endPath().endHttp().endRule().endSpec().build();
        var target = DrillDown.targets(ing).stream().findFirst().orElseThrow();
        assertEquals(ResourceKind.SERVICES, target.kind());
        assertEquals("web", target.exactName());
    }

    @Test void pvcTargetsBoundPv() {
        var pvc = new PersistentVolumeClaimBuilder().withNewMetadata().withName("data").withNamespace("demo").endMetadata()
            .withNewSpec().withVolumeName("pv-1").endSpec().build();
        var target = DrillDown.targets(pvc).stream()
            .filter(t -> t.mode() == DrillDown.Mode.EXACT_NAME).findFirst().orElseThrow();
        assertEquals(ResourceKind.PERSISTENT_VOLUMES, target.kind());
        assertEquals("pv-1", target.exactName());
    }

    @Test void pvTargetsBoundPvc() {
        var pv = new PersistentVolumeBuilder().withNewMetadata().withName("pv-1").endMetadata()
            .withNewSpec().withNewClaimRef().withName("data").withNamespace("demo").endClaimRef().endSpec().build();
        var target = DrillDown.targets(pv).stream()
            .filter(t -> t.mode() == DrillDown.Mode.EXACT_NAME).findFirst().orElseThrow();
        assertEquals(ResourceKind.PERSISTENT_VOLUME_CLAIMS, target.kind());
        assertEquals("data", target.exactName());
        assertEquals("demo", target.namespace());
    }

    @Test void nodeNamespaceEventTargets() {
        var node = new NodeBuilder().withNewMetadata().withName("minikube").endMetadata().build();
        assertEquals(DrillDown.Mode.ON_NODE, DrillDown.targets(node).getFirst().mode());
        var ns = new NamespaceBuilder().withNewMetadata().withName("demo").endMetadata().build();
        assertEquals(DrillDown.Mode.NAMESPACE_SWITCH, DrillDown.targets(ns).getFirst().mode());
        var event = new EventBuilder().withNewMetadata().withName("e").withNamespace("demo").endMetadata()
            .withNewInvolvedObject().withKind("Pod").withName("web-0").endInvolvedObject().build();
        var target = DrillDown.targets(event).getFirst();
        assertEquals(ResourceKind.PODS, target.kind());
        assertEquals("web-0", target.exactName());
    }

    @Test void matchesLabelsRequiresFullSelector() {
        assertTrue(DrillDown.matchesLabels(Map.of("app", "web", "v", "1"), Map.of("app", "web")));
        assertFalse(DrillDown.matchesLabels(Map.of("app", "web"), Map.of("app", "other")));
        assertFalse(DrillDown.matchesLabels(Map.of("app", "web"), Map.of()));
        assertFalse(DrillDown.matchesLabels(null, Map.of("app", "web")));
    }

    @Test void kindForKindNameCoversCatalog() {
        assertEquals(ResourceKind.PODS, DrillDown.kindForKindName("Pod").orElseThrow());
        assertEquals(ResourceKind.PERSISTENT_VOLUMES, DrillDown.kindForKindName("PersistentVolume").orElseThrow());
        assertTrue(DrillDown.kindForKindName("Secret").isEmpty());
        assertTrue(DrillDown.kindForKindName(null).isEmpty());
    }

    @Test void relationTargetKindMapsGraphLabels() {
        assertEquals(ResourceKind.SERVICES, DrillDown.relationTargetKind("route").orElseThrow());
        assertEquals(ResourceKind.CONFIG_MAPS, DrillDown.relationTargetKind("mounts configmap").orElseThrow());
        assertEquals(ResourceKind.PERSISTENT_VOLUMES, DrillDown.relationTargetKind("bound to").orElseThrow());
        assertTrue(DrillDown.relationTargetKind("runs").isEmpty());
    }

    @Test void configMapTargetsWorkloads() {
        var cm = new ConfigMapBuilder().withNewMetadata().withName("cfg").withNamespace("demo").endMetadata().build();
        var target = DrillDown.targets(cm).getFirst();
        assertEquals(DrillDown.Mode.USED_BY, target.mode());
        assertEquals("configmap", target.usage());
    }

    @Test void cronJobTargetsOwnedJobs() {
        var cron = new io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder()
            .withNewMetadata().withName("report").withNamespace("demo").endMetadata().build();
        assertTrue(DrillDown.targets(cron).stream().anyMatch(t -> t.mode() == DrillDown.Mode.OWNED_BY));
    }

    @Test void podSpecOfExtractsTemplates() {
        var deploy = new DeploymentBuilder().withNewMetadata().withName("web").endMetadata()
            .withNewSpec().withNewTemplate().withNewSpec()
            .addNewContainer().withName("c").withImage("i").endContainer().endSpec().endTemplate().endSpec().build();
        assertNotNull(DrillDown.podSpecOf(deploy));
        assertNull(DrillDown.podSpecOf(new NodeBuilder().withNewMetadata().withName("n").endMetadata().build()));
    }

    @Test void usesConfigMapDetectsVolumeEnvAndEnvFrom() {
        var spec = new PodBuilder().withNewMetadata().withName("p").endMetadata()
            .withNewSpec()
            .addToVolumes(new io.fabric8.kubernetes.api.model.VolumeBuilder().withName("v")
                .withNewConfigMap().withName("cfg").endConfigMap().build())
            .addNewContainer().withName("c").withImage("i")
            .addNewEnv().withName("K").withNewValueFrom().withNewConfigMapKeyRef().withName("cfg2").withKey("k").endConfigMapKeyRef().endValueFrom().endEnv()
            .addNewEnvFrom().withNewConfigMapRef().withName("cfg3").endConfigMapRef().endEnvFrom()
            .endContainer().endSpec().build().getSpec();
        assertTrue(DrillDown.usesConfigMap(spec, "cfg"));
        assertTrue(DrillDown.usesConfigMap(spec, "cfg2"));
        assertTrue(DrillDown.usesConfigMap(spec, "cfg3"));
        assertFalse(DrillDown.usesConfigMap(spec, "other"));
    }
}
