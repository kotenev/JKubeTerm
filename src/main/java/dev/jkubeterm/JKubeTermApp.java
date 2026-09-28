package dev.jkubeterm;

import io.fabric8.kubernetes.api.model.HasMetadata;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    private final TreeView<String> objectView = new TreeView<>();
    private final VBox hoodBox = new VBox(4);
    private final VBox advisorBox = new VBox(4);
    private final Label hoodText = new Label("Select a resource to see what happens under the hood.");
    private final Label catalogHint = new Label();
    private final java.util.Map<TreeItem<String>, ResourceInspector.ExportableEntry> exportableNodes = new java.util.WeakHashMap<>();
    private final java.util.Map<TreeItem<String>, DrillDown.Target> drillNodes = new java.util.WeakHashMap<>();
    private final Label status = new Label("Choose a context");
    private final TextField filter = new TextField();
    private final CheckBox editMode = new CheckBox("Edit YAML");
    private KubernetesService service;
    private Stage stage;
    private List<HasMetadata> currentItems = List.of();
    private Button connectButton;
    private Button reloadContextsButton;
    private Button refreshButton;
    private Button applyButton;
    private Button removeButton;
    private Button logsButton;
    private Button shellButton;
    private Button forwardButton;
    private Button scaleButton;
    private Button restartButton;
    private Button helmButton;
    private Button saveButton;
    private Button newYamlButton;
    private Button diagnoseButton;
    private FlowPane actionsPane;
    private Label tutorialHint;

    @Override public void start(Stage primaryStage) {
        stage = primaryStage;
        contexts.setPrefWidth(265); namespaces.setPrefWidth(165);
        connectButton = new Button("Connect"); connectButton.setOnAction(e -> connect());
        reloadContextsButton = new Button("↻ Config"); reloadContextsButton.setOnAction(e -> loadContexts());
        refreshButton = new Button("Refresh"); refreshButton.setOnAction(e -> refresh());
        diagnoseButton = new Button("Diagnose"); diagnoseButton.setOnAction(e -> diagnose());
        HBox top = new HBox(8, new Label("Context"), contexts, connectButton, reloadContextsButton, new Label("Namespace"), namespaces, refreshButton, diagnoseButton);
        top.setPadding(new Insets(10)); top.getStyleClass().add("toolbar");
        kinds.setItems(FXCollections.observableArrayList(ResourceKind.values())); kinds.setPrefWidth(180);
        kinds.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(ResourceKind item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                SVGPath icon = new SVGPath();
                icon.setContent(ClusterVisuals.svgPath(item));
                icon.setFill(javafx.scene.paint.Color.web(ClusterVisuals.colorHex(item)));
                icon.setScaleX(0.85); icon.setScaleY(0.85);
                Label label = new Label(item.label, icon);
                label.setContentDisplay(ContentDisplay.LEFT);
                label.setGraphicTextGap(8);
                setGraphic(label);
                setText(null);
            }
        });
        kinds.getSelectionModel().selectedItemProperty().addListener((obs, old, selectedKind) -> { showCatalogHint(selectedKind); refresh(); });
        filter.setPromptText("Filter by name…"); filter.textProperty().addListener((obs, old, v) -> showItems());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<HasMetadata, String> name = column("Name", o -> o.getMetadata().getName());
        TableColumn<HasMetadata, String> namespace = column("Namespace", o -> o.getMetadata().getNamespace());
        TableColumn<HasMetadata, String> kind = column("Kind", HasMetadata::getKind);
        TableColumn<HasMetadata, String> age = column("Created", o -> o.getMetadata().getCreationTimestamp());
        table.getColumns().addAll(List.of(name, namespace, kind, age));
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> {
            if (item != null && service != null) {
                details.setText(service.yaml(item));
                showObject(item);
                editMode.setSelected(false); details.setEditable(false); advanceTutorial("select-row");
            }
        });
        objectView.setRoot(new TreeItem<>("Select a resource"));
        objectView.setShowRoot(true);
        hoodText.setWrapText(true);
        hoodBox.getChildren().add(hoodText);
        catalogHint.setWrapText(true);
        catalogHint.getStyleClass().add("catalog-hint");
        objectView.setOnMouseClicked(click -> {
            if (click.getClickCount() != 2) return;
            TreeItem<String> selected = objectView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            ResourceInspector.ExportableEntry entry = exportableNodes.get(selected);
            if (entry != null) { exportEntry(entry); return; }
            DrillDown.Target target = drillNodes.get(selected);
            if (target != null) followTarget(target);
        });
        details.setEditable(false); details.setWrapText(false); details.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
        console.setEditable(false); console.setWrapText(true); console.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px;");
        editMode.selectedProperty().addListener((obs, old, enabled) -> { details.setEditable(enabled); if (enabled) advanceTutorial("edit-mode"); });
        Button apply = new Button("Apply YAML"); apply.setOnAction(e -> applyYaml());
        Button remove = new Button("Delete"); remove.setOnAction(e -> deleteSelected());
        Button logs = new Button("Pod logs"); logs.setOnAction(e -> logs());
        Button shell = new Button("Exec command"); shell.setOnAction(e -> exec());
        Button forward = new Button("Port forward"); forward.setOnAction(e -> portForward());
        Button scale = new Button("Scale"); scale.setOnAction(e -> scale());
        Button restart = new Button("Restart"); restart.setOnAction(e -> restart());
        Button helm = new Button("Helm releases"); helm.setOnAction(e -> helm());
        Button save = new Button("Save YAML…"); save.setOnAction(e -> saveYaml());
        Button newYaml = new Button("New YAML"); newYaml.setOnAction(e -> { details.setText("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: example\ndata:\n  key: value\n"); editMode.setSelected(true); advanceTutorial("new-yaml"); });
        applyButton = apply; removeButton = remove; logsButton = logs; shellButton = shell;
        forwardButton = forward; scaleButton = scale; restartButton = restart;
        helmButton = helm; saveButton = save; newYamlButton = newYaml;
        FlowPane actions = new FlowPane(6, 6, editMode, apply, remove, logs, shell, forward, scale, restart, helm, save, newYaml);
        actions.setPadding(new Insets(8));
        actionsPane = actions;
        VBox right = new VBox(8, actions, new Label("Manifest"), details, new Label("Object"), objectView, new Label("Under the hood"), hoodBox, new Label("Best practices"), advisorBox, new Label("Output / logs"), console);
        VBox.setVgrow(details, Priority.ALWAYS); VBox.setVgrow(objectView, Priority.ALWAYS); VBox.setVgrow(console, Priority.ALWAYS);
        details.setPrefRowCount(8); objectView.setPrefHeight(180); console.setPrefRowCount(6);
        VBox middle = new VBox(8, catalogHint, filter, table); VBox.setVgrow(table, Priority.ALWAYS);
        SplitPane content = new SplitPane(kinds, middle, right); content.setDividerPositions(.15, .55);
        table.setRowFactory(view -> {
            TableRow<HasMetadata> row = new TableRow<>();
            row.setOnMouseClicked(click -> {
                if (click.getClickCount() == 2 && !row.isEmpty() && service != null) drillDown(row.getItem());
            });
            ContextMenu menu = new ContextMenu();
            MenuItem drill = new MenuItem("Drill down…");
            drill.setOnAction(e -> { if (!row.isEmpty() && service != null) drillDown(row.getItem()); });
            menu.getItems().add(drill);
            row.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            return row;
        });
        Menu helpMenu = new Menu("Help");
        MenuItem userGuide = new MenuItem("User guide"); userGuide.setOnAction(e -> help("User guide", userGuideText()));
        MenuItem adminGuide = new MenuItem("Admin guide"); adminGuide.setOnAction(e -> help("Admin guide", adminGuideText()));
        MenuItem quickstart = new MenuItem("QuickStart — minikube home lab"); quickstart.setOnAction(e -> help("QuickStart", quickstartText()));
        MenuItem about = new MenuItem("About JKubeTerm"); about.setOnAction(e -> help("About JKubeTerm", aboutText()));
        helpMenu.getItems().addAll(userGuide, adminGuide, quickstart, new SeparatorMenuItem(), about);
        Menu tutorialMenu = new Menu("Tutorial");
        MenuItem guidedTour = new MenuItem("Start guided tour"); guidedTour.setOnAction(e -> startTutorial(Tutorial.guidedTour()));
        MenuItem firstDeploy = new MenuItem("First deploy drill"); firstDeploy.setOnAction(e -> startTutorial(Tutorial.firstDeploy()));
        MenuItem debugFlow = new MenuItem("Debug flow drill"); debugFlow.setOnAction(e -> startTutorial(Tutorial.debugFlow()));
        MenuItem stopTutorial = new MenuItem("Stop tutorial"); stopTutorial.setOnAction(e -> stopTutorial());
        tutorialMenu.getItems().addAll(guidedTour, firstDeploy, debugFlow, new SeparatorMenuItem(), stopTutorial);
        MenuBar menuBar = new MenuBar(helpMenu, tutorialMenu); menuBar.setUseSystemMenuBar(true);
        tutorialHint = new Label();
        tutorialHint.setWrapText(true);
        tutorialHint.setVisible(false);
        tutorialHint.setManaged(false);
        tutorialHint.getStyleClass().add("tutorial-hint");
        BorderPane root = new BorderPane(content, new VBox(menuBar, top, tutorialHint), null, new HBox(8, new Label("JKubeTerm 0.1"), status), null);
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
        output("Connecting to context '" + selected.name() + "' [" + selected.file() + "]…\n");
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
                    output("Connected: " + selected.name() + " | Kubernetes " + version + " | namespaces: " + ns.size() + ".\n");
                    refresh();
                    advanceTutorial("connect");
                });
            } catch (Exception e) {
                String report = ConnectionDiagnostics.describe(selected, e);
                Platform.runLater(() -> output(report));
                next.close();
                throw e;
            }
        });
    }
    private void diagnose() {
        var selected = contexts.getValue(); if (selected == null) { info("Select a kubeconfig context first."); return; }
        status.setText("Diagnosing " + selected.name() + "…");
        output("Diagnosing context '" + selected.name() + "' [" + selected.file() + "]…\n");
        task(() -> {
            String report = ConnectionDiagnostics.diagnose(selected);
            Platform.runLater(() -> { output(report); status.setText("Diagnosis of " + selected.name() + " done."); });
        });
    }
    private void refresh() {
        if (kinds.getSelectionModel().getSelectedItem() == null) { status.setText("Pick a kind on the left first."); return; }
        if (service == null) { status.setText("Not connected — press Connect first."); output("Not connected. Pick a context and press Connect (or Diagnose for details).\n"); return; }
        ResourceKind kind = kinds.getSelectionModel().getSelectedItem(); String ns = namespaces.getValue();
        status.setText("Loading " + kind.label + "…");
        task(() -> {
            List<HasMetadata> result = List.copyOf(service.list(kind, ns));
            Platform.runLater(() -> {
                currentItems = result;
                showItems();
                String where = kind.namespaced ? "ns=" + service.namespaceOrDefault(ns) : "cluster-scope";
                status.setText(result.size() + " " + kind.label + " | " + service.context().name());
                if (result.isEmpty())
                    output("Connected to '" + service.context().name() + "'. " + kind.label + " is empty (" + where + "). " + emptyHint(kind) + "\n");
                advanceTutorial("refresh");
            });
        });
    }
    private String emptyHint(ResourceKind kind) {
        return switch (kind) {
            case PODS -> "Try Namespace kube-system (system pods live there) or create a workload — empty default is normal on a fresh minikube.";
            case NODES, NAMESPACES -> "Empty here means a permissions or API problem — press Diagnose.";
            default -> "Empty is a valid answer — it means the API call succeeded with zero items. Press Diagnose if you expected rows.";
        };
    }
    private void showItems() {
        String query = filter.getText().strip().toLowerCase(java.util.Locale.ROOT);
        table.setItems(FXCollections.observableArrayList(currentItems.stream().filter(i -> i.getMetadata() != null && i.getMetadata().getName() != null && i.getMetadata().getName().toLowerCase(java.util.Locale.ROOT).contains(query)).toList()));
    }
    private void showCatalogHint(ResourceKind kind) {
        if (kind == null) return;
        SVGPath icon = new SVGPath();
        icon.setContent(ClusterVisuals.svgPath(kind));
        icon.setFill(javafx.scene.paint.Color.web(ClusterVisuals.colorHex(kind)));
        icon.setScaleX(0.7); icon.setScaleY(0.7);
        String text = ClusterVisuals.explain(kind);
        catalogHint.setText(text);
        catalogHint.setGraphic(icon);
    }
    private void showObject(HasMetadata item) {
        showCatalogHint(DrillDown.kindForKindName(item.getKind()).orElse(kinds.getSelectionModel().getSelectedItem()));
        ResourceInspector.Inspection inspection = ResourceInspector.inspect(item);
        TreeItem<String> root = new TreeItem<>(inspection.sections().isEmpty() ? item.getKind() + " " + item.getMetadata().getName()
            : item.getKind() + " " + item.getMetadata().getName() + " — " + inspection.sections().size() + " sections, " + inspection.relations().size() + " links");
        root.setExpanded(true);
        for (ResourceInspector.Section section : inspection.sections()) {
            TreeItem<String> sectionNode = new TreeItem<>("§ " + section.title());
            sectionNode.setExpanded(section.rows().size() <= 12);
            for (ResourceInspector.Row row : section.rows()) {
                TreeItem<String> rowNode = new TreeItem<>(row.field() + (row.value().isEmpty() ? "" : ": " + row.value()));
                ResourceInspector.ExportableEntry entry = exportableEntry(item, section, row);
                if (entry != null) {
                    rowNode.setValue("⤓ " + rowNode.getValue());
                    exportableNodes.put(rowNode, entry);
                }
                sectionNode.getChildren().add(rowNode);
            }
            root.getChildren().add(sectionNode);
        }
        if (!inspection.relations().isEmpty()) {
            TreeItem<String> graph = new TreeItem<>("⇄ Relations (double-click to drill)");
            graph.setExpanded(true);
            for (ResourceInspector.Relation relation : inspection.relations()) {
                if (relation.to() == null || relation.to().isEmpty()) continue;
                TreeItem<String> link = new TreeItem<>(relation.from() + " —[" + relation.label() + "]→ " + relation.to());
                relationKind(relation).ifPresent(kind ->
                    drillNodes.put(link, DrillDown.Target.findByName("Open " + relation.to(), "Find " + relation.to() + " in the catalog.", null, relation.to())));
                graph.getChildren().add(link);
            }
            if (!graph.getChildren().isEmpty()) root.getChildren().add(graph);
        }
        objectView.setRoot(root);
        showHoodAndAdvice(item);
    }
    private void showHoodAndAdvice(HasMetadata item) {
        hoodBox.getChildren().clear();
        advisorBox.getChildren().clear();
        Label hood = new Label(hoodTextFor(item));
        hood.setWrapText(true);
        hoodBox.getChildren().add(hood);
        List<ClusterAdvisor.Finding> findings = ClusterAdvisor.advise(item);
        if (findings.isEmpty()) {
            Label ok = new Label("✓ No best-practice issues detected for this object.");
            ok.getStyleClass().add("advisor-ok");
            advisorBox.getChildren().add(ok);
            return;
        }
        for (ClusterAdvisor.Finding finding : findings) {
            HBox row = new HBox(6);
            Label badge = new Label(switch (finding.severity()) {
                case CRITICAL -> "⛔";
                case WARN -> "⚠️";
                case INFO -> "ℹ️";
            });
            VBox text = new VBox(2);
            Label message = new Label(finding.message());
            message.setWrapText(true);
            Label fix = new Label("Fix: " + finding.fix());
            fix.setWrapText(true);
            fix.getStyleClass().add("advisor-fix");
            text.getChildren().addAll(message, fix);
            HBox.setHgrow(text, Priority.ALWAYS);
            row.getChildren().addAll(badge, text);
            row.getStyleClass().add("advisor-" + finding.severity().name().toLowerCase());
            advisorBox.getChildren().add(row);
        }
    }
    private String hoodTextFor(HasMetadata item) {
        if (item instanceof io.fabric8.kubernetes.api.model.Pod pod) return ClusterAdvisor.explainHood(pod);
        String kind = item.getKind();
        String name = item.getMetadata() == null ? "" : item.getMetadata().getName();
        if (kind == null) return ClusterVisuals.explainKind(null);
        return switch (kind) {
            case "Deployment" -> "Deployment '" + name + "' owns a ReplicaSet, which keeps N Pod copies alive. kube-controller-manager reconciles the count; kube-scheduler places each Pod; kubelet starts the containers.";
            case "StatefulSet" -> "StatefulSet '" + name + "' gives each Pod a stable name plus its own PVC (data-" + name + "-N), created and deleted strictly in order.";
            case "DaemonSet" -> "DaemonSet '" + name + "' ensures one Pod copy on every (matching) node — new nodes get one automatically.";
            case "Service" -> "Service '" + name + "' is a stable virtual IP plus DNS; kube-proxy on every node programs the forwarding to the backing Pods selected by labels.";
            case "Ingress" -> "Ingress '" + name + "' is a routing rule (host plus path → Service). The ingress-nginx controller watches it and reconfigures nginx.";
            case "Job" -> "Job '" + name + "' runs Pods until completions are reached, retrying up to backoffLimit — then stops.";
            case "CronJob" -> "CronJob '" + name + "' creates a Job on each schedule tick, keeping limited history.";
            case "ConfigMap" -> "ConfigMap '" + name + "' injects plain-text config as env or files. Env needs a Pod restart; files sync within about a minute.";
            case "PersistentVolumeClaim" -> "Claim '" + name + "' waits for a matching PersistentVolume (or a provisioner to create one), then binds 1:1.";
            case "PersistentVolume" -> "Volume '" + name + "' is cluster disk surviving Pods; a claim binds to it exclusively until released.";
            case "Node" -> "Node '" + name + "' runs kubelet plus containerd: it pulls images, starts containers, and reports capacity and conditions.";
            case "Namespace" -> "Namespace '" + name + "' scopes names, RBAC, quotas and policies — a folder, not a boundary for the network.";
            case "Event" -> "Event '" + name + "' is a timestamped note from a controller about another object — newest-first is the debugging order.";
            default -> ClusterVisuals.explainKind(kind);
        };
    }
    private Optional<ResourceKind> relationKind(ResourceInspector.Relation relation) {
        return DrillDown.relationTargetKind(relation.label());
    }
    private void drillDown(HasMetadata item) {
        List<DrillDown.Target> targets = DrillDown.targets(item);
        if (targets.isEmpty()) { info("No drill-down targets for " + item.getKind() + " " + item.getMetadata().getName() + "."); return; }
        ChoiceDialog<DrillDown.Target> dialog = new ChoiceDialog<>(targets.getFirst(), targets);
        dialog.setTitle("Drill down");
        dialog.setHeaderText(item.getKind() + " " + item.getMetadata().getName() + " → related resources");
        dialog.setContentText("Target:");
        dialog.showAndWait().ifPresent(this::followTarget);
    }
    private void followTarget(DrillDown.Target target) {
        if (service == null) return;
        if (target.mode() == DrillDown.Mode.NAMESPACE_SWITCH) {
            namespaces.getSelectionModel().select(target.namespace());
            kinds.getSelectionModel().select(ResourceKind.PODS);
            refresh();
            return;
        }
        status.setText("Drilling to " + target.title() + "…");
        String currentNs = namespaces.getValue();
        task(() -> {
            List<HasMetadata> found = DrillDown.resolve(service, target, currentNs);
            Platform.runLater(() -> followTargetResult(target, found));
        });
    }
    private void followTargetResult(DrillDown.Target target, List<HasMetadata> found) {
        if (found.isEmpty()) {
            info("Nothing found for '" + target.title() + "' in scope " + (target.namespace() != null ? target.namespace() : service.namespaceOrDefault(namespaces.getValue())) + ".");
            return;
        }
        if (target.mode() == DrillDown.Mode.FIND_BY_NAME && target.kind() == null) {
            String scope = target.namespace() != null ? target.namespace() : namespaces.getValue();
            status.setText("Drilling to '" + target.exactName() + "'…");
            task(() -> {
                Optional<DrillDown.Found> hit = DrillDown.findByName(service, target.exactName(), scope);
                Platform.runLater(() -> hit.ifPresentOrElse(found1 -> selectFound(found1.kind(), found1.item()),
                    () -> info("'" + target.exactName() + "' not found in browsable kinds.")));
            });
            return;
        }
        if (target.mode() == DrillDown.Mode.SELECTING || target.mode() == DrillDown.Mode.USED_BY || target.mode() == DrillDown.Mode.BACKEND) {
            selectFound(target.kind(), found.getFirst());
            status.setText(found.size() + " match(es) for '" + target.title() + "' — showing first.");
            return;
        }
        if (found.size() == 1) { selectFound(target.kind(), found.getFirst()); return; }
        List<String> labels = found.stream().map(item -> item.getKind() + " " + (item.getMetadata() == null ? "" : item.getMetadata().getName())).toList();
        ChoiceDialog<String> pick = new ChoiceDialog<>(labels.getFirst(), labels);
        pick.setTitle("Drill down");
        pick.setHeaderText(found.size() + " matches for '" + target.title() + "'");
        pick.setContentText("Resource:");
        pick.showAndWait().ifPresent(label -> {
            HasMetadata item = found.get(labels.indexOf(label));
            DrillDown.kindForKindName(item.getKind()).ifPresentOrElse(kind -> selectFound(kind, item), () -> selectFound(target.kind(), item));
        });
    }
    private void selectFound(ResourceKind kind, HasMetadata item) {
        if (kind != null) kinds.getSelectionModel().select(kind);
        if (kinds.getSelectionModel().getSelectedItem() != null && item.getMetadata() != null
            && item.getMetadata().getNamespace() != null && kinds.getSelectionModel().getSelectedItem().namespaced)
            namespaces.getSelectionModel().select(item.getMetadata().getNamespace());
        refresh();
        task(() -> Platform.runLater(() -> {
            for (HasMetadata row : table.getItems())
                if (row.getMetadata() != null && Objects.equals(row.getMetadata().getName(), item.getMetadata().getName())) {
                    table.getSelectionModel().select(row);
                    table.scrollTo(row);
                    break;
                }
        }));
    }
    private ResourceInspector.ExportableEntry exportableEntry(HasMetadata item, ResourceInspector.Section section, ResourceInspector.Row row) {
        if (!"Data".equals(section.title()) || !row.field().startsWith("key ")) return null;
        String key = row.field().substring("key ".length());
        return ResourceInspector.exportableEntries(item).stream().filter(e -> e.key().equals(key)).findFirst().orElse(null);
    }
    private void exportEntry(ResourceInspector.ExportableEntry entry) {
        FileChooser chooser = new FileChooser();
        chooser.setInitialFileName(entry.key());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("All files", "*.*"));
        var file = chooser.showSaveDialog(stage);
        if (file == null) return;
        try {
            Files.writeString(file.toPath(), entry.value());
            output("Saved " + entry.kind() + " " + entry.name() + " key '" + entry.key() + "' to " + file + ".\n");
        } catch (Exception ex) { error("Save failed", ex); }
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
        task(() -> { service.apply(yaml, namespace); Platform.runLater(() -> { output("Applied YAML successfully.\n"); refresh(); advanceTutorial("apply"); }); });
    }
    private void deleteSelected() {
        var item = selected(); if (item == null || service == null) return;
        if (!confirm("Delete resource", "Delete " + item.getKind() + " / " + item.getMetadata().getName() + " from " + service.context().name() + "?")) return;
        task(() -> { service.delete(item); Platform.runLater(() -> { output("Delete request submitted.\n"); refresh(); advanceTutorial("delete"); }); });
    }
    private void logs() {
        var item = selected(); if (item == null || service == null) return;
        if (!"Pod".equals(item.getKind())) { info("Select a Pod to read logs."); return; }
        advanceTutorial("logs");
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
        advanceTutorial("exec");
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
        advanceTutorial("forward");
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
        // Helm 4 removed `list --all` (all statuses are listed by default, -A = all namespaces).
        var args = ExternalTools.helm(service.context(), namespaces.getValue() == null ? "default" : namespaces.getValue(), "list");
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
    private Tutorial tutorial;
    private int tutorialIndex = -1;
    private final List<Node> tutorialHighlighted = new ArrayList<>();
    private Button tutorialNextButton;
    private Button tutorialSkipButton;
    private void startTutorial(Tutorial tour) {
        stopTutorial();
        tutorial = tour;
        tutorialIndex = -1;
        nextTutorialStep();
    }
    private void stopTutorial() {
        tutorial = null;
        tutorialIndex = -1;
        clearTutorialHighlight();
        if (tutorialHint != null) { tutorialHint.setText(""); tutorialHint.setVisible(false); tutorialHint.setManaged(false); }
        if (tutorialNextButton != null) { actionsPane.getChildren().remove(tutorialNextButton); tutorialNextButton = null; }
        if (tutorialSkipButton != null) { actionsPane.getChildren().remove(tutorialSkipButton); tutorialSkipButton = null; }
    }
    private void nextTutorialStep() {
        if (tutorial == null) return;
        tutorialIndex++;
        if (tutorialIndex >= tutorial.steps().size()) {
            String done = tutorial.doneText();
            stopTutorial();
            info(done);
            return;
        }
        showTutorialStep(tutorial.steps().get(tutorialIndex));
    }
    private void showTutorialStep(Tutorial.Step step) {
        clearTutorialHighlight();
        List<Node> targets = new ArrayList<>();
        for (String id : step.targetIds()) {
            Node node = tutorialNode(id);
            if (node != null) targets.add(node);
        }
        for (Node node : targets) {
            node.getStyleClass().add("tutorial-target");
            tutorialHighlighted.add(node);
        }
        tutorialHint.setText("Tutorial " + (tutorialIndex + 1) + "/" + tutorial.steps().size() + " — " + step.title() + "\n" + step.body());
        tutorialHint.setVisible(true);
        tutorialHint.setManaged(true);
        ensureTutorialButtons();
        String nextLabel = tutorialIndex + 1 >= tutorial.steps().size() ? "Finish" : "Next";
        tutorialNextButton.setText(nextLabel);
        for (Node node : targets) node.requestFocus();
    }
    private void ensureTutorialButtons() {
        if (tutorialNextButton == null) {
            tutorialNextButton = new Button("Next");
            tutorialNextButton.setOnAction(e -> nextTutorialStep());
            actionsPane.getChildren().add(tutorialNextButton);
        }
        if (tutorialSkipButton == null) {
            tutorialSkipButton = new Button("Exit tutorial");
            tutorialSkipButton.setOnAction(e -> stopTutorial());
            actionsPane.getChildren().add(tutorialSkipButton);
        }
    }
    private void clearTutorialHighlight() {
        for (Node node : tutorialHighlighted) node.getStyleClass().remove("tutorial-target");
        tutorialHighlighted.clear();
    }
    private void advanceTutorial(String event) {
        if (tutorial == null || tutorialIndex < 0 || tutorialIndex >= tutorial.steps().size()) return;
        Tutorial.Step step = tutorial.steps().get(tutorialIndex);
        if (step.advanceOn().contains(event)) nextTutorialStep();
    }
    private void help(String title, String body) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, body, ButtonType.OK);
        alert.setTitle(title); alert.setHeaderText(title);
        alert.getDialogPane().setMinWidth(560);
        alert.show();
    }
    private String userGuideText() {
        return """
            Connect: pick a kubeconfig context, press Connect, then Refresh.
            Browse: Kinds list + Namespace combo + name filter (local, case-insensitive).
            Manifest: select a row to view YAML, tick Edit YAML to change it, Apply YAML to server-side apply.
            Pod logs: Pod only — pick a container, last 500 lines (no streaming).
            Exec: Pod only — single executable, no shell parsing, 30 s timeout.
            Port forward: Pod/Service, local:remote (127.0.0.1 only), stops when the app exits.
            Scale/Restart: Deployments only. Helm releases: listing only (helm list).
            Docs: http://127.0.0.1:8000/guides/ (QuickStart, user guide, admin guide + PDFs).""";
    }
    private String adminGuideText() {
        return """
            JKubeTerm uses your kubeconfig RBAC — no privilege escalation.
            Cluster views (Nodes, Namespaces, PersistentVolumes) need cluster-scope read.
            kubectl and helm must be on PATH for Exec / port-forward / Helm releases.
            Port forwards bind 127.0.0.1 only and die with the app (stop() destroys processes).
            Apply YAML is server-side apply; unknown cluster-scoped kinds fail server-side (no API discovery).
            Secrets shown in YAML stay local — Save YAML writes them to disk, never commit them.
            Admin runbook: http://127.0.0.1:8000/guides/admin-guide/ (RBAC, GitOps, TLS, backups, upgrades).""";
    }
    private String quickstartText() {
        return """
            Home lab: minikube start --driver=docker --cpus=4 --memory=8192 --disk-size=40g
            1. Connect JKubeTerm to the minikube context, Namespace default, Pods + Refresh.
            2. minikube addons enable ingress metrics-server storage-provisioner.
            3. helm install: cert-manager, ingress-nginx, argocd (v3.4.8 manifest),
               kube-prometheus-stack 91.x (monitoring), headlamp.
            4. ArgoCD admin password: kubectl -n argocd get secret argocd-initial-admin-secret.
            5. UIs via JKubeTerm Port forward (127.0.0.1): ArgoCD 8080:80, Grafana 3000:80.
            Full guide: http://127.0.0.1:8000/guides/quickstart-minikube/ (+ PDF).""";
    }
    private String aboutText() {
        return """
            JKubeTerm 0.1 — JavaFX 21 desktop Kubernetes client (Fabric8 7.9.0).
            Blocking I/O on worker jkubeterm-kubernetes-io, UI updates via Platform.runLater.
            Docs: http://127.0.0.1:8000/ — architecture, diagrams, guides.""";
    }
    private Node tutorialNode(String id) {
        return switch (id) {
            case "contexts" -> contexts;
            case "connect" -> connectButton;
            case "reload-contexts" -> reloadContextsButton;
            case "namespaces" -> namespaces;
            case "refresh" -> refreshButton;
            case "kinds" -> kinds;
            case "filter" -> filter;
            case "table" -> table;
            case "details" -> details;
            case "objectView" -> objectView;
            case "hood" -> hoodBox;
            case "advisor" -> advisorBox;
            case "editMode" -> editMode;
            case "apply" -> applyButton;
            case "remove" -> removeButton;
            case "logs" -> logsButton;
            case "shell" -> shellButton;
            case "forward" -> forwardButton;
            case "scale" -> scaleButton;
            case "restart" -> restartButton;
            case "helm" -> helmButton;
            case "save" -> saveButton;
            case "newYaml" -> newYamlButton;
            case "diagnose" -> diagnoseButton;
            case "console" -> console;
            case "status" -> status;
            default -> null;
        };
    }
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
