package tr.com.envapp.terminal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class CommandPromptPane {

  private static final String JAVA_BASE_DIR = "C:/Program Files/Java";
  private static final Path CONFIG_FILE = Path.of("data", "terminal-config.json");

  private TextArea outputArea;
  private TextField pathField;
  private ComboBox<String> shellCombo;
  private ComboBox<String> javaCombo;
  private Button browseBtn;
  private Button loadBtn;
  private Process process;
  private BufferedWriter processWriter;
  private String currentShell = "cmd";
  private volatile Thread workerThread;

  private final ObjectMapper objectMapper = new ObjectMapper();

  public Pane build() {
    initComponents();
    return buildLayout();
  }

  public void shutdown() {
    killProcessTree();
  }

  private void initComponents() {
    outputArea = new TextArea();
    outputArea.setEditable(false);
    outputArea.setWrapText(false);
    outputArea.setStyle(
        "-fx-control-inner-background: #0c0c0c; " +
            "-fx-text-fill: #cccccc; " +
            "-fx-font-family: 'Consolas'; " +
            "-fx-font-size: 13px; " +
            "-fx-highlight-fill: #264f78;"
    );

    pathField = new TextField();
    pathField.setEditable(false);
    pathField.setPrefWidth(450);
    pathField.setStyle(
        "-fx-control-inner-background: #2b2b2b; -fx-text-fill: #e0e0e0; " +
            "-fx-font-family: 'Consolas'; -fx-font-size: 12px; " +
            "-fx-prompt-text-fill: #555555;"
    );

    javaCombo = new ComboBox<>();
    javaCombo.setStyle("-fx-font-size: 12px; -fx-font-family: 'Consolas';");
    javaCombo.setPromptText("Java seçin...");
    javaCombo.setPrefWidth(300);
    loadJavaVersions();

    SavedConfig initialConfig = loadInitialConfig();
    if (initialConfig != null) {
      pathField.setText(initialConfig.path);
      if (initialConfig.java != null && !initialConfig.java.isBlank()) {
        if (!javaCombo.getItems().contains(initialConfig.java)) {
          javaCombo.getItems().add(initialConfig.java);
        }
        javaCombo.setValue(initialConfig.java);
      }
      currentShell = normalizeShell(initialConfig.shell);
    }
  }

  private Pane buildLayout() {
    VBox root = new VBox(6);
    root.setPadding(new Insets(10));
    root.setStyle("-fx-background-color: #1e1e1e;");

    // ── Row 1: Shell + Path ───────────────────────────────────
    Label shellLabel = new Label("Shell:");
    shellLabel.setStyle("-fx-text-fill: #bbbbbb; -fx-font-size: 12px; -fx-font-weight: bold;");

    shellCombo = new ComboBox<>();
    shellCombo.getItems().addAll("cmd", "powershell");
    shellCombo.setValue(currentShell);
    shellCombo.setStyle("-fx-font-size: 12px; -fx-font-family: 'Consolas';");
    shellCombo.setOnAction(e -> {
      String selected = shellCombo.getValue();
      if (selected != null && !selected.equals(currentShell)) {
        currentShell = selected;
      }
    });

    Label pathLabel = new Label("Path:");
    pathLabel.setStyle("-fx-text-fill: #bbbbbb; -fx-font-size: 12px; -fx-font-weight: bold;");

    // Gözat (browse) butonu
    browseBtn = new Button("📂");
    browseBtn.setStyle(
        "-fx-background-color: #555555; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    browseBtn.setOnMouseEntered(e -> browseBtn.setStyle(
        "-fx-background-color: #777777; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    browseBtn.setOnMouseExited(e -> browseBtn.setStyle(
        "-fx-background-color: #555555; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    browseBtn.setOnAction(e -> {
      FileChooser chooser = new FileChooser();
      chooser.setTitle("Dosya Seç");
      chooser.getExtensionFilters().addAll(
          new FileChooser.ExtensionFilter("Çalıştırılabilir Dosyalar (*.jar, *.bat)", "*.jar", "*.bat"),
          new FileChooser.ExtensionFilter("JAR Dosyası (*.jar)", "*.jar"),
          new FileChooser.ExtensionFilter("Batch Dosyası (*.bat)", "*.bat")
      );
      File initDir = resolveDir(pathField.getText());
      if (initDir != null && initDir.exists()) {
        chooser.setInitialDirectory(initDir);
      }
      Window window = pathField.getScene() != null ? pathField.getScene().getWindow() : null;
      File selected = chooser.showOpenDialog(window);
      if (selected != null) {
        pathField.setText(selected.getAbsolutePath().replace("\\", "/"));
      }
    });

    // Java seçim label + ComboBox
    Label javaLabel = new Label("Java:");
    javaLabel.setStyle("-fx-text-fill: #bbbbbb; -fx-font-size: 12px; -fx-font-weight: bold;");

    // Kaydet butonu
    Button saveBtn = new Button("💾");
    saveBtn.setStyle(
        "-fx-background-color: #555555; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    saveBtn.setOnMouseEntered(e -> saveBtn.setStyle(
        "-fx-background-color: #777777; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    saveBtn.setOnMouseExited(e -> saveBtn.setStyle(
        "-fx-background-color: #555555; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    saveBtn.setOnAction(e -> saveConfig(pathField.getText(), javaCombo.getValue(), shellCombo.getValue()));

    // Yükle butonu
    loadBtn = new Button("📥");
    loadBtn.setStyle(
        "-fx-background-color: #555555; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    loadBtn.setOnMouseEntered(e -> loadBtn.setStyle(
        "-fx-background-color: #777777; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    loadBtn.setOnMouseExited(e -> loadBtn.setStyle(
        "-fx-background-color: #555555; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 13px; -fx-padding: 3 8 3 8; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    loadBtn.setOnAction(e -> showLoadDialog());

    Region row1LeftSpacer = new Region();
    HBox.setHgrow(row1LeftSpacer, Priority.ALWAYS);

    HBox row1 = new HBox(8,
        shellLabel, shellCombo,
        pathLabel, pathField, browseBtn,
        javaLabel, javaCombo,
        saveBtn, loadBtn,
        row1LeftSpacer
    );
    row1.setAlignment(Pos.CENTER_LEFT);
    row1.setPadding(new Insets(6, 10, 6, 10));
    row1.setStyle(
        "-fx-border-color: #3a3a3a; -fx-border-radius: 4 4 0 0; " +
            "-fx-background-color: #2d2d2d; -fx-background-radius: 4 4 0 0;"
    );

    // ── Row 2: Action Buttons ─────────────────────────────────
    // Çalıştır
    Button runBtn = new Button("▶  Çalıştır");
    runBtn.setStyle(
        "-fx-background-color: #27ae60; -fx-text-fill: white; " +
            "-fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-padding: 4 12 4 12; -fx-background-radius: 4; -fx-cursor: hand;"
    );
    runBtn.setOnMouseEntered(e -> runBtn.setStyle(
        "-fx-background-color: #2ecc71; -fx-text-fill: white; " +
            "-fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-padding: 4 12 4 12; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    runBtn.setOnMouseExited(e -> runBtn.setStyle(
        "-fx-background-color: #27ae60; -fx-text-fill: white; " +
            "-fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-padding: 4 12 4 12; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    runBtn.setOnAction(e -> {
      if (process == null || !process.isAlive()) {
        setControlsDisabled(true);
        startProcess(currentShell);
      } else {
        appendOutput("⚠  Terminal zaten çalışıyor.\n");
      }
    });

    // Durdur
    Button stopBtn = new Button("⏹  Durdur");
    stopBtn.setStyle(
        "-fx-background-color: #c0392b; -fx-text-fill: white; " +
            "-fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-padding: 4 12 4 12; -fx-background-radius: 4; -fx-cursor: hand;"
    );
    stopBtn.setOnMouseEntered(e -> stopBtn.setStyle(
        "-fx-background-color: #e74c3c; -fx-text-fill: white; " +
            "-fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-padding: 4 12 4 12; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    stopBtn.setOnMouseExited(e -> stopBtn.setStyle(
        "-fx-background-color: #c0392b; -fx-text-fill: white; " +
            "-fx-font-size: 11px; -fx-font-weight: bold; " +
            "-fx-padding: 4 12 4 12; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    stopBtn.setOnAction(e -> {
      if (workerThread != null && workerThread.isAlive()) {
        killProcessTree();
        setControlsDisabled(false);
        appendOutput("\n[⏹ Process durduruldu]\n");
      } else {
        appendOutput("⚠  Terminal çalışmıyor.\n");
      }
    });

    Button clearBtn = new Button("🗑  Temizle");
    clearBtn.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    clearBtn.setOnMouseEntered(e -> clearBtn.setStyle(
        "-fx-background-color: #666666; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    clearBtn.setOnMouseExited(e -> clearBtn.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    clearBtn.setOnAction(e -> outputArea.clear());

    Region row2Spacer = new Region();
    HBox.setHgrow(row2Spacer, Priority.ALWAYS);

    HBox row2 = new HBox(8, runBtn, stopBtn, clearBtn, row2Spacer);
    row2.setAlignment(Pos.CENTER_LEFT);
    row2.setPadding(new Insets(6, 10, 6, 10));
    row2.setStyle(
        "-fx-border-color: #3a3a3a; -fx-border-radius: 0 0 4 4; " +
            "-fx-background-color: #252525; -fx-background-radius: 0 0 4 4;"
    );

    // ── Output area ──────────────────────────────────────────
    VBox.setVgrow(outputArea, Priority.ALWAYS);

    root.getChildren().addAll(row1, row2, outputArea);
    VBox.setVgrow(root, Priority.ALWAYS);
    root.setMaxWidth(Double.MAX_VALUE);
    root.setMaxHeight(Double.MAX_VALUE);

    return root;
  }

  // ── Config persistence ────────────────────────────────────────────────────

  private SavedConfig loadInitialConfig() {
    TerminalConfigStore store = readConfigStore();
    if (store.configs.isEmpty()) {
      return null;
    }
    if (store.lastSelected != null && !store.lastSelected.isBlank()) {
      for (SavedConfig config : store.configs) {
        if (store.lastSelected.equals(buildConfigKey(config))) {
          return config;
        }
      }
    }
    return store.configs.getLast();
  }

  private void loadJavaVersions() {
    javaCombo.getItems().clear();
    File javaBaseDir = new File(JAVA_BASE_DIR);
    if (javaBaseDir.exists() && javaBaseDir.isDirectory()) {
      File[] dirs = javaBaseDir.listFiles(File::isDirectory);
      if (dirs != null) {
        for (File dir : dirs) {
          javaCombo.getItems().add(dir.getAbsolutePath().replace("\\", "/"));
        }
      }
    }
    if (javaCombo.getItems().isEmpty()) {
      javaCombo.setPromptText("Java bulunamadı");
    }
  }

  private void saveConfig(String path, String javaHome, String shell) {
    String normalizedPath = normalizeSavedPath(path);

    if (normalizedPath == null || normalizedPath.isBlank()) {
      appendOutput("❌ Kaydetme için dosya yolu boş olamaz.\n");
      return;
    }

    boolean isBat = normalizedPath.toLowerCase().endsWith(".bat");
    String normalizedJava = (javaHome == null || isBat) ? "" : javaHome.strip();
    String normalizedShell = normalizeShell(shell);
    if (!isBat && normalizedJava.isBlank()) {
      appendOutput("❌ Kaydetme için Java seçimi boş olamaz.\n");
      return;
    }

    try {
      Path dir = CONFIG_FILE.getParent();
      if (dir != null && !Files.exists(dir)) {
        Files.createDirectories(dir);
      }

      TerminalConfigStore store = readConfigStore();
      SavedConfig newConfig = new SavedConfig();
      newConfig.path = normalizedPath;
      newConfig.java = normalizedJava;
      newConfig.shell = shell;

      boolean updated = false;
      for (int i = 0; i < store.configs.size(); i++) {
        SavedConfig existing = store.configs.get(i);
        if (isSameConfig(existing, newConfig)) {
          store.configs.set(i, newConfig);
          updated = true;
          break;
        }
      }
      if (!updated) {
        store.configs.add(newConfig);
      }
      store.lastSelected = buildConfigKey(newConfig);

      writeConfigStore(store);
      appendOutput("💾 Kaydedildi → shell: " + normalizedShell + " | java: " + (normalizedJava.isEmpty() ? "-" : normalizedJava) + " | path: " + normalizedPath + "\n");
    } catch (IOException e) {
      appendOutput("❌ Kaydetme hatası: " + e.getMessage() + "\n");
    }
  }

  private void showLoadDialog() {
    TerminalConfigStore store = readConfigStore();
    if (store.configs.isEmpty()) {
      appendOutput("⚠  terminal-config.json içinde kayıt bulunamadı.\n");
      return;
    }

    Window window = pathField.getScene() != null ? pathField.getScene().getWindow() : null;
    LoadDialogResult dialogResult = showLoadTableDialog(store.configs, store.lastSelected, window);
    if (dialogResult.selected == null && !dialogResult.deleted) {
      return;
    }

    if (dialogResult.selected != null) {
      applyConfigToFields(dialogResult.selected);
      store.lastSelected = buildConfigKey(dialogResult.selected);
    } else if (store.configs.isEmpty()) {
      store.lastSelected = "";
    }

    if (dialogResult.deleted || dialogResult.selected != null) {
      try {
        writeConfigStore(store);
      } catch (IOException e) {
        appendOutput("❌ Seçim kaydı yazılamadı: " + e.getMessage() + "\n");
      }
    }

    if (dialogResult.deleted) {
      appendOutput("🗑 Silindi → " + safe(dialogResult.deletedLabel) + "\n");
    }
    if (dialogResult.selected != null) {
      appendOutput("📥 Yüklendi → " + buildConfigLabel(dialogResult.selected) + "\n");
    }
  }

  private LoadDialogResult showLoadTableDialog(List<SavedConfig> configs, String selectedKey, Window owner) {
    Stage dialog = new Stage();
    dialog.setTitle("Konfigürasyon Yükle");
    URL iconUrl = getClass().getResource("/setting.png");
    if (iconUrl != null) {
      dialog.getIcons().add(new Image(iconUrl.toExternalForm()));
    }
    dialog.initModality(Modality.WINDOW_MODAL);
    if (owner != null) {
      dialog.initOwner(owner);
    }

    TableView<SavedConfig> table = new TableView<>();
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    table.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 14px;");

    TableColumn<SavedConfig, String> fileCol = new TableColumn<>("Dosya");
    fileCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(new File(safe(data.getValue().path)).getName()));

    TableColumn<SavedConfig, String> javaCol = new TableColumn<>("Java");
    javaCol.setCellValueFactory(data -> {
      String j = data.getValue().java;
      return new ReadOnlyStringWrapper(j == null || j.isBlank() ? "-" : new File(j).getName());
    });

    TableColumn<SavedConfig, String> shellCol = new TableColumn<>("Shell");
    shellCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(normalizeShell(data.getValue().shell)));

    TableColumn<SavedConfig, String> pathCol = new TableColumn<>("Path");
    pathCol.setCellValueFactory(data -> new ReadOnlyStringWrapper(safe(data.getValue().path)));

    table.getColumns().add(fileCol);
    table.getColumns().add(javaCol);
    table.getColumns().add(shellCol);
    table.getColumns().add(pathCol);
    table.setItems(FXCollections.observableArrayList(configs));

    SavedConfig preselected = null;
    if (selectedKey != null && !selectedKey.isBlank()) {
      for (SavedConfig config : configs) {
        if (selectedKey.equals(buildConfigKey(config))) {
          preselected = config;
          break;
        }
      }
    }
    if (preselected == null && !configs.isEmpty()) {
      preselected = configs.getLast();
    }
    if (preselected != null) {
      table.getSelectionModel().select(preselected);
      table.scrollTo(preselected);
    }

    LoadDialogResult result = new LoadDialogResult();

    Button selectBtn = new Button("✔  Seç");
    selectBtn.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    selectBtn.setOnMouseEntered(e -> selectBtn.setStyle(
        "-fx-background-color: #666666; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    selectBtn.setOnMouseExited(e -> selectBtn.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    selectBtn.setDisable(table.getSelectionModel().getSelectedItem() == null);
    selectBtn.setOnAction(e -> {
      result.selected = table.getSelectionModel().getSelectedItem();
      dialog.close();
    });

    Button deleteBtn = new Button("🗑  Sil");
    deleteBtn.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    deleteBtn.setOnMouseEntered(e -> deleteBtn.setStyle(
        "-fx-background-color: #666666; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    deleteBtn.setOnMouseExited(e -> deleteBtn.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    deleteBtn.setDisable(table.getSelectionModel().getSelectedItem() == null);
    deleteBtn.setOnAction(e -> {
      SavedConfig toDelete = table.getSelectionModel().getSelectedItem();
      if (toDelete == null) {
        return;
      }

      Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
      confirm.setTitle("Silme Onayı");
      confirm.setHeaderText(null);
      confirm.setContentText("Seçili konfigürasyon silinsin mi?\n" + buildConfigLabel(toDelete));
      ButtonType evetBtn = new ButtonType("✓  Evet", ButtonBar.ButtonData.YES);
      ButtonType hayirBtn = new ButtonType("✕  Hayır", ButtonBar.ButtonData.NO);
      confirm.getButtonTypes().setAll(evetBtn, hayirBtn);
      styleConfirmDialog(confirm, evetBtn, hayirBtn);

      if (confirm.showAndWait().orElse(hayirBtn) != evetBtn) {
        return;
      }

      int selectedIndex = table.getSelectionModel().getSelectedIndex();
      result.deleted = true;
      result.deletedLabel = buildConfigLabel(toDelete);
      configs.remove(toDelete);
      table.getItems().remove(toDelete);

      if (!table.getItems().isEmpty()) {
        int newIndex = Math.min(selectedIndex, table.getItems().size() - 1);
        table.getSelectionModel().select(newIndex);
      }
    });

    Button cancelBtn = new Button("✕  İptal");
    cancelBtn.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle(
        "-fx-background-color: #666666; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    cancelBtn.setOnAction(e -> dialog.close());

    table.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) ->
    {
      boolean disabled = newSel == null;
      selectBtn.setDisable(disabled);
      deleteBtn.setDisable(disabled);
    });

    table.setRowFactory(tv -> {
      TableRow<SavedConfig> row = new TableRow<>();
      row.setOnMouseClicked(event -> {
        if (event.getClickCount() == 2 && !row.isEmpty()) {
          result.selected = row.getItem();
          dialog.close();
        }
      });
      return row;
    });

    Label titleLabel = new Label("Kayıtlı konfigürasyon");
    titleLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px; -fx-font-weight: bold;");

    HBox actions = new HBox(8, selectBtn, deleteBtn, cancelBtn);
    actions.setAlignment(Pos.CENTER_RIGHT);

    VBox content = new VBox(10,
        titleLabel,
        table,
        actions
    );
    content.setPadding(new Insets(10));
    content.setStyle(
        "-fx-base: #3c3f41;" +
            "-fx-background: #2b2b2b;" +
            "-fx-control-inner-background: #45494a;" +
            "-fx-control-inner-background-alt: #3c3f41;" +
            "-fx-accent: #4b6eaf;" +
            "-fx-focus-color: #4b6eaf;" +
            "-fx-faint-focus-color: #4b6eaf22;" +
            "-fx-text-fill: #e0e0e0;" +
            "-fx-text-background-color: #e0e0e0;" +
            "-fx-text-inner-color: #e0e0e0;" +
            "-fx-control-text-fill: #e0e0e0;" +
            "-fx-prompt-text-fill: #999999;" +
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 11px;"
    );
    VBox.setVgrow(table, Priority.ALWAYS);

    Scene dialogScene = new Scene(content, 980, 380);
    URL darkThemeCss = getClass().getResource("/dark-theme.css");
    if (darkThemeCss != null) {
      dialogScene.getStylesheets().add(darkThemeCss.toExternalForm());
    }
    dialog.setScene(dialogScene);
    dialog.showAndWait();

    return result;
  }

  private void applyConfigToFields(SavedConfig config) {
    String normalizedPath = normalizeSavedPath(config.path);
    if (normalizedPath != null && !normalizedPath.isBlank()) {
      pathField.setText(normalizedPath);
    }
    if (config.java != null && !config.java.isBlank()) {
      if (!javaCombo.getItems().contains(config.java)) {
        javaCombo.getItems().add(config.java);
      }
      javaCombo.setValue(config.java);
    }
    currentShell = normalizeShell(config.shell);
    if (shellCombo != null) {
      shellCombo.setValue(currentShell);
    }
  }

  private TerminalConfigStore readConfigStore() {
    TerminalConfigStore store = new TerminalConfigStore();
    if (!Files.exists(CONFIG_FILE)) {
      return store;
    }

    try {
      Map<?, ?> raw = objectMapper.readValue(CONFIG_FILE.toFile(), Map.class);
      Object configs = raw.get("configs");

      if (configs instanceof List<?> configList) {
        for (Object item : configList) {
          if (item instanceof Map<?, ?> itemMap) {
            SavedConfig config = toSavedConfig(itemMap);
            if (config != null) {
              store.configs.add(config);
            }
          }
        }
        store.lastSelected = asNonBlankString(raw.get("lastSelected"));
      } else {
        // Eski format: {"path":..., "java":..., "shell":...}
        SavedConfig legacy = toSavedConfig(raw);
        if (legacy != null) {
          store.configs.add(legacy);
          store.lastSelected = buildConfigKey(legacy);
        }
      }
    } catch (IOException ignored) {
    }

    return store;
  }

  private void writeConfigStore(TerminalConfigStore store) throws IOException {
    List<Map<String, String>> configs = new ArrayList<>();
    for (SavedConfig config : store.configs) {
      Map<String, String> row = new LinkedHashMap<>();
      row.put("path", config.path == null ? "" : config.path);
      row.put("java", config.java == null ? "" : config.java);
      row.put("shell", normalizeShell(config.shell));
      configs.add(row);
    }

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("lastSelected", store.lastSelected == null ? "" : store.lastSelected);
    root.put("configs", configs);

    objectMapper.writerWithDefaultPrettyPrinter().writeValue(CONFIG_FILE.toFile(), root);
  }

  private SavedConfig toSavedConfig(Map<?, ?> map) {
    String path = normalizeSavedPath(asNonBlankString(map.get("path")));
    String javaHome = asNonBlankString(map.get("java"));
    String shell = normalizeShell(asNonBlankString(map.get("shell")));

    if (path == null || path.isBlank()) {
      return null;
    }

    boolean isBat = path.toLowerCase().endsWith(".bat");
    if (!isBat && (javaHome == null || javaHome.isBlank())) {
      return null;
    }

    SavedConfig config = new SavedConfig();
    config.path = path;
    config.java = javaHome;
    config.shell = shell;
    return config;
  }

  private boolean isSameConfig(SavedConfig a, SavedConfig b) {
    if (a == null || b == null) {
      return false;
    }
    return safe(a.path).equals(safe(b.path))
        && safe(a.java).equals(safe(b.java))
        && normalizeShell(a.shell).equals(normalizeShell(b.shell));
  }

  private String buildConfigKey(SavedConfig config) {
    return safe(config.path) + "|" + safe(config.java) + "|" + normalizeShell(config.shell);
  }

  private String buildConfigLabel(SavedConfig config) {
    String fileName = new File(safe(config.path)).getName();
    String javaName = config.java == null || config.java.isBlank() ? "N/A" : new File(safe(config.java)).getName();
    return fileName + " | " + javaName + " | " + normalizeShell(config.shell) + " | " + safe(config.path);
  }

  private String normalizeSavedPath(String rawPath) {
    if (rawPath == null || rawPath.isBlank()) {
      return null;
    }
    File file = new File(rawPath);
    return file.getAbsolutePath().replace("\\", "/");
  }

  private String normalizeShell(String shell) {
    return "powershell".equalsIgnoreCase(shell) ? "powershell" : "cmd";
  }

  private String asNonBlankString(Object value) {
    if (value instanceof String s && !s.isBlank()) {
      return s.strip();
    }
    return null;
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  /**
   * Bir path string'inden dizin çıkarır.
   */
  private File resolveDir(String path) {
    if (path == null || path.isBlank()) {
      return null;
    }
    File f = new File(path);
    return f.isFile() ? f.getParentFile() : f;
  }

  // ── Process Management ────────────────────────────────────────────────────

  private void startProcess(String shell) {
    String filePath = pathField != null ? pathField.getText().strip() : "";
    String javaHome = javaCombo != null ? javaCombo.getValue() : null;

    // Doğrulama — UI thread'inde yapılır
    if (filePath.isBlank()) {
      appendOutput("❌ Dosya yolu belirtilmemiş.\n");
      setControlsDisabled(false);
      return;
    }

    File execFile = new File(filePath);
    if (!execFile.exists()) {
      appendOutput("❌ Dosya bulunamadı: " + filePath + "\n");
      setControlsDisabled(false);
      return;
    }

    final String fileDir = execFile.getParent();
    final String fileName = execFile.getName();
    final boolean isBat = fileName.toLowerCase().endsWith(".bat");

    if (!isBat && (javaHome == null || javaHome.isBlank())) {
      appendOutput("❌ Java sürümü seçilmemiş.\n");
      setControlsDisabled(false);
      return;
    }

    if (isBat) {
      appendOutput("▶  " + shell + " → " + fileName + "\n\n");
    } else {
      final String javaBin = javaHome.replace("/", "\\") + "\\bin\\java";
      appendOutput("▶  " + shell + " → " + javaBin + " -jar " + fileName + "\n\n");
    }

    // Process başlatma + okuma ayrı daemon thread'de — UI thread'ini bloklamaz
    Thread worker = new Thread(() -> {
      try {
        ProcessBuilder pb;

        if (isBat) {
          if ("powershell".equalsIgnoreCase(shell)) {
            String cmd = "Set-Location '" + fileDir + "'; & '.\\" + fileName + "'";
            pb = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoExit", "-Command", cmd);
          } else {
            String cmd = "cd /d \"" + fileDir + "\" & \"" + fileName + "\"";
            pb = new ProcessBuilder("cmd.exe", "/k", cmd);
          }
        } else {
          final String javaBin = javaHome.replace("/", "\\") + "\\bin\\java";
          if ("powershell".equalsIgnoreCase(shell)) {
            String cmd = "Set-Location '" + fileDir + "'; & '" + javaBin + "' -jar '" + fileName + "'";
            pb = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoExit", "-Command", cmd);
          } else {
            String cmd = "cd /d \"" + fileDir + "\" & \"" + javaBin + "\" -jar " + fileName;
            pb = new ProcessBuilder("cmd.exe", "/k", cmd);
          }
        }

        pb.redirectErrorStream(true);
        pb.directory(execFile.getParentFile());

        process = pb.start();
        processWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), Charset.defaultCharset()));

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
          StringBuilder batch = new StringBuilder();
          String line;
          while ((line = reader.readLine()) != null) {
            batch.append(line).append("\n");
            if (!reader.ready()) {
              final String text = batch.toString();
              batch.setLength(0);
              Platform.runLater(() -> {
                outputArea.appendText(text);
                outputArea.setScrollTop(Double.MAX_VALUE);
              });
            }
          }
          if (!batch.isEmpty()) {
            final String text = batch.toString();
            Platform.runLater(() -> {
              outputArea.appendText(text);
              outputArea.setScrollTop(Double.MAX_VALUE);
            });
          }
        }

      } catch (Exception e) {
        appendOutput("❌ Başlatma hatası: " + e.getMessage() + "\n");
        Platform.runLater(() -> setControlsDisabled(false));
      }
      Platform.runLater(() -> outputArea.appendText("\n[Process sonlandı]\n"));
    });
    worker.setDaemon(true);
    worker.setName("terminal-worker");
    worker.start();
    workerThread = worker;
  }

  private void setControlsDisabled(boolean disabled) {
    shellCombo.setDisable(disabled);
    browseBtn.setDisable(disabled);
    loadBtn.setDisable(disabled);
    javaCombo.setDisable(disabled);
  }

  private static class TerminalConfigStore {
    private final List<SavedConfig> configs = new ArrayList<>();
    private String lastSelected;
  }

  private static class SavedConfig {
    private String path;
    private String java;
    private String shell;
  }

  private static class LoadDialogResult {
    private SavedConfig selected;
    private boolean deleted;
    private String deletedLabel;
  }

  private void styleConfirmDialog(Alert confirm, ButtonType evetBtn, ButtonType hayirBtn) {
    Scene scene = confirm.getDialogPane().getScene();
    if (scene != null) {
      URL darkThemeCss = getClass().getResource("/dark-theme.css");
      if (darkThemeCss != null && !scene.getStylesheets().contains(darkThemeCss.toExternalForm())) {
        scene.getStylesheets().add(darkThemeCss.toExternalForm());
      }

      URL iconUrl = getClass().getResource("/setting.png");
      if (iconUrl != null && scene.getWindow() instanceof Stage stage) {
        stage.getIcons().add(new Image(iconUrl.toExternalForm()));
      }
    }

    confirm.getDialogPane().setStyle(
        "-fx-base: #3c3f41;" +
            "-fx-background: #2b2b2b;" +
            "-fx-control-inner-background: #45494a;" +
            "-fx-control-inner-background-alt: #3c3f41;" +
            "-fx-accent: #4b6eaf;" +
            "-fx-focus-color: #4b6eaf;" +
            "-fx-faint-focus-color: #4b6eaf22;" +
            "-fx-text-fill: #e0e0e0;" +
            "-fx-text-background-color: #e0e0e0;" +
            "-fx-text-inner-color: #e0e0e0;" +
            "-fx-control-text-fill: #e0e0e0;" +
            "-fx-prompt-text-fill: #999999;" +
            "-fx-font-family: 'Segoe UI';" +
            "-fx-font-size: 11px;"
    );

    Button evetButtonNode = (Button) confirm.getDialogPane().lookupButton(evetBtn);
    evetButtonNode.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    evetButtonNode.setOnMouseEntered(e -> evetButtonNode.setStyle(
        "-fx-background-color: #666666; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    evetButtonNode.setOnMouseExited(e -> evetButtonNode.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));

    Button hayirButtonNode = (Button) confirm.getDialogPane().lookupButton(hayirBtn);
    hayirButtonNode.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    );
    hayirButtonNode.setOnMouseEntered(e -> hayirButtonNode.setStyle(
        "-fx-background-color: #666666; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
    hayirButtonNode.setOnMouseExited(e -> hayirButtonNode.setStyle(
        "-fx-background-color: #444444; -fx-text-fill: #e0e0e0; " +
            "-fx-font-size: 11px; -fx-padding: 4 12 4 12; " +
            "-fx-background-radius: 4; -fx-cursor: hand;"
    ));
  }

  private void killProcessTree() {
    if (process != null && process.isAlive()) {
      long pid = process.pid();
      try {
        // taskkill /F /T: shell + tüm alt process'leri (java dahil) zorla sonlandırır
        new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
            .redirectErrorStream(true)
            .start()
            .waitFor();
      } catch (Exception ignored) {
      }
      process.destroyForcibly();
      process.descendants().forEach(h -> {
        try {
          h.destroyForcibly();
        } catch (Exception ignored) {
        }
      });
    }
    try {
      if (processWriter != null) {
        processWriter.close();
      }
    } catch (Exception ignored) {
    }
    process = null;
    processWriter = null;
  }

  private void appendOutput(String text) {
    Platform.runLater(() -> {
      outputArea.appendText(text);
      outputArea.setScrollTop(Double.MAX_VALUE);
    });
  }
}