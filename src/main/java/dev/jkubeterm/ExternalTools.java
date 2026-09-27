package dev.jkubeterm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Safe argv-based invocation: commands never go through a shell. */
public final class ExternalTools {
    private ExternalTools() {}
    public static String run(KubeconfigLoader.ContextRef context, String namespace, List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>(command);
        ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
        builder.environment().put("KUBECONFIG", context.file().toString());
        Process process = builder.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); throw new IOException("Command timed out after " + timeoutSeconds + " seconds"); }
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        if (process.exitValue() != 0) throw new IOException(output.isBlank() ? "Command exited: " + process.exitValue() : output);
        return output;
    }
    public static List<String> kubectl(KubeconfigLoader.ContextRef context, String namespace, String... args) {
        List<String> argv = new ArrayList<>(List.of("kubectl", "--context", context.name(), "--kubeconfig", context.file().toString(), "--namespace", namespace));
        argv.addAll(List.of(args)); return argv;
    }
    public static List<String> helm(KubeconfigLoader.ContextRef context, String namespace, String... args) {
        List<String> argv = new ArrayList<>(List.of("helm", "--kube-context", context.name(), "--kubeconfig", context.file().toString(), "--namespace", namespace));
        argv.addAll(List.of(args)); return argv;
    }
}
