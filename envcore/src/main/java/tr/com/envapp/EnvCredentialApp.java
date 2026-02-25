package tr.com.envapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.converter.DefaultStringConverter;
import javax.imageio.ImageIO;

public class EnvCredentialApp extends Application {

  private static final String VERSION = "2.0.0";

  private final ObservableList<Credential> data = FXCollections.observableArrayList();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private ComboBox<String> envCombo;
  private TableView<Credential> table;

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

    envCombo = new ComboBox<>();
    envCombo.setMaxWidth(Double.MAX_VALUE);
    envCombo.setStyle("-fx-font-size: 16px;");
    envCombo.setButtonCell(new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? null : item);
        setAlignment(Pos.CENTER);
        setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
      }
    });
    envCombo.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        setText(empty ? null : item);
        setAlignment(Pos.CENTER);
        setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
      }
    });
    // Eski hard-coded item eklemeleri kaldırıldı; JSON'dan yükle
    ObservableList<String> environments = FXCollections.observableArrayList(loadEnvironments());
    envCombo.setItems(environments);
    envCombo.setPromptText("Select Environment");
    envCombo.setOnAction(e -> loadDataForSelectedEnv());

    // 🔹 Table + Filtering
    FilteredList<Credential> filteredData = new FilteredList<>(data, p -> true);
    SortedList<Credential> sortedData = new SortedList<>(filteredData);

    table = new TableView<>();
    table.setEditable(true);
    table.setItems(sortedData);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    sortedData.comparatorProperty().bind(table.comparatorProperty());

    // 🔹 Altına Key Filter ekleme
    TextField keyFilterField = new TextField();
    keyFilterField.setPromptText("Key ara...");
    keyFilterField.setStyle("-fx-prompt-text-fill: #bbbbbb;");
    keyFilterField.textProperty().addListener((obs, oldVal, newVal) ->
        filteredData.setPredicate(item -> {
          if (newVal == null || newVal.isBlank()) return true;
          return item.getKey().toLowerCase().contains(newVal.toLowerCase());
        }));

    keyFilterField.setMaxWidth(Double.MAX_VALUE);
    HBox keyFilterBox = new HBox(keyFilterField);
    keyFilterBox.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(keyFilterField, Priority.ALWAYS);

    VBox tableContainer = new VBox(10, keyFilterBox, table);
    VBox.setVgrow(table, Priority.ALWAYS);
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

    TextField passwordField = new TextField();
    passwordField.setPromptText("Password");
    passwordField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

    Button addButton = new Button("✚  Add");
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

      Credential newItem = new Credential(
          env,
          keyField.getText().trim(),
          urlField.getText().trim(),
          usernameField.getText().trim(),
          passwordField.getText().trim()
      );
      data.add(newItem);
      saveCurrentEnv(env);

      keyField.clear();
      urlField.clear();
      usernameField.clear();
      passwordField.clear();
    });

    // Delete butonu - seçili satırı siler
    Button deleteButton = new Button("🗑  Delete");
    deleteButton.setStyle(
        "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 11px;" +
        "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    );
    deleteButton.setOnMouseEntered(e -> deleteButton.setStyle(
        "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-size: 11px;" +
        "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    deleteButton.setOnMouseExited(e -> deleteButton.setStyle(
        "-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-size: 11px;" +
        "-fx-font-weight: bold; -fx-padding: 4 14 4 14; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    deleteButton.setOnAction(e -> {
      Credential selected = table.getSelectionModel().getSelectedItem();
      if (selected == null) {
        showAlert("Lütfen silmek istediğiniz kaydı seçin.");
        return;
      }
      Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
      confirm.setTitle("Silme Onayı");
      confirm.setHeaderText(null);
      confirm.setContentText("Bu kaydı silmek istediğinize emin misiniz?\nKey: " + selected.getKey());
      confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
      if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
        data.remove(selected);
        saveCurrentEnv(getSelectedEnv());
      }
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

    // Row 1: Key
    Label keyLabel = new Label("Key:");
    keyLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(keyLabel, 0, 0);
    inputGrid.add(keyField, 1, 0);

    // Row 2: URL (daha geniş)
    Label urlLabel = new Label("URL:");
    urlLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(urlLabel, 0, 1);
    urlField.setMaxWidth(Double.MAX_VALUE);
    inputGrid.add(urlField, 1, 1);

    // Row 3: Username
    Label usernameLabel = new Label("Username:");
    usernameLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(usernameLabel, 0, 2);
    inputGrid.add(usernameField, 1, 2);

    // Row 4: Password
    Label passwordLabel = new Label("Password:");
    passwordLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-weight: bold;");
    inputGrid.add(passwordLabel, 0, 3);
    inputGrid.add(passwordField, 1, 3);

    // Row 5: Buttons
    HBox buttonBox = new HBox(10, addButton, new Region(), deleteButton);
    HBox.setHgrow(buttonBox.getChildren().get(1), Priority.ALWAYS);
    buttonBox.setAlignment(Pos.CENTER_LEFT);
    GridPane.setColumnSpan(buttonBox, 2);
    inputGrid.add(buttonBox, 0, 4);

    // Input grid'i bir panel içine al (Search paneli gibi) - Açılır/Kapanır
    VBox inputPanel = new VBox(inputGrid);
    inputPanel.setStyle("-fx-border-color: #555555; -fx-border-radius: 4; -fx-background-color: #3c3f41; -fx-background-radius: 4;");
    inputPanel.setPadding(new Insets(8));
    inputPanel.setVisible(false);
    inputPanel.setManaged(false);

    Button toggleBtn = new Button("▶  Yeni Kayıt Ekle");
    toggleBtn.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 11px; -fx-padding: 4 12 4 12;");
    toggleBtn.setOnAction(e -> {
      boolean expanded = inputPanel.isVisible();
      inputPanel.setVisible(!expanded);
      inputPanel.setManaged(!expanded);
      toggleBtn.setText(expanded ? "▶  Yeni Kayıt Ekle" : "▼  Yeni Kayıt Ekle");
    });

    VBox inputBox = new VBox(5, toggleBtn, inputPanel);

    // NOW define table columns (deleteCheckBox is now defined)
    TableColumn<Credential, String> keyCol = makeEditableColumn("Key", Credential::getKey, Credential::setKey);
    keyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));
    keyCol.setCellFactory(col -> new TextFieldTableCell<>(new DefaultStringConverter()) {
      @Override
      public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty && item != null) {
          setText(item);
          setStyle("-fx-font-weight: bold; -fx-text-fill: #e0e0e0; -fx-font-size: 14px;");
        } else {
          setText(null);
          setStyle("");
        }
      }
    });
    TableColumn<Credential, String> urlCol = makeEditableColumn("URL", Credential::getUrl, Credential::setUrl);
    urlCol.setCellFactory(col -> new TableCell<>() {
      private final TextField textField = new TextField();
      private final Hyperlink link = new Hyperlink();
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, link, spacer, copyBtn);

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
          Credential item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getUrl(), item.getKey() + " - URL");
        });

        textField.setOnAction(e -> commitEdit(textField.getText()));
        textField.focusedProperty().addListener((obs, oldV, newV) -> {
          if (!newV) commitEdit(textField.getText());
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
        super.commitEdit(newValue);
        getTableView().getItems().get(getIndex()).setUrl(newValue);
        saveCurrentEnv(getSelectedEnv());
      }

      @Override
      public void cancelEdit() {
        super.cancelEdit();
        link.setText(getItem());
        setGraphic(box);
      }
    });

    TableColumn<Credential, String> userCol = makeEditableColumn("Username", Credential::getUsername, Credential::setUsername);
    userCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getUsername()));
    userCol.setCellFactory(col -> new TableCell<>() {
      private final Label userLabel = new Label();
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, userLabel, spacer, copyBtn);
      private final TextField editField = new TextField();

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        userLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 14px;");
        copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;");
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle("-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;"));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          Credential item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getUsername(), item.getKey() + " - Username");
        });

        editField.setStyle("-fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41; -fx-font-size: 14px;");
        editField.setOnAction(e -> commitEdit(editField.getText()));
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
        super.commitEdit(newValue);
        getTableView().getItems().get(getIndex()).setUsername(newValue);
        saveCurrentEnv(getSelectedEnv());
        setGraphic(box);
      }

      @Override
      public void cancelEdit() {
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

    TableColumn<Credential, String> passCol = new TableColumn<>("Password");
    passCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getPassword()));
    passCol.setCellFactory(col -> new TableCell<>() {
      private final Label passLabel = new Label("******");
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(8, passLabel, spacer, copyBtn);
      private final TextField editField = new TextField();

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
          Credential item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getPassword(), item.getKey() + " - Password");
        });

        editField.setStyle("-fx-text-fill: #e0e0e0; -fx-control-inner-background: #3c3f41; -fx-font-size: 14px;");
        editField.setOnAction(e -> commitEdit(editField.getText()));
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
        super.commitEdit(newValue);
        Credential item = getTableView().getItems().get(getIndex());
        item.setPassword(newValue);
        saveCurrentEnv(getSelectedEnv());
        setGraphic(box);
      }

      @Override
      public void cancelEdit() {
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
    passCol.setEditable(true);

    table.getColumns().addAll(keyCol, urlCol, userCol, passCol);

    // Sütun genişliklerini tablo genişliğine orantılı bağla
    table.widthProperty().addListener((obs, oldW, newW) -> {
      double w = newW.doubleValue() - 2;
      keyCol.setPrefWidth(w * 0.15);
      urlCol.setPrefWidth(w * 0.70);
      userCol.setPrefWidth(w * 0.09);
      passCol.setPrefWidth(w * 0.08);
    });


    VBox root = new VBox(10, envCombo, inputBox, tableContainer);
    root.setPadding(new Insets(10));
    VBox.setVgrow(tableContainer, Priority.ALWAYS);
    root.setMaxWidth(Double.MAX_VALUE);
    root.setMaxHeight(Double.MAX_VALUE);

    // === TABS ===
    Tab credTab = new Tab("🔐  Credentials");
    credTab.setContent(root);
    credTab.setClosable(false);

    Tab searchTab = new Tab("🔍  Search");
    tr.com.envapp.search.SearchPane searchPane = new tr.com.envapp.search.SearchPane(appStage);
    searchTab.setContent(searchPane.build());
    searchTab.setClosable(false);

    TabPane tabPane = new TabPane(credTab, searchTab);
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
    String buttonStyle = "-fx-text-fill: #e0e0e0; -fx-font-size: 11px;";
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
    loadDataForSelectedEnv();
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
        new Alert(Alert.AlertType.INFORMATION, "Env Credentials\nSürüm: " + VERSION, ButtonType.OK).showAndWait();
      });
      ActionListener exitAction = e -> Platform.runLater(() -> {
        // Tray ikonunu kaldır ve uygulamayı sonlandır
        try {
          SystemTray.getSystemTray().remove(trayIcon);
        } catch (Exception ignored) {}
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

  private TableColumn<Credential, String> makeEditableColumn(
      String title,
      java.util.function.Function<Credential, String> getter,
      java.util.function.BiConsumer<Credential, String> setter) {

    TableColumn<Credential, String> col = new TableColumn<>(title);
    col.setCellValueFactory(cell -> new SimpleStringProperty(getter.apply(cell.getValue())));
    col.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
    col.setOnEditCommit(evt -> {
      Credential c = evt.getRowValue();
      setter.accept(c, evt.getNewValue());
      saveCurrentEnv(getSelectedEnv());
    });
    return col;
  }

  private String getSelectedEnv() { return envCombo.getValue(); }

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

  private void loadDataForSelectedEnv() {
    String env = getSelectedEnv();
    if (env == null || env.isBlank()) {
      data.clear();
      return;
    }

    Path file = Path.of("data", env + ".json");
    if (!Files.exists(file)) {
      data.clear();
      return;
    }

    try {
      List<Credential> list = objectMapper.readValue(file.toFile(), new TypeReference<List<Credential>>() {});
      data.setAll(list);
    } catch (IOException e) {
      showAlert("Yükleme hatası: " + e.getMessage());
    }
  }

  private void saveCurrentEnv(String env) {
    if (env == null || env.isBlank()) return;
    try {
      Path dir = Path.of("data");
      if (!Files.exists(dir)) Files.createDirectories(dir);
      Path file = dir.resolve(env + ".json");
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
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
      alert.show();
    });
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

  // Basit veri modeli
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Credential {
    private String env;
    private String key;
    private String url;
    private String username;
    private String password;

    public Credential() {}

    public Credential(String env, String key, String url, String username, String password) {
      this.env = env;
      this.key = key;
      this.url = url;
      this.username = username;
      this.password = password;
    }

    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
  }
}
