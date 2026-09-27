package dev.jkubeterm;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step-by-step connection diagnosis for the Diagnose button and for Connect failures.
 * Every check is short, ordered, and prints FIX hints — no silent empty tables.
 */
public final class ConnectionDiagnostics {
    private ConnectionDiagnostics() {}

    public record Check(String name, boolean ok, String detail, String fix) {}

    public static String diagnose(KubeconfigLoader.ContextRef context) {
        List<Check> checks = runChecks(context);
        StringBuilder report = new StringBuilder();
        report.append("Diagnosis of '").append(context.name()).append("' [").append(context.file()).append("]\n");
        int index = 1;
        for (Check check : checks) {
            report.append(index++).append(". [").append(check.ok() ? "OK" : "FAIL").append("] ")
                .append(check.name()).append(": ").append(check.detail()).append('\n');
            if (!check.ok() && check.fix() != null && !check.fix().isBlank())
                report.append("   FIX: ").append(check.fix()).append('\n');
        }
        boolean allOk = checks.stream().allMatch(Check::ok);
        report.append(allOk
            ? "All checks passed. If the table is still empty, the listing is genuinely empty — switch Namespace to kube-system or create a workload.\n"
            : "First FAIL above is the place to fix — later checks may be its consequence.\n");
        return report.toString();
    }

    public static String describe(KubeconfigLoader.ContextRef context, Throwable failure) {
        return "Connect to '" + context.name() + "' failed: "
            + (failure == null ? "unknown error" : String.valueOf(failure.getMessage())) + '\n'
            + causeHint(failure) + '\n'
            + diagnose(context);
    }

    static List<Check> runChecks(KubeconfigLoader.ContextRef context) {
        List<Check> checks = new ArrayList<>();
        checks.add(checkFile(context));
        Config config;
        try {
            config = KubeconfigLoader.config(context);
            checks.add(new Check("kubeconfig parse", true,
                "context '" + context.name() + "' resolves to " + config.getMasterUrl(), null));
        } catch (Exception e) {
            checks.add(new Check("kubeconfig parse", false, String.valueOf(e.getMessage()),
                "Open " + context.file() + ", verify the context, cluster and user blocks exist."));
            return checks;
        }
        checks.add(checkReachability(config));
        checks.add(checkVersionAndNamespaces(config));
        return checks;
    }

    private static Check checkFile(KubeconfigLoader.ContextRef context) {
        if (!Files.isRegularFile(context.file()))
            return new Check("kubeconfig file", false, "not a file: " + context.file(),
                "Fix KUBECONFIG or ~/.kube/config, then press Config to rescan.");
        return new Check("kubeconfig file", true, context.file().toString(), null);
    }

    private static Check checkReachability(Config config) {
        String url = config.getMasterUrl();
        if (url == null || url.isBlank())
            return new Check("API endpoint", false, "empty master URL",
                "Check the cluster.server entry for this context.");
        String host = url.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "").replaceFirst("[:/].*$", "");
        String resolved;
        try {
            resolved = java.net.InetAddress.getByName(host).getHostAddress();
        } catch (Exception e) {
            return new Check("API endpoint", false, host + " does not resolve (" + e.getMessage() + ")",
                "For minikube: 'minikube status' / 'minikube start'. For remote: check DNS/VPN.");
        }
        try (var socket = new java.net.Socket()) {
            int port = portOf(url);
            socket.connect(new java.net.InetSocketAddress(host, port), 5000);
            return new Check("API endpoint", true, host + " (" + resolved + "):" + port + " accepts TCP", null);
        } catch (Exception e) {
            return new Check("API endpoint", false, url + " refused (" + e.getMessage() + ")",
                "Start the cluster ('minikube start'), or fix the server address.");
        }
    }

    private static int portOf(String url) {
        try {
            String afterScheme = url.replaceFirst("^[a-zA-Z][a-zA-Z0-9+.-]*://", "");
            int colon = afterScheme.lastIndexOf(':');
            int slash = afterScheme.indexOf('/');
            if (colon >= 0 && (slash < 0 || colon < slash)) {
                String port = slash < 0 ? afterScheme.substring(colon + 1) : afterScheme.substring(colon + 1, slash);
                return Integer.parseInt(port.replaceAll("[^0-9]", ""));
            }
        } catch (Exception ignored) { /* fall through */ }
        return url.toLowerCase(Locale.ROOT).startsWith("http://") ? 80 : 443;
    }

    private static Check checkVersionAndNamespaces(Config config) {
        try (KubernetesClient client = new KubernetesClientBuilder().withConfig(config).build()) {
            String version = client.getKubernetesVersion().getGitVersion();
            List<String> namespaces = client.namespaces().list().getItems().stream()
                .map(n -> n.getMetadata().getName()).sorted().toList();
            return new Check("API auth (version + namespaces)", true,
                "Kubernetes " + version + ", namespaces: " + namespaces.size(), null);
        } catch (Exception e) {
            return new Check("API auth (version + namespaces)", false, String.valueOf(e.getMessage()),
                causeHint(e) + " Press Config after fixing kubeconfig, then Connect again.");
        }
    }

    static String causeHint(Throwable failure) {
        String text = failure == null ? "" : String.valueOf(failure.getMessage()).toLowerCase(Locale.ROOT)
            + " " + failure.getClass().getName().toLowerCase(Locale.ROOT);
        if (text.contains("cert") || text.contains("ssl") || text.contains("pkix") || text.contains("verify"))
            return "Looks like TLS/client-certificate trouble: wrong cluster CA or user cert for this context. Do not disable verification — fix the kubeconfig.";
        if (text.contains("unauthorized") || text.contains("401"))
            return "API refused credentials (401): expired or wrong token/cert for this context.";
        if (text.contains("forbidden") || text.contains("403"))
            return "Connected, but RBAC denies listing (403): grant view/cluster-read or pick a namespace you may read.";
        if (text.contains("refused") || text.contains("connect") || text.contains("timeout") || text.contains("unreachable"))
            return "API server is not reachable: cluster is down or the server address is stale ('minikube start' / 'minikube ip' changed?).";
        if (text.contains("unknownhost") || text.contains("no such host"))
            return "Server hostname does not resolve: DNS/VPN or a stale minikube IP in kubeconfig.";
        return "See the step-by-step diagnosis below.";
    }
}
