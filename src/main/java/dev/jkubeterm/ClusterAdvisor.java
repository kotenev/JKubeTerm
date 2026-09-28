package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.Container;
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

    public record Finding(Severity severity, String check, String message, String fix) {}

    public static List<Finding> advise(HasMetadata resource) {
        List<Finding> findings = new ArrayList<>();
        switch (resource) {
            case io.fabric8.kubernetes.api.model.Pod pod -> podFindings(pod, findings);
            case io.fabric8.kubernetes.api.model.apps.Deployment deploy -> deploymentFindings(deploy, findings);
            case io.fabric8.kubernetes.api.model.apps.StatefulSet sts -> stsFindings(sts, findings);
            case io.fabric8.kubernetes.api.model.Service svc -> serviceFindings(svc, findings);
            case io.fabric8.kubernetes.api.model.networking.v1.Ingress ing -> ingressFindings(ing, findings);
            case io.fabric8.kubernetes.api.model.batch.v1.CronJob cron -> cronFindings(cron, findings);
            default -> { /* kinds without advisor checks */ }
        }
        return List.copyOf(findings);
    }

    private static void podFindings(io.fabric8.kubernetes.api.model.Pod pod, List<Finding> findings) {
        checkProbe(pod.getSpec(), findings);
        checkResources(pod.getSpec(), findings);
        checkSecurity(pod.getSpec(), findings);
        checkImageTags(pod.getSpec(), findings);
        if (pod.getStatus() != null && ("CrashLoopBackOff".equals(statusReason(pod)) || "ImagePullBackOff".equals(statusReason(pod))))
            findings.add(new Finding(Severity.CRITICAL, "pod-status", "Pod is stuck in " + statusReason(pod) + ".",
                "Read container logs plus Events, then fix the image or command — recreating the Pod will not help."));
    }

    private static void deploymentFindings(io.fabric8.kubernetes.api.model.apps.Deployment deploy, List<Finding> findings) {
        if (deploy.getSpec() != null) {
            Integer replicas = deploy.getSpec().getReplicas();
            if (replicas != null && replicas < 2)
                findings.add(new Finding(Severity.WARN, "replicas", "Single replica — a node failure means downtime.",
                    "Use replicas: 2+ (JKubeTerm Scale button or Edit YAML)."));
            if (deploy.getSpec().getStrategy() != null && "Recreate".equals(deploy.getSpec().getStrategy().getType()))
                findings.add(new Finding(Severity.INFO, "strategy", "Recreate strategy causes downtime on every rollout.",
                    "Prefer RollingUpdate unless a ReadWriteOnce volume forces Recreate."));
            if (deploy.getSpec().getTemplate() != null && deploy.getSpec().getTemplate().getSpec() != null) {
                checkProbe(deploy.getSpec().getTemplate().getSpec(), findings);
                checkResources(deploy.getSpec().getTemplate().getSpec(), findings);
                checkSecurity(deploy.getSpec().getTemplate().getSpec(), findings);
                checkImageTags(deploy.getSpec().getTemplate().getSpec(), findings);
            }
        }
        if (deploy.getStatus() != null) {
            Integer updated = deploy.getStatus().getUpdatedReplicas();
            Integer ready = deploy.getStatus().getReadyReplicas();
            if (updated != null && ready != null && !updated.equals(ready))
                findings.add(new Finding(Severity.WARN, "rollout", "Rollout is not finished: updated=" + updated + " ready=" + ready + ".",
                    "Watch Pods and Events; check image, probes and resources."));
        }
    }

    private static void stsFindings(io.fabric8.kubernetes.api.model.apps.StatefulSet sts, List<Finding> findings) {
        if (sts.getSpec() != null && sts.getSpec().getTemplate() != null && sts.getSpec().getTemplate().getSpec() != null) {
            checkProbe(sts.getSpec().getTemplate().getSpec(), findings);
            checkResources(sts.getSpec().getTemplate().getSpec(), findings);
            checkSecurity(sts.getSpec().getTemplate().getSpec(), findings);
        }
        if (sts.getSpec() != null && (sts.getSpec().getVolumeClaimTemplates() == null || sts.getSpec().getVolumeClaimTemplates().isEmpty()))
            findings.add(new Finding(Severity.INFO, "storage", "StatefulSet without volumeClaimTemplates — Pods lose data on reschedule.",
                "Add a volumeClaimTemplates entry (1Gi RWO is enough for a lab)."));
    }

    private static void serviceFindings(io.fabric8.kubernetes.api.model.Service svc, List<Finding> findings) {
        if (svc.getSpec() != null) {
            if ((svc.getSpec().getSelector() == null || svc.getSpec().getSelector().isEmpty())
                && !"ExternalName".equals(svc.getSpec().getType()))
                findings.add(new Finding(Severity.WARN, "selector", "No selector — this Service routes to zero Pods unless Endpoints are managed manually.",
                    "Add spec.selector matching the Pod labels, or use ExternalName for outside addresses."));
            if ("NodePort".equals(svc.getSpec().getType()))
                findings.add(new Finding(Severity.INFO, "exposure", "NodePort opens a high port on every node.",
                    "Prefer ClusterIP plus Ingress for user traffic; keep NodePort for quick lab checks."));
            if ("LoadBalancer".equals(svc.getSpec().getType()))
                findings.add(new Finding(Severity.INFO, "exposure", "LoadBalancer needs a cloud LB or 'minikube tunnel' in a lab.",
                    "Without a tunnel the EXTERNAL-IP stays pending — use port-forward or Ingress instead."));
        }
    }

    private static void ingressFindings(io.fabric8.kubernetes.api.model.networking.v1.Ingress ing, List<Finding> findings) {
        boolean hasTls = ing.getSpec() != null && ing.getSpec().getTls() != null && !ing.getSpec().getTls().isEmpty();
        if (!hasTls)
            findings.add(new Finding(Severity.WARN, "tls", "No TLS section — traffic to this Ingress is plain HTTP.",
                "Add spec.tls with a cert-manager Certificate secret (staging issuer first)."));
        if (ing.getSpec() != null && ing.getSpec().getIngressClassName() == null)
            findings.add(new Finding(Severity.INFO, "class", "No ingressClassName — the Ingress may be ignored when several controllers exist.",
                "Set ingressClassName: nginx (the minikube addon class)."));
    }

    private static void cronFindings(io.fabric8.kubernetes.api.model.batch.v1.CronJob cron, List<Finding> findings) {
        if (cron.getSpec() != null && Boolean.TRUE.equals(cron.getSpec().getSuspend()))
            findings.add(new Finding(Severity.INFO, "suspend", "CronJob is suspended — no Jobs are scheduled.",
                "Set spec.suspend: false to resume."));
        if (cron.getSpec() != null && cron.getSpec().getSchedule() != null
            && cron.getSpec().getSchedule().trim().split("\\s+").length < 5)
            findings.add(new Finding(Severity.WARN, "schedule", "Schedule '" + cron.getSpec().getSchedule() + "' is not a 5-field cron expression.",
                "Use minute hour day month weekday, e.g. '0 * * * *' for hourly."));
    }

    private static void checkProbe(PodSpec spec, List<Finding> findings) {
        if (spec == null) return;
        for (Container container : allContainers(spec)) {
            if (container.getReadinessProbe() == null)
                findings.add(new Finding(Severity.WARN, "readinessProbe",
                    "Container '" + container.getName() + "' has no readinessProbe — broken Pods keep receiving traffic.",
                    "Add an httpGet/tcpSocket readinessProbe (periodSeconds: 5 is a sane start)."));
            if (container.getLivenessProbe() == null)
                findings.add(new Finding(Severity.INFO, "livenessProbe",
                    "Container '" + container.getName() + "' has no livenessProbe — deadlocks are never restarted.",
                    "Add a livenessProbe different from readiness (cheap endpoint, longer period)."));
        }
    }

    private static void checkResources(PodSpec spec, List<Finding> findings) {
        if (spec == null) return;
        for (Container container : allContainers(spec)) {
            boolean noRequests = container.getResources() == null || container.getResources().getRequests() == null
                || container.getResources().getRequests().isEmpty();
            boolean noLimits = container.getResources() == null || container.getResources().getLimits() == null
                || container.getResources().getLimits().isEmpty();
            if (noRequests)
                findings.add(new Finding(Severity.WARN, "resources",
                    "Container '" + container.getName() + "' has no resource requests — scheduling and HPA are blind.",
                    "Set requests (e.g. cpu: 50m, memory: 64Mi) matching idle usage."));
            if (noLimits)
                findings.add(new Finding(Severity.INFO, "resources",
                    "Container '" + container.getName() + "' has no resource limits — one leak can OOM the node.",
                    "Set limits (e.g. cpu: 200m, memory: 128Mi) and watch Grafana."));
        }
    }

    private static void checkSecurity(PodSpec spec, List<Finding> findings) {
        if (spec == null) return;
        if (Boolean.TRUE.equals(spec.getHostNetwork()) || Boolean.TRUE.equals(spec.getHostPID()) || Boolean.TRUE.equals(spec.getHostIPC()))
            findings.add(new Finding(Severity.CRITICAL, "host-namespaces",
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
            findings.add(new Finding(Severity.WARN, "runAsNonRoot",
                "Containers may run as root — a breakout has full privileges.",
                "Set securityContext.runAsNonRoot: true (plus runAsUser, seccompProfile RuntimeDefault)."));
        if (escalationRisk)
            findings.add(new Finding(Severity.INFO, "privilege-escalation",
                "Privilege escalation is not explicitly disabled.",
                "Set securityContext.allowPrivilegeEscalation: false."));
    }

    private static void checkImageTags(PodSpec spec, List<Finding> findings) {
        if (spec == null) return;
        for (Container container : allContainers(spec)) {
            String image = container.getImage();
            if (image == null) continue;
            if (image.endsWith(":latest") || !image.contains(":"))
                findings.add(new Finding(Severity.WARN, "image-tag",
                    "Container '" + container.getName() + "' uses a floating tag ('" + image + "') — rollouts are not reproducible.",
                    "Pin image:tag (e.g. nginx:1.27) and bump deliberately."));
            if ("IfNotPresent".equals(container.getImagePullPolicy()) && image != null && image.endsWith(":latest"))
                findings.add(new Finding(Severity.INFO, "image-pull",
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
