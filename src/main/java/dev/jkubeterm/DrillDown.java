package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.Volume;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Drill-down navigation: from any selected resource to the resources it relates to.
 * {@link #targets(HasMetadata)} is pure (no I/O) and proposes navigation targets;
 * {@link #resolve(KubernetesService, Target, String)} runs the API calls behind a
 * target on the worker thread. Pure data + static resolvers, no JavaFX — unit tested.
 */
public final class DrillDown {
    private DrillDown() {}

    public enum Mode {
        EXACT_NAME,
        BY_LABELS,
        BY_NAME_PREFIX,
        OWNED_BY,
        USED_BY,
        BACKEND,
        ON_NODE,
        ABOUT,
        SELECTING,
        NAMESPACE_SWITCH,
        FIND_BY_NAME
    }

    public record Target(String title, String description, Mode mode, ResourceKind kind,
                         String namespace, String exactName, Map<String, String> matchLabels,
                         String namePrefix, String ref, String usage) {
        @Override public String toString() { return title; }

        public static Target exact(String title, String description, ResourceKind kind, String namespace, String exactName) {
            return new Target(title, description, Mode.EXACT_NAME, kind, namespace, exactName, null, null, null, null);
        }
        public static Target byLabels(String title, String description, ResourceKind kind, String namespace, Map<String, String> matchLabels) {
            return new Target(title, description, Mode.BY_LABELS, kind, namespace, null, matchLabels, null, null, null);
        }
        public static Target namePrefix(String title, String description, ResourceKind kind, String namespace, String namePrefix) {
            return new Target(title, description, Mode.BY_NAME_PREFIX, kind, namespace, null, null, namePrefix, null, null);
        }
        public static Target ownedBy(String title, String description, ResourceKind kind, String namespace, String ownerName) {
            return new Target(title, description, Mode.OWNED_BY, kind, namespace, null, null, null, ownerName, null);
        }
        public static Target usedBy(String title, String description, String namespace, String usage, String usageName) {
            return new Target(title, description, Mode.USED_BY, null, namespace, null, null, null, usageName, usage);
        }
        public static Target backend(String title, String description, String namespace, String serviceName) {
            return new Target(title, description, Mode.BACKEND, ResourceKind.INGRESSES, namespace, null, null, null, serviceName, null);
        }
        public static Target onNode(String title, String description, String nodeName) {
            return new Target(title, description, Mode.ON_NODE, ResourceKind.PODS, null, null, null, null, nodeName, null);
        }
        public static Target about(String title, String description, ResourceKind kind, String namespace, String involvedKind, String involvedName) {
            return new Target(title, description, Mode.ABOUT, kind, namespace, involvedName, null, null, involvedKind, null);
        }
        public static Target selecting(String title, String description, String namespace, Map<String, String> podLabels) {
            return new Target(title, description, Mode.SELECTING, ResourceKind.SERVICES, namespace, null, podLabels, null, null, null);
        }
        public static Target namespaceSwitch(String namespace) {
            return new Target("Pods in '" + namespace + "'", "Switch the Namespace combo to '" + namespace + "' and list Pods.",
                Mode.NAMESPACE_SWITCH, ResourceKind.PODS, namespace, null, null, null, null, null);
        }
        public static Target findByName(String title, String description, String namespace, String name) {
            return new Target(title, description, Mode.FIND_BY_NAME, null, namespace, name, null, null, null, null);
        }
    }

    public record Found(ResourceKind kind, HasMetadata item) {}

    public static List<Target> targets(HasMetadata item) {
        List<Target> targets = new ArrayList<>();
        if (item == null || item.getMetadata() == null) return targets;
        String name = item.getMetadata().getName();
        String ns = item.getMetadata().getNamespace();
        if (name == null) return targets;
        switch (item) {
            case io.fabric8.kubernetes.api.model.Pod pod -> {
                if (pod.getSpec() != null && pod.getSpec().getNodeName() != null)
                    targets.add(Target.exact("Node '" + pod.getSpec().getNodeName() + "'", "The node this Pod runs on.",
                        ResourceKind.NODES, null, pod.getSpec().getNodeName()));
                targets.add(Target.about("Events about this Pod", "Events whose involved object is this Pod.",
                    ResourceKind.EVENTS, ns, "Pod", name));
                Map<String, String> labels = nullSafe(item.getMetadata().getLabels());
                if (!labels.isEmpty())
                    targets.add(Target.selecting("Services selecting this Pod", "Services whose selector matches this Pod's labels.", ns, labels));
                if (pod.getSpec() != null) {
                    for (String cm : configMapRefs(pod.getSpec()))
                        targets.add(Target.exact("ConfigMap '" + cm + "'", "ConfigMap mounted or referenced by this Pod.",
                            ResourceKind.CONFIG_MAPS, ns, cm));
                    for (String claim : pvcRefs(pod.getSpec()))
                        targets.add(Target.exact("PVC '" + claim + "'", "Claim mounted by this Pod.",
                            ResourceKind.PERSISTENT_VOLUME_CLAIMS, ns, claim));
                    if (labels.containsKey("job-name"))
                        targets.add(Target.exact("Owning Job '" + labels.get("job-name") + "'", "Job that created this Pod.",
                            ResourceKind.JOBS, ns, labels.get("job-name")));
                }
            }
            case io.fabric8.kubernetes.api.model.apps.Deployment deploy -> {
                Map<String, String> selector = selectorOf(deploy.getSpec() == null ? null : deploy.getSpec().getSelector());
                if (!selector.isEmpty())
                    targets.add(Target.byLabels("Pods of this Deployment", "Pods whose labels match the Deployment selector.", ResourceKind.PODS, ns, selector));
                targets.add(Target.about("Events about this Deployment", "Events whose involved object is this Deployment.",
                    ResourceKind.EVENTS, ns, "Deployment", name));
            }
            case io.fabric8.kubernetes.api.model.apps.StatefulSet sts -> {
                Map<String, String> selector = selectorOf(sts.getSpec() == null ? null : sts.getSpec().getSelector());
                if (!selector.isEmpty())
                    targets.add(Target.byLabels("Pods of this StatefulSet", "Pods whose labels match the StatefulSet selector.", ResourceKind.PODS, ns, selector));
                if (sts.getSpec() != null && sts.getSpec().getServiceName() != null)
                    targets.add(Target.exact("Headless Service '" + sts.getSpec().getServiceName() + "'", "Governing service of this StatefulSet.",
                        ResourceKind.SERVICES, ns, sts.getSpec().getServiceName()));
                targets.add(Target.namePrefix("PVCs of this StatefulSet", "Claims named data-" + name + "-N.",
                    ResourceKind.PERSISTENT_VOLUME_CLAIMS, ns, "data-" + name + "-"));
                targets.add(Target.about("Events about this StatefulSet", "Events whose involved object is this StatefulSet.",
                    ResourceKind.EVENTS, ns, "StatefulSet", name));
            }
            case io.fabric8.kubernetes.api.model.apps.DaemonSet ds -> {
                Map<String, String> selector = selectorOf(ds.getSpec() == null ? null : ds.getSpec().getSelector());
                if (!selector.isEmpty())
                    targets.add(Target.byLabels("Pods of this DaemonSet", "Pods whose labels match the DaemonSet selector.", ResourceKind.PODS, ns, selector));
                targets.add(Target.about("Events about this DaemonSet", "Events whose involved object is this DaemonSet.",
                    ResourceKind.EVENTS, ns, "DaemonSet", name));
            }
            case io.fabric8.kubernetes.api.model.Service svc -> {
                Map<String, String> selector = nullSafe(svc.getSpec() == null ? null : svc.getSpec().getSelector());
                if (!selector.isEmpty())
                    targets.add(Target.byLabels("Pods behind this Service", "Pods whose labels match the Service selector.", ResourceKind.PODS, ns, selector));
                targets.add(Target.backend("Ingresses routing to this Service", "Ingresses with a backend pointing at this Service.", ns, name));
            }
            case io.fabric8.kubernetes.api.model.ConfigMap ignored ->
                targets.add(Target.usedBy("Workloads using this ConfigMap", "Pods, Deployments, StatefulSets, DaemonSets and Jobs referencing this ConfigMap.", ns, "configmap", name));
            case io.fabric8.kubernetes.api.model.batch.v1.Job job -> {
                targets.add(Target.byLabels("Pods of this Job", "Pods labelled job-name=" + name + ".", ResourceKind.PODS, ns, Map.of("job-name", name)));
                targets.add(Target.about("Events about this Job", "Events whose involved object is this Job.",
                    ResourceKind.EVENTS, ns, "Job", name));
            }
            case io.fabric8.kubernetes.api.model.batch.v1.CronJob cron -> {
                targets.add(Target.ownedBy("Jobs of this CronJob", "Jobs owned by this CronJob.", ResourceKind.JOBS, ns, name));
                targets.add(Target.about("Events about this CronJob", "Events whose involved object is this CronJob.",
                    ResourceKind.EVENTS, ns, "CronJob", name));
            }
            case io.fabric8.kubernetes.api.model.networking.v1.Ingress ing -> {
                for (String svcName : backendServices(ing))
                    targets.add(Target.exact("Backend Service '" + svcName + "'", "Service this Ingress routes to.",
                        ResourceKind.SERVICES, ns, svcName));
            }
            case io.fabric8.kubernetes.api.model.PersistentVolumeClaim pvc -> {
                if (pvc.getSpec() != null && pvc.getSpec().getVolumeName() != null)
                    targets.add(Target.exact("Bound PV '" + pvc.getSpec().getVolumeName() + "'", "PersistentVolume this claim is bound to.",
                        ResourceKind.PERSISTENT_VOLUMES, null, pvc.getSpec().getVolumeName()));
                targets.add(Target.usedBy("Pods mounting this PVC", "Pods with a volume for this claim.", ns, "pvc", name));
            }
            case io.fabric8.kubernetes.api.model.PersistentVolume pv -> {
                if (pv.getSpec() != null && pv.getSpec().getClaimRef() != null && pv.getSpec().getClaimRef().getName() != null)
                    targets.add(Target.exact("Bound PVC '" + pv.getSpec().getClaimRef().getName() + "'", "Claim this volume is bound to.",
                        ResourceKind.PERSISTENT_VOLUME_CLAIMS, pv.getSpec().getClaimRef().getNamespace(), pv.getSpec().getClaimRef().getName()));
            }
            case io.fabric8.kubernetes.api.model.Node ignored ->
                targets.add(Target.onNode("Pods on this node", "Pods in the current namespace scheduled on this node.", name));
            case io.fabric8.kubernetes.api.model.Namespace ignored ->
                targets.add(Target.namespaceSwitch(name));
            case io.fabric8.kubernetes.api.model.Event event -> {
                if (event.getInvolvedObject() != null && event.getInvolvedObject().getName() != null) {
                    String involvedKind = event.getInvolvedObject().getKind();
                    Optional<ResourceKind> mapped = kindForKindName(involvedKind);
                    if (mapped.isPresent())
                        targets.add(Target.about(mapped.get().label + " '" + event.getInvolvedObject().getName() + "'",
                            "The object this Event is about.", mapped.get(), ns, involvedKind, event.getInvolvedObject().getName()));
                }
            }
            default -> { /* Secrets and other kinds: no drill targets */ }
        }
        return targets;
    }

    public static List<HasMetadata> resolve(KubernetesService service, Target target, String currentNamespace) {
        String ns = target.namespace() != null ? target.namespace() : service.namespaceOrDefault(currentNamespace);
        return switch (target.mode()) {
            case EXACT_NAME -> findExact(service, target.kind(), ns, target.exactName()).map(List::of).orElse(List.of());
            case BY_LABELS -> service.list(target.kind(), ns).stream()
                .filter(i -> matchesLabels(labelsOf(i), target.matchLabels())).map(i -> (HasMetadata) i).toList();
            case BY_NAME_PREFIX -> service.list(target.kind(), ns).stream()
                .filter(i -> i.getMetadata() != null && i.getMetadata().getName() != null && i.getMetadata().getName().startsWith(target.namePrefix()))
                .map(i -> (HasMetadata) i).toList();
            case OWNED_BY -> service.list(target.kind(), ns).stream()
                .filter(i -> ownerNames(i).contains(target.ref())).map(i -> (HasMetadata) i).toList();
            case USED_BY -> usedBy(service, ns, target.usage(), target.ref());
            case BACKEND -> service.list(ResourceKind.INGRESSES, ns).stream()
                .filter(i -> i instanceof io.fabric8.kubernetes.api.model.networking.v1.Ingress ing && backendServices(ing).contains(target.ref()))
                .map(i -> (HasMetadata) i).toList();
            case ON_NODE -> service.list(ResourceKind.PODS, ns).stream()
                .filter(i -> i instanceof io.fabric8.kubernetes.api.model.Pod pod && pod.getSpec() != null && target.ref().equals(pod.getSpec().getNodeName()))
                .map(i -> (HasMetadata) i).toList();
            case ABOUT -> service.list(ResourceKind.EVENTS, ns).stream()
                .filter(i -> i instanceof io.fabric8.kubernetes.api.model.Event event && event.getInvolvedObject() != null
                    && target.ref().equals(event.getInvolvedObject().getKind()) && target.exactName().equals(event.getInvolvedObject().getName()))
                .map(i -> (HasMetadata) i).toList();
            case SELECTING -> service.list(ResourceKind.SERVICES, ns).stream()
                .filter(i -> i instanceof io.fabric8.kubernetes.api.model.Service svc && svc.getSpec() != null
                    && matchesLabels(target.matchLabels(), nullSafe(svc.getSpec().getSelector())))
                .map(i -> (HasMetadata) i).toList();
            case FIND_BY_NAME -> findByName(service, target.exactName(), ns).map(f -> List.of(f.item())).orElse(List.of());
            case NAMESPACE_SWITCH -> List.of();
        };
    }

    public static Optional<HasMetadata> findExact(KubernetesService service, ResourceKind kind, String namespace, String name) {
        if (kind == null || name == null) return Optional.empty();
        return service.list(kind, namespace).stream()
            .filter(i -> i.getMetadata() != null && name.equals(i.getMetadata().getName()))
            .map(i -> (HasMetadata) i).findFirst();
    }

    public static Optional<Found> findByName(KubernetesService service, String name, String namespace) {
        if (name == null) return Optional.empty();
        List<ResourceKind> order = List.of(ResourceKind.PODS, ResourceKind.DEPLOYMENTS, ResourceKind.STATEFUL_SETS,
            ResourceKind.DAEMON_SETS, ResourceKind.SERVICES, ResourceKind.CONFIG_MAPS, ResourceKind.JOBS,
            ResourceKind.CRON_JOBS, ResourceKind.INGRESSES, ResourceKind.PERSISTENT_VOLUME_CLAIMS, ResourceKind.EVENTS,
            ResourceKind.NODES, ResourceKind.NAMESPACES, ResourceKind.PERSISTENT_VOLUMES);
        for (ResourceKind kind : order) {
            Optional<HasMetadata> hit = findExact(service, kind, namespace, name);
            if (hit.isPresent()) return Optional.of(new Found(kind, hit.get()));
        }
        return Optional.empty();
    }

    public static Optional<ResourceKind> kindForKindName(String kind) {
        if (kind == null) return Optional.empty();
        return switch (kind) {
            case "Pod" -> Optional.of(ResourceKind.PODS);
            case "Deployment" -> Optional.of(ResourceKind.DEPLOYMENTS);
            case "StatefulSet" -> Optional.of(ResourceKind.STATEFUL_SETS);
            case "DaemonSet" -> Optional.of(ResourceKind.DAEMON_SETS);
            case "Service" -> Optional.of(ResourceKind.SERVICES);
            case "ConfigMap" -> Optional.of(ResourceKind.CONFIG_MAPS);
            case "Job" -> Optional.of(ResourceKind.JOBS);
            case "CronJob" -> Optional.of(ResourceKind.CRON_JOBS);
            case "Ingress" -> Optional.of(ResourceKind.INGRESSES);
            case "PersistentVolumeClaim" -> Optional.of(ResourceKind.PERSISTENT_VOLUME_CLAIMS);
            case "PersistentVolume" -> Optional.of(ResourceKind.PERSISTENT_VOLUMES);
            case "Node" -> Optional.of(ResourceKind.NODES);
            case "Namespace" -> Optional.of(ResourceKind.NAMESPACES);
            case "Event" -> Optional.of(ResourceKind.EVENTS);
            default -> Optional.empty();
        };
    }

    public static Optional<ResourceKind> relationTargetKind(String label) {
        if (label == null) return Optional.empty();
        return switch (label) {
            case "route" -> Optional.of(ResourceKind.SERVICES);
            case "mounts configmap" -> Optional.of(ResourceKind.CONFIG_MAPS);
            case "mounts pvc" -> Optional.of(ResourceKind.PERSISTENT_VOLUME_CLAIMS);
            case "bound to" -> Optional.of(ResourceKind.PERSISTENT_VOLUMES);
            case "claimed by" -> Optional.of(ResourceKind.PERSISTENT_VOLUME_CLAIMS);
            case "headless service" -> Optional.of(ResourceKind.SERVICES);
            default -> Optional.empty();
        };
    }

    public static boolean matchesLabels(Map<String, String> labels, Map<String, String> selector) {
        if (selector == null || selector.isEmpty()) return false;
        if (labels == null) return false;
        for (var entry : selector.entrySet())
            if (!entry.getValue().equals(labels.get(entry.getKey()))) return false;
        return true;
    }

    private static List<HasMetadata> usedBy(KubernetesService service, String namespace, String usage, String usageName) {
        List<ResourceKind> workloads = List.of(ResourceKind.PODS, ResourceKind.DEPLOYMENTS, ResourceKind.STATEFUL_SETS,
            ResourceKind.DAEMON_SETS, ResourceKind.JOBS, ResourceKind.CRON_JOBS);
        List<HasMetadata> matches = new ArrayList<>();
        for (ResourceKind kind : workloads)
            for (HasMetadata item : service.list(kind, namespace)) {
                PodSpec spec = podSpecOf(item);
                if (spec == null) continue;
                boolean hit = switch (usage) {
                    case "configmap" -> usesConfigMap(spec, usageName);
                    case "secret" -> usesSecret(spec, usageName);
                    case "pvc" -> usesPvc(spec, usageName);
                    default -> false;
                };
                if (hit) matches.add(item);
            }
        return matches;
    }

    static PodSpec podSpecOf(HasMetadata item) {
        return switch (item) {
            case io.fabric8.kubernetes.api.model.Pod pod -> pod.getSpec();
            case io.fabric8.kubernetes.api.model.apps.Deployment deploy ->
                deploy.getSpec() == null || deploy.getSpec().getTemplate() == null ? null : deploy.getSpec().getTemplate().getSpec();
            case io.fabric8.kubernetes.api.model.apps.StatefulSet sts ->
                sts.getSpec() == null || sts.getSpec().getTemplate() == null ? null : sts.getSpec().getTemplate().getSpec();
            case io.fabric8.kubernetes.api.model.apps.DaemonSet ds ->
                ds.getSpec() == null || ds.getSpec().getTemplate() == null ? null : ds.getSpec().getTemplate().getSpec();
            case io.fabric8.kubernetes.api.model.batch.v1.Job job ->
                job.getSpec() == null || job.getSpec().getTemplate() == null ? null : job.getSpec().getTemplate().getSpec();
            case io.fabric8.kubernetes.api.model.batch.v1.CronJob cron ->
                cron.getSpec() == null || cron.getSpec().getJobTemplate() == null || cron.getSpec().getJobTemplate().getSpec() == null
                    || cron.getSpec().getJobTemplate().getSpec().getTemplate() == null ? null
                    : cron.getSpec().getJobTemplate().getSpec().getTemplate().getSpec();
            default -> null;
        };
    }

    static boolean usesConfigMap(PodSpec spec, String name) {
        if (spec == null || name == null) return false;
        for (Volume volume : nullSafe(spec.getVolumes()))
            if (volume.getConfigMap() != null && name.equals(volume.getConfigMap().getName())) return true;
        for (Container container : allContainers(spec)) {
            for (EnvVar env : nullSafe(container.getEnv()))
                if (env.getValueFrom() != null && env.getValueFrom().getConfigMapKeyRef() != null
                    && name.equals(env.getValueFrom().getConfigMapKeyRef().getName())) return true;
            if (container.getEnvFrom() != null)
                for (var from : container.getEnvFrom())
                    if (from.getConfigMapRef() != null && name.equals(from.getConfigMapRef().getName())) return true;
        }
        return false;
    }

    static boolean usesSecret(PodSpec spec, String name) {
        if (spec == null || name == null) return false;
        for (Volume volume : nullSafe(spec.getVolumes()))
            if (volume.getSecret() != null && name.equals(volume.getSecret().getSecretName())) return true;
        for (Container container : allContainers(spec)) {
            for (EnvVar env : nullSafe(container.getEnv()))
                if (env.getValueFrom() != null && env.getValueFrom().getSecretKeyRef() != null
                    && name.equals(env.getValueFrom().getSecretKeyRef().getName())) return true;
            if (container.getEnvFrom() != null)
                for (var from : container.getEnvFrom())
                    if (from.getSecretRef() != null && name.equals(from.getSecretRef().getName())) return true;
        }
        return false;
    }

    static boolean usesPvc(PodSpec spec, String name) {
        if (spec == null || name == null) return false;
        for (Volume volume : nullSafe(spec.getVolumes()))
            if (volume.getPersistentVolumeClaim() != null && name.equals(volume.getPersistentVolumeClaim().getClaimName())) return true;
        return false;
    }

    private static List<Container> allContainers(PodSpec spec) {
        List<Container> all = new ArrayList<>(nullSafe(spec.getInitContainers()));
        all.addAll(nullSafe(spec.getContainers()));
        return all;
    }

    private static List<String> configMapRefs(PodSpec spec) {
        List<String> names = new ArrayList<>();
        for (Volume volume : nullSafe(spec.getVolumes()))
            if (volume.getConfigMap() != null && volume.getConfigMap().getName() != null) names.add(volume.getConfigMap().getName());
        for (Container container : allContainers(spec)) {
            for (EnvVar env : nullSafe(container.getEnv()))
                if (env.getValueFrom() != null && env.getValueFrom().getConfigMapKeyRef() != null
                    && env.getValueFrom().getConfigMapKeyRef().getName() != null)
                    names.add(env.getValueFrom().getConfigMapKeyRef().getName());
            if (container.getEnvFrom() != null)
                for (var from : container.getEnvFrom())
                    if (from.getConfigMapRef() != null && from.getConfigMapRef().getName() != null)
                        names.add(from.getConfigMapRef().getName());
        }
        return names.stream().distinct().toList();
    }

    private static List<String> pvcRefs(PodSpec spec) {
        return nullSafe(spec.getVolumes()).stream()
            .filter(v -> v.getPersistentVolumeClaim() != null && v.getPersistentVolumeClaim().getClaimName() != null)
            .map(v -> v.getPersistentVolumeClaim().getClaimName()).distinct().toList();
    }

    private static List<String> backendServices(io.fabric8.kubernetes.api.model.networking.v1.Ingress ing) {
        List<String> names = new ArrayList<>();
        if (ing.getSpec() == null) return names;
        if (ing.getSpec().getDefaultBackend() != null && ing.getSpec().getDefaultBackend().getService() != null
            && ing.getSpec().getDefaultBackend().getService().getName() != null)
            names.add(ing.getSpec().getDefaultBackend().getService().getName());
        for (var rule : nullSafe(ing.getSpec().getRules())) {
            if (rule.getHttp() == null) continue;
            for (var path : nullSafe(rule.getHttp().getPaths()))
                if (path.getBackend() != null && path.getBackend().getService() != null
                    && path.getBackend().getService().getName() != null)
                    names.add(path.getBackend().getService().getName());
        }
        return names.stream().distinct().toList();
    }

    private static Map<String, String> selectorOf(LabelSelector selector) {
        return selector == null ? Map.of() : nullSafe(selector.getMatchLabels());
    }

    private static Map<String, String> labelsOf(HasMetadata item) {
        return item.getMetadata() == null ? Map.of() : nullSafe(item.getMetadata().getLabels());
    }

    private static List<String> ownerNames(HasMetadata item) {
        if (item.getMetadata() == null || item.getMetadata().getOwnerReferences() == null) return List.of();
        return item.getMetadata().getOwnerReferences().stream()
            .map(o -> o.getName()).filter(n -> n != null).toList();
    }

    private static <T> List<T> nullSafe(List<T> list) { return list == null ? List.of() : list; }

    private static <K, V> Map<K, V> nullSafe(Map<K, V> map) { return map == null ? Map.of() : map; }

    public static Map<String, List<String>> describeTargets(HasMetadata item) {
        Map<String, List<String>> summary = new LinkedHashMap<>();
        for (Target target : targets(item))
            summary.computeIfAbsent(target.mode().name(), key -> new ArrayList<>()).add(target.title());
        return summary;
    }
}
