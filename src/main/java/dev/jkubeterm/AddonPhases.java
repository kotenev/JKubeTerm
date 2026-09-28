package dev.jkubeterm;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Dynamic ETA for minikube addon installs: maps streaming output lines to
 * install phases and converts phase progress into a 0..1 fraction plus a
 * human ETA string. Pure logic, no JavaFX or I/O — unit tested.
 *
 * <p>Phase weights come from typical `minikube addons enable` runs:
 * pull ~45%, verify ~25%, enable ~30%. Each line nudges progress forward
 * monotonically; star-prefix lines (`* Verifying…`) jump to phase starts.
 */
public final class AddonPhases {
    private AddonPhases() {}

    private static final List<Pattern> PULL = List.of(
        Pattern.compile("(?i)pull(ing)?\\s+(imag|docker)|download(ing)?\\s+imag"),
        Pattern.compile("(?i)imag.*(pull|download)|load.*imag"));
    private static final List<Pattern> VERIFY = List.of(
        Pattern.compile("(?i)\\bverif(y|ying|ication)\\b|validat|inspect"),
        Pattern.compile("(?i)kubernetes.*version|cluster.*(info|status)"));
    private static final List<Pattern> ENABLE = List.of(
        Pattern.compile("(?i)\\benabl(e|ing|ed)\\b|creat|apply|deploy|start|launch|configur|restart|\\bcheck\\b"),
        Pattern.compile("\\*\u2764?\\s*(Verifying|Enabling|Starting|Creating|Using|Preparing)"));

    public enum Phase { IDLE, PULL, VERIFY, ENABLE, DONE }

    public static Phase phaseOf(String line) {
        if (line == null) return Phase.IDLE;
        // Star-prefix lines (`* Verifying…`) describe the phase directly and win.
        if (line.startsWith("*")) {
            if (line.contains("Verifying")) return Phase.VERIFY;
            if (PULL.stream().anyMatch(p -> p.matcher(line).find())) return Phase.PULL;
            return Phase.ENABLE;
        }
        if (matches(line, ENABLE)) return Phase.ENABLE;
        if (matches(line, VERIFY)) return Phase.VERIFY;
        if (matches(line, PULL)) return Phase.PULL;
        return Phase.IDLE;
    }

    private static boolean matches(String line, List<Pattern> patterns) {
        for (Pattern pattern : patterns)
            if (pattern.matcher(line).find()) return true;
        return false;
    }

    /** Monotonic fraction: full-transcript scan, latest phase wins, capped at 0.97 until DONE. */
    public static double fractionOf(String latestLine, String transcript) {
        Phase phase = Phase.IDLE;
        if (transcript != null)
            for (String line : transcript.split("\n")) {
                if (line.startsWith("$")) continue; // echoed command line carries "enable" but is not progress
                Phase seen = phaseOf(line);
                if (seen.ordinal() > phase.ordinal()) phase = seen;
            }
        else phase = phaseOf(latestLine);
        return switch (phase) {
            case IDLE -> 0.05;
            case PULL -> 0.25;
            case VERIFY -> 0.60;
            case ENABLE -> 0.85;
            case DONE -> 1.0;
        };
    }

    /** Human ETA from elapsed time, fraction and timeout: linear extrapolation, clamped. */
    public static String eta(double fraction, long startedNanos, int timeoutSeconds) {
        long elapsed = (System.nanoTime() - startedNanos) / 1_000_000_000L;
        if (fraction >= 1.0) return "done in " + elapsed + "s";
        if (fraction <= 0.0 || elapsed <= 0) return "estimating… (elapsed " + elapsed + "s)";
        long total = (long) (elapsed / fraction);
        long remain = Math.max(0, total - elapsed);
        long budget = Math.max(0, timeoutSeconds - elapsed);
        return "elapsed " + elapsed + "s · ~" + remain + "s left (timeout in " + budget + "s)";
    }
}
