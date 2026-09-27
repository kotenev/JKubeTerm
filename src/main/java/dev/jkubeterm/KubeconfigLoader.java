package dev.jkubeterm;

import io.fabric8.kubernetes.client.Config;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads context names without sending kubeconfig or credentials anywhere. */
public final class KubeconfigLoader {
    private KubeconfigLoader() {}
    public record ContextRef(Path file, String name) {
        @Override public String toString() { return name + "  [" + file.getFileName() + "]"; }
    }
    public static List<Path> paths(String env, String home) {
        String value = env == null || env.isBlank() ? Path.of(home, ".kube", "config").toString() : env;
        List<Path> result = new ArrayList<>();
        for (String entry : value.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (!entry.isBlank()) {
                Path path = Path.of(entry.trim()).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) result.add(path);
            }
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    public static List<ContextRef> contexts(List<Path> paths) throws IOException {
        Map<String, ContextRef> found = new LinkedHashMap<>();
        for (Path path : paths) {
            Object document;
            try (var reader = Files.newBufferedReader(path)) { document = new Yaml().load(reader); }
            if (!(document instanceof Map<?, ?> doc)) continue;
            Object entries = doc.get("contexts");
            if (!(entries instanceof List<?> list)) continue;
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> map && map.get("name") instanceof String name && !name.isBlank())
                    found.putIfAbsent(name, new ContextRef(path, name));
            }
        }
        return List.copyOf(found.values());
    }
    public static Config config(ContextRef context) throws IOException {
        // Fabric8 resolves relative certificate/key paths against kubeconfig file path.
        return Config.fromKubeconfig(context.name(), Files.readString(context.file()), context.file().toString());
    }
}
