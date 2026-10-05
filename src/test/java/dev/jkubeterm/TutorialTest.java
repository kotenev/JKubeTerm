package dev.jkubeterm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TutorialTest {
    @Test void guidedTourHasStepsWithTargets() {
        Tutorial tour = Tutorial.guidedTour();
        assertFalse(tour.steps().isEmpty());
        for (Tutorial.Step step : tour.steps()) {
            assertFalse(step.title().isBlank());
            assertFalse(step.body().isBlank());
            assertFalse(step.targetIds().isEmpty());
        }
        assertFalse(tour.doneText().isBlank());
    }
    @Test void drillsHaveAdvanceEventsFromKnownVocabulary() {
        var known = java.util.Set.of("connect", "refresh", "select-row", "edit-mode", "new-yaml",
            "apply", "delete", "logs", "exec", "forward", "addons");
        for (Tutorial tour : java.util.List.of(Tutorial.firstDeploy(), Tutorial.debugFlow(), Tutorial.guidedTour())) {
            assertFalse(tour.steps().isEmpty());
            for (Tutorial.Step step : tour.steps())
                for (String event : step.advanceOn())
                    assertTrue(known.contains(event), event);
        }
    }
    @Test void targetIdsAreKnown() {
        var known = java.util.Set.of("contexts", "connect", "reload-contexts", "namespaces",
            "refresh", "kinds", "filter", "table", "details", "editMode", "apply", "remove",
            "logs", "shell", "forward", "scale", "restart", "helm", "addons", "save", "newYaml",
            "diagnose", "hood", "advisor", "assistant", "console", "status");
        for (Tutorial tour : java.util.List.of(Tutorial.guidedTour(), Tutorial.firstDeploy(), Tutorial.debugFlow()))
            for (Tutorial.Step step : tour.steps())
                for (String id : step.targetIds())
                    assertTrue(known.contains(id), id);
    }
}
