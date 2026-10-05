package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssistantEngineTest {
    private static List<ClusterAdvisor.Finding> findingsOf(io.fabric8.kubernetes.api.model.HasMetadata resource) {
        return ClusterAdvisor.advise(resource, PracticeRegistry.cached(), null);
    }

    @Test void emptyQuestionWithSelectionExplains() {
        var pod = new PodBuilder().withNewMetadata().withName("web-0").withNamespace("demo").endMetadata()
            .withNewSpec().addNewContainer().withName("c").withImage("nginx:1.27").endContainer().endSpec().build();
        var response = AssistantEngine.answer("", pod, findingsOf(pod), PracticeRegistry.cached(), "demo");
        assertTrue(response.text().contains("web-0"), response.text());
        assertTrue(response.text().contains("Оффлайн") || response.text().contains("Offline"), response.text());
    }

    @Test void emptyQuestionWithoutSelectionListsCapabilities() {
        var response = AssistantEngine.answer("", null, List.of(), PracticeRegistry.cached(), "default");
        assertFalse(response.text().isBlank());
        assertTrue(response.text().contains("kubectl") || response.text().contains("wiki"), response.text());
    }

    @Test void nullQuestionNeverThrows() {
        var response = AssistantEngine.answer(null, null, null, null, null);
        assertFalse(response.text().isBlank());
    }

    @Test void writeIntentIsRefusedReadOnly() {
        var pod = new PodBuilder().withNewMetadata().withName("p").withNamespace("demo").endMetadata().build();
        for (String q : List.of("delete this pod", "scale to 5", "restart it", "удали под", "примени yaml")) {
            var response = AssistantEngine.answer(q, pod, findingsOf(pod), PracticeRegistry.cached(), "demo");
            String lower = response.text().toLowerCase(java.util.Locale.ROOT);
            assertTrue(lower.contains("read-only") || lower.contains("только чтение"), q + " -> " + response.text());
            assertFalse(lower.contains("applied") || lower.contains("deleted"), q);
        }
    }

    @Test void diagnoseIntentRanksCriticalFirst() {
        var pod = new PodBuilder().withNewMetadata().withName("bad").withNamespace("demo").endMetadata()
            .withNewSpec().withHostNetwork(true)
            .addNewContainer().withName("c").withImage("nginx").endContainer().endSpec().build();
        var response = AssistantEngine.answer("why is it failing?", pod, findingsOf(pod), PracticeRegistry.cached(), "demo");
        int critical = response.text().indexOf("CRITICAL");
        int warn = response.text().indexOf("[WARN]");
        assertTrue(critical >= 0, response.text());
        // CRITICAL (host-namespaces) must come before any WARN finding.
        assertTrue(warn < 0 || critical < warn, response.text());
        assertTrue(response.text().contains("Fix:") || response.text().contains("Исправление:"), response.text());
    }

    @Test void diagnoseWithoutSelectionAsksToSelect() {
        var response = AssistantEngine.answer("почему не работает?", null, List.of(), PracticeRegistry.cached(), "demo");
        assertTrue(response.text().contains("Select") || response.text().contains("Выберите"), response.text());
    }

    @Test void wikiSearchFindsSeccompPractice() {
        var hits = AssistantEngine.search("seccomp profile", PracticeRegistry.cached());
        assertFalse(hits.isEmpty());
        assertTrue(hits.stream().anyMatch(p -> p.id().contains("seccomp")), hits.toString());
    }

    @Test void wikiSearchAnswersFromLocalRegistry() {
        var response = AssistantEngine.answer("seccomp RuntimeDefault", null, List.of(), PracticeRegistry.cached(), "default");
        assertTrue(response.text().contains("seccomp"), response.text());
        assertFalse(response.sources().isEmpty(), "expected docs URLs as sources");
    }

    @Test void commandsForPodAreReadOnly() {
        var pod = new PodBuilder().withNewMetadata().withName("web-0").withNamespace("demo").endMetadata().build();
        var response = AssistantEngine.answer("какие команды kubectl?", pod, findingsOf(pod), PracticeRegistry.cached(), "demo");
        assertTrue(response.text().contains("kubectl get pod web-0 -n demo"), response.text());
        assertTrue(response.text().contains("kubectl logs web-0"), response.text());
        assertFalse(response.text().contains("delete"), response.text());
    }

    @Test void commandsForServiceSuggestEndpoints() {
        var svc = new ServiceBuilder().withNewMetadata().withName("web").withNamespace("demo").endMetadata().build();
        String text = AssistantEngine.commandsFor(svc, "demo", false);
        assertTrue(text.contains("kubectl get endpoints web -n demo"), text);
    }

    @Test void secretValuesNeverLeakIntoExplanation() {
        var secret = new SecretBuilder().withNewMetadata().withName("s3cr3t").withNamespace("demo").endMetadata()
            .withType("Opaque").addToData("password", "c3VwZXItc2VjcmV0LXZhbHVl").build();
        String text = AssistantEngine.explainSelected(secret, List.of(), false);
        assertFalse(text.contains("c3VwZXItc2VjcmV0LXZhbHVl"), text);
        assertTrue(text.contains("password"), text);
    }

    @Test void explainDeploymentMentionsDocs() {
        var deploy = new io.fabric8.kubernetes.api.model.apps.DeploymentBuilder()
            .withNewMetadata().withName("web").withNamespace("demo").endMetadata()
            .withNewSpec().withReplicas(2).endSpec().build();
        String text = AssistantEngine.explainSelected(deploy, findingsOf(deploy), true);
        assertTrue(text.contains("kubernetes.io"), text);
        assertTrue(text.contains("Оффлайн"), text);
    }

    @Test void whatIsScaleExplainsInsteadOfRefusing() {
        var deploy = new io.fabric8.kubernetes.api.model.apps.DeploymentBuilder()
            .withNewMetadata().withName("web").withNamespace("demo").endMetadata()
            .withNewSpec().withReplicas(2).endSpec().build();
        var response = AssistantEngine.answer("what is scale?", deploy, findingsOf(deploy), PracticeRegistry.cached(), "demo");
        assertTrue(response.text().contains("web"), response.text());
        String lower = response.text().toLowerCase(java.util.Locale.ROOT);
        assertFalse(lower.contains("read-only: i change nothing"), response.text());
    }

    @Test void whyDoesDeleteFailDiagnoses() {
        var pod = new PodBuilder().withNewMetadata().withName("p").withNamespace("demo").endMetadata().build();
        var response = AssistantEngine.answer("why does delete fail?", pod, findingsOf(pod), PracticeRegistry.cached(), "demo");
        String lower = response.text().toLowerCase(java.util.Locale.ROOT);
        assertTrue(lower.contains("diagnos") || lower.contains("диагностик") || lower.contains("likely causes")
            || lower.contains("причина"), response.text());
    }

    @Test void configMapTriggersNoWrite() {
        var cm = new ConfigMapBuilder().withNewMetadata().withName("cfg").withNamespace("demo").endMetadata()
            .addToData("key", "value").build();
        var response = AssistantEngine.answer("explain", cm, findingsOf(cm), PracticeRegistry.cached(), "demo");
        assertTrue(response.text().contains("cfg"), response.text());
    }
}
