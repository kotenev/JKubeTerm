package dev.jkubeterm;

public enum ResourceKind {
    PODS("Pods", true), DEPLOYMENTS("Deployments", true), STATEFUL_SETS("StatefulSets", true), DAEMON_SETS("DaemonSets", true),
    SERVICES("Services", true), CONFIG_MAPS("ConfigMaps", true), JOBS("Jobs", true), CRON_JOBS("CronJobs", true),
    INGRESSES("Ingresses", true), PERSISTENT_VOLUME_CLAIMS("PVCs", true), EVENTS("Events", true),
    NODES("Nodes", false), NAMESPACES("Namespaces", false), PERSISTENT_VOLUMES("PersistentVolumes", false);
    public final String label;
    public final boolean namespaced;
    ResourceKind(String label, boolean namespaced) { this.label = label; this.namespaced = namespaced; }
    @Override public String toString() { return label; }
}
