package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.PodSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * Best-practice checks for Kubernetes resources: each finding is a severity,
 * a human message, and a fix hint. Pure data (JavaFX-free) — unit tested.
 */
public final class ClusterAdvisor {
    private ClusterAdvisor() {}

    public enum Severity { INFO, WARN, CRITICAL }

    public record Finding(Severity severity, String check, String message, String fix, List<String> docs) {
        public Finding(Severity severity, String check, String message, String fix) {
            this(severity, check, message, fix, List.of());
        }
    }

    public static List<Finding> advise(HasMetadata resource) {
        return advise(resource, PracticeRegistry.cached(), null);
    }

    public static List<Finding> advise(HasMetadata resource, List<PracticeRegistry.Practice> registry) {
        return advise(resource, registry, null);
    }

    public static List<Finding> advise(HasMetadata resource, List<PracticeRegistry.Practice> registry, String serverVersion) {
        List<Finding> findings = new ArrayList<>();
        triggerChecks(resource, findings, registry);
        versionChecks(resource, findings, registry, serverVersion);
        registryBackfill(resource, findings, registry);
        return List.copyOf(findings);
    }

    private static void triggerChecks(HasMetadata resource, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        switch (resource) {
            case io.fabric8.kubernetes.api.model.Pod pod -> podFindings(pod, findings, registry);
            case io.fabric8.kubernetes.api.model.apps.Deployment deploy -> deploymentFindings(deploy, findings, registry);
            case io.fabric8.kubernetes.api.model.apps.StatefulSet sts -> stsFindings(sts, findings, registry);
            case io.fabric8.kubernetes.api.model.apps.DaemonSet ds -> daemonSetFindings(ds, findings, registry);
            case io.fabric8.kubernetes.api.model.Service svc -> serviceFindings(svc, findings, registry);
            case io.fabric8.kubernetes.api.model.ConfigMap cm -> configMapFindings(cm, findings, registry);
            case io.fabric8.kubernetes.api.model.batch.v1.Job job -> jobFindings(job, findings, registry);
            case io.fabric8.kubernetes.api.model.batch.v1.CronJob cron -> cronFindings(cron, findings, registry);
            case io.fabric8.kubernetes.api.model.networking.v1.Ingress ing -> ingressFindings(ing, findings, registry);
            case io.fabric8.kubernetes.api.model.PersistentVolumeClaim pvc -> pvcFindings(pvc, findings, registry);
            case io.fabric8.kubernetes.api.model.PersistentVolume pv -> pvFindings(pv, findings, registry);
            case io.fabric8.kubernetes.api.model.Node node -> nodeFindings(node, findings, registry);
            case io.fabric8.kubernetes.api.model.Namespace ns -> namespaceFindings(ns, findings, registry);
            case Event event -> eventFindings(event, findings, registry);
            default -> { /* kinds without trigger checks — registry backfill still applies */ }
        }
    }

    private static PracticeRegistry.Practice lookup(List<PracticeRegistry.Practice> registry, String id) {
        if (registry == null) return null;
        for (PracticeRegistry.Practice practice : registry)
            if (practice.id().equals(id)) return practice;
        return null;
    }

    private static Finding finding(List<PracticeRegistry.Practice> registry, String id,
                                   Severity fallbackSeverity, String fallbackMessage, String fallbackFix) {
        PracticeRegistry.Practice practice = lookup(registry, id);
        if (practice == null) return new Finding(fallbackSeverity, id, fallbackMessage, fallbackFix);
        String fix = practice.fix().isEmpty() ? fallbackFix : practice.fix();
        return new Finding(practice.severity(), id, fallbackMessage, fix, practice.docs());
    }

    private static void registryBackfill(HasMetadata resource, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (registry == null || resource == null || resource.getKind() == null) return;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Finding finding : findings) seen.add(finding.check());
        for (PracticeRegistry.Practice practice : PracticeRegistry.forKind(registry, resource.getKind()))
            if (!seen.contains(practice.id()))
                findings.add(new Finding(Severity.INFO, practice.id(), practice.title() + " — no issue detected on this object.",
                    practice.fix().isEmpty() ? "See the linked docs for the recommended setup." : practice.fix(), practice.docs()));
    }

    private static void registryBacked(HasMetadata resource, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        registryBackfill(resource, findings, registry);
    }

    static void versionChecks(HasMetadata resource, List<Finding> findings,
                              List<PracticeRegistry.Practice> registry, String serverVersion) {
        int[] parsed = parseVersion(serverVersion);
        if (parsed == null) return;
        int minor = parsed[1];
        if (resource instanceof io.fabric8.kubernetes.api.model.apps.StatefulSet sts
            && sts.getSpec() != null && sts.getSpec().getVolumeClaimTemplates() != null
            && !sts.getSpec().getVolumeClaimTemplates().isEmpty() && minor >= 35)
            findings.add(finding(registry, "stateful-retention", Severity.INFO,
                "Server is Kubernetes 1." + minor + " — StatefulSet PVC auto-delete (retentionPolicy) is available.",
                "Set spec.persistentVolumeClaimRetentionPolicy to control PVC cleanup on scale-down/delete."));
        if (resource instanceof io.fabric8.kubernetes.api.model.batch.v1.Job && minor >= 36)
            findings.add(finding(registry, "job-success-policy", Severity.INFO,
                "Server is Kubernetes 1." + minor + " — Job successPolicy can short-circuit completions.",
                "Add spec.successPolicy rules instead of waiting for all indexes."));
        if (resource instanceof io.fabric8.kubernetes.api.model.Pod && minor >= 37)
            findings.add(finding(registry, "pod-resources", Severity.INFO,
                "Server is Kubernetes 1." + minor + " — Pod-level resources are GA; HPA can scale on container size.",
                "Compare 'kubectl top pods' with the Pod-level usage API on this workload."));
    }

    static int[] parseVersion(String version) {
        if (version == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("v?(\\d+)\\.(\\d+)").matcher(version);
        if (!matcher.find()) return null;
        try {
            return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void podFindings(io.fabric8.kubernetes.api.model.Pod pod, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        checkProbe(pod.getSpec(), findings, registry);
        checkResources(pod.getSpec(), findings, registry);
        checkSecurity(pod.getSpec(), findings, registry);
        checkImageTags(pod.getSpec(), findings, registry);
        if (pod.getStatus() != null && ("CrashLoopBackOff".equals(statusReason(pod)) || "ImagePullBackOff".equals(statusReason(pod))))
            findings.add(finding(registry, "pod-status", Severity.CRITICAL, "Pod is stuck in " + statusReason(pod) + ".",
                "Read container logs plus Events, then fix the image or command — recreating the Pod will not help."));
    }

    private static void deploymentFindings(io.fabric8.kubernetes.api.model.apps.Deployment deploy, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (deploy.getSpec() != null) {
            Integer replicas = deploy.getSpec().getReplicas();
            if (replicas != null && replicas < 2)
                findings.add(finding(registry, "replica-count", Severity.WARN, "Single replica — a node failure means downtime.",
                    "Use replicas: 2+ (JKubeTerm Scale button or Edit YAML)."));
            if (deploy.getSpec().getStrategy() != null && "Recreate".equals(deploy.getSpec().getStrategy().getType()))
                findings.add(finding(registry, "rolling-update", Severity.INFO, "Recreate strategy causes downtime on every rollout.",
                    "Prefer RollingUpdate unless a ReadWriteOnce volume forces Recreate."));
            if (deploy.getSpec().getTemplate() != null && deploy.getSpec().getTemplate().getSpec() != null) {
                checkProbe(deploy.getSpec().getTemplate().getSpec(), findings, registry);
                checkResources(deploy.getSpec().getTemplate().getSpec(), findings, registry);
                checkSecurity(deploy.getSpec().getTemplate().getSpec(), findings, registry);
                checkImageTags(deploy.getSpec().getTemplate().getSpec(), findings, registry);
            }
            if (deploy.getSpec().getTemplate() != null && deploy.getSpec().getTemplate().getMetadata() != null
                && deploy.getSpec().getTemplate().getMetadata().getLabels() == null)
                findings.add(finding(registry, "labels-standard", Severity.INFO, "Pod template has no labels — selectors and drill-down cannot match it.",
                    "Add app.kubernetes.io/name and component labels to the Pod template."));
        }
        if (deploy.getStatus() != null) {
            Integer updated = deploy.getStatus().getUpdatedReplicas();
            Integer ready = deploy.getStatus().getReadyReplicas();
            if (updated != null && ready != null && !updated.equals(ready))
                findings.add(finding(registry, "rollout-complete", Severity.WARN, "Rollout is not finished: updated=" + updated + " ready=" + ready + ".",
                    "Watch Pods and Events; check image, probes and resources."));
        }
    }

    private static void daemonSetFindings(io.fabric8.kubernetes.api.model.apps.DaemonSet ds, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (ds.getSpec() != null && ds.getSpec().getTemplate() != null && ds.getSpec().getTemplate().getSpec() != null) {
            checkProbe(ds.getSpec().getTemplate().getSpec(), findings, registry);
            checkResources(ds.getSpec().getTemplate().getSpec(), findings, registry);
            checkSecurity(ds.getSpec().getTemplate().getSpec(), findings, registry);
            checkImageTags(ds.getSpec().getTemplate().getSpec(), findings, registry);
        }
        boolean toleratesAll = ds.getSpec() != null && ds.getSpec().getTemplate() != null && ds.getSpec().getTemplate().getSpec() != null
            && ds.getSpec().getTemplate().getSpec().getTolerations() != null
            && !ds.getSpec().getTemplate().getSpec().getTolerations().isEmpty();
        if (!toleratesAll)
            findings.add(finding(registry, "daemonset-tolerations", Severity.INFO, "DaemonSet has no tolerations — tainted nodes are silently skipped.",
                "Add tolerations matching the cluster taints, or a nodeSelector for the intended nodes."));
    }

    private static void jobFindings(io.fabric8.kubernetes.api.model.batch.v1.Job job, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (job.getSpec() != null) {
            if (job.getSpec().getBackoffLimit() == null)
                findings.add(finding(registry, "job-backoff", Severity.INFO, "No backoffLimit — retries are unbounded by default.",
                    "Set backoffLimit: 3-6 and activeDeadlineSeconds for long tasks."));
            if (job.getSpec().getTemplate() != null && job.getSpec().getTemplate().getSpec() != null) {
                String policy = job.getSpec().getTemplate().getSpec().getRestartPolicy();
                if (!"Never".equals(policy) && !"OnFailure".equals(policy))
                    findings.add(finding(registry, "job-restart-policy", Severity.WARN, "Job restartPolicy '" + policy + "' is rejected by the API.",
                        "Set restartPolicy: Never (or OnFailure for retryable steps) in the Pod template."));
                checkResources(job.getSpec().getTemplate().getSpec(), findings, registry);
                checkSecurity(job.getSpec().getTemplate().getSpec(), findings, registry);
                checkImageTags(job.getSpec().getTemplate().getSpec(), findings, registry);
            }
        }
    }

    private static void stsFindings(io.fabric8.kubernetes.api.model.apps.StatefulSet sts, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (sts.getSpec() != null && sts.getSpec().getTemplate() != null && sts.getSpec().getTemplate().getSpec() != null) {
            checkProbe(sts.getSpec().getTemplate().getSpec(), findings, registry);
            checkResources(sts.getSpec().getTemplate().getSpec(), findings, registry);
            checkSecurity(sts.getSpec().getTemplate().getSpec(), findings, registry);
            checkImageTags(sts.getSpec().getTemplate().getSpec(), findings, registry);
        }
        if (sts.getSpec() != null && (sts.getSpec().getVolumeClaimTemplates() == null || sts.getSpec().getVolumeClaimTemplates().isEmpty()))
            findings.add(finding(registry, "stateful-storage", Severity.INFO, "StatefulSet without volumeClaimTemplates — Pods lose data on reschedule.",
                "Add a volumeClaimTemplates entry (1Gi RWO is enough for a lab)."));
        if (sts.getSpec() != null && sts.getSpec().getTemplate() != null && sts.getSpec().getTemplate().getMetadata() != null
            && sts.getSpec().getTemplate().getMetadata().getLabels() == null)
            findings.add(finding(registry, "labels-standard", Severity.INFO, "Pod template has no labels — selectors cannot match it.",
                "Add app.kubernetes.io/name and component labels to the Pod template."));
    }

    private static void serviceFindings(io.fabric8.kubernetes.api.model.Service svc, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (svc.getSpec() != null) {
            if ((svc.getSpec().getSelector() == null || svc.getSpec().getSelector().isEmpty())
                && !"ExternalName".equals(svc.getSpec().getType()))
                findings.add(finding(registry, "service-selector", Severity.WARN, "No selector — this Service routes to zero Pods unless Endpoints are managed manually.",
                    "Add spec.selector matching the Pod labels, or use ExternalName for outside addresses."));
            if ("NodePort".equals(svc.getSpec().getType()) || "LoadBalancer".equals(svc.getSpec().getType()))
                findings.add(finding(registry, "service-type", Severity.INFO, "Type " + svc.getSpec().getType() + " exposes the Service beyond the cluster.",
                    "Prefer ClusterIP plus Ingress for user traffic; NodePort only for quick lab checks."));
        }
    }

    private static void configMapFindings(io.fabric8.kubernetes.api.model.ConfigMap cm, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (cm.getData() != null)
            for (var entry : cm.getData().entrySet()) {
                String value = entry.getValue();
                if (value != null && (value.contains("password") || value.contains("secret") || value.contains("token"))
                    && value.length() < 200)
                    findings.add(finding(registry, "config-no-secrets", Severity.WARN, "Key '" + entry.getKey() + "' looks like a credential inside a ConfigMap.",
                        "Move it to a Secret (or SealedSecrets/ExternalSecrets) and reference via envFrom."));
            }
        if (!Boolean.TRUE.equals(cm.getImmutable()))
            findings.add(finding(registry, "config-immutable", Severity.INFO, "ConfigMap is mutable — mounted files skew across Pods for up to a minute.",
                "Set immutable: true for versioned config; roll the name instead of editing."));
    }

    private static void pvcFindings(io.fabric8.kubernetes.api.model.PersistentVolumeClaim pvc, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (pvc.getStatus() != null && "Pending".equals(pvc.getStatus().getPhase()))
            findings.add(finding(registry, "pvc-storage-class", Severity.WARN, "PVC is Pending — no StorageClass provisioner or matching PV.",
                "Enable a provisioner (minikube storage-provisioner addon) or pre-create the PV."));
        if (pvc.getSpec() != null && pvc.getSpec().getAccessModes() != null && pvc.getSpec().getAccessModes().contains("ReadWriteOnce"))
            findings.add(finding(registry, "pvc-rwo-replicas", Severity.INFO, "ReadWriteOnce allows a single writer node.",
                "Use strategy Recreate for single-writer, or an RWX driver for shared writes."));
    }

    private static void pvFindings(io.fabric8.kubernetes.api.model.PersistentVolume pv, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (pv.getSpec() != null && pv.getSpec().getPersistentVolumeReclaimPolicy() != null)
            findings.add(finding(registry, "pv-reclaim", Severity.INFO, "Reclaim policy is " + pv.getSpec().getPersistentVolumeReclaimPolicy() + ".",
                "Use Retain for valuable data, Delete for scratch; document the choice per StorageClass."));
    }

    private static void nodeFindings(io.fabric8.kubernetes.api.model.Node node, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (node.getStatus() != null && node.getStatus().getConditions() != null)
            for (var condition : node.getStatus().getConditions()) {
                if (("DiskPressure".equals(condition.getType()) || "MemoryPressure".equals(condition.getType()))
                    && "True".equals(condition.getStatus()))
                    findings.add(finding(registry, "node-pressure", Severity.WARN, "Node reports " + condition.getType() + " — evictions are starting.",
                        "Free disk, raise memory, or cordon and drain before the kubelet kills Pods."));
                if ("Ready".equals(condition.getType()) && !"True".equals(condition.getStatus()))
                    findings.add(finding(registry, "node-pressure", Severity.CRITICAL, "Node is NotReady — workloads cannot schedule here.",
                        "Check kubelet, containerd and network; cordon until healthy."));
            }
    }

    private static void namespaceFindings(io.fabric8.kubernetes.api.model.Namespace ns, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        String name = ns.getMetadata() == null ? "" : ns.getMetadata().getName();
        if (!List.of("default", "kube-system", "kube-public", "kube-node-lease").contains(name))
            findings.add(finding(registry, "namespace-quotas", Severity.INFO, "Namespace '" + name + "' has no quota guard in this view.",
                "Add ResourceQuota plus LimitRange defaults so one namespace cannot starve the cluster."));
    }

    private static void eventFindings(Event event, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (event.getInvolvedObject() != null)
            findings.add(finding(registry, "event-hygiene", Severity.INFO, "Event about " + event.getInvolvedObject().getKind() + " " + event.getInvolvedObject().getName() + ".",
                "Read Events newest-first, filter by involved object, then describe that object."));
    }

    private static void ingressFindings(io.fabric8.kubernetes.api.model.networking.v1.Ingress ing, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        boolean hasTls = ing.getSpec() != null && ing.getSpec().getTls() != null && !ing.getSpec().getTls().isEmpty();
        if (!hasTls)
            findings.add(finding(registry, "ingress-tls", Severity.WARN, "No TLS section — traffic to this Ingress is plain HTTP.",
                "Add spec.tls with a cert-manager Certificate secret (staging issuer first)."));
        if (ing.getSpec() != null && ing.getSpec().getIngressClassName() == null)
            findings.add(finding(registry, "ingress-class", Severity.INFO, "No ingressClassName — the Ingress may be ignored when several controllers exist.",
                "Set ingressClassName: nginx (the minikube addon class)."));
    }

    private static void cronFindings(io.fabric8.kubernetes.api.model.batch.v1.CronJob cron, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (cron.getSpec() != null && Boolean.TRUE.equals(cron.getSpec().getSuspend()))
            findings.add(finding(registry, "cron-suspend", Severity.INFO, "CronJob is suspended — no Jobs are scheduled.",
                "Set spec.suspend: false to resume."));
        if (cron.getSpec() != null && cron.getSpec().getSchedule() != null
            && cron.getSpec().getSchedule().trim().split("\\s+").length < 5)
            findings.add(finding(registry, "cron-schedule", Severity.WARN, "Schedule '" + cron.getSpec().getSchedule() + "' is not a 5-field cron expression.",
                "Use minute hour day month weekday, e.g. '0 * * * *' for hourly."));
    }

    private static void checkProbe(PodSpec spec, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (spec == null) return;
        for (Container container : allContainers(spec)) {
            if (container.getReadinessProbe() == null)
                findings.add(finding(registry, "readiness-probe", Severity.WARN,
                    "Container '" + container.getName() + "' has no readinessProbe — broken Pods keep receiving traffic.",
                    "Add an httpGet/tcpSocket readinessProbe (periodSeconds: 5 is a sane start)."));
            if (container.getLivenessProbe() == null)
                findings.add(finding(registry, "liveness-probe", Severity.INFO,
                    "Container '" + container.getName() + "' has no livenessProbe — deadlocks are never restarted.",
                    "Add a livenessProbe different from readiness (cheap endpoint, longer period)."));
        }
    }

    private static void checkResources(PodSpec spec, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (spec == null) return;
        for (Container container : allContainers(spec)) {
            boolean noRequests = container.getResources() == null || container.getResources().getRequests() == null
                || container.getResources().getRequests().isEmpty();
            boolean noLimits = container.getResources() == null || container.getResources().getLimits() == null
                || container.getResources().getLimits().isEmpty();
            if (noRequests)
                findings.add(finding(registry, "resource-requests", Severity.WARN,
                    "Container '" + container.getName() + "' has no resource requests — scheduling and HPA are blind.",
                    "Set requests (e.g. cpu: 50m, memory: 64Mi) matching idle usage."));
            if (noLimits)
                findings.add(finding(registry, "resource-limits", Severity.INFO,
                    "Container '" + container.getName() + "' has no resource limits — one leak can OOM the node.",
                    "Set limits (e.g. cpu: 200m, memory: 128Mi) and watch Grafana."));
        }
    }

    private static void checkSecurity(PodSpec spec, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (spec == null) return;
        if (Boolean.TRUE.equals(spec.getHostNetwork()) || Boolean.TRUE.equals(spec.getHostPID()) || Boolean.TRUE.equals(spec.getHostIPC()))
            findings.add(finding(registry, "host-namespaces", Severity.CRITICAL,
                "Pod shares host network/PID/IPC — a container escape becomes a node escape.",
                "Remove hostNetwork/hostPID/hostIPC unless a CNI-class DaemonSet truly needs them."));
        boolean rootRisk = false;
        boolean escalationRisk = false;
        for (Container container : allContainers(spec)) {
            if (container.getSecurityContext() == null
                || !Boolean.TRUE.equals(container.getSecurityContext().getRunAsNonRoot())) rootRisk = true;
            if (container.getSecurityContext() == null
                || !Boolean.FALSE.equals(container.getSecurityContext().getAllowPrivilegeEscalation())) escalationRisk = true;
        }
        if (rootRisk)
            findings.add(finding(registry, "run-as-non-root", Severity.WARN,
                "Containers may run as root — a breakout has full privileges.",
                "Set securityContext.runAsNonRoot: true (plus runAsUser, seccompProfile RuntimeDefault)."));
        if (escalationRisk)
            findings.add(finding(registry, "no-privilege-escalation", Severity.INFO,
                "Privilege escalation is not explicitly disabled.",
                "Set securityContext.allowPrivilegeEscalation: false."));
    }

    private static void checkImageTags(PodSpec spec, List<Finding> findings, List<PracticeRegistry.Practice> registry) {
        if (spec == null) return;
        for (Container container : allContainers(spec)) {
            String image = container.getImage();
            if (image == null) continue;
            if (image.endsWith(":latest") || !image.contains(":"))
                findings.add(finding(registry, "pinned-image", Severity.WARN,
                    "Container '" + container.getName() + "' uses a floating tag ('" + image + "') — rollouts are not reproducible.",
                    "Pin image:tag (e.g. nginx:1.27) and bump deliberately."));
            if ("IfNotPresent".equals(container.getImagePullPolicy()) && image.endsWith(":latest"))
                findings.add(finding(registry, "image-pull-policy", Severity.INFO,
                    "latest plus IfNotPresent can run stale images on different nodes.",
                    "Use Always with floating tags, or better: pin the tag."));
        }
    }

    static String statusReason(io.fabric8.kubernetes.api.model.Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) return "";
        for (var cs : pod.getStatus().getContainerStatuses()) {
            if (cs.getState() != null && cs.getState().getWaiting() != null && cs.getState().getWaiting().getReason() != null)
                return cs.getState().getWaiting().getReason();
        }
        return "";
    }

    private static List<Container> allContainers(PodSpec spec) {
        List<Container> all = new ArrayList<>(spec.getInitContainers() == null ? List.of() : spec.getInitContainers());
        if (spec.getContainers() != null) all.addAll(spec.getContainers());
        return all;
    }

    public static String explainHood(io.fabric8.kubernetes.api.model.Pod pod) {
        if (pod.getSpec() == null) return "No pod spec available.";
        String node = pod.getSpec().getNodeName() == null ? "not scheduled yet" : "running on node '" + pod.getSpec().getNodeName() + "'";
        String sa = pod.getSpec().getServiceAccountName() == null ? "default" : pod.getSpec().getServiceAccountName();
        return "This Pod is " + node + " as ServiceAccount '" + sa + "' (restartPolicy " + pod.getSpec().getRestartPolicy()
            + "). kubelet on the node pulls the images, kube-proxy programs the Service virtual IP, and controllers recreate the Pod when it dies.";
    }
}
