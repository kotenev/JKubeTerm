package dev.jkubeterm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Wiki registry of best practices in Markdown notation.
 * Bundled DB ships as classpath resource {@code /practices.md}; the user DB
 * lives at {@code ~/.jkubeterm/practices.md} and overrides/adds entries by id.
 * Pure parsing plus small FS helpers — unit tested.
 */
public final class PracticeRegistry {
    private PracticeRegistry() {}

    public record Practice(String id, Set<String> kinds, ClusterAdvisor.Severity severity,
                           String title, List<String> docs, String why, String fix, boolean builtin) {
        public boolean appliesTo(String kindName) { return kinds.isEmpty() || kinds.contains(kindName); }
    }

    public record Highlight(String version, String stable, List<String> docs, String lab, String fix) {}

    private static volatile List<Practice> cached;

    public static Path userFile() {
        return Path.of(System.getProperty("user.home"), ".jkubeterm", "practices.md");
    }

    public static List<Practice> load() {
        List<Practice> bundled = loadBundled();
        Map<String, Practice> merged = new LinkedHashMap<>();
        for (Practice practice : bundled) merged.put(practice.id(), practice);
        Path file = userFile();
        if (Files.isRegularFile(file)) {
            try {
                for (Practice practice : parse(Files.readString(file, StandardCharsets.UTF_8))) {
                    merged.put(practice.id(), new Practice(practice.id(), practice.kinds(), practice.severity(),
                        practice.title(), practice.docs(), practice.why(), practice.fix(), false));
                }
            } catch (IOException ignored) { /* fall back to bundled */ }
        }
        return List.copyOf(merged.values());
    }

    public static List<Practice> cached() {
        List<Practice> snapshot = cached;
        if (snapshot == null) {
            snapshot = load();
            cached = snapshot;
        }
        return snapshot;
    }

    public static void reload() { cached = load(); }

    public static Practice byId(List<Practice> practices, String id) {
        for (Practice practice : practices)
            if (practice.id().equals(id)) return practice;
        return null;
    }

    public static List<Practice> forKind(List<Practice> practices, String kindName) {
        return practices.stream().filter(p -> p.appliesTo(kindName)).toList();
    }

    public static void saveUserFile(Path file, List<Practice> practices) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, header() + format(practices), StandardCharsets.UTF_8);
        if (file.equals(userFile())) reload();
    }

    static List<Practice> loadBundled() {
        try (InputStream in = PracticeRegistry.class.getResourceAsStream("/practices.md")) {
            if (in == null) return List.of();
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).stream()
                .map(p -> new Practice(p.id(), p.kinds(), p.severity(), p.title(), p.docs(), p.why(), p.fix(), true))
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    static String header() {
        return "# JKubeTerm best-practices wiki\n# One `## practice <id>` section per entry. User entries override bundled ones by id.\n\n";
    }

    public static List<Highlight> highlights() {
        try (InputStream in = PracticeRegistry.class.getResourceAsStream("/whats-new.md")) {
            if (in == null) return List.of();
            return parseHighlights(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return List.of();
        }
    }

    static List<Highlight> parseHighlights(String text) {
        List<Highlight> highlights = new ArrayList<>();
        String version = null;
        String stable = null;
        List<String> docs = new ArrayList<>();
        StringBuilder lab = new StringBuilder();
        StringBuilder fix = new StringBuilder();
        String section = null;
        boolean inDocs = false;
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            if (line.startsWith("## ")) {
                if (version != null) highlights.add(new Highlight(version, stable == null ? "" : stable,
                    List.copyOf(docs), lab.toString().strip(), fix.toString().strip()));
                version = line.substring(3).trim();
                stable = null;
                docs = new ArrayList<>();
                lab = new StringBuilder();
                fix = new StringBuilder();
                section = null;
                inDocs = false;
            } else if (version == null) {
                continue; // header — skip
            } else if (line.startsWith("Stable:")) {
                stable = line.substring("Stable:".length()).trim();
                inDocs = false;
                section = null;
            } else if (line.equals("### Try it in the lab")) {
                section = "lab";
                inDocs = false;
            } else if (line.equals("### Fix")) {
                section = "fix";
                inDocs = false;
            } else if (line.equals("Docs:")) {
                inDocs = true;
                section = null;
            } else if (inDocs && line.startsWith("- ")) {
                docs.add(line.substring(2).trim());
            } else if ("lab".equals(section)) {
                lab.append(line).append('\n');
            } else if ("fix".equals(section)) {
                fix.append(line).append('\n');
            }
        }
        if (version != null) highlights.add(new Highlight(version, stable == null ? "" : stable,
            List.copyOf(docs), lab.toString().strip(), fix.toString().strip()));
        return highlights;
    }

    static List<Practice> parse(String text) {
        List<Practice> practices = new ArrayList<>();
        String id = null;
        String kinds = null;
        String severity = "INFO";
        String title = null;
        List<String> docs = new ArrayList<>();
        StringBuilder why = new StringBuilder();
        StringBuilder fix = new StringBuilder();
        String section = null;
        boolean inDocs = false;
        for (String rawLine : text.split("\n", -1)) {
            String line = rawLine.stripTrailing();
            if (line.startsWith("## practice ")) {
                if (id != null && title != null) practices.add(build(id, kinds, severity, title, docs, why, fix));
                id = line.substring("## practice ".length()).trim();
                kinds = null;
                severity = "INFO";
                title = null;
                docs = new ArrayList<>();
                why = new StringBuilder();
                fix = new StringBuilder();
                section = null;
                inDocs = false;
            } else if (id == null) {
                continue; // header or blank — skip
            } else if (line.equals("### Why")) {
                section = "why";
                inDocs = false;
            } else if (line.equals("### Fix")) {
                section = "fix";
                inDocs = false;
            } else if (line.equals("Docs:")) {
                inDocs = true;
                section = null;
            } else if (inDocs && line.startsWith("- ")) {
                docs.add(line.substring(2).trim());
            } else if (line.startsWith("Kinds:")) {
                kinds = line.substring("Kinds:".length()).trim();
                inDocs = false;
                section = null;
            } else if (line.startsWith("Severity:")) {
                severity = line.substring("Severity:".length()).trim();
                inDocs = false;
                section = null;
            } else if (line.startsWith("Title:") && section == null) {
                title = line.substring("Title:".length()).trim();
                inDocs = false;
            } else if ("why".equals(section)) {
                why.append(line).append('\n');
            } else if ("fix".equals(section)) {
                fix.append(line).append('\n');
            }
        }
        if (id != null && title != null) practices.add(build(id, kinds, severity, title, docs, why, fix));
        return practices;
    }

    private static Practice build(String id, String kinds, String severity, String title,
                                  List<String> docs, StringBuilder why, StringBuilder fix) {
        Set<String> kindSet = new TreeSet<>();
        if (kinds != null && !kinds.isBlank() && !kinds.trim().equals("*"))
            for (String kind : kinds.split(",")) {
                String trimmed = kind.trim();
                if (!trimmed.isEmpty()) kindSet.add(trimmed);
            }
        ClusterAdvisor.Severity level;
        try {
            level = ClusterAdvisor.Severity.valueOf(severity.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            level = ClusterAdvisor.Severity.INFO;
        }
        return new Practice(id, Set.copyOf(kindSet), level, title, List.copyOf(docs),
            why.toString().strip(), fix.toString().strip(), false);
    }

    static String format(List<Practice> practices) {
        StringBuilder out = new StringBuilder();
        for (Practice practice : practices) {
            out.append("## practice ").append(practice.id()).append('\n');
            out.append("Kinds: ").append(practice.kinds().isEmpty() ? "*" : String.join(", ", practice.kinds())).append('\n');
            out.append("Severity: ").append(practice.severity().name()).append('\n');
            out.append("Title: ").append(practice.title()).append('\n');
            out.append("Docs:\n");
            for (String url : practice.docs()) out.append("- ").append(url).append('\n');
            out.append("### Why\n").append(practice.why()).append('\n');
            out.append("### Fix\n").append(practice.fix()).append('\n');
        }
        return out.toString();
    }
}
