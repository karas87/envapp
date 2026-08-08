package tr.com.envapp.token;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import tr.com.envapp.http.HttpService;
import tr.com.envapp.model.CredentialDTO;
import tr.com.envapp.model.EnvironmentDTO;
import tr.com.envapp.model.HisExceptionDTO;
import tr.com.envapp.model.LoginDTO;
import tr.com.envapp.model.OrganizasyonDTO;
import tr.com.envapp.model.TokenResponseDTO;

/**
 * Entity Search - JavaFX Dark Pane
 */

public class TokenPane {

  // Token için yeni field'lar
  private TableView<EnvironmentDTO> environmentTable;
  private TableView<CredentialDTO> credentialTable;
  private final ObservableList<EnvironmentDTO> environmentData = FXCollections.observableArrayList();
  private final ObservableList<CredentialDTO> credentialData = FXCollections.observableArrayList();
  private Label cookieLabel;
  private TextArea accessTokenArea;
  private Button tokenButton;
  private Button yenileButton;
  private TextField hazelcastSessionIdField;
  private ComboBox<OrganizasyonDTO> organizasyonCombo;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpService httpService = new HttpService();

  public Pane build() {
    initComponents();
    return buildLayout();
  }

  private void initComponents() {
    // Token tabloları - eşit boyutlarda
    environmentTable = new TableView<>();
    environmentTable.setItems(environmentData);
    environmentTable.setStyle("-fx-background-color: #2b2b2b; -fx-control-inner-background: #2b2b2b; -fx-table-cell-border-color: #444444;");
    setupEnvironmentTableColumns();

    credentialTable = new TableView<>();
    credentialTable.setItems(credentialData);
    credentialTable.setStyle("-fx-background-color: #2b2b2b; -fx-control-inner-background: #2b2b2b; -fx-table-cell-border-color: #444444;");
    setupCredentialTableColumns();

    // Environment seçildiğinde credential'ları yükle ve ilk elemanı seç
    environmentTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null) {
        loadCredentialsForEnvironment(newVal.getName());
        // Credential'lar yüklendikten sonra ilk elemanı seç
        Platform.runLater(() -> {
          if (!credentialData.isEmpty()) {
            credentialTable.getSelectionModel().selectFirst();
            CredentialDTO firstCred = credentialData.getFirst();
            loadOrganizasyonList(firstCred);
          }
        });
      }
    });

    // Ortamları yükle
    loadEnvironments();

    // Credential seçildiğinde organizasyon listesini yükle
    credentialTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
      if (newVal != null && newVal.getUrl() != null) {
        loadOrganizasyonList(newVal);
      }
    });
  }

  private Pane buildLayout() {
    VBox root = new VBox(10);
    root.setPadding(new Insets(15));

    // === HBox resultsBox - Tablolar yan yana ===
    HBox resultsBox = new HBox(5);
    resultsBox.setStyle("-fx-border-color: #555555; -fx-border-radius: 4; -fx-background-color: #3c3f41; -fx-background-radius: 4;");
    resultsBox.setPadding(new Insets(8));

    // Environment Tablosu (Sol) - 8 satır
    VBox envBox = new VBox(5);
    Label envLabel = new Label("Ortamlar");
    envLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #bbbbbb;");
    environmentTable.setPrefHeight(8 * 24 + 30);
    environmentTable.setMinHeight(8 * 24 + 30);
    environmentTable.setMaxHeight(8 * 24 + 30);
    envBox.getChildren().addAll(envLabel, environmentTable);
    HBox.setHgrow(envBox, Priority.ALWAYS);

    // Credential Tablosu + Organizasyon ComboBox (Sağ)
    VBox credBox = new VBox(5);

    // Credential Tablosu - 6 satır (toplam yükseklik eşitlemesi için küçültüldü)
    Label credLabel = new Label("Credential Bilgileri");
    credLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #bbbbbb;");
    credentialTable.setPrefHeight(environmentTable.getPrefHeight() - 30); // Toplam yükseklik - ComboBox yüksekliği - spacing
    credentialTable.setMinHeight(environmentTable.getMinHeight() - 43 - 5);
    credentialTable.setMaxHeight(environmentTable.getMaxHeight() - 5);

    // Organizasyon ComboBox + Token Al Container
    HBox organizasyonTokenAlHbox = new HBox(5);

    organizasyonCombo = new ComboBox<>();
    organizasyonCombo.setPromptText("Organizasyon");
    organizasyonCombo.setMaxWidth(Double.MAX_VALUE - 150 - 5);
    organizasyonCombo.setPrefWidth(500);
    organizasyonCombo.setStyle("-fx-font-size: 13px;");
    organizasyonCombo.setConverter(new javafx.util.StringConverter<>() {
      @Override
      public String toString(OrganizasyonDTO dto) {
        return dto.getAdi();
      }

      @Override
      public OrganizasyonDTO fromString(String s) {
        return null;
      }
    });

    // === Token Al Butonu ===
    tokenButton = new Button("🔑  Token Al");
    tokenButton.setPrefWidth(150);
    tokenButton.setMinWidth(150);
    tokenButton.setMaxWidth(150);
    tokenButton.setStyle(
        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 13px;" +
            "-fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;"
    );
    tokenButton.setOnMouseEntered(e -> tokenButton.setStyle(
        "-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 13px;" +
            "-fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    tokenButton.setOnMouseExited(e -> tokenButton.setStyle(
        "-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 13px;" +
            "-fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    tokenButton.setOnAction(e -> fetchToken());

    yenileButton = new Button("🔄");
    yenileButton.setStyle(
        "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 13px;" +
            "-fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;"
    );
    yenileButton.setOnMouseEntered(e -> yenileButton.setStyle(
        "-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 13px;" +
            "-fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    yenileButton.setOnMouseExited(e -> yenileButton.setStyle(
        "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 13px;" +
            "-fx-font-weight: bold; -fx-background-radius: 4; -fx-cursor: hand;"
    ));
    yenileButton.setOnAction(e -> {
      environmentData.clear();
      credentialData.clear();
      loadEnvironments();
    });

    organizasyonTokenAlHbox.getChildren().addAll(organizasyonCombo, tokenButton, yenileButton);

    credBox.getChildren().addAll(credLabel, credentialTable, organizasyonTokenAlHbox);
    HBox.setHgrow(credBox, Priority.ALWAYS);

    resultsBox.getChildren().addAll(envBox, credBox);

    // === Cookie ve Response Container ===
    VBox cookieResponseContainer = new VBox(8);
    cookieResponseContainer.setStyle("-fx-border-color: #555555; -fx-border-radius: 4; -fx-background-color: #3c3f41; -fx-background-radius: 4;");
    cookieResponseContainer.setPadding(new Insets(8));
    VBox.setVgrow(cookieResponseContainer, Priority.ALWAYS);

    // === Cookie TextField ve Kopyala Butonu ===
    cookieLabel = new Label("Cookie");
    cookieLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #bbbbbb;");

    hazelcastSessionIdField = new TextField();
    hazelcastSessionIdField.setStyle(
        "-fx-control-inner-background: #2b2b2b; -fx-text-fill: #e0e0e0;" +
            "-fx-prompt-text-fill: #777777; -fx-font-family: 'Consolas'; -fx-font-size: 12px;"
    );

    Button copyCookieBtn = new Button("📋");
    copyCookieBtn.setPrefHeight(hazelcastSessionIdField.getPrefHeight()); // TextField ile aynı yükseklik
    copyCookieBtn.setStyle(
        "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
    );
    copyCookieBtn.setOnMouseEntered(e -> copyCookieBtn.setStyle(
        "-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
    ));
    copyCookieBtn.setOnMouseExited(e -> copyCookieBtn.setStyle(
        "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
    ));
    copyCookieBtn.setOnAction(e -> {
      String cookie = hazelcastSessionIdField.getText();
      if (cookie != null && !cookie.trim().isEmpty()) {
        copyToClipboard(cookie, "Cookie");
      }
    });
    HBox cookieInner = new HBox(5, hazelcastSessionIdField, copyCookieBtn);
    HBox.setHgrow(hazelcastSessionIdField, Priority.ALWAYS);
    cookieInner.setAlignment(Pos.CENTER_LEFT);

    Label tokenLabel = new Label("Token");
    tokenLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #bbbbbb;");

    accessTokenArea = new javafx.scene.control.TextArea();
    accessTokenArea.setWrapText(true);
    accessTokenArea.setEditable(false);
    accessTokenArea.setStyle(
        "-fx-control-inner-background: #2b2b2b; -fx-text-fill: #e0e0e0;" +
            "-fx-prompt-text-fill: #777777; -fx-font-family: 'Consolas'; -fx-font-size: 12px;"
    );

    Button copyResponseBtn = new Button("📋");
    copyResponseBtn.setStyle(
        "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
    );
    copyResponseBtn.setOnMouseEntered(e -> copyResponseBtn.setStyle(
        "-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
    ));
    copyResponseBtn.setOnMouseExited(e -> copyResponseBtn.setStyle(
        "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px;" +
            "-fx-padding: 2 8 2 8; -fx-cursor: hand; -fx-background-radius: 4;"
    ));
    copyResponseBtn.setOnAction(e -> {
      String response = accessTokenArea.getText();
      if (response != null && !response.trim().isEmpty()) {
        copyToClipboard(response, "Token");
      }
    });

    // Response ve kopyala butonu yan yana
    HBox responseBox = new HBox(5, accessTokenArea, copyResponseBtn);
    HBox.setHgrow(accessTokenArea, Priority.ALWAYS);
    VBox.setVgrow(responseBox, Priority.ALWAYS);
    responseBox.setAlignment(Pos.TOP_LEFT);

    // Cookie ve Response'u container'a ekle
    cookieResponseContainer.getChildren().addAll(cookieLabel, cookieInner, tokenLabel, responseBox);
    root.getChildren().addAll(resultsBox, cookieResponseContainer);
    return root;
  }

  private void copyToClipboard(String text, String fieldName) {
    if (text == null) {
      return;
    }
    Platform.runLater(() -> {
      ClipboardContent content = new ClipboardContent();
      content.putString(text);
      Clipboard.getSystemClipboard().setContent(content);
      showToast("✔  " + fieldName + " kopyalandı!");
    });
  }

  private void showToast(String message) {
    Stage appStage = (Stage) credentialTable.getScene().getWindow();

    Popup popup = new Popup();

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

    PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
    pause.setOnFinished(e -> popup.hide());
    pause.play();
  }

  private void showError(String msg) {
    ButtonType tamamType = new ButtonType("Tamam", ButtonBar.ButtonData.OK_DONE);
    Alert alert = new Alert(Alert.AlertType.ERROR, msg, tamamType);
    alert.setHeaderText(null);
    alert.setTitle("Hata");
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
    if (credentialTable.getScene() != null) {
      Stage ownerStage = (Stage) credentialTable.getScene().getWindow();
      alert.initOwner(ownerStage);
      alert.showingProperty().addListener((obs, wasShowing, isNowShowing) -> {
        if (isNowShowing) {
          Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
          alertStage.getIcons().addAll(ownerStage.getIcons());
        }
      });
    }
    alert.showAndWait();
  }

  // === TOKEN YÖNETİMİ METODLARI ===

  private void setupEnvironmentTableColumns() {
    TableColumn<EnvironmentDTO, String> nameCol = new TableColumn<>("Ortam Adı");
    nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getName()));
    nameCol.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");
    nameCol.setPrefWidth(200);

    environmentTable.getColumns().add(nameCol);
    environmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
  }

  private void setupCredentialTableColumns() {

    TableColumn<CredentialDTO, String> urlCol = new TableColumn<>("URL");
    urlCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getUrl()));
    urlCol.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
    urlCol.setPrefWidth(250);
    urlCol.setCellFactory(col -> createCredentialCopyCell(CredentialDTO::getUrl, "URL"));

    TableColumn<CredentialDTO, String> usernameCol = new TableColumn<>("Username");
    usernameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getUsername()));
    usernameCol.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
    usernameCol.setPrefWidth(120);
    usernameCol.setCellFactory(col -> createCredentialCopyCell(CredentialDTO::getUsername, "Username"));

    TableColumn<CredentialDTO, String> passwordCol = new TableColumn<>("Password");
    passwordCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getPassword()));
    passwordCol.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");
    passwordCol.setPrefWidth(120);
    passwordCol.setCellFactory(col -> createPasswordCell());

    credentialTable.getColumns().addAll(List.of(urlCol, usernameCol, passwordCol));
    credentialTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
  }

  private TableCell<CredentialDTO, String> createCredentialCopyCell(Function<CredentialDTO, String> textProvider, String fieldName) {
    return new TableCell<>() {
      private final String copyBtnBase = "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;";
      private final String copyBtnHover = "-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;";
      private final Label valueLabel = new Label();
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, valueLabel, spacer, copyBtn);

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        valueLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px;");
        copyBtn.setStyle(copyBtnBase);
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle(copyBtnHover));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle(copyBtnBase));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          CredentialDTO item = getCurrentRowItem();
          if (item != null) {
            String text = textProvider.apply(item);
            copyToClipboard(text, fieldName);
          }
        });
      }

      @Override
      protected void updateItem(String item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setGraphic(null);
        } else {
          valueLabel.setText(item);
          setGraphic(box);
        }
      }

      private CredentialDTO getCurrentRowItem() {
        int idx = getIndex();
        if (idx < 0 || getTableView() == null || idx >= getTableView().getItems().size()) {
          return null;
        }
        return getTableView().getItems().get(idx);
      }
    };
  }

  private TableCell<CredentialDTO, String> createPasswordCell() {
    return new TableCell<>() {
      private final String copyBtnBase = "-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;";
      private final String copyBtnHover = "-fx-background-color: #6a8fd8; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 2 6 2 6; -fx-cursor: hand; -fx-background-radius: 4;";
      private final Label valueLabel = new Label("******");
      private final Button copyBtn = new Button("📋");
      private final Region spacer = new Region();
      private final HBox box = new HBox(6, valueLabel, spacer, copyBtn);

      {
        HBox.setHgrow(spacer, Priority.ALWAYS);
        valueLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px;");
        copyBtn.setStyle(copyBtnBase);
        copyBtn.setOnMouseEntered(e -> copyBtn.setStyle(copyBtnHover));
        copyBtn.setOnMouseExited(e -> copyBtn.setStyle(copyBtnBase));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(Double.MAX_VALUE);

        copyBtn.setOnAction(e -> {
          CredentialDTO item = getCurrentRowItem();
          if (item != null) {
            copyToClipboard(item.getPassword(), "Password");
          }
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

      private CredentialDTO getCurrentRowItem() {
        int idx = getIndex();
        if (idx < 0 || getTableView() == null || idx >= getTableView().getItems().size()) {
          return null;
        }
        return getTableView().getItems().get(idx);
      }
    };
  }

  private void loadEnvironments() {
    var dir = Path.of("data");
    var file = dir.resolve("envs.json");
    try {
      if (Files.exists(file)) {
        var envs = objectMapper.readValue(file.toFile(), new TypeReference<List<EnvironmentDTO>>() {
        });
        environmentData.clear();
        environmentData.addAll(envs);
      }
    } catch (IOException e) {
      showError("Ortam listesi yüklenemedi: " + e.getMessage());
    }
  }

  private void loadCredentialsForEnvironment(String envName) {
    credentialData.clear();
    var file = Path.of("data", envName + ".json");
    if (!Files.exists(file)) {
      return;
    }

    var hbysUrlPatterns = Set.of("prp.", "hbys.", "pilot.");
    try {
      var creds = objectMapper.readValue(file.toFile(), new TypeReference<List<CredentialDTO>>() {
      });
      if (creds != null && !creds.isEmpty()) {
        creds.forEach(credentialItem -> {
          String rawUrl = credentialItem.getUrl();
          if (rawUrl == null) {
            return;
          }

          var cleanUrl = rawUrl;
          boolean matches = hbysUrlPatterns.stream().anyMatch(rawUrl::contains);
          if (matches) {
            // .yerel veya .local'a kadar al, sonuna / ekle
            for (String suffix : new String[]{".yerel", ".local"}) {
              int idx = rawUrl.indexOf(suffix);
              if (idx != -1) {
                cleanUrl = rawUrl.substring(0, idx + suffix.length());
                if (!cleanUrl.endsWith("/")) {
                  cleanUrl += "/";
                }
                break;
              }
            }
            credentialItem.setUrl(cleanUrl);
            credentialData.add(credentialItem);
          } else if (cleanUrl.contains("hbys-web")) {
            int idx = rawUrl.indexOf("hbys-web");
            if (idx != -1) {
              cleanUrl = rawUrl.substring(0, idx);
              if (!cleanUrl.endsWith("/")) {
                cleanUrl += "/";
              }
            }
            credentialItem.setUrl(cleanUrl);
            credentialData.add(credentialItem);
          }
        });
      }
    } catch (IOException e) {
      showError("Credential yükleme hatası: " + e.getMessage());
    }
  }

  private void loadOrganizasyonList(CredentialDTO credentialDTO) {
    if (organizasyonCombo == null) {
      return;
    }
    organizasyonCombo.getItems().clear();
    organizasyonCombo.setPromptText("Yükleniyor...");

    var task = new Task<List<OrganizasyonDTO>>() {
      @Override
      protected List<OrganizasyonDTO> call() throws Exception {
        var targetUrl = credentialDTO.getUrl() + "hbys-rs/hbys/Yonetim/Organizasyon/getLoginOrganizasyonList/" + credentialDTO.getUsername() + "?page=1&start=0&limit=2000";
        var response = httpService.get(targetUrl);

        var orgList = new ArrayList<OrganizasyonDTO>();
        var root = objectMapper.readTree(response.body());
        if (root.has("exception")) {
          var exception = root.get("exception");
          var hisExceptionDTO = objectMapper.readValue(exception.toString(), new TypeReference<HisExceptionDTO>() {
          });
          showError("Organizasyon listesi yüklenemedi: " + hisExceptionDTO.getMessage());
        } else {
          var data = root.get("data");
          if (data != null && data.isArray()) {
            orgList = objectMapper.treeToValue(data, new TypeReference<>() {
            });
          }
        }
        return orgList;
      }
    };

    task.setOnSucceeded(e -> {
      var orgs = task.getValue();
      organizasyonCombo.getItems().setAll(orgs);
      if (!orgs.isEmpty()) {
        orgs.stream()
            .filter(o -> o.getAdi() != null && o.getAdi().toLowerCase().contains("şehir"))
            .findFirst()
            .ifPresentOrElse(
                o -> organizasyonCombo.getSelectionModel().select(o),
                () -> organizasyonCombo.getSelectionModel().selectFirst()
            );
        organizasyonCombo.setPromptText("Organizasyon");
      } else {
        organizasyonCombo.setPromptText("Giriş yapılacak organizasyon bulunamadı");
      }
    });

    task.setOnFailed(e -> organizasyonCombo.setPromptText("Organizasyon listesi yüklenemedi"));

    var thread = new Thread(task);
    thread.setDaemon(true);
    thread.start();
  }

  private void fetchToken() {
    var selectedEnv = environmentTable.getSelectionModel().getSelectedItem();
    var selectedCred = credentialTable.getSelectionModel().getSelectedItem();
    var selectedOrg = organizasyonCombo.getSelectionModel().getSelectedItem();

    if (selectedEnv == null) {
      showError("Lütfen bir ortam seçin!");
      return;
    }

    if (selectedCred == null) {
      showError("Lütfen bir credential seçin!");
      return;
    }

    if (selectedOrg == null) {
      showError("Lütfen bir organizasyon seçin!");
      return;
    }

    var url = selectedCred.getUrl();
    var username = selectedCred.getUsername();
    var password = selectedCred.getPassword();
    var organizasyonId = selectedOrg.getId();

    if (url == null || url.trim().isEmpty()) {
      showError("URL boş olamaz!");
      return;
    }

    // Buton disable et
    tokenButton.setDisable(true);
    accessTokenArea.setText("Token alınıyor...\n\nURL: " + url + "\nUsername: " + username + "\nOrganizasyon : " + organizasyonId + " - " + selectedOrg.getAdi() + "\n\nLütfen bekleyin...");
    hazelcastSessionIdField.clear();

    // Arka planda HTTP isteği yap
    var task = new Task<TokenResponseDTO>() {
      @Override
      protected TokenResponseDTO call() throws Exception {
        var loginJson = new LoginDTO(username, password, organizasyonId);
        var jsonBody = objectMapper.writeValueAsString(loginJson);
        return callTokenEndpoint(url, jsonBody);
      }
    };

    task.setOnSucceeded(e -> {
      var result = task.getValue();
      hazelcastSessionIdField.setText(result.getHazelcastSessionId());
      accessTokenArea.setText(result.getTokenResult());
      tokenButton.setDisable(false);
      cookieLabel.setText("Cookie (" + selectedEnv.getName() + " - " + username + " - " + selectedOrg.getAdi() + " - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + ")");
    });

    task.setOnFailed(e -> {
      Throwable ex = task.getException();
      accessTokenArea.setText("❌ HATA:\n\n" + ex.getMessage());
      tokenButton.setDisable(false);
      cookieLabel.setText("Cookie (" + selectedEnv.getName() + " - " + username + " - " + selectedOrg.getAdi() + " - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + ")");
    });

    var thread = new Thread(task);
    thread.setDaemon(true);
    thread.start();
  }

  private TokenResponseDTO callTokenEndpoint(String baseUrl, String jsonBody) throws Exception {
    var response = httpService.postJson(baseUrl + "/hbys-rs/hbys/HBYSSistem/KullaniciGiris/login", jsonBody);

    var hazelcastSessionId = "";
    var cookie = "";
    var tokenResult = "";
    var root = objectMapper.readTree(response.body());
    if (root.has("exception")) {
      var exception = root.get("exception");
      var hisExceptionDTO = objectMapper.readValue(exception.toString(), new TypeReference<HisExceptionDTO>() {
      });
      tokenResult = hisExceptionDTO.toString();
    } else {
      var data = root.get("data");
      if (data != null) {
        hazelcastSessionId = httpService.parseCookieValue(response, "hazelcast.sessionId");
        cookie = httpService.joinCookies(response);

        var tokenResponse = httpService.get(
            baseUrl + "/hbys-rs/hbys/Sistem/KullaniciGiris/hisnext-login",
            Map.of("Cookie", cookie)
        );
        var tokenRoot = objectMapper.readTree(tokenResponse.body());
        if (tokenRoot != null) {
          tokenResult = tokenRoot.get("access_token").asText();
          if (tokenResult != null) {
            tokenResult = "Bearer " + tokenResult;
          }
        }
      }
    }

    return new TokenResponseDTO(hazelcastSessionId, tokenResult);
  }

}

