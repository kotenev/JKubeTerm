package dev.jkubeterm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class KubeconfigLoaderTest {
    @TempDir Path temp;
    @Test void discoversContextsWithoutClusterConnection() throws Exception {
        Path config = temp.resolve("config");
        Files.writeString(config, "apiVersion: v1\nkind: Config\ncontexts:\n  - name: minikube\n    context: {cluster: minikube, user: minikube}\n  - name: development\n    context: {cluster: dev, user: dev}\n");
        assertEquals(List.of("minikube", "development"), KubeconfigLoader.contexts(List.of(config)).stream().map(KubeconfigLoader.ContextRef::name).toList());
    }
    @Test void missingPathIsIgnored() {
        assertTrue(KubeconfigLoader.paths(temp.resolve("missing").toString(), temp.toString()).isEmpty());
    }
}
