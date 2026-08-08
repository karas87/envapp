package tr.com.envapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import tr.com.envapp.git.GitTabPane;
import tr.com.envapp.model.CredentialDTO;
import tr.com.envapp.search.SearchPane;
import tr.com.envapp.terminal.CommandPromptPane;
import tr.com.envapp.token.TokenPane;

public class EnvCredentialApp extends Application {

  private static final String VERSION = "3.0.0";

  private final ObservableList<CredentialDTO> data = FXCollections.observableArrayList();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private ComboBox<String> envCombo;
  private TableView<CredentialDTO> table;
  private CommandPromptPane commandPromptPane;
  private GitTabPane gitTabPane;

  // TrayIcon referansı
  private TrayIcon trayIcon;

  public static void start() {
    launch();
  }

  @Override
  public void start(Stage stage) {
    Platform.setImplicitExit(false);

    // Asıl UI penceresi
    Stage appStage = stage;

    appStage.setTitle("Environment Credentials Manager v" + VERSION);
    appStage.getIcons().add(new Image(getClass().getResourceAsStream("/setting.png")));

    // Çarpı ile kapanmak yerine gizle
    appStage.setOnCloseRequest(evt -> {
      evt.consume();
      appStage.hide();
    });
    // Simge durumuna (minimize) geçince gizleme — devre dışı, normal minimize olsun
    // appStage.iconifiedProperty().addListener(...);

    // Sistem tepsisi (tray) ikonu kur
    setupSystemTray(appStage);
    ObservableList<CredentialDTO> detailData = FXCollections.observableArrayList();
    TableView<String> envListTable = new TableView<>();

    envCombo = new ComboBox<>();
    envCombo.setMaxWidth(Double.MAX_VALUE);
    envCombo.setStyle("-fx-prompt-text-fill: #bbbbbb;");
    envCombo.setButtonCell(new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? null : item);
        setAlignment(Pos.CENTER);
        setStyle("-fx-font-weight: bold;");
      }
    });
    envCombo.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? null : item);
        setAlignment(Pos.CENTER);
        setStyle("-fx-font-weight: bold;");
      }
    });
    // Eski hard-coded item eklemeleri kaldırıldı; JSON'dan yükle
    ObservableList<String> environments = FXCollections.observableArrayList(loadEnvironments());
    envCombo.setItems(environments);
    envCombo.setPromptText("Select Environment");

    // 🔹 Table + Filtering
    FilteredList<CredentialDTO> filteredData = new FilteredList<>(data, p -> p.getEnv().equalsIgnoreCase("dev"));
    SortedList<CredentialDTO> sortedData = new SortedList<>(filteredData);

    table = new TableView<>();
    table.setEditable(true);
    table.setItems(sortedData);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    sortedData.comparatorProperty().bind(table.comparatorProperty());

    VBox tableContainer = new VBox(5);
    tableContainer.setStyle("-fx-border-color: #555555; -fx-border-radius: 4; -fx-background-color: #3c3f41; -fx-background-radius: 4;");
    tableContainer.setPadding(new Insets(8));

    Label tableLabel = new Label("DEV Ortamı");
    tableLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #bbbbbb;");

    // 🔹 Altına Key Filter ekleme
    TextField keyFilterField = new TextField();
    keyFilterField.setPromptText("Key ara...");
    keyFilterField.setStyle("-fx-prompt-text-fill: #bbbbbb;");
    Button keyFilterClearButton = new Button("x");
    keyFilterClearButton.setFocusTraversable(false);
    keyFilterClearButton.setDisable(true);
    keyFilterClearButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-padding: 2 7 2 7;");
    keyFilterClearButton.setOnAction(e -> keyFilterField.clear());
    keyFilterField.textProperty().addListener((obs, oldVal, newVal) -> {
        keyFilterClearButton.setDisable(newVal == null || newVal.isBlank());
        filteredData.setPredicate(item -> {
          if (newVal == null || newVal.isBlank()) return true;
          return item.getKey().toLowerCase().contains(newVal.toLowerCase());
        });
    });

    keyFilterField.setMaxWidth(Double.MAX_VALUE);

    HBox keyFilterBox = new HBox(4, keyFilterField, keyFilterClearButton);
    keyFilterBox.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(keyFilterField, Priority.ALWAYS);

    HBox searchBox = new HBox(8, keyFilterBox);
    searchBox.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(keyFilterBox, Priority.ALWAYS);

    VBox.setVgrow(table, Priority.ALWAYS);
    tableContainer.getChildren().addAll(tableLabel, searchBox, table);
    VBox.setVgrow(tableContainer, Priority.ALWAYS);
    tableContainer.setMaxWidth(Double.MAX_VALUE);

    // 🔹 Input Alanları
    TextField keyField = new TextField();
    keyField.setPromptText("Key");
    keyField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

    TextField urlField = new TextField();
    urlField.setPromptText("URL");
    urlField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

    TextField usernameField = new TextField();
    usernameField.setPromptText("Username");
    usernameField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

    PasswordField passwordField = new PasswordField();
    passwordField.setPromptText("Password");
    passwordField.setStyle("-fx-prompt-text-fill: #bbbbbb;");
    passwordField.setMaxWidth(Double.MAX_VALUE);

    // Şifreyi göster/gizle için eşleştirilmiş TextField
    TextField passwordVisible = new TextField();
    passwordVisible.setPromptText("Password");
    passwordVisible.setStyle("-fx-prompt-text-fill: #bbbbbb;");
    passwordVisible.setMaxWidth(Double.MAX_VALUE);
    passwordVisible.setVisible(false);
    passwordVisible.setManaged(false);

    // İki alanı senkron tut
    passwordField.textProperty().addListener((obs, o, n) -> { if (!passwordVisible.isFocused()) passwordVisible.setText(n); });
    passwordVisible.textProperty().addListener((obs, o, n) -> { if (!passwordField.isFocused()) passwordField.setText(n); });

    Button showPasswordButton = new Button("👁");
    showPasswordButton.setStyle(
        "-fx-background-color: transparent; -fx-text-fill: #aaaaaa; " +
        "-fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand;"
    );
    showPasswordButton.setOnMousePressed(e -> {
      passwordVisible.setText(passwordField.getText());
      passwordField.setVisible(false);
      passwordField.setManaged(false);
      passwordVisible.setVisible(true);
      passwordVisible.setManaged(true);
    });
    showPasswordButton.setOnMouseReleased(e -> {
      passwordField.setText(passwordVisible.getText());
      passwordVisible.setVisible(false);
      passwordVisible.setManaged(false);
      passwordField.setVisible(true);
      passwordField.setManaged(true);
    });

    StackPane passwordStack = new StackPane(passwordField, passwordVisible);
    HBox.setHgrow(passwordStack, Priority.ALWAYS);
    HBox passwordBox = new HBox(4, passwordStack, showPasswordButton);
    passwordBox.setAlignment(Pos.CENTER_LEFT);
    passwordBox.setMaxWidth(Double.MAX_VALUE);
    HBox.setHgrow(passwordBox, Priority.ALWAYS);

    Button addButton = new Button("✚  Ekle");
    addButton.setStyle(
        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 11px;" +
        "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    );
    addButton.setOnMouseEntered(e -> addButton.setStyle(
        "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 11px;" +
        "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    addButton.setOnMouseExited(e -> addButton.setStyle(
        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 11px;" +
        "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    addButton.setOnAction(e -> {
      String env = getSelectedEnv();
      if (env == null) {
        showAlert("Lütfen bir ortam seçin.");
        return;
      }
      if (keyField.getText().isEmpty()) {
        showAlert("Key boş olamaz.");
        return;
      }

      String key = keyField.getText().trim();
      CredentialDTO newItem = new CredentialDTO(
          env,
          key,
          urlField.getText().trim(),
          usernameField.getText().trim(),
          passwordField.getText().trim()
      );
      saveCurrentEnv(env, newItem);
      String bottomEnv = envListTable.getSelectionModel().getSelectedItem();
      if (bottomEnv != null && bottomEnv.equalsIgnoreCase(env)) {
        detailData.add(newItem);
      }
      if (env.equalsIgnoreCase("dev")) {
        loadDataForDevEnv();
      }

      keyField.clear();
      urlField.clear();
      usernameField.clear();
      passwordField.clear();
      showToast("✔  " + env + " ortamına \"" + key + "\" kaydı eklendi");
    });

    // Input alanlarını GridPane ile düzenle
    GridPane inputGrid = new GridPane();
    inputGrid.setHgap(10);
    inputGrid.setVgap(8);
    inputGrid.setStyle("-fx-padding: 10;");

    // Sütun constraints - URL alanı daha geniş olsun
    ColumnConstraints labelCol = new ColumnConstraints(100);
    ColumnConstraints normalFieldCol = new ColumnConstraints();
    normalFieldCol.setHgrow(Priority.ALWAYS);
    ColumnConstraints urlFieldCol = new ColumnConstraints();
    urlFieldCol.setHgrow(Priority.ALWAYS);
    urlFieldCol.setPrefWidth(400); // URL için daha geniş alan

    inputGrid.getColumnConstraints().addAll(labelCol, normalFieldCol);
    Label envComboLabel = new Label("Environment :");
    envComboLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(envComboLabel, 0, 0);
    inputGrid.add(envCombo, 1, 0);

    // Row 1: Key
    Label keyLabel = new Label("Key:");
    keyLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(keyLabel, 0, 1);
    inputGrid.add(keyField, 1, 1);

    // Row 2: URL (daha geniş)
    Label urlLabel = new Label("URL:");
    urlLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(urlLabel, 0, 2);
    urlField.setMaxWidth(Double.MAX_VALUE);
    inputGrid.add(urlField, 1, 2);

    // Row 3: Username
    Label usernameLabel = new Label("Username:");
    usernameLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(usernameLabel, 0, 3);
    inputGrid.add(usernameField, 1, 3);

    // Row 4: Password
    Label passwordLabel = new Label("Password:");
    passwordLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(passwordLabel, 0, 4);
    inputGrid.add(passwordBox, 1, 4);

    // Row 5: Buttons
    HBox buttonBox = new HBox(10, addButton);
    HBox.setHgrow(buttonBox.getChildren().getFirst(), Priority.ALWAYS);
    buttonBox.setAlignment(Pos.CENTER_LEFT);
    GridPane.setColumnSpan(buttonBox, 2);
    inputGrid.add(buttonBox, 0, 5);

    // Input grid'i bir panel içine al (Search paneli gibi) - Açılır/Kapanır
    VBox inputPanel = new VBox(inputGrid);
    inputPanel.setStyle("-fx-border-color: #555555; -fx-border-radius: 4; -fx-background-color: #3c3f41; -fx-background-radius: 4;");
    inputPanel.setPadding(new Insets(8));
    inputPanel.setVisible(false);
    inputPanel.setManaged(false);

    // Row 1: Ortam

    TextField ortamField = new TextField();
    ortamField.setPromptText("Ortam");
    ortamField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

    GridPane ortamInputGrid = new GridPane();
    ortamInputGrid.setHgap(10);
    ortamInputGrid.setVgap(8);
    ortamInputGrid.setStyle("-fx-padding: 10;");
    ortamInputGrid.getColumnConstraints().addAll(labelCol, normalFieldCol);

    Label ortamLabel = new Label("Ortam:");
    ortamLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    ortamInputGrid.add(ortamLabel, 0, 0);
    ortamInputGrid.add(ortamField, 1, 0);

    Button ortamAddButton = new Button("✚  Ekle");
    ortamAddButton.setStyle(
        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    );
    ortamAddButton.setOnMouseEntered(e -> ortamAddButton.setStyle(
        "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    addButton.setOnMouseExited(e -> ortamAddButton.setStyle(
        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    ortamAddButton.setOnAction(e -> {
      if (ortamField.getText().trim().isEmpty()) {
        showAlert("Ortam boş olamaz.");
        return;
      }
      if (environments.contains(ortamField.getText().trim())) {
        showAlert("Ortam daha önceden eklenmiş.");
        return;
      }
      String ortamAdi = ortamField.getText().trim();
      environments.add(ortamAdi);
      ortamField.clear();
      saveEnvironments();
      showToast("✔  \"" + ortamAdi + "\" ortamı eklendi");
    });
    HBox ortamButtonBox = new HBox(10, ortamAddButton, new Region());
    HBox.setHgrow(ortamButtonBox.getChildren().get(1), Priority.ALWAYS);
    ortamButtonBox.setAlignment(Pos.CENTER_LEFT);
    GridPane.setColumnSpan(ortamButtonBox, 2);
    ortamInputGrid.add(ortamButtonBox, 0, 2);

    // Input grid'i bir panel içine al (Search paneli gibi) - Açılır/Kapanır
    VBox ortamInputPanel = new VBox(ortamInputGrid);
    ortamInputPanel.setStyle("-fx-border-color: #555555; -fx-border-radius: 4; -fx-background-color: #3c3f41; -fx-background-radius: 4;");
    ortamInputPanel.setPadding(new Insets(8));
    ortamInputPanel.setVisible(false);
    ortamInputPanel.setManaged(false);

    Button yeniOrtamEkleToggleBtn = new Button("▶  Yeni Ortam Ekle");
    yeniOrtamEkleToggleBtn.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 11px; -fx-padding: 4 12 4 12;");
    yeniOrtamEkleToggleBtn.setOnAction(e -> {
      boolean expanded = ortamInputPanel.isVisible();
      ortamInputPanel.setVisible(!expanded);
      ortamInputPanel.setManaged(!expanded);
      yeniOrtamEkleToggleBtn.setText(expanded ? "▶  Yeni Ortam Ekle" : "▼  Yeni Ortam Ekle");
      inputPanel.setVisible(false);
      inputPanel.setManaged(false);
    });

    Button yeniKayitEkleToggleBtn = new Button("▶  Yeni Kayıt Ekle");
    yeniKayitEkleToggleBtn.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 11px; -fx-padding: 4 12 4 12;");
    yeniKayitEkleToggleBtn.setOnAction(e -> {
      boolean expanded = inputPanel.isVisible();
      inputPanel.setVisible(!expanded);
      inputPanel.setManaged(!expanded);
      yeniKayitEkleToggleBtn.setText(expanded ? "▶  Yeni Kayıt Ekle" : "▼  Yeni Kayıt Ekle");
      ortamInputPanel.setVisible(false);
      ortamInputPanel.setManaged(false);
    });

    HBox butonBox = new HBox(5, yeniOrtamEkleToggleBtn, yeniKayitEkleToggleBtn);
    VBox inputBox = new VBox(5, butonBox, inputPanel, ortamInputPanel);

    TableColumn<CredentialDTO, String> keyCol = new TableColumn<>("Key");
    keyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));

    TableColumn<CredentialDTO, String> urlCol = new TableColumn<>("URL");
    urlCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUrl()));
    urlCol.setCellFactory(col -> new TableCell<>() {
      private final Hyperlink link = new Hyperlink();
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, link, spacer, copyBtn);

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        link.setStyle("-fx-text-fill: #64b5f6; -fx-underline: true; -fx-font-size: 11px;");
        copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;");
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle("-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          CredentialDTO item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getUrl(), item.getKey() + " - URL");
        });

        link.setOnAction(e -> {
          String url = link.getText();
          if (url != null && !url.isBlank()) {
            try {
              java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception ex) {
              showAlert("URL açılamadı: " + ex.getMessage());
            }
          }
        });
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
        } else {
          link.setText(item);
          setGraphic(box);
        }
      }
    });

    TableColumn<CredentialDTO, String> userCol = new TableColumn<>("Username");
    userCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUsername()));
    userCol.setCellFactory(col -> new TableCell<>() {
      private final Label userLabel = new Label();
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, userLabel, spacer, copyBtn);

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        userLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 14px;");
        copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;");
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle("-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          CredentialDTO item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getUsername(), item.getKey() + " - Username");
        });
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
        } else {
          userLabel.setText(item);
          setGraphic(box);
        }
      }
    });

    TableColumn<CredentialDTO, String> passCol = new TableColumn<>("Password");
    passCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPassword()));
    passCol.setCellFactory(col -> new TableCell<>() {
      private final Label passLabel = new Label("******");
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(8, passLabel, spacer, copyBtn);

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        passLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 14px;");
        copyBtn.setStyle(
            "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
        );
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle(
            "-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 14px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
        ));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle(
            "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
        ));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          CredentialDTO item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getPassword(), item.getKey() + " - Password");
        });
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
        } else {
          setGraphic(box);
        }
      }
    });

    table.getColumns().addAll(keyCol, urlCol, userCol, passCol);

    // Sütun genişliklerini tablo genişliğine orantılı bağla
    table.widthProperty().addListener((obs, oldW, newW) -> {
      double w = newW.doubleValue();
      keyCol.setPrefWidth(w * 0.15);
      urlCol.setPrefWidth(w * 0.55);
      userCol.setPrefWidth(w * 0.15);
      passCol.setPrefWidth(w * 0.15);
    });

    BiConsumer<String, List<CredentialDTO>> saveDetailEnv = (env, list) -> {
      if (env == null || env.isBlank()) return;
      try {
        Path dir = Path.of("data");
        if (!Files.exists(dir)) Files.createDirectories(dir);
        Path file = dir.resolve(env + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), list);
      } catch (IOException ex) {
        System.err.println("Detay kaydetme hatası: " + ex.getMessage());
      }
    };

    TextField envSearchField = new TextField();
    envSearchField.setPromptText("Environment ara...");
    envSearchField.setStyle("-fx-prompt-text-fill: #bbbbbb; -fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41;");

    Button envSearchClearButton = new Button("x");
    envSearchClearButton.setFocusTraversable(false);
    envSearchClearButton.setDisable(true);
    envSearchClearButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-padding: 2 7 2 7;");
    envSearchClearButton.setOnAction(e -> envSearchField.clear());

    TableColumn<String, String> envNameCol = new TableColumn<>("Environment Listesi");
    envNameCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue()));
    envListTable.getColumns().add(envNameCol);
    envListTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    FilteredList<String> filteredEnvs = new FilteredList<>(environments, p -> true);
    envSearchField.textProperty().addListener((obs, oldV, newV) -> {
      envSearchClearButton.setDisable(newV == null || newV.isBlank());
      filteredEnvs.setPredicate(env -> {
        if (newV == null || newV.isBlank()) return true;
        return env.toLowerCase().contains(newV.toLowerCase());
      });
    });
    SortedList<String> sortedEnvs = new SortedList<>(filteredEnvs);
    sortedEnvs.comparatorProperty().bind(envListTable.comparatorProperty());
    envListTable.setItems(sortedEnvs);

    HBox envSearchBox = new HBox(4, envSearchField, envSearchClearButton);
    HBox.setHgrow(envSearchField, Priority.ALWAYS);
    envSearchBox.setAlignment(Pos.CENTER_LEFT);
    VBox leftBottomBox = new VBox(5, envSearchBox, envListTable);
    VBox.setVgrow(envListTable, Priority.ALWAYS);

    // 2. Sağ Bölüm: Seçilen Environment'ın Detayları (Arama ve Düzenlenebilir Liste)
    TextField detailSearchField = new TextField();
    detailSearchField.setPromptText("Detay ara (Key)...");
    detailSearchField.setStyle("-fx-prompt-text-fill: #bbbbbb; -fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41;");

    Button detailSearchClearButton = new Button("x");
    detailSearchClearButton.setFocusTraversable(false);
    detailSearchClearButton.setDisable(true);
    detailSearchClearButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-padding: 2 7 2 7;");
    detailSearchClearButton.setOnAction(e -> detailSearchField.clear());

    TableView<CredentialDTO> detailTable = new TableView<>();
    detailTable.setEditable(true);
    detailTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

    // Detay Tablosu - Key Sütunu (Düzenlenebilir)
    TableColumn<CredentialDTO, String> dKeyCol = new TableColumn<>("Key");
    dKeyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));
    dKeyCol.setCellFactory(col -> new TableCell<>() {
      private final Label keyLabel = new Label();
      private final Button deleteBtn = new Button("🗑");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, keyLabel, spacer, deleteBtn);
      private final TextField editField = new TextField();
      private boolean cancelledByEscape = false;
      private boolean justCommitted = false;

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        keyLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #e0e0e0; -fx-font-size: 14px;");

        // Silme Butonu Tasarımı (Kırmızı temalı)
        deleteBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;");
        deleteBtn.setOnMouseEntered(e -> deleteBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        deleteBtn.setOnMouseExited(e -> deleteBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        // Silme Aksiyonu
        deleteBtn.setOnAction(e -> {
          CredentialDTO item = getTableView().getItems().get(getIndex());

          ButtonType evetType = new ButtonType("Evet", ButtonBar.ButtonData.YES);
          ButtonType hayirType = new ButtonType("Hayır", ButtonBar.ButtonData.NO);
          Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
          confirm.setTitle("Silme Onayı");
          confirm.setHeaderText(null);
          confirm.setContentText("Bu kaydı silmek istediğinize emin misiniz?\nKey: " + item.getKey());
          confirm.getButtonTypes().setAll(evetType, hayirType);
          applyAlertTheme(confirm);

          if (confirm.showAndWait().orElse(hayirType) == evetType) {
            String sel = envListTable.getSelectionModel().getSelectedItem();

            // Tablodan değil, bağlı olduğu ana veriden (detailData) siliyoruz.
            // Bu işlem otomatik olarak TableView'ı da güncelleyecektir.
            detailData.remove(item);

            if (sel != null) {
              // Güncel ana listeyi JSON'a yazıyoruz
              saveDetailEnv.accept(sel, detailData);
              if (sel.equalsIgnoreCase("dev")) {
                loadDataForDevEnv();
              }
            }
          }
        });

        // Düzenleme Alanı Tasarımı
        editField.setStyle("-fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41; -fx-font-size: 14px;");
        editField.setOnAction(e -> commitEdit(editField.getText()));
        editField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
          if (e.getCode() == KeyCode.ESCAPE) cancelledByEscape = true;
        });
        editField.focusedProperty().addListener((obs, oldV, newV) -> {
          if (!newV && isEditing()) {
            commitEdit(editField.getText());
            String sel = envListTable.getSelectionModel().getSelectedItem();
            if (sel.equalsIgnoreCase("dev")) {
              loadDataForDevEnv();
            }
          }
        });
      }

      @Override
      public void startEdit() {
        super.startEdit();
        editField.setText(getItem());
        setGraphic(editField);
        editField.selectAll();
        editField.requestFocus();
      }

      @Override
      public void commitEdit(String newValue) {
        justCommitted = true;
        super.commitEdit(newValue);
        setGraphic(box);
      }

      @Override
      public void cancelEdit() {
        if (!cancelledByEscape && !justCommitted) {
          int idx = getIndex();
          if (idx >= 0 && idx < getTableView().getItems().size()) {
            String text = editField.getText();
            getTableView().getItems().get(idx).setKey(text);
            String sel = envListTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
              saveDetailEnv.accept(sel, detailData);
              if (sel.equalsIgnoreCase("dev")) loadDataForDevEnv();
            }
            keyLabel.setText(text);
          }
        }
        cancelledByEscape = false;
        justCommitted = false;
        super.cancelEdit();
        setGraphic(box);
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
        } else {
          keyLabel.setText(item);
          setGraphic(isEditing() ? editField : box);
        }
      }
    });
    dKeyCol.setOnEditCommit(evt -> {
      CredentialDTO c = evt.getRowValue();
      c.setKey(evt.getNewValue());
      String sel = envListTable.getSelectionModel().getSelectedItem();
      if (sel != null) saveDetailEnv.accept(sel, detailData);
    });

    // Detay Tablosu - URL Sütunu (Kopyalama Butonlu & Düzenlenebilir)
    TableColumn<CredentialDTO, String> dUrlCol = new TableColumn<>("URL");
    dUrlCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUrl()));
    dUrlCol.setCellFactory(col -> new TableCell<>() {
      private final TextField textField = new TextField();
      private final Hyperlink link = new Hyperlink();
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, link, spacer, copyBtn);
      private boolean cancelledByEscape = false;
      private boolean justCommitted = false;

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        textField.setStyle("-fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41; -fx-font-size: 11px;");
        link.setStyle("-fx-text-fill: #64b5f6; -fx-underline: true; -fx-font-size: 11px;");
        copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;");
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle("-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          CredentialDTO item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getUrl(), item.getKey() + " - URL");
        });

        textField.setOnAction(e -> commitEdit(textField.getText()));
        textField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
          if (e.getCode() == KeyCode.ESCAPE) cancelledByEscape = true;
        });
        textField.focusedProperty().addListener((obs, oldV, newV) -> {
          if (!newV && isEditing()) commitEdit(textField.getText());
        });

        link.setOnAction(e -> {
          String url = link.getText();
          if (url != null && !url.isBlank()) {
            try {
              java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } catch (Exception ex) {
              showAlert("URL açılamadı: " + ex.getMessage());
            }
          }
        });
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setStyle("-fx-font-size: 11px;");
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
        } else {
          if (isEditing()) {
            textField.setText(item);
            setGraphic(textField);
          } else {
            link.setText(item);
            setGraphic(box);
          }
        }
      }

      @Override
      public void startEdit() {
        super.startEdit();
        if (getItem() != null) {
          textField.setText(getItem());
          setGraphic(textField);
          textField.selectAll();
          textField.requestFocus();
        }
      }

      @Override
      public void commitEdit(String newValue) {
        justCommitted = true;
        super.commitEdit(newValue);
        getTableView().getItems().get(getIndex()).setUrl(newValue);
        String sel = envListTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
          saveDetailEnv.accept(sel, detailData);

          if (sel.equalsIgnoreCase("dev")) {
            loadDataForDevEnv();
          }
        }
      }

      @Override
      public void cancelEdit() {
        if (!cancelledByEscape && !justCommitted) {
          int idx = getIndex();
          if (idx >= 0 && idx < getTableView().getItems().size()) {
            String text = textField.getText();
            getTableView().getItems().get(idx).setUrl(text);
            String sel = envListTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
              saveDetailEnv.accept(sel, detailData);
              if (sel.equalsIgnoreCase("dev")) loadDataForDevEnv();
            }
            link.setText(text);
          }
        }
        cancelledByEscape = false;
        justCommitted = false;
        super.cancelEdit();
        if (link.getText() == null) link.setText(getItem() != null ? getItem() : "");
        setGraphic(box);
      }
    });

    // Detay Tablosu - Username Sütunu (Kopyalama Butonlu & Düzenlenebilir)
    TableColumn<CredentialDTO, String> dUserCol = new TableColumn<>("Username");
    dUserCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUsername()));
    dUserCol.setCellFactory(col -> new TableCell<>() {
      private final Label userLabel = new Label();
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, userLabel, spacer, copyBtn);
      private final TextField editField = new TextField();
      private boolean cancelledByEscape = false;
      private boolean justCommitted = false;

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        userLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 14px;");
        copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;");
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle("-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          CredentialDTO item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getUsername(), item.getKey() + " - Username");
        });

        editField.setStyle("-fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41; -fx-font-size: 14px;");
        editField.setOnAction(e -> commitEdit(editField.getText()));
        editField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
          if (e.getCode() == KeyCode.ESCAPE) cancelledByEscape = true;
        });
        editField.focusedProperty().addListener((obs, oldV, newV) -> {
          if (!newV && isEditing()) commitEdit(editField.getText());
        });
      }

      @Override
      public void startEdit() {
        super.startEdit();
        editField.setText(getItem());
        setGraphic(editField);
        editField.selectAll();
        editField.requestFocus();
      }

      @Override
      public void commitEdit(String newValue) {
        justCommitted = true;
        super.commitEdit(newValue);
        getTableView().getItems().get(getIndex()).setUsername(newValue);
        String sel = envListTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
          saveDetailEnv.accept(sel, detailData);
          if (sel.equalsIgnoreCase("dev")) {
            loadDataForDevEnv();
          }
        }
        setGraphic(box);
      }

      @Override
      public void cancelEdit() {
        if (!cancelledByEscape && !justCommitted) {
          int idx = getIndex();
          if (idx >= 0 && idx < getTableView().getItems().size()) {
            String text = editField.getText();
            getTableView().getItems().get(idx).setUsername(text);
            String sel = envListTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
              saveDetailEnv.accept(sel, detailData);
              if (sel.equalsIgnoreCase("dev")) loadDataForDevEnv();
            }
            userLabel.setText(text);
          }
        }
        cancelledByEscape = false;
        justCommitted = false;
        super.cancelEdit();
        setGraphic(box);
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
        } else {
          userLabel.setText(item);
          setGraphic(isEditing() ? editField : box);
        }
      }
    });

    // Detay Tablosu - Password Sütunu (****** Maskeli, Kopyalama Butonlu & Düzenlenebilir)
    TableColumn<CredentialDTO, String> dPassCol = new TableColumn<>("Password");
    dPassCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPassword()));
    dPassCol.setCellFactory(col -> new TableCell<>() {
      private final Label passLabel = new Label("******");
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(8, passLabel, spacer, copyBtn);
      private final TextField editField = new TextField();
      private boolean cancelledByEscape = false;
      private boolean justCommitted = false;

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        passLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 14px;");
        copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;");
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle("-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          CredentialDTO item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getPassword(), item.getKey() + " - Password");
        });

        editField.setStyle("-fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41; -fx-font-size: 14px;");
        editField.setOnAction(e -> commitEdit(editField.getText()));
        editField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
          if (e.getCode() == KeyCode.ESCAPE) cancelledByEscape = true;
        });
        editField.focusedProperty().addListener((obs, oldV, newV) -> {
          if (!newV && isEditing()) commitEdit(editField.getText());
        });
      }

      @Override
      public void startEdit() {
        super.startEdit();
        editField.setText(getItem());
        setGraphic(editField);
        editField.selectAll();
        editField.requestFocus();
      }

      @Override
      public void commitEdit(String newValue) {
        justCommitted = true;
        super.commitEdit(newValue);
        getTableView().getItems().get(getIndex()).setPassword(newValue);
        String sel = envListTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
          saveDetailEnv.accept(sel, detailData);
          if (sel.equalsIgnoreCase("dev")) {
            loadDataForDevEnv();
          }
        }
        setGraphic(box);
      }

      @Override
      public void cancelEdit() {
        if (!cancelledByEscape && !justCommitted) {
          int idx = getIndex();
          if (idx >= 0 && idx < getTableView().getItems().size()) {
            String text = editField.getText();
            getTableView().getItems().get(idx).setPassword(text);
            String sel = envListTable.getSelectionModel().getSelectedItem();
            if (sel != null) {
              saveDetailEnv.accept(sel, detailData);
              if (sel.equalsIgnoreCase("dev")) loadDataForDevEnv();
            }
          }
        }
        cancelledByEscape = false;
        justCommitted = false;
        super.cancelEdit();
        setGraphic(box);
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
        } else {
          setGraphic(isEditing() ? editField : box);
        }
      }
    });
    dPassCol.setEditable(true);

    detailTable.getColumns().addAll(dKeyCol, dUrlCol, dUserCol, dPassCol);

    // Detay tablosu sütun genişliklerini oranlama
    detailTable.widthProperty().addListener((obs, oldW, newW) -> {
      double w = newW.doubleValue();
      dKeyCol.setPrefWidth(w * 0.15);
      dUrlCol.setPrefWidth(w * 0.55);
      dUserCol.setPrefWidth(w * 0.15);
      dPassCol.setPrefWidth(w * 0.15);
    });

    FilteredList<CredentialDTO> filteredDetails = new FilteredList<>(detailData, p -> true);
    detailSearchField.textProperty().addListener((obs, oldV, newV) -> {
      detailSearchClearButton.setDisable(newV == null || newV.isBlank());
      filteredDetails.setPredicate(item -> {
        if (newV == null || newV.isBlank()) return true;
        return item.getKey().toLowerCase().contains(newV.toLowerCase());
      });
    });
    SortedList<CredentialDTO> sortedDetails = new SortedList<>(filteredDetails);
    sortedDetails.comparatorProperty().bind(detailTable.comparatorProperty());
    detailTable.setItems(sortedDetails);

    HBox detailSearchBox = new HBox(4, detailSearchField, detailSearchClearButton);
    HBox.setHgrow(detailSearchField, Priority.ALWAYS);
    detailSearchBox.setAlignment(Pos.CENTER_LEFT);
    VBox rightBottomBox = new VBox(5, detailSearchBox, detailTable);
    VBox.setVgrow(detailTable, Priority.ALWAYS);

    // Sol taraftan seçim yapıldığında sağdaki detay tablosunu tetikleyen dinleyici
    envListTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
      detailData.clear();
      if (newSelection != null) {
        Path file = Path.of("data", newSelection + ".json");
        if (Files.exists(file)) {
          try {
            List<CredentialDTO> list = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
            detailData.setAll(list);
          } catch (IOException ex) {
            System.err.println("Detay verisi okunamadı: " + ex.getMessage());
          }
        }
      }
    });

    // 3. Bölüm Düzeni: SplitPane kaldırıldı, %30 - %70 genişlik oranlı HBox eklendi
    HBox bottomHBox = new HBox(10, leftBottomBox, rightBottomBox);
    VBox.setVgrow(bottomHBox, Priority.ALWAYS);

    // Dinamik genişlik bağlama oranları (%30 ve %70)
    leftBottomBox.prefWidthProperty().bind(bottomHBox.widthProperty().multiply(0.3));
    rightBottomBox.prefWidthProperty().bind(bottomHBox.widthProperty().multiply(0.7));

    VBox root = new VBox(10, inputBox, tableContainer, bottomHBox);
    root.setPadding(new Insets(10));
    VBox.setVgrow(tableContainer, Priority.ALWAYS);
    root.setMaxWidth(Double.MAX_VALUE);
    root.setMaxHeight(Double.MAX_VALUE);

    // === TABS ===
    Tab credTab = new Tab("🔐  Credentials");
    credTab.setContent(root);
    credTab.setClosable(false);

    Tab searchTab = new Tab("🔍  Search");
    SearchPane searchPane = new SearchPane(appStage);
    searchTab.setContent(searchPane.build());
    searchTab.setClosable(false);

    Tab tokenTab = new Tab("🔑  Token");
    TokenPane tokenPane = new TokenPane();
    tokenTab.setContent(tokenPane.build());
    tokenTab.setClosable(false);

    Tab terminalTab = new Tab(">_  Terminal");
    commandPromptPane = new CommandPromptPane();
    terminalTab.setContent(commandPromptPane.build());
    terminalTab.setClosable(false);

    Tab gitTab = new Tab("🔀 Git");
    gitTabPane = new GitTabPane();
    gitTab.setContent(gitTabPane);
    gitTab.setClosable(false);

    TabPane tabPane = new TabPane(credTab, searchTab, tokenTab, terminalTab, gitTab);
    tabPane.setTabMinHeight(32);
    tabPane.setTabMinWidth(120);
    VBox.setVgrow(tabPane, Priority.ALWAYS);

    VBox mainRoot = new VBox(tabPane);
    VBox.setVgrow(tabPane, Priority.ALWAYS);

    Scene scene = new Scene(mainRoot, 1200, 600);

    // Dark tema CSS dosyasını yükle
    String css = getClass().getResource("/dark-theme.css").toExternalForm();
    scene.getStylesheets().add(css);

    // Dark tema CSS - İyileştirilmiş okunabilirlik
    scene.getRoot().setStyle(
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

    // TextFields, ComboBox ve diğer kontroller için ek CSS
    String fieldStyle = "-fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41; -fx-font-size: 11px;";
    String tableStyle = "-fx-text-fill: #e0e0e0; -fx-font-size: 14px;";

    // Kontroller için stil uygulayalım
    keyFilterField.setStyle(fieldStyle + " -fx-prompt-text-fill: #bbbbbb;");
    table.setStyle(tableStyle);

    appStage.setScene(scene);
    appStage.setMaximized(true);
    appStage.show();

    // Başlangıçta ilk ortamı seç ve veriyi yükle
    if (!environments.isEmpty()) {
      envCombo.getSelectionModel().selectFirst();
    }
    loadDataForDevEnv();
  }

  // Sistem tepsisi (tray) kurulum metodu
  private void setupSystemTray(Stage stage) {
    // stage parametresi appStage olacak şekilde kullanılıyor
    if (!SystemTray.isSupported()) {
      return;
    }

    try {
      SystemTray tray = SystemTray.getSystemTray();

      // Resource'dan ikon oku
      BufferedImage trayImage;
      try {
        trayImage = ImageIO.read(getClass().getResource("/setting.png"));
      } catch (Exception ignored) {
        // Yedek basit ikon
        trayImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = trayImage.createGraphics();
        g2.setColor(Color.DARK_GRAY);
        g2.fillOval(0, 0, 16, 16);
        g2.dispose();
      }

      PopupMenu popup = new PopupMenu();

      java.awt.MenuItem showItem = new java.awt.MenuItem("Göster");
      java.awt.MenuItem hideItem = new java.awt.MenuItem("Gizle");
      java.awt.MenuItem aboutItem = new java.awt.MenuItem("Hakkında");
      java.awt.MenuItem exitItem = new java.awt.MenuItem("Çıkış");

      ActionListener showAction = e -> Platform.runLater(() -> {
        if (!stage.isShowing()) {
          stage.show();
        }
        // Pencereyi kesin öne getir
        bringToFront(stage);
      });
      ActionListener hideAction = e -> Platform.runLater(stage::hide);
      ActionListener aboutAction = e -> Platform.runLater(() -> {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Hakkında");
        alert.setHeaderText("Env Credentials");
        alert.setContentText("Sürüm: " + VERSION);
        applyAlertTheme(alert);
        alert.showAndWait();
      });
      ActionListener exitAction = e -> Platform.runLater(() -> {
        // Tray ikonunu kaldır ve uygulamayı sonlandır
        try {
          SystemTray.getSystemTray().remove(trayIcon);
        } catch (Exception ignored) {}
        commandPromptPane.shutdown();
        gitTabPane.shutdown(); // EKLENEN SATIR: Git işlemlerini de öldür
        Platform.exit();
      });

      showItem.addActionListener(showAction);
      hideItem.addActionListener(hideAction);
      aboutItem.addActionListener(aboutAction);
      exitItem.addActionListener(exitAction);

      popup.add(showItem);
      popup.add(hideItem);
      popup.add(aboutItem);
      popup.addSeparator();
      popup.add(exitItem);

      TrayIcon icon = new TrayIcon(trayImage, "Env Credentials v" + VERSION, popup);
      icon.setImageAutoSize(true);

      // Tek tık ile toggle (Göster/Gizle)
      icon.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseReleased(MouseEvent e) {
          if (e.getButton() == MouseEvent.BUTTON1) {
            Platform.runLater(() -> {
              if (stage.isShowing()) {
                stage.hide();
              } else {
                stage.show();
                bringToFront(stage);
              }
            });
          }
        }
      });

      // TrayIcon referansını çıkışta kaldırmak için field'da tut
      this.trayIcon = icon;

      tray.add(icon);
    } catch (Exception ex) {
      showAlert("Tray kurulumu başarısız: " + ex.getMessage());
    }
  }

  private String getSelectedEnv() {
    return envCombo.getValue();
  }

  // Ortam listesini ayrı JSON'dan yükler: data/envs.json (List<String>)
  private List<String> loadEnvironments() {
    Path dir = Path.of("data");
    Path file = dir.resolve("envs.json");
    try {
      if (!Files.exists(dir)) {
        Files.createDirectories(dir);
      }
      if (Files.exists(file)) {
        return objectMapper.readValue(file.toFile(), new TypeReference<List<String>>() {});
      } else {
        // Varsayılan listeyi oluştur ve dosyaya yaz
        List<String> defaults = List.of(
            "Dev"
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), defaults);
        return defaults;
      }
    } catch (IOException e) {
      showAlert("Ortam listesi yüklenemedi: " + e.getMessage());
      // Hata durumunda yine de varsayılanı döndür
      return List.of(
          "Dev"
      );
    }
  }

  // Ortam listesini ayrı JSON'a kaydeder: data/envs.json (List<String>)
  private void saveEnvironments() {
    Path dir = Path.of("data");
    Path file = dir.resolve("envs.json");
    try {
      if (!Files.exists(dir)) {
        Files.createDirectories(dir);
      }
      if (Files.exists(file)) {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), envCombo.getItems());
      }
    } catch (IOException e) {
      showAlert("Ortam dosyası bulunamadı: " + e.getMessage());
    }
  }

  private void loadDataForDevEnv() {
    Path file = Path.of("data", "Dev.json");
    if (!Files.exists(file)) {
      data.clear();
      return;
    }

    try {
      List<CredentialDTO> list = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
      data.setAll(list);
    } catch (IOException e) {
      showAlert("Yükleme hatası: " + e.getMessage());
    }
  }

  private void saveCurrentEnv(String env, CredentialDTO newItem) {
    if (env == null || env.isBlank()) return;
    try {
      Path dir = Path.of("data");
      if (!Files.exists(dir)) Files.createDirectories(dir);
      Path file = dir.resolve(env + ".json");
      List<CredentialDTO> list;
      if (!Files.exists(file)) {
        Files.createFile(file);
        list = List.of(newItem);
      } else {
        list = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
        list.add(newItem);
      }
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), list);
    } catch (IOException e) {
      showAlert("Kaydetme hatası: " + e.getMessage());
    }
  }

  private void showAlert(String message) {
    Platform.runLater(() -> {
      Alert alert = new Alert(Alert.AlertType.ERROR);
      alert.setTitle("Hata");
      alert.setHeaderText(null);
      alert.setContentText(message);
      applyAlertTheme(alert);
      alert.show();
    });
  }

  private void applyAlertTheme(Alert alert) {
    alert.getDialogPane().getStylesheets().add(
        getClass().getResource("/dark-theme.css").toExternalForm()
    );
    alert.getDialogPane().setStyle(
        "-fx-base: #3c3f41;" +
        "-fx-background: #2b2b2b;" +
        "-fx-control-inner-background: #45494a;" +
        "-fx-text-fill: #e0e0e0;" +
        "-fx-font-family: 'Segoe UI';" +
        "-fx-font-size: 11px;"
    );
    if (table != null && table.getScene() != null) {
      Stage ownerStage = (Stage) table.getScene().getWindow();
      alert.initOwner(ownerStage);
      alert.showingProperty().addListener((obs, wasShowing, isNowShowing) -> {
        if (isNowShowing) {
          Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
          alertStage.getIcons().addAll(ownerStage.getIcons());
        }
      });
    }
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
    Stage appStage = (Stage) table.getScene().getWindow();

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

    // Popup gösterildikten sonra genişliği bilindiğinde ortala
    popup.show(appStage, 0, 0); // önce göster, genişlik hesaplansın
    double x = appStage.getX() + (appStage.getWidth() / 2) - (popup.getWidth() / 2);
    double y = appStage.getY() + appStage.getHeight() - 80;
    popup.setX(x);
    popup.setY(y);

    // 1.5 saniye sonra otomatik kapat
    javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
        javafx.util.Duration.seconds(1.5)
    );
    pause.setOnFinished(e -> popup.hide());
    pause.play();
  }

  private void bringToFront(Stage stage) {
    if (stage == null) return;
    // Windows'ta güvenilir şekilde öne getirmek için AlwaysOnTop toggle kullan
    boolean prev = stage.isAlwaysOnTop();
    stage.setAlwaysOnTop(true);
    stage.toFront();
    stage.requestFocus();
    // kısa gecikme ile eski haline dön
    Platform.runLater(() -> stage.setAlwaysOnTop(prev));
  }
}
