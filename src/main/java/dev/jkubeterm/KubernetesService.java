package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** All network access happens on the UI's worker executor, never on the JavaFX thread. */
public final class KubernetesService implements AutoCloseable {
    private final KubernetesClient client;
    private final KubeconfigLoader.ContextRef context;
    public KubernetesService(KubeconfigLoader.ContextRef context) throws IOException {
        this.context = Objects.requireNonNull(context);
        Config config = KubeconfigLoader.config(context);
        // TLS verification remains enabled; CA and client certificates come from kubeconfig.
        this.client = new KubernetesClientBuilder().withConfig(config).build();
    }
    public KubeconfigLoader.ContextRef context() { return context; }
    public String namespaceOrDefault(String namespace) { return namespace == null || namespace.isBlank() ? "default" : namespace; }
    public List<String> namespaces() { return client.namespaces().list().getItems().stream().map(n -> n.getMetadata().getName()).sorted().toList(); }
    public String version() { return client.getKubernetesVersion().getGitVersion(); }
    public List<? extends HasMetadata> list(ResourceKind kind, String namespace) {
        String ns = namespaceOrDefault(namespace);
        return switch (kind) {
            case PODS -> client.pods().inNamespace(ns).list().getItems();
            case DEPLOYMENTS -> client.apps().deployments().inNamespace(ns).list().getItems();
            case STATEFUL_SETS -> client.apps().statefulSets().inNamespace(ns).list().getItems();
            case DAEMON_SETS -> client.apps().daemonSets().inNamespace(ns).list().getItems();
            case SERVICES -> client.services().inNamespace(ns).list().getItems();
            case CONFIG_MAPS -> client.configMaps().inNamespace(ns).list().getItems();
            case JOBS -> client.batch().v1().jobs().inNamespace(ns).list().getItems();
            case CRON_JOBS -> client.batch().v1().cronjobs().inNamespace(ns).list().getItems();
            case INGRESSES -> client.network().v1().ingresses().inNamespace(ns).list().getItems();
            case PERSISTENT_VOLUME_CLAIMS -> client.persistentVolumeClaims().inNamespace(ns).list().getItems();
            case EVENTS -> client.v1().events().inNamespace(ns).list().getItems();
            case NODES -> client.nodes().list().getItems();
            case NAMESPACES -> client.namespaces().list().getItems();
            case PERSISTENT_VOLUMES -> client.persistentVolumes().list().getItems();
        };
    }
    public String yaml(HasMetadata resource) { return Serialization.asYaml(resource); }
    public String logs(String namespace, String pod, String container, int tail) {
        var operation = client.pods().inNamespace(namespace).withName(pod).inContainer(container);
        return operation.tailingLines(Math.max(1, tail)).getLog();
    }
    public List<String> containers(String namespace, String pod) {
        var obj = client.pods().inNamespace(namespace).withName(pod).get();
        if (obj == null || obj.getSpec() == null) return List.of();
        return obj.getSpec().getContainers().stream().map(c -> c.getName()).toList();
    }
    public void delete(HasMetadata resource) { client.resource(resource).delete(); }
    public void apply(String yaml, String namespace) {
        HasMetadata obj = Serialization.unmarshal(yaml, HasMetadata.class);
        if (obj == null || obj.getMetadata() == null || obj.getMetadata().getName() == null)
            throw new IllegalArgumentException("YAML must have kind, apiVersion and metadata.name");
        if (obj.getKind() == null || obj.getApiVersion() == null)
            throw new IllegalArgumentException("YAML must have kind and apiVersion");
        if (obj.getMetadata().getNamespace() == null && !isClusterScoped(obj.getKind()))
            obj.getMetadata().setNamespace(namespaceOrDefault(namespace));
        // Explicitly selected edit/create operation; never executed on loading a resource.
        client.resource(obj).createOrReplace();
    }
    private static boolean isClusterScoped(String kind) {
        return List.of("Node", "Namespace", "PersistentVolume", "ClusterRole", "ClusterRoleBinding", "CustomResourceDefinition", "StorageClass").contains(kind);
    }
    public void scaleDeployment(String namespace, String name, int replicas) {
        if (replicas < 0) throw new IllegalArgumentException("Replicas cannot be negative");
        client.apps().deployments().inNamespace(namespace).withName(name).scale(replicas);
    }
    public void restartDeployment(String namespace, String name) {
        client.apps().deployments().inNamespace(namespace).withName(name).edit(d -> {
            if (d.getSpec() == null || d.getSpec().getTemplate() == null) throw new IllegalStateException("Missing pod template");
            if (d.getSpec().getTemplate().getMetadata() == null)
                d.getSpec().getTemplate().setMetadata(new io.fabric8.kubernetes.api.model.ObjectMeta());
            var meta = d.getSpec().getTemplate().getMetadata();
            if (meta.getAnnotations() == null) meta.setAnnotations(new java.util.HashMap<>());
            meta.getAnnotations().put("kubectl.kubernetes.io/restartedAt", Instant.now().toString());
            return d;
        });
    }
    @Override public void close() { client.close(); }
}
