package dev.jkubeterm;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Vector icons and beginner-friendly explanations for every browsable kind.
 * Icons are inline SVG path data (24x24 space, EVEN_ODD fill) — no image assets.
 * Pure data, no JavaFX — safe to unit test; the app wraps paths in SVGPath nodes.
 */
public final class ClusterVisuals {
    private ClusterVisuals() {}

    public record Icon(String svgPath, String colorHex) {}

    private static final Pattern HEX = Pattern.compile("#[0-9a-f]{6}");

    private static final Map<ResourceKind, Icon> ICONS = new EnumMap<>(ResourceKind.class);
    private static final Map<ResourceKind, String> EXPLAINS = new EnumMap<>(ResourceKind.class);

    static {
        ICONS.put(ResourceKind.PODS, new Icon("M12 1 L23 7.5 V16.5 L12 23 L1 16.5 V7.5 Z", "#4d9cf7"));
        ICONS.put(ResourceKind.DEPLOYMENTS, new Icon("M3 3 H21 V8 H3 Z M3 10 H21 V15 H3 Z M3 17 H21 V22 H3 Z", "#7e57c2"));
        ICONS.put(ResourceKind.STATEFUL_SETS, new Icon("M6 2 C6 0.5 9 0 12 0 C15 0 18 0.5 18 2 V22 C18 23.5 15 24 12 24 C9 24 6 23.5 6 22 Z M6 2 C6 3.5 9 4.5 12 4.5 C15 4.5 18 3.5 18 2", "#26a69a"));
        ICONS.put(ResourceKind.DAEMON_SETS, new Icon("M2 2 H11 V11 H2 Z M13 2 H22 V11 H13 Z M2 13 H11 V22 H2 Z M13 13 H22 V22 H13 Z", "#ffa726"));
        ICONS.put(ResourceKind.SERVICES, new Icon("M1 8 H15 V3 L23 11 L15 19 V14 H1 Z", "#66bb6a"));
        ICONS.put(ResourceKind.CONFIG_MAPS, new Icon("M6 1 H15 L21 7 V23 H6 Z M15 1 V7 H21", "#8d9eaf"));
        ICONS.put(ResourceKind.JOBS, new Icon("M7 3 L21 12 L7 21 Z", "#ffa000"));
        ICONS.put(ResourceKind.CRON_JOBS, new Icon("M12 1 A11 11 0 1 0 12 23 A11 11 0 1 0 12 1 Z M11 6 H13 V13 L18 16 L16.5 18 L11 14 Z", "#5c6bc0"));
        ICONS.put(ResourceKind.INGRESSES, new Icon("M12 1 A11 11 0 1 0 12 23 A11 11 0 1 0 12 1 Z M2 9 H22 M2 15 H22 M12 1 C16 6 16 18 12 23 C8 18 8 6 12 1 Z", "#29b6f6"));
        ICONS.put(ResourceKind.PERSISTENT_VOLUME_CLAIMS, new Icon("M5 3 C5 1.5 8 0.5 12 0.5 C16 0.5 19 1.5 19 3 V21 C19 22.5 16 23.5 12 23.5 C8 23.5 5 22.5 5 21 Z M5 3 C5 4.5 8 5.5 12 5.5 C16 5.5 19 4.5 19 3 M5 12 C5 13.5 8 14.5 12 14.5 C16 14.5 19 13.5 19 12", "#26c6da"));
        ICONS.put(ResourceKind.EVENTS, new Icon("M12 1 C8 1 6 5 6 9 V15 L2.5 18.5 H21.5 L18 15 V9 C18 5 16 1 12 1 Z M9.5 20 H14.5", "#ffca28"));
        ICONS.put(ResourceKind.NODES, new Icon("M4 2 H20 V22 H4 Z M7 5 H10 V8 H7 Z M14 5 H17 V8 H14 Z M7 11 H17 V14 H7 Z", "#78909c"));
        ICONS.put(ResourceKind.NAMESPACES, new Icon("M1 4 H9 L11.5 7.5 H23 V20 H1 Z", "#9ccc65"));
        ICONS.put(ResourceKind.PERSISTENT_VOLUMES, new Icon("M2 5 H22 V19 H2 Z M12 7.5 A4.5 4.5 0 1 0 12 16.5 A4.5 4.5 0 1 0 12 7.5 Z", "#b39ddb"));

        EXPLAINS.put(ResourceKind.PODS, "A Pod is one or more containers sharing one IP — the smallest thing Kubernetes runs. Pods are disposable: when one dies, a controller makes a new one with a new IP.");
        EXPLAINS.put(ResourceKind.DEPLOYMENTS, "A Deployment keeps N identical Pod copies alive and rolls them out gradually. If a Pod dies, the Deployment replaces it automatically.");
        EXPLAINS.put(ResourceKind.STATEFUL_SETS, "Like a Deployment, but each Pod keeps a stable name (web-0, web-1) and its own disk — for databases and queues.");
        EXPLAINS.put(ResourceKind.DAEMON_SETS, "Runs exactly one Pod copy on every node — for log collectors and monitoring agents.");
        EXPLAINS.put(ResourceKind.SERVICES, "A stable virtual IP and DNS name in front of ever-changing Pods. Pods die and IPs change; the Service address does not.");
        EXPLAINS.put(ResourceKind.CONFIG_MAPS, "Plain-text settings (files or env values) stored separately from the container image, so config changes need no rebuild.");
        EXPLAINS.put(ResourceKind.JOBS, "Runs Pods until a task finishes (migration, backup), then stops. Retries a few times on failure.");
        EXPLAINS.put(ResourceKind.CRON_JOBS, "A Job on a schedule — cron syntax, for example every hour.");
        EXPLAINS.put(ResourceKind.INGRESSES, "The front door: routes outside traffic (host plus URL path) to Services inside the cluster, ideally with TLS.");
        EXPLAINS.put(ResourceKind.PERSISTENT_VOLUME_CLAIMS, "A request for disk space ('I need 1Gi, read-write'). Kubernetes binds it to a real volume (PV).");
        EXPLAINS.put(ResourceKind.EVENTS, "The cluster news feed: what was scheduled, pulled, started or failed. Read newest-first when debugging.");
        EXPLAINS.put(ResourceKind.NODES, "A worker machine (here: one virtual node inside minikube). The scheduler places Pods onto Nodes.");
        EXPLAINS.put(ResourceKind.NAMESPACES, "Folders separating projects inside one cluster: demo, monitoring, kube-system and friends.");
        EXPLAINS.put(ResourceKind.PERSISTENT_VOLUMES, "A real piece of disk in the cluster that outlives Pods; claims (PVCs) bind to it.");
    }

    public static Icon icon(ResourceKind kind) {
        return ICONS.getOrDefault(kind, new Icon("M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 Z", "#8d9eaf"));
    }

    public static String svgPath(ResourceKind kind) { return icon(kind).svgPath(); }

    public static String colorHex(ResourceKind kind) { return icon(kind).colorHex(); }

    public static boolean validHex(String color) { return color != null && HEX.matcher(color).matches(); }

    public static String explain(ResourceKind kind) {
        return EXPLAINS.getOrDefault(kind, "A Kubernetes object — select it to see its YAML, object breakdown and relations.");
    }

    public static String explainKind(String kindName) {
        return DrillDown.kindForKindName(kindName).map(ClusterVisuals::explain)
            .orElse("A Kubernetes object — select it to see its YAML, object breakdown and relations.");
    }
}
