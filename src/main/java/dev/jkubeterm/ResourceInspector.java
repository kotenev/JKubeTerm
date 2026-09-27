package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvFromSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.Taint;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeMount;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed object parser for Kubernetes manifests: converts the 14 browsable
 * {@link HasMetadata} types into ordered {@link Section}s of rows for the
 * Object view, plus cross-resource {@link Relation}s for the Graph view.
 * Pure data, no JavaFX — safe to unit test.
 */
public final class ResourceInspector {
    private ResourceInspector() {}

    public record Row(String field, String value) {}
    public record Section(String title, List<Row> rows) {
        public Section(String title) { this(title, new ArrayList<>()); }
    }
    public record Relation(String from, String to, String label) {}
    public record Inspection(List<Section> sections, List<Relation> relations) {}
    public record ExportableEntry(String kind, String name, String key, String value) {}

    public static Inspection inspect(HasMetadata resource) {
        List<Section> sections = new ArrayList<>();
        List<Relation> relations = new ArrayList<>();
        sections.add(header(resource));
        switch (resource) {
            case io.fabric8.kubernetes.api.model.Pod pod -> pod(pod, sections, relations);
            case io.fabric8.kubernetes.api.model.apps.Deployment deploy -> deployment(deploy, sections, relations);
            case io.fabric8.kubernetes.api.model.apps.StatefulSet sts -> statefulSet(sts, sections, relations);
            case io.fabric8.kubernetes.api.model.apps.DaemonSet ds -> daemonSet(ds, sections, relations);
            case io.fabric8.kubernetes.api.model.Service svc -> service(svc, sections, relations);
            case io.fabric8.kubernetes.api.model.ConfigMap cm -> configMap(cm, sections);
            case io.fabric8.kubernetes.api.model.Secret secret -> secret(secret, sections);
            case io.fabric8.kubernetes.api.model.batch.v1.Job job -> job(job, sections, relations);
            case io.fabric8.kubernetes.api.model.batch.v1.CronJob cron -> cronJob(cron, sections, relations);
            case io.fabric8.kubernetes.api.model.networking.v1.Ingress ing -> ingress(ing, sections, relations);
            case io.fabric8.kubernetes.api.model.PersistentVolumeClaim pvc -> pvc(pvc, sections, relations);
            case Event event -> event(event, sections, relations);
            case io.fabric8.kubernetes.api.model.Node node -> node(node, sections);
            case io.fabric8.kubernetes.api.model.Namespace ns -> namespace(ns, sections);
            case io.fabric8.kubernetes.api.model.PersistentVolume pv -> pv(pv, sections, relations);
            default -> sections.add(new Section("Details", List.of(new Row("kind", resource.getKind()))));
        }
        return new Inspection(List.copyOf(sections), List.copyOf(relations));
    }

    private static Section header(HasMetadata resource) {
        ObjectMeta meta = resource.getMetadata();
        Section section = new Section("Object");
        section.rows().add(new Row("kind", str(resource.getKind())));
        section.rows().add(new Row("apiVersion", str(resource.getApiVersion())));
        section.rows().add(new Row("name", meta == null ? "" : str(meta.getName())));
        if (meta != null && meta.getNamespace() != null) section.rows().add(new Row("namespace", meta.getNamespace()));
        if (meta != null && meta.getCreationTimestamp() != null) section.rows().add(new Row("created", meta.getCreationTimestamp()));
        if (meta != null) {
            owners(meta, section);
            mapRows(section, "label", meta.getLabels());
            mapRows(section, "annotation", meta.getAnnotations());
        }
        return section;
    }

    private static void pod(io.fabric8.kubernetes.api.model.Pod pod, List<Section> sections, List<Relation> relations) {
        String name = nameOf(pod);
        if (pod.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("phase", str(pod.getStatus().getPhase())));
            status.rows().add(new Row("podIP", str(pod.getStatus().getPodIP())));
            status.rows().add(new Row("hostIP", str(pod.getStatus().getHostIP())));
            status.rows().add(new Row("qosClass", str(pod.getStatus().getQosClass())));
            if (pod.getStatus().getMessage() != null) status.rows().add(new Row("message", pod.getStatus().getMessage()));
            if (pod.getStatus().getReason() != null) status.rows().add(new Row("reason", pod.getStatus().getReason()));
            for (PodCondition c : nullSafe(pod.getStatus().getConditions()))
                status.rows().add(new Row("condition " + c.getType(), c.getStatus() + suffix(c.getReason())));
            addIfRows(sections, status);
        }
        if (pod.getSpec() != null) {
            Section spec = new Section("Pod spec");
            spec.rows().add(new Row("nodeName", str(pod.getSpec().getNodeName())));
            spec.rows().add(new Row("restartPolicy", str(pod.getSpec().getRestartPolicy())));
            spec.rows().add(new Row("serviceAccount", str(pod.getSpec().getServiceAccountName())));
            addIfRows(sections, spec);
        }
        containerStatuses(pod, sections);
        podTemplateLike(null, pod.getSpec() == null ? null : pod.getSpec().getInitContainers(),
            pod.getSpec() == null ? null : pod.getSpec().getContainers(),
            pod.getSpec() == null ? null : pod.getSpec().getVolumes(), name, sections, relations);
    }

    private static void deployment(io.fabric8.kubernetes.api.model.apps.Deployment deploy, List<Section> sections, List<Relation> relations) {
        String name = nameOf(deploy);
        if (deploy.getSpec() != null) {
            Section spec = new Section("Deployment spec");
            spec.rows().add(new Row("replicas", str(deploy.getSpec().getReplicas())));
            if (deploy.getSpec().getStrategy() != null) {
                spec.rows().add(new Row("strategy", str(deploy.getSpec().getStrategy().getType())));
                if (deploy.getSpec().getStrategy().getRollingUpdate() != null) {
                    spec.rows().add(new Row("maxSurge", intOrString(deploy.getSpec().getStrategy().getRollingUpdate().getMaxSurge())));
                    spec.rows().add(new Row("maxUnavailable", intOrString(deploy.getSpec().getStrategy().getRollingUpdate().getMaxUnavailable())));
                }
            }
            selectorRows(spec, deploy.getSpec().getSelector());
            addIfRows(sections, spec);
        }
        if (deploy.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("replicas", str(deploy.getStatus().getReplicas())));
            status.rows().add(new Row("ready", str(deploy.getStatus().getReadyReplicas())));
            status.rows().add(new Row("available", str(deploy.getStatus().getAvailableReplicas())));
            status.rows().add(new Row("updated", str(deploy.getStatus().getUpdatedReplicas())));
            status.rows().add(new Row("unavailable", str(deploy.getStatus().getUnavailableReplicas())));
            addIfRows(sections, status);
        }
        if (deploy.getSpec() != null && deploy.getSpec().getTemplate() != null)
            podTemplate(deploy.getSpec().getTemplate().getMetadata(), deploy.getSpec().getTemplate().getSpec(), name, sections, relations);
    }

    private static void statefulSet(io.fabric8.kubernetes.api.model.apps.StatefulSet sts, List<Section> sections, List<Relation> relations) {
        String name = nameOf(sts);
        if (sts.getSpec() != null) {
            Section spec = new Section("StatefulSet spec");
            spec.rows().add(new Row("replicas", str(sts.getSpec().getReplicas())));
            spec.rows().add(new Row("serviceName", str(sts.getSpec().getServiceName())));
            selectorRows(spec, sts.getSpec().getSelector());
            for (var claim : nullSafe(sts.getSpec().getVolumeClaimTemplates()))
                spec.rows().add(new Row("claimTemplate", claim.getMetadata() == null ? "" : str(claim.getMetadata().getName())));
            addIfRows(sections, spec);
        }
        if (sts.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("replicas", str(sts.getStatus().getReplicas())));
            status.rows().add(new Row("ready", str(sts.getStatus().getReadyReplicas())));
            status.rows().add(new Row("current", str(sts.getStatus().getCurrentReplicas())));
            addIfRows(sections, status);
        }
        relations.add(new Relation(name, sts.getSpec() == null ? "" : str(sts.getSpec().getServiceName()), "headless service"));
        if (sts.getSpec() != null && sts.getSpec().getTemplate() != null)
            podTemplate(sts.getSpec().getTemplate().getMetadata(), sts.getSpec().getTemplate().getSpec(), name, sections, relations);
    }

    private static void daemonSet(io.fabric8.kubernetes.api.model.apps.DaemonSet ds, List<Section> sections, List<Relation> relations) {
        String name = nameOf(ds);
        if (ds.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("desired", str(ds.getStatus().getDesiredNumberScheduled())));
            status.rows().add(new Row("ready", str(ds.getStatus().getNumberReady())));
            status.rows().add(new Row("updated", str(ds.getStatus().getUpdatedNumberScheduled())));
            addIfRows(sections, status);
        }
        if (ds.getSpec() != null && ds.getSpec().getUpdateStrategy() != null) {
            Section strategy = new Section("Update strategy");
            strategy.rows().add(new Row("type", str(ds.getSpec().getUpdateStrategy().getType())));
            addIfRows(sections, strategy);
        }
        if (ds.getSpec() != null && ds.getSpec().getTemplate() != null)
            podTemplate(ds.getSpec().getTemplate().getMetadata(), ds.getSpec().getTemplate().getSpec(), name, sections, relations);
    }

    private static void service(io.fabric8.kubernetes.api.model.Service svc, List<Section> sections, List<Relation> relations) {
        String name = nameOf(svc);
        if (svc.getSpec() != null) {
            Section spec = new Section("Service spec");
            spec.rows().add(new Row("type", str(svc.getSpec().getType())));
            spec.rows().add(new Row("clusterIP", str(svc.getSpec().getClusterIP())));
            mapRows(spec, "selector", svc.getSpec().getSelector());
            for (var port : nullSafe(svc.getSpec().getPorts()))
                spec.rows().add(new Row("port " + port.getName(),
                    port.getPort() + "→" + targetPort(port.getTargetPort()) + suffix("nodePort " + port.getNodePort(), port.getNodePort() != null) + " " + str(port.getProtocol())));
            for (String ip : nullSafe(svc.getSpec().getExternalIPs())) spec.rows().add(new Row("externalIP", ip));
            addIfRows(sections, spec);
        }
        if (svc.getStatus() != null && svc.getStatus().getLoadBalancer() != null)
            for (var ingress : nullSafe(svc.getStatus().getLoadBalancer().getIngress()))
                relations.add(new Relation(name, str(ingress.getIp()) + str(ingress.getHostname()), "loadbalancer"));
    }

    private static void configMap(io.fabric8.kubernetes.api.model.ConfigMap cm, List<Section> sections) {
        Section data = new Section("Data");
        for (var entry : nullSafe(cm.getData()).entrySet()) {
            String hint = isPemCertificate(entry.getValue()) ? " — double-click to save" : "";
            data.rows().add(new Row("key " + entry.getKey(), preview(entry.getValue()) + hint));
        }
        data.rows().add(new Row("binaryKeys", String.valueOf(nullSafe(cm.getBinaryData()).size())));
        data.rows().add(new Row("immutable", str(cm.getImmutable())));
        addIfRows(sections, data);
    }

    private static void secret(io.fabric8.kubernetes.api.model.Secret secret, List<Section> sections) {
        Section data = new Section("Data (values hidden)");
        for (var entry : nullSafe(secret.getData()).entrySet())
            data.rows().add(new Row("key " + entry.getKey(), "secret (" + entry.getValue().length() + " chars base64)"));
        for (var entry : nullSafe(secret.getStringData()).entrySet())
            data.rows().add(new Row("key " + entry.getKey(), "stringData (" + entry.getValue().length() + " chars)"));
        data.rows().add(new Row("keys", String.valueOf(nullSafe(secret.getData()).size() + nullSafe(secret.getStringData()).size())));
        data.rows().add(new Row("type", str(secret.getType())));
        data.rows().add(new Row("immutable", str(secret.getImmutable())));
        addIfRows(sections, data);
    }

    private static void job(io.fabric8.kubernetes.api.model.batch.v1.Job job, List<Section> sections, List<Relation> relations) {
        String name = nameOf(job);
        if (job.getSpec() != null) {
            Section spec = new Section("Job spec");
            spec.rows().add(new Row("completions", str(job.getSpec().getCompletions())));
            spec.rows().add(new Row("parallelism", str(job.getSpec().getParallelism())));
            spec.rows().add(new Row("backoffLimit", str(job.getSpec().getBackoffLimit())));
            addIfRows(sections, spec);
        }
        if (job.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("active", str(job.getStatus().getActive())));
            status.rows().add(new Row("succeeded", str(job.getStatus().getSucceeded())));
            status.rows().add(new Row("failed", str(job.getStatus().getFailed())));
            addIfRows(sections, status);
        }
        if (job.getSpec() != null && job.getSpec().getTemplate() != null)
            podTemplate(job.getSpec().getTemplate().getMetadata(), job.getSpec().getTemplate().getSpec(), name, sections, relations);
    }

    private static void cronJob(io.fabric8.kubernetes.api.model.batch.v1.CronJob cron, List<Section> sections, List<Relation> relations) {
        if (cron.getSpec() != null) {
            Section spec = new Section("CronJob spec");
            spec.rows().add(new Row("schedule", str(cron.getSpec().getSchedule())));
            spec.rows().add(new Row("suspend", str(cron.getSpec().getSuspend())));
            spec.rows().add(new Row("concurrencyPolicy", str(cron.getSpec().getConcurrencyPolicy())));
            spec.rows().add(new Row("successfulHistory", str(cron.getSpec().getSuccessfulJobsHistoryLimit())));
            spec.rows().add(new Row("failedHistory", str(cron.getSpec().getFailedJobsHistoryLimit())));
            addIfRows(sections, spec);
        }
        if (cron.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("lastSchedule", str(cron.getStatus().getLastScheduleTime())));
            status.rows().add(new Row("active", String.valueOf(nullSafe(cron.getStatus().getActive()).size())));
            addIfRows(sections, status);
        }
        if (cron.getSpec() != null && cron.getSpec().getJobTemplate() != null && cron.getSpec().getJobTemplate().getSpec() != null
            && cron.getSpec().getJobTemplate().getSpec().getTemplate() != null)
            podTemplate(cron.getSpec().getJobTemplate().getSpec().getTemplate().getMetadata(),
                cron.getSpec().getJobTemplate().getSpec().getTemplate().getSpec(), nameOf(cron), sections, relations);
    }

    private static void ingress(io.fabric8.kubernetes.api.model.networking.v1.Ingress ing, List<Section> sections, List<Relation> relations) {
        String name = nameOf(ing);
        if (ing.getSpec() != null) {
            Section spec = new Section("Ingress spec");
            spec.rows().add(new Row("class", str(ing.getSpec().getIngressClassName())));
            backendRow(spec, "default", ing.getSpec().getDefaultBackend(), relations, name);
            for (var rule : nullSafe(ing.getSpec().getRules())) {
                String host = rule.getHost() == null ? "*" : rule.getHost();
                if (rule.getHttp() == null) { spec.rows().add(new Row("rule " + host, "")); continue; }
                for (var path : nullSafe(rule.getHttp().getPaths()))
                    backendRow(spec, host + " " + str(path.getPath()) + suffix("(" + path.getPathType() + ")", path.getPathType() != null),
                        path.getBackend(), relations, name);
            }
            for (var tls : nullSafe(ing.getSpec().getTls()))
                spec.rows().add(new Row("tls", String.join(",", nullSafe(tls.getHosts())) + " → " + str(tls.getSecretName())));
            addIfRows(sections, spec);
        }
        if (ing.getStatus() != null && ing.getStatus().getLoadBalancer() != null)
            for (var ingress : nullSafe(ing.getStatus().getLoadBalancer().getIngress()))
                relations.add(new Relation(name, str(ingress.getIp()) + str(ingress.getHostname()), "ingress"));
    }

    private static void backendRow(Section spec, String where, io.fabric8.kubernetes.api.model.networking.v1.IngressBackend backend,
                                   List<Relation> relations, String from) {
        if (backend == null || backend.getService() == null) { spec.rows().add(new Row("backend " + where, "")); return; }
        String svc = str(backend.getService().getName());
        String port = backend.getService().getPort() == null ? "" : str(backend.getService().getPort().getNumber());
        spec.rows().add(new Row("backend " + where, svc + suffix(":" + port, !port.isEmpty())));
        relations.add(new Relation(from, svc, "route"));
    }

    private static void pvc(io.fabric8.kubernetes.api.model.PersistentVolumeClaim pvc, List<Section> sections, List<Relation> relations) {
        String name = nameOf(pvc);
        if (pvc.getSpec() != null) {
            Section spec = new Section("Claim spec");
            spec.rows().add(new Row("accessModes", String.join(",", nullSafe(pvc.getSpec().getAccessModes()))));
            spec.rows().add(new Row("storageClass", str(pvc.getSpec().getStorageClassName())));
            spec.rows().add(new Row("volumeName", str(pvc.getSpec().getVolumeName())));
            if (pvc.getSpec().getResources() != null)
                for (var entry : nullSafe(pvc.getSpec().getResources().getRequests()).entrySet())
                    spec.rows().add(new Row("request " + entry.getKey(), quantity(entry.getValue())));
            addIfRows(sections, spec);
        }
        if (pvc.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("phase", str(pvc.getStatus().getPhase())));
            addIfRows(sections, status);
        }
        if (pvc.getSpec() != null && pvc.getSpec().getVolumeName() != null)
            relations.add(new Relation(name, pvc.getSpec().getVolumeName(), "bound to"));
    }

    private static void event(Event event, List<Section> sections, List<Relation> relations) {
        Section body = new Section("Event");
        body.rows().add(new Row("type", str(event.getType())));
        body.rows().add(new Row("reason", str(event.getReason())));
        body.rows().add(new Row("message", str(event.getMessage())));
        body.rows().add(new Row("count", str(event.getCount())));
        if (event.getInvolvedObject() != null)
            body.rows().add(new Row("involved", event.getInvolvedObject().getKind() + " " + str(event.getInvolvedObject().getName())));
        addIfRows(sections, body);
        if (event.getInvolvedObject() != null && event.getInvolvedObject().getName() != null)
            relations.add(new Relation(nameOf(event), event.getInvolvedObject().getName(), "about"));
    }

    private static void node(io.fabric8.kubernetes.api.model.Node node, List<Section> sections) {
        if (node.getStatus() != null) {
            Section status = new Section("Node status");
            capacityRows(status, node.getStatus().getCapacity(), "capacity");
            capacityRows(status, node.getStatus().getAllocatable(), "allocatable");
            if (node.getStatus().getNodeInfo() != null) {
                status.rows().add(new Row("kubelet", str(node.getStatus().getNodeInfo().getKubeletVersion())));
                status.rows().add(new Row("os", str(node.getStatus().getNodeInfo().getOsImage())));
                status.rows().add(new Row("arch", str(node.getStatus().getNodeInfo().getArchitecture())));
            }
            for (NodeCondition c : nullSafe(node.getStatus().getConditions()))
                status.rows().add(new Row("condition " + c.getType(), str(c.getStatus())));
            if (node.getStatus().getAddresses() != null)
                for (var addr : node.getStatus().getAddresses())
                    status.rows().add(new Row("address " + addr.getType(), str(addr.getAddress())));
            addIfRows(sections, status);
        }
        if (node.getSpec() != null) {
            Section spec = new Section("Node spec");
            spec.rows().add(new Row("unschedulable", str(node.getSpec().getUnschedulable())));
            for (Taint taint : nullSafe(node.getSpec().getTaints()))
                spec.rows().add(new Row("taint", taint.getKey() + "=" + str(taint.getValue()) + ":" + str(taint.getEffect())));
            addIfRows(sections, spec);
        }
    }

    private static void namespace(io.fabric8.kubernetes.api.model.Namespace ns, List<Section> sections) {
        if (ns.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("phase", str(ns.getStatus().getPhase())));
            addIfRows(sections, status);
        }
    }

    private static void pv(io.fabric8.kubernetes.api.model.PersistentVolume pv, List<Section> sections, List<Relation> relations) {
        if (pv.getSpec() != null) {
            Section spec = new Section("Volume spec");
            capacityRows(spec, pv.getSpec().getCapacity(), "capacity");
            spec.rows().add(new Row("accessModes", String.join(",", nullSafe(pv.getSpec().getAccessModes()))));
            spec.rows().add(new Row("storageClass", str(pv.getSpec().getStorageClassName())));
            spec.rows().add(new Row("reclaim", str(pv.getSpec().getPersistentVolumeReclaimPolicy())));
            spec.rows().add(new Row("source", volumeSource(pv.getSpec())));
            if (pv.getSpec().getClaimRef() != null)
                spec.rows().add(new Row("claimRef", pv.getSpec().getClaimRef().getNamespace() + "/" + str(pv.getSpec().getClaimRef().getName())));
            addIfRows(sections, spec);
        }
        if (pv.getStatus() != null) {
            Section status = new Section("Status");
            status.rows().add(new Row("phase", str(pv.getStatus().getPhase())));
            if (pv.getStatus().getMessage() != null) status.rows().add(new Row("message", pv.getStatus().getMessage()));
            addIfRows(sections, status);
        }
        if (pv.getSpec() != null && pv.getSpec().getClaimRef() != null && pv.getSpec().getClaimRef().getName() != null)
            relations.add(new Relation(nameOf(pv), pv.getSpec().getClaimRef().getName(), "claimed by"));
    }

    private static String volumeSource(io.fabric8.kubernetes.api.model.PersistentVolumeSpec spec) {
        if (spec.getHostPath() != null) return "hostPath " + str(spec.getHostPath().getPath());
        if (spec.getNfs() != null) return "nfs " + str(spec.getNfs().getServer()) + ":" + str(spec.getNfs().getPath());
        if (spec.getCsi() != null) return "csi " + str(spec.getCsi().getDriver());
        return "";
    }

    private static void containerStatuses(io.fabric8.kubernetes.api.model.Pod pod, List<Section> sections) {
        if (pod.getStatus() == null) return;
        for (ContainerStatus cs : nullSafe(pod.getStatus().getContainerStatuses())) {
            Section section = new Section("Container status: " + cs.getName());
            section.rows().add(new Row("ready", str(cs.getReady())));
            section.rows().add(new Row("restarts", str(cs.getRestartCount())));
            section.rows().add(new Row("image", str(cs.getImage())));
            section.rows().add(new Row("state", containerState(cs.getState())));
            addIfRows(sections, section);
        }
    }

    private static String containerState(ContainerState state) {
        if (state == null) return "";
        if (state.getRunning() != null) return "Running";
        if (state.getWaiting() != null) return "Waiting: " + str(state.getWaiting().getReason()) + suffix(" — " + state.getWaiting().getMessage(), state.getWaiting().getMessage() != null);
        if (state.getTerminated() != null) return "Terminated: " + str(state.getTerminated().getReason()) + " exit=" + state.getTerminated().getExitCode();
        return "";
    }

    private static void podTemplate(ObjectMeta meta, io.fabric8.kubernetes.api.model.PodSpec spec, String owner,
                                    List<Section> sections, List<Relation> relations) {
        if (meta != null && meta.getAnnotations() != null)
            for (var entry : meta.getAnnotations().entrySet())
                if (entry.getKey() != null && entry.getKey().contains("restartedAt")) {
                    Section section = new Section("Rollout");
                    section.rows().add(new Row("restartedAt", entry.getValue()));
                    sections.add(section);
                }
        podTemplateLike(meta, spec == null ? null : spec.getInitContainers(),
            spec == null ? null : spec.getContainers(), spec == null ? null : spec.getVolumes(), owner, sections, relations);
    }

    private static void podTemplateLike(ObjectMeta meta, List<Container> init, List<Container> containers, List<Volume> volumes,
                                        String owner, List<Section> sections, List<Relation> relations) {
        for (Container container : nullSafe(init)) containerSection(container, "Init container", sections, relations, owner);
        for (Container container : nullSafe(containers)) containerSection(container, "Container", sections, relations, owner);
        if (volumes != null) {
            Section section = new Section("Volumes");
            for (Volume volume : volumes) {
                section.rows().add(new Row("volume " + volume.getName(), volumeBackend(volume, relations, owner)));
            }
            addIfRows(sections, section);
        }
        if (meta != null && meta.getLabels() != null && !meta.getLabels().isEmpty()) {
            Section labels = new Section("Pod template labels");
            mapRows(labels, "label", meta.getLabels());
            addIfRows(sections, labels);
        }
    }

    private static void containerSection(Container container, String prefix, List<Section> sections, List<Relation> relations, String owner) {        Section section = new Section(prefix + ": " + container.getName());
        section.rows().add(new Row("image", str(container.getImage()) + suffix(" (" + container.getImagePullPolicy() + ")", container.getImagePullPolicy() != null)));
        for (ContainerPort port : nullSafe(container.getPorts()))
            section.rows().add(new Row("port", port.getContainerPort() + suffix("/" + port.getProtocol(), port.getProtocol() != null)));
        for (EnvVar env : nullSafe(container.getEnv()))
            section.rows().add(new Row("env " + env.getName(), env.getValue() == null ? valueFrom(env) : env.getValue()));
        for (EnvFromSource from : nullSafe(container.getEnvFrom()))
            section.rows().add(new Row("envFrom", from.getConfigMapRef() != null ? "configmap " + from.getConfigMapRef().getName()
                : from.getSecretRef() != null ? "secret " + from.getSecretRef().getName() : ""));
        resourcesRows(section, container.getResources());
        for (VolumeMount mount : nullSafe(container.getVolumeMounts()))
            section.rows().add(new Row("mount " + mount.getName(), mount.getMountPath() + suffix(" ro", Boolean.TRUE.equals(mount.getReadOnly()))));
        probeRow(section, "readiness", container.getReadinessProbe());
        probeRow(section, "liveness", container.getLivenessProbe());
        addIfRows(sections, section);
        if (owner != null && container.getImage() != null) relations.add(new Relation(owner, container.getImage(), "runs"));
    }

    private static String valueFrom(EnvVar env) {
        if (env.getValueFrom() == null) return "";
        if (env.getValueFrom().getConfigMapKeyRef() != null) return "configmap " + env.getValueFrom().getConfigMapKeyRef().getName();
        if (env.getValueFrom().getSecretKeyRef() != null) return "secret " + env.getValueFrom().getSecretKeyRef().getName();
        return "valueFrom";
    }

    private static void resourcesRows(Section section, ResourceRequirements resources) {
        if (resources == null) return;
        for (var entry : nullSafe(resources.getRequests()).entrySet())
            section.rows().add(new Row("request " + entry.getKey(), quantity(entry.getValue())));
        for (var entry : nullSafe(resources.getLimits()).entrySet())
            section.rows().add(new Row("limit " + entry.getKey(), quantity(entry.getValue())));
    }

    private static void probeRow(Section section, String name, Probe probe) {
        if (probe == null) return;
        StringBuilder value = new StringBuilder();
        if (probe.getHttpGet() != null) value.append("http ").append(probe.getHttpGet().getPath()).append(":").append(intOrString(probe.getHttpGet().getPort()));
        else if (probe.getTcpSocket() != null) value.append("tcp :").append(intOrString(probe.getTcpSocket().getPort()));
        else if (probe.getExec() != null) value.append("exec");
        if (probe.getPeriodSeconds() != null) value.append(" every ").append(probe.getPeriodSeconds()).append("s");
        section.rows().add(new Row(name, value.toString()));
    }

    private static String volumeBackend(Volume volume, List<Relation> relations, String owner) {
        if (volume.getConfigMap() != null) {
            if (owner != null) relations.add(new Relation(owner, volume.getConfigMap().getName(), "mounts configmap"));
            return "configmap " + str(volume.getConfigMap().getName());
        }
        if (volume.getSecret() != null) {
            String secret = volume.getSecret().getSecretName();
            if (owner != null) relations.add(new Relation(owner, secret, "mounts secret"));
            return "secret " + str(secret);
        }
        if (volume.getPersistentVolumeClaim() != null) {
            if (owner != null) relations.add(new Relation(owner, volume.getPersistentVolumeClaim().getClaimName(), "mounts pvc"));
            return "pvc " + str(volume.getPersistentVolumeClaim().getClaimName());
        }
        if (volume.getEmptyDir() != null) return "emptyDir";
        if (volume.getHostPath() != null) return "hostPath " + str(volume.getHostPath().getPath());
        return "";
    }

    private static void selectorRows(Section spec, LabelSelector selector) {
        if (selector == null) return;
        mapRows(spec, "selector", selector.getMatchLabels());
    }

    private static void capacityRows(Section section, Map<String, Quantity> capacity, String prefix) {
        for (var entry : nullSafe(capacity).entrySet())
            section.rows().add(new Row(prefix + " " + entry.getKey(), quantity(entry.getValue())));
    }

    private static void owners(ObjectMeta meta, Section section) {
        if (meta.getOwnerReferences() == null) return;
        for (var owner : meta.getOwnerReferences())
            section.rows().add(new Row("owned by", owner.getKind() + " " + str(owner.getName())));
    }

    private static void mapRows(Section section, String prefix, Map<String, String> map) {
        for (var entry : nullSafe(map).entrySet())
            section.rows().add(new Row(prefix + " " + entry.getKey(), entry.getValue()));
    }

    private static String nameOf(HasMetadata resource) {
        return resource.getMetadata() == null ? "" : str(resource.getMetadata().getName());
    }

    private static String str(Object value) { return value == null ? "" : String.valueOf(value); }

    private static String suffix(String text, boolean present) { return present ? text : ""; }

    private static String suffix(String text) { return text == null || text.isEmpty() ? "" : text; }

    private static String intOrString(IntOrString value) {
        if (value == null) return "";
        if (value.getIntVal() != null) return String.valueOf(value.getIntVal());
        return str(value.getStrVal());
    }

    private static String targetPort(IntOrString value) { return intOrString(value); }

    private static String quantity(Quantity value) {
        if (value == null) return "";
        return str(value.getAmount()) + str(value.getFormat());
    }

    private static String preview(String value) {
        if (value == null) return "";
        String flat = value.replace("\n", "\\n");
        return flat.length() > 120 ? flat.substring(0, 120) + "…" : flat;
    }

    static boolean isPemCertificate(String value) {
        return value != null && value.contains("-----BEGIN CERTIFICATE-----");
    }

    public static List<ExportableEntry> exportableEntries(HasMetadata resource) {
        if (resource instanceof io.fabric8.kubernetes.api.model.ConfigMap cm && cm.getData() != null) {
            String name = nameOf(resource);
            return cm.getData().entrySet().stream()
                .filter(entry -> isPemCertificate(entry.getValue()))
                .map(entry -> new ExportableEntry("ConfigMap", name, entry.getKey(), entry.getValue()))
                .toList();
        }
        return List.of();
    }

    private static <T> List<T> nullSafe(List<T> list) { return list == null ? List.of() : list; }

    private static <K, V> Map<K, V> nullSafe(Map<K, V> map) { return map == null ? Map.of() : map; }

    private static void addIfRows(List<Section> sections, Section section) {
        if (!section.rows().isEmpty()) sections.add(section);
    }

    public static Map<String, List<String>> adjacency(Inspection inspection) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Relation relation : inspection.relations()) {
            if (relation.from() == null || relation.from().isEmpty() || relation.to() == null || relation.to().isEmpty()) continue;
            graph.computeIfAbsent(relation.from(), key -> new ArrayList<>()).add(relation.to() + suffix(" (" + relation.label() + ")", relation.label() != null));
            graph.computeIfAbsent(relation.to(), key -> new ArrayList<>());
        }
        return graph;
    }
}
