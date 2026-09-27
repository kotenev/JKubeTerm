package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.NodeBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DaemonSetBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.StatefulSetBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResourceInspectorTest {
    private static String value(ResourceInspector.Inspection inspection, String section, String field) {
        return inspection.sections().stream().filter(s -> s.title().equals(section)).flatMap(s -> s.rows().stream())
            .filter(r -> r.field().equals(field) || r.field().startsWith(field + " ")).map(ResourceInspector.Row::value).findFirst().orElse(null);
    }

    private static boolean hasSection(ResourceInspector.Inspection inspection, String prefix) {
        return inspection.sections().stream().anyMatch(s -> s.title().startsWith(prefix));
    }

    @Test void podParsesContainersVolumesAndStatus() {
        var pod = new PodBuilder()
            .withNewMetadata().withName("web-0").withNamespace("demo").withLabels(Map.of("app", "web")).endMetadata()
            .withNewSpec()
            .addNewContainer().withName("nginx").withImage("nginx:1.27").addNewPort().withContainerPort(80).endPort()
            .addNewEnv().withName("MODE").withValue("prod").endEnv()
            .addToVolumeMounts(new io.fabric8.kubernetes.api.model.VolumeMountBuilder().withName("cfg").withMountPath("/etc/cfg").build())
            .endContainer()
            .addToVolumes(new io.fabric8.kubernetes.api.model.VolumeBuilder().withName("cfg").withNewConfigMap().withName("demo-config").endConfigMap().build())
            .endSpec()
            .withNewStatus().withPhase("Running").withPodIP("10.0.0.7").endStatus()
            .build();
        var inspection = ResourceInspector.inspect(pod);
        assertEquals("Running", value(inspection, "Status", "phase"));
        assertEquals("10.0.0.7", value(inspection, "Status", "podIP"));
        assertTrue(hasSection(inspection, "Container: nginx"));
        assertEquals("nginx:1.27", value(inspection, "Container: nginx", "image"));
        assertTrue(inspection.relations().stream().anyMatch(r -> r.from().equals("web-0") && r.to().equals("demo-config")));
    }

    @Test void deploymentParsesReplicasStrategyAndTemplate() {
        var deploy = new DeploymentBuilder()
            .withNewMetadata().withName("web").withNamespace("demo").endMetadata()
            .withNewSpec().withReplicas(3)
            .withNewSelector().withMatchLabels(Map.of("app", "web")).endSelector()
            .withNewTemplate().withNewMetadata().withLabels(Map.of("app", "web")).endMetadata()
            .withNewSpec().addNewContainer().withName("nginx").withImage("nginx:1.27").endContainer().endSpec().endTemplate()
            .endSpec()
            .withNewStatus().withReadyReplicas(3).withAvailableReplicas(3).endStatus()
            .build();
        var inspection = ResourceInspector.inspect(deploy);
        assertEquals("3", value(inspection, "Deployment spec", "replicas"));
        assertEquals("3", value(inspection, "Status", "ready"));
        assertTrue(hasSection(inspection, "Container: nginx"));
    }

    @Test void statefulSetParsesServiceNameAndClaims() {
        var sts = new StatefulSetBuilder()
            .withNewMetadata().withName("db").withNamespace("demo").endMetadata()
            .withNewSpec().withReplicas(1).withServiceName("db-hs")
            .withNewSelector().withMatchLabels(Map.of("app", "db")).endSelector()
            .withNewTemplate().withNewMetadata().withLabels(Map.of("app", "db")).endMetadata()
            .withNewSpec().addNewContainer().withName("db").withImage("postgres:16").endContainer().endSpec().endTemplate()
            .endSpec().build();
        var inspection = ResourceInspector.inspect(sts);
        assertEquals("db-hs", value(inspection, "StatefulSet spec", "serviceName"));
        assertTrue(inspection.relations().stream().anyMatch(r -> r.from().equals("db") && r.to().equals("db-hs")));
    }

    @Test void daemonSetParsesStatus() {
        var ds = new DaemonSetBuilder()
            .withNewMetadata().withName("agent").endMetadata()
            .withNewStatus().withDesiredNumberScheduled(1).withNumberReady(1).endStatus()
            .build();
        var inspection = ResourceInspector.inspect(ds);
        assertEquals("1", value(inspection, "Status", "desired"));
        assertEquals("1", value(inspection, "Status", "ready"));
    }

    @Test void serviceParsesPortsAndSelectors() {
        var svc = new ServiceBuilder()
            .withNewMetadata().withName("web").withNamespace("demo").endMetadata()
            .withNewSpec().withType("ClusterIP").withClusterIP("10.96.0.5")
            .withSelector(Map.of("app", "web"))
            .addNewPort().withName("http").withPort(80).withNewTargetPort(80).endPort()
            .endSpec().build();
        var inspection = ResourceInspector.inspect(svc);
        assertEquals("ClusterIP", value(inspection, "Service spec", "type"));
        assertNotNull(value(inspection, "Service spec", "port http"));
    }

    @Test void configMapParsesData() {
        var cm = new ConfigMapBuilder().withNewMetadata().withName("cfg").withNamespace("demo").endMetadata()
            .withData(Map.of("key", "value")).build();
        var inspection = ResourceInspector.inspect(cm);
        assertEquals("value", value(inspection, "Data", "key key"));
    }

    @Test void jobAndCronJobParse() {
        var job = new JobBuilder().withNewMetadata().withName("migrate").endMetadata()
            .withNewSpec().withCompletions(1).withBackoffLimit(3)
            .withNewTemplate().withNewSpec().withRestartPolicy("Never")
            .addNewContainer().withName("m").withImage("busybox:1.36").endContainer().endSpec().endTemplate().endSpec()
            .withNewStatus().withSucceeded(1).endStatus().build();
        assertEquals("1", value(ResourceInspector.inspect(job), "Job spec", "completions"));
        var cron = new CronJobBuilder().withNewMetadata().withName("report").endMetadata()
            .withNewSpec().withSchedule("0 * * * *")
            .withNewJobTemplate().withNewSpec().withNewTemplate().withNewSpec().withRestartPolicy("OnFailure")
            .addNewContainer().withName("r").withImage("busybox:1.36").endContainer().endSpec().endTemplate().endSpec().endJobTemplate().endSpec().build();
        assertEquals("0 * * * *", value(ResourceInspector.inspect(cron), "CronJob spec", "schedule"));
    }

    @Test void ingressParsesRulesAndTls() {
        var ing = new IngressBuilder().withNewMetadata().withName("demo").withNamespace("demo").endMetadata()
            .withNewSpec().withIngressClassName("nginx")
            .addNewRule().withHost("example.com").withNewHttp().addNewPath().withPath("/").withPathType("Prefix")
            .withNewBackend().withNewService().withName("web").withNewPort().withNumber(80).endPort().endService().endBackend().endPath().endHttp().endRule()
            .addToTls(new io.fabric8.kubernetes.api.model.networking.v1.IngressTLSBuilder().withHosts("example.com").withSecretName("demo-tls").build())
            .endSpec().build();
        var inspection = ResourceInspector.inspect(ing);
        assertEquals("nginx", value(inspection, "Ingress spec", "class"));
        assertTrue(inspection.relations().stream().anyMatch(r -> r.from().equals("demo") && r.to().equals("web")));
    }

    @Test void pvcAndPvParseAndRelate() {
        var pvc = new PersistentVolumeClaimBuilder().withNewMetadata().withName("data").withNamespace("demo").endMetadata()
            .withNewSpec().withAccessModes("ReadWriteOnce").withStorageClassName("standard")
            .withNewResources().addToRequests("storage", new Quantity("1Gi")).endResources().endSpec()
            .withNewStatus().withPhase("Bound").endStatus().build();
        var inspection = ResourceInspector.inspect(pvc);
        assertEquals("Bound", value(inspection, "Status", "phase"));
        assertEquals("standard", value(inspection, "Claim spec", "storageClass"));
        var pv = new PersistentVolumeBuilder().withNewMetadata().withName("pv-1").endMetadata()
            .withNewSpec().withStorageClassName("standard")
            .withNewClaimRef().withName("data").withNamespace("demo").endClaimRef().endSpec()
            .withNewStatus().withPhase("Bound").endStatus().build();
        assertTrue(ResourceInspector.inspect(pv).relations().stream().anyMatch(r -> r.from().equals("pv-1") && r.to().equals("data")));
    }

    @Test void nodeNamespaceEventParse() {
        var node = new NodeBuilder().withNewMetadata().withName("minikube").endMetadata()
            .withNewStatus().addNewAddress().withType("InternalIP").withAddress("192.168.1.2").endAddress()
            .addNewCondition().withType("Ready").withStatus("True").endCondition().endStatus().build();
        assertTrue(hasSection(ResourceInspector.inspect(node), "Node status"));
        var ns = new NamespaceBuilder().withNewMetadata().withName("demo").endMetadata()
            .withNewStatus().withPhase("Active").endStatus().build();
        assertEquals("Active", value(ResourceInspector.inspect(ns), "Status", "phase"));
        var event = new io.fabric8.kubernetes.api.model.EventBuilder()
            .withNewMetadata().withName("e1").withNamespace("demo").endMetadata()
            .withType("Warning").withReason("BackOff").withMessage("restarting").withCount(3)
            .withNewInvolvedObject().withKind("Pod").withName("web-0").endInvolvedObject().build();
        var inspection = ResourceInspector.inspect(event);
        assertEquals("BackOff", value(inspection, "Event", "reason"));
        assertTrue(inspection.relations().stream().anyMatch(r -> r.to().equals("web-0")));
    }

    @Test void adjacencySkipsEmptyEndpoints() {
        var inspection = new ResourceInspector.Inspection(List.of(),
            List.of(new ResourceInspector.Relation("a", "", "x"), new ResourceInspector.Relation("", "b", "y")));
        assertTrue(ResourceInspector.adjacency(inspection).isEmpty());
    }

    @Test void secretValuesAreNeverExposed() {
        var secret = new io.fabric8.kubernetes.api.model.SecretBuilder()
            .withNewMetadata().withName("s").withNamespace("demo").endMetadata()
            .withType("Opaque").withData(Map.of("password", "czNjcjN0")).build();
        var inspection = ResourceInspector.inspect(secret);
        String all = inspection.sections().stream().flatMap(s -> s.rows().stream())
            .map(r -> r.field() + "=" + r.value()).reduce("", (a, b) -> a + "\n" + b);
        assertFalse(all.contains("czNjcjN0"), "secret payload must never appear");
        assertTrue(all.contains("keys=1") || all.contains("password"));
    }
}
