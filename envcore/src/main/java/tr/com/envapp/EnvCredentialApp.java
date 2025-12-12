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

  private static final String VERSION = "1.0.1";

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

    // Görünmez owner: görev çubuğunda ikon oluşmasın
    Stage owner = new Stage();
    owner.initStyle(javafx.stage.StageStyle.UTILITY);
    owner.setOpacity(0);
    owner.setWidth(0);
    owner.setHeight(0);
    owner.setIconified(true);
    owner.show();

    // Asıl UI penceresi
    Stage appStage = new Stage();
    appStage.initOwner(owner);

    appStage.setTitle("Environment Credentials Manager v" + VERSION);
    appStage.getIcons().add(new Image(getClass().getResourceAsStream("/setting.png")));

    // Çarpı ile kapanmak yerine gizle
    appStage.setOnCloseRequest(evt -> {
      evt.consume();
      appStage.hide();
    });
    // Simge durumuna (minimize) geçince gizle
    appStage.iconifiedProperty().addListener((obs, oldV, newV) -> {
      if (newV) {
        appStage.hide();
      }
    });
    // Odak kaybında gizleme çok agresifti; kaldırıyoruz
    // appStage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
    //   if (!isFocused) {
    //     appStage.hide();
    //   }
    // });

    // Sistem tepsisi (tray) ikonu kur
    setupSystemTray(appStage);

    envCombo = new ComboBox<>();
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
    sortedData.comparatorProperty().bind(table.comparatorProperty());

    TableColumn<Credential, String> keyCol = makeEditableColumn("Key", Credential::getKey, Credential::setKey);
    keyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getKey()));
    keyCol.setCellFactory(col -> new TextFieldTableCell<>(new DefaultStringConverter()) {
      @Override
      public void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (!empty && item != null) {
          setText(item);
          setStyle("-fx-font-weight: bold;"); // 🔹 Bold görünüm
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

      {
        // Enter ile edit commit
        textField.setOnAction(e -> commitEdit(textField.getText()));
        textField.focusedProperty().addListener((obs, oldV, newV) -> {
          if (!newV) commitEdit(textField.getText());
        });

        // Hyperlink tıklayınca aç
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
          setText(null);
          setGraphic(null);
        } else {
          if (isEditing()) {
            textField.setText(item);
            setGraphic(textField);
          } else {
            link.setText(item);
            setGraphic(link);
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
        saveCurrentEnv(getSelectedEnv()); // JSON kaydı
      }

      @Override
      public void cancelEdit() {
        super.cancelEdit();
        link.setText(getItem());
        setGraphic(link);
      }
    });
    TableColumn<Credential, String> userCol = makeEditableColumn("Username", Credential::getUsername, Credential::setUsername);

    TableColumn<Credential, String> passCol = new TableColumn<>("Password");
    passCol.setCellValueFactory(cell -> new SimpleStringProperty("******"));
    passCol.setCellFactory(TextFieldTableCell.forTableColumn(new DefaultStringConverter()));
    passCol.setOnEditCommit(evt -> {
      Credential c = evt.getRowValue();
      c.setPassword(evt.getNewValue());
      saveCurrentEnv(getSelectedEnv());
    });
    passCol.setPrefWidth(220);

    // Sağda delete kontrolü
    CheckBox deleteCheckBox = new CheckBox("Delete");
    deleteCheckBox.setSelected(false);

    TableColumn<Credential, Void> actionCol = new TableColumn<>("Actions");
    actionCol.setCellFactory(param -> new TableCell<>() {
      private final Button copyPassBtn = new Button("Copy Pass");
      private final Button deleteBtn = new Button("Delete");
      private final HBox box = new HBox(8, copyPassBtn, deleteBtn);

      {
        copyPassBtn.setStyle("-fx-font-size: 10px;");
        deleteBtn.setStyle("-fx-font-size: 10px;");
        // Delete butonunun görünürlüğünü checkbox'a bağla
        deleteBtn.visibleProperty().bind(deleteCheckBox.selectedProperty());

        copyPassBtn.setOnAction(e -> {
          Credential item = getTableView().getItems().get(getIndex());
          copyToClipboard(item.getPassword());
        });
        deleteBtn.setOnAction(e -> {
          Credential item = getTableView().getItems().get(getIndex());
          Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
          confirm.setTitle("Silme Onayı");
          confirm.setHeaderText(null);
          confirm.setContentText("Bu kaydı silmek istediğinize emin misiniz?\nKey: " + item.getKey());
          confirm.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
          ButtonType result = confirm.showAndWait().orElse(ButtonType.NO);
          if (result == ButtonType.YES) {
            data.remove(item);
            saveCurrentEnv(getSelectedEnv());
          }
        });
      }

      @Override
      protected void updateItem(Void v, boolean empty) {
        super.updateItem(v, empty);
        setGraphic(empty ? null : box);
      }
    });
    actionCol.setPrefWidth(160);

    table.getColumns().addAll(keyCol, urlCol, userCol, passCol, actionCol);

    // 🔹 Altına Key Filter ekleme
    TextField keyFilterField = new TextField();
    keyFilterField.setPromptText("Key ara...");
    keyFilterField.textProperty().addListener((obs, oldVal, newVal) ->
        filteredData.setPredicate(item -> {
          if (newVal == null || newVal.isBlank()) return true;
          return item.getKey().toLowerCase().contains(newVal.toLowerCase());
        }));

    keyFilterField.setMaxWidth(Double.MAX_VALUE); // Grid kadar genişlik
    HBox keyFilterBox = new HBox(keyFilterField);
    keyFilterBox.setPadding(new Insets(5));
    keyFilterBox.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(keyFilterField, Priority.ALWAYS);

    VBox tableContainer = new VBox(table, keyFilterBox);
    VBox.setVgrow(table, Priority.ALWAYS);

    // 🔹 Input Alanları
    TextField keyField = new TextField();
    keyField.setPromptText("Key");
    TextField urlField = new TextField();
    urlField.setPromptText("URL");
    TextField usernameField = new TextField();
    usernameField.setPromptText("Username");
    TextField passwordField = new TextField();
    passwordField.setPromptText("Password");

    Button addButton = new Button("Add");
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

    // Add butonu ile delete checkbox'ı aynı hizada, checkbox en sağda
    Region inputSpacer = new Region();
    HBox.setHgrow(inputSpacer, Priority.ALWAYS);
    HBox inputBox = new HBox(10, keyField, urlField, usernameField, passwordField, addButton, inputSpacer, deleteCheckBox);
    inputBox.setPadding(new Insets(10));
    inputBox.setAlignment(Pos.CENTER_LEFT);

    VBox root = new VBox(10, envCombo, inputBox, tableContainer);
    root.setPadding(new Insets(10));

    appStage.setScene(new Scene(root, 1200, 600));
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
    col.setPrefWidth(220);
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

  private void copyToClipboard(String text) {
    if (text == null) return;
    Platform.runLater(() -> {
      javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
      content.putString(text);
      javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
    });
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
