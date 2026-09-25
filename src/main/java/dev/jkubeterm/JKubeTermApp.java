package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.HasMetadata;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class JKubeTermApp extends Application {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "jkubeterm-kubernetes-io"); t.setDaemon(true); return t;
    });
    private final ComboBox<KubeconfigLoader.ContextRef> contexts = new ComboBox<>();
    private final ComboBox<String> namespaces = new ComboBox<>();
    private final ListView<ResourceKind> kinds = new ListView<>();
    private final TableView<HasMetadata> table = new TableView<>();
    private final TextArea details = new TextArea();
    private final TextArea console = new TextArea();
    private final Label status = new Label("Choose a context");
    private final TextField filter = new TextField();
    private final CheckBox editMode = new CheckBox("Edit YAML");
    private KubernetesService service;
    private Stage stage;
    private List<HasMetadata> currentItems = List.of();

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        contexts.setPrefWidth(265); namespaces.setPrefWidth(165);
        Button connect = new Button("Connect"); connect.setOnAction(e -> connect());
        Button reloadContexts = new Button("↻ Config"); reloadContexts.setOnAction(e -> loadContexts());
        Button refresh = new Button("Refresh"); refresh.setOnAction(e -> refresh());
        HBox top = new HBox(8, new Label("Context"), contexts, connect, reloadContexts, new Label("Namespace"), namespaces, refresh);
        top.setPadding(new Insets(10)); top.getStyleClass().add("toolbar");
        kinds.setItems(FXCollections.observableArrayList(ResourceKind.values())); kinds.setPrefWidth(180);
        kinds.getSelectionModel().selectedItemProperty().addListener((obs, old, kind) -> refresh());
        filter.setPromptText("Filter by name…"); filter.textProperty().addListener((obs, old, v) -> showItems());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<HasMetadata, String> name = column("Name", o -> o.getMetadata().getName());
        TableColumn<HasMetadata, String> namespace = column("Namespace", o -> o.getMetadata().getNamespace());
        TableColumn<HasMetadata, String> kind = column("Kind", HasMetadata::getKind);
        TableColumn<HasMetadata, String> age = column("Created", o -> o.getMetadata().getCreationTimestamp());
        table.getColumns().addAll(List.of(name, namespace, kind, age));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item != null && service != null) { details.setText(service.yaml(item)); editMode.setSelected(false); details.setEditable(false); }
        });
        details.setEditable(false); details.setWrapText(false); details.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
        console.setEditable(false); console.setWrapText(true); console.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
        editMode.selectedProperty().addListener((obs, old, enabled) -> details.setEditable(enabled));
        Button apply = new Button("Apply YAML"); apply.setOnAction(e -> applyYaml());
        Button remove = new Button("Delete"); remove.setOnAction(e -> deleteSelected());
        Button logs = new Button("Pod logs"); logs.setOnAction(e -> logs());
        Button shell = new Button("Exec command"); shell.setOnAction(e -> exec());
        Button forward = new Button("Port forward"); forward.setOnAction(e -> portForward());
        Button scale = new Button("Scale"); scale.setOnAction(e -> scale());
        Button restart = new Button("Restart"); restart.setOnAction(e -> restart());
        Button helm = new Button("Helm releases"); helm.setOnAction(e -> helm());
        Button save = new Button("Save YAML…"); save.setOnAction(e -> saveYaml());
        Button newYaml = new Button("New YAML"); newYaml.setOnAction(e -> { details.setText("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: example\ndata:\n  key: value\n"); editMode.setSelected(true); });
        FlowPane actions = new FlowPane(6, 6, editMode, apply, remove, logs, shell, forward, scale, restart, helm, save, newYaml);
        actions.setPadding(new Insets(8));
        VBox right = new VBox(8, actions, new Label("Manifest"), details, new Label("Output / logs"), console);
        VBox.setVgrow(details, Priority.ALWAYS); VBox.setVgrow(console, Priority.ALWAYS);
        details.setPrefRowCount(16); console.setPrefRowCount(9);
        VBox middle = new VBox(8, filter, table); VBox.setVgrow(table, Priority.ALWAYS);
        SplitPane content = new SplitPane(kinds, middle, right); content.setDividerPositions(.15, .55);
        BorderPane root = new BorderPane(content, top, null, new HBox(8, new Label("JKubeTerm 0.1"), status), null);
        root.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/jkubeterm.css")).toExternalForm());
        Scene scene = new Scene(root, 1380, 840);
        primaryStage.setTitle("JKubeTerm — Kubernetes Desktop"); primaryStage.setScene(scene); primaryStage.show();
        kinds.getSelectionModel().select(ResourceKind.PODS);
        namespaces.valueProperty().addListener((obs, old, value) -> refresh());
        loadContexts();
    }
    private TableColumn<HasMetadata, String> column(String label, java.util.function.Function<HasMetadata, String> value) {
        TableColumn<HasMetadata, String> c = new TableColumn<>(label);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(Optional.ofNullable(value.apply(cell.getValue())).orElse("")));
        return c;
    }
    private void loadContexts() {
        try {
            String home = System.getProperty("user.home");
            List<Path> paths = KubeconfigLoader.paths(System.getenv("KUBECONFIG"), home);
            var available = KubeconfigLoader.contexts(paths);
            contexts.setItems(FXCollections.observableArrayList(available));
            if (!available.isEmpty()) contexts.getSelectionModel().selectFirst();
            status.setText(available.size() + " contexts from " + paths.size() + " kubeconfig file(s)");
        } catch (Exception ex) { error("Cannot read kubeconfig", ex); }
    }
    private void connect() {
        var selected = contexts.getValue(); if (selected == null) { info("Select a kubeconfig context first."); return; }
        status.setText("Connecting to " + selected.name() + "…");
        task(() -> {
            KubernetesService next = new KubernetesService(selected);
            try {
                String version = next.version();
                var ns = next.namespaces();
                Platform.runLater(() -> {
                    KubernetesService old = service; service = next;
                    if (old != null) old.close();
                    namespaces.setItems(FXCollections.observableArrayList(ns));
                    namespaces.getSelectionModel().select(ns.contains("default") ? "default" : ns.isEmpty() ? null : ns.getFirst());
                    status.setText("Connected: " + selected.name() + " | Kubernetes " + version);
                    refresh();
                });
            } catch (Exception e) { next.close(); throw e; }
        });
    }
    private void refresh() {
        if (service == null || kinds.getSelectionModel().getSelectedItem() == null) return;
        ResourceKind kind = kinds.getSelectionModel().getSelectedItem(); String ns = namespaces.getValue();
        status.setText("Loading " + kind.label + "…");
        task(() -> {
            List<HasMetadata> result = List.copyOf(service.list(kind, ns));
            Platform.runLater(() -> { currentItems = result; showItems(); status.setText(result.size() + " " + kind.label + " | " + service.context().name()); });
        });
    }
    private void showItems() {
        String query = filter.getText().strip().toLowerCase(java.util.Locale.ROOT);
        table.setItems(FXCollections.observableArrayList(currentItems.stream().filter(i -> i.getMetadata() != null && i.getMetadata().getName() != null && i.getMetadata().getName().toLowerCase(java.util.Locale.ROOT).contains(query)).toList()));
    }
    private HasMetadata selected() { HasMetadata item = table.getSelectionModel().getSelectedItem(); if (item == null) info("Select a resource first."); return item; }
    private boolean confirm(String title, String body) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, body, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title); alert.setHeaderText(title);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void applyYaml() {
        if (service == null || details.getText().isBlank()) return;
        if (!confirm("Apply resource YAML", "Create or replace the resource in context " + service.context().name() + "? Review the manifest and namespace before continuing.")) return;
        String yaml = details.getText(), namespace = namespaces.getValue();
        task(() -> { service.apply(yaml, namespace); Platform.runLater(() -> { output("Applied YAML successfully.\n"); refresh(); }); });
    }
    private void deleteSelected() {
        var item = selected(); if (item == null || service == null) return;
        if (!confirm("Delete resource", "Delete " + item.getKind() + " / " + item.getMetadata().getName() + " from " + service.context().name() + "?")) return;
        task(() -> { service.delete(item); Platform.runLater(() -> { output("Delete request submitted.\n"); refresh(); }); });
    }
    private void logs() {
        var item = selected(); if (item == null || service == null) return;
        if (!"Pod".equals(item.getKind())) { info("Select a Pod to read logs."); return; }
        String ns = item.getMetadata().getNamespace(), pod = item.getMetadata().getName();
        task(() -> {
            List<String> containers = service.containers(ns, pod);
            Platform.runLater(() -> {
                if (containers.isEmpty()) { info("Pod has no regular containers."); return; }
                ChoiceDialog<String> dialog = new ChoiceDialog<>(containers.getFirst(), containers);
                dialog.setHeaderText("Container in " + pod);
                dialog.showAndWait().ifPresent(container -> task(() -> {
                    String log = service.logs(ns, pod, container, 500);
                    Platform.runLater(() -> output(log));
                }));
            });
        });
    }
    private void exec() {
        var item = selected(); if (item == null || service == null) return;
        if (!"Pod".equals(item.getKind())) { info("Select a Pod for exec."); return; }
        String ns = item.getMetadata().getNamespace(), pod = item.getMetadata().getName();
        TextInputDialog dialog = new TextInputDialog("/bin/sh"); dialog.setHeaderText("Container command (single executable, no shell parsing)");
        dialog.setContentText("Executable:");
        dialog.showAndWait().filter(s -> !s.isBlank()).ifPresent(command -> {
            var argv = ExternalTools.kubectl(service.context(), ns, "exec", pod, "--", command.strip());
            task(() -> { String result = ExternalTools.run(service.context(), argv, 30); Platform.runLater(() -> output(result)); });
        });
    }
    private void portForward() {
        var item = selected(); if (item == null || service == null) return;
        if (!List.of("Pod", "Service").contains(item.getKind())) { info("Select a Pod or Service."); return; }
        TextInputDialog dialog = new TextInputDialog("8080:80"); dialog.setHeaderText("Port forwarding: local:remote");
        dialog.showAndWait().ifPresent(ports -> {
            if (!ports.matches("[0-9]{1,5}:[0-9]{1,5}")) { info("Expected local:remote, e.g. 8080:80"); return; }
            String[] split = ports.split(":");
            int local = Integer.parseInt(split[0]), remote = Integer.parseInt(split[1]);
            if (local < 1 || local > 65535 || remote < 1 || remote > 65535) { info("Port must be 1–65535."); return; }
            var argv = ExternalTools.kubectl(service.context(), item.getMetadata().getNamespace(), "port-forward", "--address", "127.0.0.1", item.getKind().toLowerCase() + "/" + item.getMetadata().getName(), ports);
            try {
                ProcessBuilder builder = new ProcessBuilder(argv).redirectErrorStream(true);
                builder.environment().put("KUBECONFIG", service.context().file().toString());
                Process process = builder.start();
                portProcesses.add(process);
                output("Port-forward started on 127.0.0.1:" + local + " (PID " + process.pid() + "). It stops when JKubeTerm exits.\n");
                worker.submit(() -> { try { String msg = new String(process.getInputStream().readAllBytes()); Platform.runLater(() -> output(msg)); } catch (Exception ignored) {} });
            } catch (Exception ex) { error("Port forwarding failed (kubectl required)", ex); }
        });
    }
    private final java.util.List<Process> portProcesses = new java.util.concurrent.CopyOnWriteArrayList<>();
    private void scale() {
        var item = selected(); if (item == null || service == null) return;
        if (!"Deployment".equals(item.getKind())) { info("Select a Deployment."); return; }
        TextInputDialog d = new TextInputDialog("1"); d.setHeaderText("Set replica count for " + item.getMetadata().getName());
        d.showAndWait().ifPresent(value -> {
            try {
                int replicas = Integer.parseInt(value);
                if (replicas < 0) throw new NumberFormatException();
                if (confirm("Scale Deployment", "Scale " + item.getMetadata().getName() + " to " + replicas + " replicas?"))
                    task(() -> { service.scaleDeployment(item.getMetadata().getNamespace(), item.getMetadata().getName(), replicas); Platform.runLater(this::refresh); });
            } catch (NumberFormatException ex) { info("Enter a non-negative integer."); }
        });
    }
    private void restart() {
        var item = selected(); if (item == null || service == null) return;
        if (!"Deployment".equals(item.getKind())) { info("Select a Deployment."); return; }
        if (confirm("Restart Deployment", "Roll out a restart of " + item.getMetadata().getName() + "?"))
            task(() -> { service.restartDeployment(item.getMetadata().getNamespace(), item.getMetadata().getName()); Platform.runLater(this::refresh); });
    }
    private void helm() {
        if (service == null) return;
        var args = ExternalTools.helm(service.context(), namespaces.getValue() == null ? "default" : namespaces.getValue(), "list", "--all");
        task(() -> { String result = ExternalTools.run(service.context(), args, 30); Platform.runLater(() -> output(result)); });
    }
    private void saveYaml() {
        if (details.getText().isBlank()) return;
        FileChooser chooser = new FileChooser(); chooser.setInitialFileName("resource.yaml");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("YAML", "*.yaml", "*.yml"));
        var file = chooser.showSaveDialog(stage);
        if (file != null) try { Files.writeString(file.toPath(), details.getText()); } catch (Exception ex) { error("Save failed", ex); }
    }
    private void output(String message) { console.setText(message); }
    private void info(String message) { Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK); alert.setHeaderText(null); alert.showAndWait(); }
    private void error(String title, Throwable ex) { status.setText(title + ": " + ex.getMessage()); Alert alert = new Alert(Alert.AlertType.ERROR, ex.getMessage() == null ? ex.toString() : ex.getMessage(), ButtonType.OK); alert.setTitle(title); alert.setHeaderText(title); alert.show(); }
    private void task(ThrowingAction action) { worker.submit(() -> { try { action.run(); } catch (Exception e) { Platform.runLater(() -> error("Kubernetes operation failed", e)); } }); }
    @FunctionalInterface private interface ThrowingAction { void run() throws Exception; }
    @Override public void stop() {
        for (Process process : portProcesses) if (process.isAlive()) process.destroy();
        worker.shutdownNow(); if (service != null) service.close();
    }
    @SuppressWarnings("unused")
    public static void main(String[] args) { launch(args); }
}
