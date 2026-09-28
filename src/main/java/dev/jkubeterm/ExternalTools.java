package dev.jkubeterm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Safe argv-based invocation: commands never go through a shell. */
public final class ExternalTools {
    private ExternalTools() {}
    /** Line callback for streaming child output: receives merged stdout+stderr lines as they arrive. */
    @FunctionalInterface public interface LineSink { void accept(String line); }
    public static String run(KubeconfigLoader.ContextRef context, List<String> command, int timeoutSeconds) throws IOException, InterruptedException {
        return runStreaming(context, command, timeoutSeconds, null, () -> false);
    }
    /**
     * Streaming variant: drains merged output line-by-line on the calling thread,
     * forwarding each line to {@code sink} (may be null) and collecting the full text.
     * {@code cancelled} is polled between lines; on true the process is destroyed
     * and an {@code IOException("Cancelled…")} is thrown. Never blocks the FX thread —
     * call from the worker.
     */
    public static String runStreaming(KubeconfigLoader.ContextRef context, List<String> command, int timeoutSeconds,
                                      LineSink sink, java.util.function.BooleanSupplier cancelled) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>(command);
        ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
        builder.environment().put("KUBECONFIG", context.file().toString());
        long started = System.nanoTime();
        Process process = builder.start();
        StringBuilder collected = new StringBuilder();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                collected.append(line).append('\n');
                if (sink != null) {
                    String snapshot = line;
                    sink.accept(snapshot);
                }
                if (cancelled != null && cancelled.getAsBoolean()) {
                    process.destroyForcibly();
                    throw new IOException("Cancelled by user after " + elapsedOf(started));
                }
                if (elapsedSeconds(started) > timeoutSeconds) {
                    process.destroyForcibly();
                    throw new IOException("Command timed out after " + timeoutSeconds + " seconds. Partial output:\n" + collected);
                }
            }
        }
        boolean finished = process.waitFor(Math.max(1, timeoutSeconds - (int) elapsedSeconds(started)), TimeUnit.SECONDS);
        if (!finished) { process.destroyForcibly(); throw new IOException("Command timed out after " + timeoutSeconds + " seconds. Partial output:\n" + collected); }
        String output = collected.toString();
        if (process.exitValue() != 0) throw new IOException(output.isBlank() ? "Command exited: " + process.exitValue() : output);
        return output;
    }
    static long elapsedSeconds(long startedNanos) { return (System.nanoTime() - startedNanos) / 1_000_000_000L; }
    static String elapsedOf(long startedNanos) {
        long seconds = elapsedSeconds(startedNanos);
        return seconds + "s";
    }
    public static List<String> kubectl(KubeconfigLoader.ContextRef context, String namespace, String... args) {
        List<String> argv = new ArrayList<>(List.of("kubectl", "--context", context.name(), "--kubeconfig", context.file().toString(), "--namespace", namespace));
        argv.addAll(List.of(args)); return argv;
    }
    public static List<String> helm(KubeconfigLoader.ContextRef context, String namespace, String... args) {
        List<String> argv = new ArrayList<>(List.of("helm", "--kube-context", context.name(), "--kubeconfig", context.file().toString(), "--namespace", namespace));
        argv.addAll(List.of(args)); return argv;
    }
    public static List<String> minikube(String profile, String... args) {
        List<String> argv = new ArrayList<>(List.of("minikube"));
        if (profile != null && !profile.isBlank()) { argv.add("-p"); argv.add(profile.trim()); }
        argv.addAll(List.of(args)); return argv;
    }
}
