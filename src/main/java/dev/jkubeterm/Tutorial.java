package dev.jkubeterm;

import java.util.List;

/**
 * Tutorial scripts for the in-app guided tour.
 * Pure data (no JavaFX): target ids are resolved to nodes by
 * {@code JKubeTermApp.tutorialNode(String)}. Known ids:
 * contexts, connect, reload-contexts, namespaces, refresh, kinds, filter,
 * table, details, editMode, apply, remove, logs, shell, forward, scale,
 * restart, helm, addons, save, newYaml, diagnose, hood, advisor, console, status.
 */
public record Tutorial(String name, String doneText, List<Step> steps) {
    public record Step(String title, String body, List<String> targetIds, List<String> advanceOn) {
        public Step(String title, String body, List<String> targetIds) {
            this(title, body, targetIds, List.of());
        }
    }

    public static Tutorial guidedTour() {
        return new Tutorial("Guided tour",
            "Tour complete. Full docs: http://127.0.0.1:8000/guides/ .",
            List.of(
                new Step("1 — Connect",
                    "Pick a kubeconfig context in the Context combo, then press Connect. Status must show Connected.",
                    List.of("contexts", "connect"), List.of("connect")),
                new Step("2 — Namespace and Refresh",
                    "Pick a namespace, then press Refresh. The table loads on a worker thread. Empty result is a valid answer — the console explains why. Press Diagnose for a step-by-step connection report.",
                    List.of("namespaces", "refresh", "diagnose"), List.of("refresh")),
                new Step("3 — Browse",
                    "Pick a kind on the left (each has its own vector icon and a one-line explanation under the filter), type into the filter (local, no API calls), then click a table row to load its YAML.",
                    List.of("kinds", "filter", "table"), List.of("select-row")),
                new Step("4 — Manifest view",
                    "The row YAML appears read-only on the right. Below it, Under the hood explains what the controllers do with this object, and Best practices flags issues with fixes. Tick Edit YAML to make it editable.",
                    List.of("details", "hood", "advisor", "editMode"), List.of("edit-mode")),
                new Step("5 — Apply flow",
                    "Press New YAML for a ConfigMap template, edit it, then Apply YAML (confirm dialog, server-side apply). Save YAML exports locally.",
                    List.of("newYaml", "apply", "save"), List.of("apply")),
                new Step("6 — Pod tools",
                    "Select a Pod row first. Pod logs asks for a container (last 500 lines). Exec runs one executable (30 s). Port forward binds 127.0.0.1 and lives until exit.",
                    List.of("logs", "shell", "forward"), List.of("logs")),
                new Step("7 — Deployments, Helm and Addons",
                    "Scale and Restart need a Deployment row. Helm releases only lists (helm list) in the current namespace. Addons… enables a minikube addon (e.g. ingress) on the current profile with one click. Output lands in the console below.",
                    List.of("scale", "restart", "helm", "addons", "console"))));
    }

    public static Tutorial firstDeploy() {
        return new Tutorial("First deploy drill",
            "Drill complete: template, edit, namespace check, apply, find, delete. See training module 04.",
            List.of(
                new Step("1 — Template",
                    "Press New YAML. A ConfigMap template appears and Edit YAML turns on.",
                    List.of("newYaml"), List.of("new-yaml")),
                new Step("2 — Edit",
                    "Change metadata.name in the Manifest field to something unique, e.g. tutorial-demo. Press Next when done.",
                    List.of("details", "editMode")),
                new Step("3 — Namespace check",
                    "Verify the Namespace combo (empty namespace in YAML is filled from it). Press Next.",
                    List.of("namespaces")),
                new Step("4 — Apply",
                    "Press Apply YAML and confirm the context. Watch the console for Applied YAML successfully.",
                    List.of("apply"), List.of("apply")),
                new Step("5 — Find it",
                    "Pick ConfigMaps on the left, filter by your name, click the row to view its YAML.",
                    List.of("kinds", "filter", "table"), List.of("select-row")),
                new Step("6 — Clean up (optional)",
                    "Select the row and press Delete to remove it, or Exit tutorial to keep it.",
                    List.of("remove"), List.of("delete"))));
    }

    public static Tutorial debugFlow() {
        return new Tutorial("Debug flow drill",
            "Drill complete: select Pod, logs, console, exec, forward. See training modules 08 and 11.",
            List.of(
                new Step("1 — Select a Pod",
                    "Pick Pods on the left and click a Pod row.",
                    List.of("kinds", "table"), List.of("select-row")),
                new Step("2 — Logs",
                    "Press Pod logs, pick a container in the dialog. Last 500 lines, no streaming.",
                    List.of("logs"), List.of("logs")),
                new Step("3 — Read the console",
                    "The Output / logs field is overwritten each time — history is not kept. Check Events kind for scheduling clues. Press Next.",
                    List.of("console", "kinds")),
                new Step("4 — Exec",
                    "Press Exec command, run a single executable (e.g. /bin/sh or env). No shell parsing, 30 s timeout.",
                    List.of("shell"), List.of("exec")),
                new Step("5 — Port forward",
                    "Press Port forward on a Pod or Service row, enter local:remote (e.g. 8080:80). It binds 127.0.0.1 until the app exits.",
                    List.of("forward"), List.of("forward"))));
    }
}
