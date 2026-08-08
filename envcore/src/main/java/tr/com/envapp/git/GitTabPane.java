package tr.com.envapp.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import tr.com.envapp.model.GitProjectDTO;

public class GitTabPane extends BorderPane {

  private final TableView<GitProjectDTO> projectTableView;
  private final TextArea consoleOutput;
  private final CheckBox masterCheckbox;
  private final Button stopBtn;
  private final ObjectMapper mapper = new ObjectMapper();

  private final Button browseBtn;
  private final Button fetchBtn;
  private final Button pullBtn;
  private final TextField branchInput;
  private final Button checkoutBtn;
  private final Button checkoutReleaseBtn;
  private final Button checkoutReleaseAndPullBtn;
  private final Button mvnCleanInstallBtn;
  private final Button mvnCleanInstallSkipTestsBtn;
  private final Button mvnCleanInstall4CBtn;
  private final Button mvnCleanInstall4CSkipTestsBtn;
  private final Button yarnInstallBtn;

  private volatile boolean stopRequested = false;
  private volatile Process currentProcess = null;
  private final List<Process> activeProcesses = Collections.synchronizedList(new ArrayList<>());

  public GitTabPane() {
    setPadding(new Insets(10));

    // --- ÜST KISIM: Klasör Seçimi ve Ana Checkbox ---
    VBox topVBox = new VBox(5);

    HBox topBox = new HBox(10);
    topBox.setAlignment(Pos.CENTER_LEFT);
    topBox.setPadding(new Insets(0, 0, 10, 0));

    Label pathLabel = new Label("Workspace:");
    pathLabel.setStyle("-fx-text-fill: #e0e0e0;");
    TextField pathField = new TextField();
    pathField.setEditable(false);
    pathField.setPrefWidth(400);

    browseBtn = new Button("📂");
    browseBtn.setStyle("-fx-background-color: #555555; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
    browseBtn.setOnMouseEntered(e -> browseBtn.setStyle("-fx-background-color: #777777; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;"));
    browseBtn.setOnMouseExited(e -> browseBtn.setStyle("-fx-background-color: #555555; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;"));
    browseBtn.setOnAction(e -> {
      DirectoryChooser directoryChooser = new DirectoryChooser();
      directoryChooser.setTitle("Git Workspace Klasörünü Seçin");
      File dir = directoryChooser.showDialog(this.getScene().getWindow());
      if (dir != null) {
        pathField.setText(dir.getAbsolutePath());
        loadGitProjects(dir);
      }
    });

    topBox.getChildren().addAll(pathLabel, pathField, browseBtn);

    projectTableView = new TableView<>();
    projectTableView.setEditable(true);
    projectTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    projectTableView.setRowFactory(tv -> {
      TableRow<GitProjectDTO> row = new TableRow<>();
      row.setOnMouseClicked(event -> {
        if (!row.isEmpty() && event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
          boolean clickedOnCheckbox = false;
          javafx.scene.Node target = (javafx.scene.Node) event.getTarget();
          while (target != null) {
            if (target instanceof CheckBox) {
              clickedOnCheckbox = true;
              break;
            }
            target = target.getParent();
          }
          if (!clickedOnCheckbox) {
            GitProjectDTO project = row.getItem();
            project.setSelected(!project.isSelected());
          }
        }
      });
      return row;
    });

    TableColumn<GitProjectDTO, Boolean> selectCol = new TableColumn<>("");
    selectCol.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
    selectCol.setCellValueFactory(cellData -> cellData.getValue().selectedProperty());
    selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
    selectCol.setPrefWidth(40);
    selectCol.setResizable(false);

    TableColumn<GitProjectDTO, String> nameCol = new TableColumn<>("Proje Adı");
    nameCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getFile().getName()));
    nameCol.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
    nameCol.setPrefWidth(250);

    TableColumn<GitProjectDTO, String> branchCol = new TableColumn<>("Aktif Branch");
    branchCol.setCellValueFactory(cellData -> cellData.getValue().currentBranchProperty());
    branchCol.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
    branchCol.setPrefWidth(180);

    TableColumn<GitProjectDTO, String> versionCol = new TableColumn<>("Versiyon");
    versionCol.setCellValueFactory(cellData -> cellData.getValue().versionProperty());
    versionCol.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
    versionCol.setPrefWidth(180);
    versionCol.setCellFactory(col -> new TableCell<>() {
      private final Label versionLabel = new Label();
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, versionLabel, spacer, copyBtn);

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        versionLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-family: 'Consolas'; -fx-font-size: 13px;");
        copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;");
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle("-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          GitProjectDTO item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getVersion(), item.getFile().getName() + " - Versiyon");
        });
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null || item.isBlank()) {
          setGraphic(null);
        } else {
          versionLabel.setText(item);
          setGraphic(box);
        }
      }
    });

    TableColumn<GitProjectDTO, String> typeCol = new TableColumn<>("Tip");
    typeCol.setCellValueFactory(cellData -> cellData.getValue().projectTypeProperty());
    typeCol.setVisible(false);

    projectTableView.getColumns().addAll(List.of(selectCol, nameCol, branchCol, versionCol, typeCol));

    masterCheckbox = new CheckBox("Tümünü Seç / Bırak");
    masterCheckbox.setDisable(true);
    masterCheckbox.setOnAction(e -> {
      boolean allSelected = masterCheckbox.isSelected();
      projectTableView.getItems().forEach(project -> project.setSelected(allSelected));
      projectTableView.getSelectionModel().clearSelection();
    });

    topVBox.getChildren().addAll(topBox, masterCheckbox);
    setTop(topVBox);

    VBox centerBox = new VBox(5, projectTableView);
    centerBox.setPadding(new Insets(5, 0, 0, 0));
    VBox.setVgrow(projectTableView, Priority.ALWAYS);
    setCenter(centerBox);

    // --- SAĞ MENÜ VE BUTONLAR ---
    VBox rightBox = new VBox(5);
    rightBox.setPadding(new Insets(5, 0, 0, 10));
    rightBox.setPrefWidth(300);

    fetchBtn = createStyledButton("Fetch", e -> executeOnSelected("git", "fetch"));
    pullBtn = createStyledButton("Pull", e -> executeOnSelected("git", "pull"));

    branchInput = new TextField();
    branchInput.setPromptText("Branch veya Release Tag");
    branchInput.setStyle("-fx-prompt-text-fill: #bbbbbb; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4;");

    checkoutBtn = createStyledButton("Checkout", e -> {
      String branch = branchInput.getText().strip();
      if (!branch.isEmpty()) {
        executeOnSelected("git", "checkout", branch);
      } else {
        logToConsole("HATA: Lütfen checkout için bir branch veya tag girin.");
      }
    });

    HBox checkoutWithbranchInputBox = new HBox(5, branchInput, checkoutBtn);
    checkoutWithbranchInputBox.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(branchInput, Priority.ALWAYS);

    checkoutReleaseBtn = createStyledButton("Checkout Release", e -> executeOnSelected("git", "checkout", "release"));
    checkoutReleaseAndPullBtn = createStyledButton("Checkout Release & Pull", e -> executeMultipleCommands(List.of(new String[]{"git", "checkout", "release"}, new String[]{"git", "pull"})));
    checkoutReleaseAndPullBtn.setStyle("-fx-accent: #107c41; -fx-background-color: #555555; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;");

    VBox buildCommandsBox = new VBox(5);
    buildCommandsBox.setPadding(new Insets(10, 0, 0, 0));
    buildCommandsBox.setVisible(false);
    buildCommandsBox.setManaged(false);

    Label buildLabel = new Label("Build İşlemleri");
    buildLabel.setStyle("-fx-text-fill: #a9b7c6; -fx-font-weight: bold;");

    mvnCleanInstallBtn = createStyledButton("Maven Clean Install", e -> executeBuildCommand("mvn", "clean", "install"));
    mvnCleanInstallSkipTestsBtn = createStyledButton("Maven Clean Install (Skip Tests)", e -> executeBuildCommand("mvn", "clean", "install", "-DskipTests"));
    mvnCleanInstall4CBtn = createStyledButton("Maven Clean Install (4C)", e -> executeBuildCommand("mvn", "clean", "install", "-T", "4C"));
    mvnCleanInstall4CSkipTestsBtn = createStyledButton("Maven Clean Install (4C, Skip Tests)", e -> executeBuildCommand("mvn", "clean", "install", "-T", "4C", "-DskipTests"));
    yarnInstallBtn = createStyledButton("Yarn Install", e -> executeBuildCommand("yarn", "install", "--network-timeout", "1000000000"));

    buildCommandsBox.getChildren().addAll(buildLabel, mvnCleanInstallBtn, mvnCleanInstallSkipTestsBtn, mvnCleanInstall4CBtn, mvnCleanInstall4CSkipTestsBtn, yarnInstallBtn);

    projectTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
      if (newSelection != null) {
        String type = newSelection.getProjectType();
        if ("JAVA".equals(type)) {
          buildCommandsBox.setVisible(true);
          buildCommandsBox.setManaged(true);
          setNodeVisibility(yarnInstallBtn, false);
          setNodeVisibility(mvnCleanInstallBtn, true);
          setNodeVisibility(mvnCleanInstallSkipTestsBtn, true);
          setNodeVisibility(mvnCleanInstall4CBtn, true);
          setNodeVisibility(mvnCleanInstall4CSkipTestsBtn, true);
        } else if ("REACT".equals(type)) {
          buildCommandsBox.setVisible(true);
          buildCommandsBox.setManaged(true);
          setNodeVisibility(mvnCleanInstallBtn, false);
          setNodeVisibility(mvnCleanInstallSkipTestsBtn, false);
          setNodeVisibility(mvnCleanInstall4CBtn, false);
          setNodeVisibility(mvnCleanInstall4CSkipTestsBtn, false);
          setNodeVisibility(yarnInstallBtn, true);
        } else {
          buildCommandsBox.setVisible(false);
          buildCommandsBox.setManaged(false);
        }
      } else {
        buildCommandsBox.setVisible(false);
        buildCommandsBox.setManaged(false);
      }
    });

    consoleOutput = new TextArea();
    Button clearBtn = createStyledButton("🗑  Temizle", e -> consoleOutput.clear());

    stopBtn = new Button("İşlemi Durdur");
    stopBtn.setDisable(true);
    stopBtn.setMaxWidth(Double.MAX_VALUE);
    stopBtn.setStyle("-fx-background-color: #a31515; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
    stopBtn.setOnMouseEntered(e -> stopBtn.setStyle("-fx-background-color: #F31515; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;"));
    stopBtn.setOnMouseExited(e -> stopBtn.setStyle("-fx-background-color: #a31515; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;"));
    stopBtn.setOnAction(e -> stopCurrentExecution());

    Region spacer = new Region();
    VBox.setVgrow(spacer, Priority.ALWAYS);

    rightBox.getChildren().addAll(fetchBtn, pullBtn, checkoutWithbranchInputBox, checkoutReleaseBtn, checkoutReleaseAndPullBtn, buildCommandsBox, spacer, clearBtn, stopBtn);
    //setRight(rightBox);

    consoleOutput.setWrapText(false);
    consoleOutput.setEditable(false);
    consoleOutput.setStyle("-fx-control-inner-background: #2b2b2b; -fx-text-fill: #e0e0e0; -fx-prompt-text-fill: #777777; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");

    BorderPane upperContent = new BorderPane();
    upperContent.setTop(topVBox);
    upperContent.setCenter(centerBox);
    upperContent.setRight(rightBox);

    Label dragLabel = new Label("≡ Konsol / Log (Boyutlandırmak için ayırıcı çizgiyi sürükleyin) ≡");
    dragLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 11px; -fx-font-weight: bold;");

    HBox consoleHeader = new HBox(dragLabel);
    consoleHeader.setAlignment(Pos.CENTER);
    consoleHeader.setStyle("-fx-background-color: #333333; -fx-background-radius: 3 3 0 0; -fx-padding: 3;");

    VBox consoleWrapper = new VBox(consoleHeader, consoleOutput);
    VBox.setVgrow(consoleOutput, Priority.ALWAYS);

    SplitPane splitPane = new SplitPane();
    splitPane.setOrientation(javafx.geometry.Orientation.VERTICAL);
    splitPane.getItems().addAll(upperContent, consoleWrapper);
    splitPane.setDividerPositions(0.75);
    this.setCenter(splitPane);
  }

  public void shutdown() {
    killProcessTree(currentProcess);
    synchronized (activeProcesses) {
      for (Process p : new ArrayList<>(activeProcesses)) {
        killProcessTree(p);
      }
      activeProcesses.clear();
    }
  }

  private void killProcessTree(Process p) {
    if (p != null && p.isAlive()) {
      long pid = p.pid();
      try {
        new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
            .redirectErrorStream(true)
            .start()
            .waitFor();
      } catch (Exception ignored) {
      }

      p.destroyForcibly();
      p.descendants().forEach(h -> {
        try {
          h.destroyForcibly();
        } catch (Exception ignored) {
        }
      });
    }
  }

  private Button createStyledButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> event) {
    Button btn = new Button(text);
    btn.setMaxWidth(Double.MAX_VALUE);
    btn.setOnAction(event);
    btn.setStyle("-fx-background-color: #555555; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
    btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: #777777; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;"));
    btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: #555555; -fx-text-fill: #e0e0e0; -fx-font-size: 13px; -fx-padding: 3 8 3 8; -fx-background-radius: 4; -fx-cursor: hand;"));
    return btn;
  }

  private void setNodeVisibility(javafx.scene.Node node, boolean isVisible) {
    node.setVisible(isVisible);
    node.setManaged(isVisible);
  }

  private void stopCurrentExecution() {
    stopRequested = true;
    logToConsole("\n[!] İşlem kullanıcı tarafından durduruluyor...");

    killProcessTree(currentProcess);
    stopBtn.setDisable(true);
  }

  private void setActionButtonsDisabled(boolean disabled) {
    browseBtn.setDisable(disabled);
    fetchBtn.setDisable(disabled);
    pullBtn.setDisable(disabled);
    checkoutBtn.setDisable(disabled);
    branchInput.setDisable(disabled);
    checkoutReleaseBtn.setDisable(disabled);
    checkoutReleaseAndPullBtn.setDisable(disabled);
    mvnCleanInstallBtn.setDisable(disabled);
    mvnCleanInstallSkipTestsBtn.setDisable(disabled);
    mvnCleanInstall4CBtn.setDisable(disabled);
    mvnCleanInstall4CSkipTestsBtn.setDisable(disabled);
    yarnInstallBtn.setDisable(disabled);
    projectTableView.setDisable(disabled);
    masterCheckbox.setDisable(disabled || projectTableView.getItems().isEmpty());
  }

  private void loadGitProjects(File workspace) {
    projectTableView.getItems().clear();
    masterCheckbox.setDisable(true);
    masterCheckbox.setSelected(false);
    logToConsole("Klasör taranıyor...");

    Thread.startVirtualThread(() -> {
      try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
        File gitFolder = new File(workspace, ".git");
        if (gitFolder.exists() && gitFolder.isDirectory()) {
          addProjectToView(workspace, executor);
          Platform.runLater(() -> logToConsole("Seçilen klasör tek bir Git projesi olarak listelendi."));
        } else {
          try (Stream<Path> paths = Files.list(workspace.toPath())) {
            paths.filter(Files::isDirectory)
                .filter(path -> Files.exists(path.resolve(".git")))
                .forEach(path -> addProjectToView(path.toFile(), executor));
            Platform.runLater(() -> logToConsole("Tarama bitti, [" + workspace.getAbsolutePath() + "] altındaki tüm projeler listelendi."));
          }
        }
      } catch (Exception ex) {
        Platform.runLater(() -> logToConsole("Klasör okunurken hata oluştu: " + ex.getMessage()));
      }
    });
  }

  private void addProjectToView(File path, java.util.concurrent.ExecutorService executor) {
    GitProjectDTO project = new GitProjectDTO(path);
    Platform.runLater(() -> {
      projectTableView.getItems().add(project);
      masterCheckbox.setDisable(false);
      project.selectedProperty().addListener((obs, oldVal, newVal) -> updateMasterCheckboxState());
      updateMasterCheckboxState();
    });

    executor.submit(() -> {
      String branch = getActiveBranchName(project.getFile());
      String version = getProjectVersion(project.getFile());

      String type = "-";
      if (new File(project.getFile(), "pom.xml").exists()) {
        type = "JAVA";
      } else if (new File(project.getFile(), "package.json").exists()) {
        type = "REACT";
      }
      final String finalType = type;
      Platform.runLater(() -> {
        project.setCurrentBranch(branch);
        project.setVersion(version);
        project.setProjectType(finalType);
      });
    });
  }

  private String getActiveBranchName(File directory) {
    File headFile = new File(directory, ".git/HEAD");
    if (headFile.exists() && headFile.isFile()) {
      try {
        String content = Files.readString(headFile.toPath()).strip();
        if (content.startsWith("ref: refs/heads/")) {
          return content.substring(16);
        } else if (content.matches("^[a-fA-F0-9]{40}$")) {
          return "Detached (" + content.substring(0, 7) + ")";
        }
      } catch (Exception ignored) {
      }
    }

    try {
      ProcessBuilder pb = new ProcessBuilder("git", "branch", "--show-current");
      pb.directory(directory);
      Process process = pb.start();
      String branch = "";
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line = reader.readLine();
        if (line != null) {
          branch = line.strip();
        }
      }
      process.waitFor();
      return branch.isEmpty() ? "-" : branch;
    } catch (Exception e) {
      return "HATA";
    }
  }

  private String getProjectVersion(File directory) {
    File pomFile = new File(directory, "pom.xml");
    if (pomFile.exists() && pomFile.isFile()) {
      try {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(pomFile);
        document.getDocumentElement().normalize();
        NodeList childNodes = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
          Node node = childNodes.item(i);
          if (node.getNodeType() == Node.ELEMENT_NODE && node.getNodeName().equals("version")) {
            return node.getTextContent().strip();
          }
        }
      } catch (Exception ignored) {
      }
    }

    File jsonFile = new File(directory, "package.json");
    if (jsonFile.exists() && jsonFile.isFile()) {
      try {
        JsonNode rootNode = mapper.readTree(jsonFile);
        if (rootNode.has("version")) {
          return rootNode.get("version").asText().strip();
        }
      } catch (Exception ignored) {
      }
    }
    return "-";
  }

  private void executeBuildCommand(String... commandArgs) {
    List<GitProjectDTO> selectedProjects = projectTableView.getItems().stream()
        .filter(GitProjectDTO::isSelected).toList();
    if (selectedProjects.isEmpty()) {
      logToConsole("UYARI: Lütfen build işlemi için tablodan bir proje seçin.");
      return;
    }
    if (selectedProjects.size() > 1) {
      logToConsole("UYARI: Build işlemleri aynı anda sadece 1 proje üzerinde yapılabilir. Lütfen tek bir proje seçin.");
      return;
    }
    executeMultipleCommands(Collections.singletonList(commandArgs));
  }

  private void executeOnSelected(String... fullCommand) {
    executeMultipleCommands(Collections.singletonList(fullCommand));
  }

  private void executeMultipleCommands(List<String[]> commandsList) {
    List<GitProjectDTO> selectedProjects = projectTableView.getItems().stream()
        .filter(GitProjectDTO::isSelected).toList();

    if (selectedProjects.isEmpty()) {
      logToConsole("UYARI: Lütfen listeden en az bir proje seçin (onay kutusunu işaretleyin).");
      return;
    }

    stopRequested = false;
    stopBtn.setDisable(false);

    setActionButtonsDisabled(true);

    Thread.startVirtualThread(() -> {
      for (GitProjectDTO project : selectedProjects) {
        if (stopRequested) {
          break;
        }

        for (String[] commandArgs : commandsList) {
          if (stopRequested) {
            break;
          }

          String baseCommand = commandArgs[0].toLowerCase();
          String type = project.getProjectType();

          if (baseCommand.equals("mvn") && !"JAVA".equals(type)) {
            continue;
          }
          if (baseCommand.equals("yarn") && !"REACT".equals(type)) {
            continue;
          }

          runSystemCommand(project.getFile(), commandArgs);
        }

        String updatedBranch = getActiveBranchName(project.getFile());
        String updatedVersion = getProjectVersion(project.getFile());

        Platform.runLater(() -> {
          project.setCurrentBranch(updatedBranch);
          project.setVersion(updatedVersion);
        });
      }

      Platform.runLater(() -> {
        if (!stopRequested) {
          logToConsole("--- İşlemler Tamamlandı ---");
        } else {
          logToConsole("--- İptal Edildi ---");
        }

        stopBtn.setDisable(true);
        setActionButtonsDisabled(false);
      });
    });
  }

  private void runSystemCommand(File directory, String... args) {
    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
    String[] finalCommand;

    if (isWindows && (args[0].equals("mvn") || args[0].equals("yarn"))) {
      finalCommand = new String[args.length + 2];
      finalCommand[0] = "cmd.exe";
      finalCommand[1] = "/c";
      System.arraycopy(args, 0, finalCommand, 2, args.length);
    } else {
      finalCommand = args;
    }

    Platform.runLater(() -> logToConsole("\n> [" + directory.getName() + "] " + String.join(" ", args)));

    try {
      ProcessBuilder pb = new ProcessBuilder(finalCommand);
      pb.directory(directory);
      pb.redirectErrorStream(true);

      currentProcess = pb.start();
      activeProcesses.add(currentProcess);

      try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream(), Charset.defaultCharset()))) {
        StringBuilder batch = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
          batch.append("  ").append(line).append("\n");

          if (!reader.ready()) {
            appendLogBatched(batch);
          }
        }
        appendLogBatched(batch);
      }

      currentProcess.waitFor();
    } catch (Exception e) {
      if (!stopRequested) {
        Platform.runLater(() -> logToConsole("  HATA: " + e.getMessage()));
      }
    } finally {
      activeProcesses.remove(currentProcess);
      currentProcess = null;
    }
  }

  private void appendLogBatched(StringBuilder batch) {
    if (batch.isEmpty()) {
      return;
    }
    final String text = batch.toString();
    batch.setLength(0);

    Platform.runLater(() -> consoleOutput.appendText(text));
  }

  private void updateMasterCheckboxState() {
    if (projectTableView.getItems().isEmpty()) {
      masterCheckbox.setSelected(false);
      return;
    }
    long selectedCount = projectTableView.getItems().stream().filter(GitProjectDTO::isSelected).count();
    int totalCount = projectTableView.getItems().size();
    if (selectedCount == totalCount) {
      masterCheckbox.setSelected(true);
      masterCheckbox.setIndeterminate(false);
    } else if (selectedCount == 0) {
      masterCheckbox.setSelected(false);
      masterCheckbox.setIndeterminate(false);
    } else {
      masterCheckbox.setSelected(false);
      masterCheckbox.setIndeterminate(true);
    }
  }

  private void logToConsole(String message) {
    consoleOutput.appendText(message + "\n");
  }

  private void copyToClipboard(String text, String fieldName) {
    if (text == null) return;
    Platform.runLater(() -> {
      javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
      content.putString(text);
      javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
      showToast("✔  " + fieldName + " kopyalandı!");
    });
  }

  private void showToast(String message) {
    javafx.stage.Stage appStage = (javafx.stage.Stage) projectTableView.getScene().getWindow();

    javafx.stage.Popup popup = new javafx.stage.Popup();

    Label label = new Label(message);
    label.setStyle(
        "-fx-background-color: #27ae60;" +
        "-fx-text-fill: white;" +
        "-fx-font-size: 13px;" +
        "-fx-font-weight: bold;" +
        "-fx-padding: 10 20 10 20;" +
        "-fx-background-radius: 6;"
    );

    popup.getContent().add(label);
    popup.setAutoHide(true);

    popup.show(appStage, 0, 0);
    double x = appStage.getX() + (appStage.getWidth() / 2) - (popup.getWidth() / 2);
    double y = appStage.getY() + appStage.getHeight() - 80;
    popup.setX(x);
    popup.setY(y);

    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
        javafx.util.Duration.seconds(1.5)
    );
    pause.setOnFinished(e -> popup.hide());
    pause.play();
  }
}