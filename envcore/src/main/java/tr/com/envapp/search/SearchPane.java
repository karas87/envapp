package tr.com.envapp.search;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.DirectoryChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import tr.com.envapp.model.SearchResultDTO;

/**
 * Entity Search - JavaFX Dark Pane
 */
public class SearchPane {

    private TextField pathField;
    private TextField searchStringField;
    private TextField patternField;
    private TextField folderFilterField;
    private TextField clientSideSearchField;
    private Button clientSideSearchClearButton;
    private Button clientSearchInfoButton;
    private CheckBox testIncluded;
    private CheckBox caseSensitiveCheckBox;
    private CheckBox wholeWordCheckBox;
    private CheckBox useIndex;
    private TableView<SearchResultDTO> resultTable;
    private Button refreshIndexButton;
    private Button deleteIndexButton;
    private Button searchButton;
    private Button stopButton;
    private Button clearButton;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Task<List<SearchResultDTO>> currentTask;
    private String currentSearchString;
    private boolean currentCaseSensitive;
    private boolean currentWholeWord;
    private Window ownerWindow;
    private List<SearchResultDTO> allResults;
    private Popup summaryPopup;
    private PauseTransition summaryPopupTimer;
    private int summaryProjectCount;
    private int summaryFileCount;
    private int summaryMatchCount;
    private long summaryDuration;
    private boolean hasSummary;
    private String summaryProjectDetail;
    private static final String SUMMARY_LINE_INDENT = "   ";

    public SearchPane(Window ownerWindow) {
        this.ownerWindow = ownerWindow;
    }

    public Pane build() {
        initComponents();
        return buildLayout();
    }

    private void initComponents() {
        pathField = new TextField();
        pathField.setPromptText("Arama yapılacak klasör yolu");
        pathField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

        searchStringField = new TextField();
        searchStringField.setPromptText("Aranacak metin");
        searchStringField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

        patternField = new TextField("*");
        patternField.setPromptText("Proje pattern (* = tümü, his-api-* vb.)");
        patternField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

        folderFilterField = new TextField();
        folderFilterField.setPromptText("Üst klasör filtresi (domain, entity... boş = tümü)");
        folderFilterField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

        clientSideSearchField = new TextField();
        clientSideSearchField.setPromptText("Sonuçlarda ara");
        clientSideSearchField.setStyle("-fx-prompt-text-fill: #bbbbbb;");

        clientSideSearchClearButton = new Button("x");
        clientSideSearchClearButton.setFocusTraversable(false);
        clientSideSearchClearButton.setDisable(true);
        clientSideSearchClearButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: #ffffff; -fx-font-size: 11px; -fx-padding: 2 7 2 7;");
        clientSideSearchClearButton.setOnAction(e -> clientSideSearchField.clear());

        clientSearchInfoButton = new Button("i");
        clientSearchInfoButton.setFocusTraversable(false);
        clientSearchInfoButton.setDisable(true);
        clientSearchInfoButton.setStyle("-fx-background-color: #1E88E5; -fx-text-fill: white; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 12; -fx-padding: 2 8 2 8;");
        clientSearchInfoButton.setOnAction(e -> toggleSummaryPopup());

        testIncluded = new CheckBox("Testler Dahil");
        testIncluded.setSelected(true);
        caseSensitiveCheckBox = new CheckBox("Case Sensitive");
        wholeWordCheckBox = new CheckBox("Whole Word");
        useIndex = new CheckBox("Index Kullan");

        refreshIndexButton = new Button("🔄  Index Yenile");
        refreshIndexButton.setStyle("-fx-background-color: #4b6eaf; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 6 16 6 16;");
        refreshIndexButton.setVisible(false);

        deleteIndexButton = new Button("🗑  Index Sil");
        deleteIndexButton.setStyle("-fx-background-color: #633726; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 6 16 6 16;");
        deleteIndexButton.setVisible(false);

        searchButton = new Button("▶  Aramayı Başlat");
        searchButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 6 16 6 16;");

        stopButton = new Button("■  Durdur");
        stopButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 6 16 6 16;");
        stopButton.setDisable(true);

        clearButton = new Button("Temizle");
        clearButton.setStyle("-fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 6 16 6 16;");

        progressBar = new ProgressBar();
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label("Hazır");
        statusLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-style: italic;");

        resultTable = new TableView<>();
        resultTable.setStyle("-fx-background-color: #2b2b2b; -fx-control-inner-background: #2b2b2b; -fx-table-cell-border-color: #444444;");
        resultTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        resultTable.setFixedCellSize(24);
        Label placeholder = new Label("Sonuç bulunamadı!");
        placeholder.setStyle("-fx-text-fill: #ffffff;");
        resultTable.setPlaceholder(placeholder);
        setupTableColumns();

        summaryPopup = new Popup();
        summaryPopup.setAutoHide(true);
        summaryPopup.setAutoFix(true);

        summaryPopupTimer = new PauseTransition(Duration.seconds(5));
        summaryPopupTimer.setOnFinished(e -> summaryPopup.hide());
    }

    private Pane buildLayout() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));


        // === Input Panel ===
        GridPane inputGrid = new GridPane();
        inputGrid.setHgap(10);
        inputGrid.setVgap(10);
        inputGrid.setPadding(new Insets(12));
        inputGrid.setStyle("-fx-border-color: #555555; -fx-border-radius: 4; -fx-background-color: #3c3f41; -fx-background-radius: 4;");

        Label paramsLabel = new Label("Arama Parametreleri");
        paramsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #bbbbbb;");
        inputGrid.add(paramsLabel, 0, 0, 3, 1);

        // Sütun genişlikleri
        ColumnConstraints labelCol = new ColumnConstraints(130);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        ColumnConstraints btnCol = new ColumnConstraints(90);
        inputGrid.getColumnConstraints().addAll(labelCol, fieldCol, btnCol);

        // Klasör yolu
        inputGrid.add(styledLabel("Klasör Yolu:"), 0, 1);
        inputGrid.add(pathField, 1, 1);
        Button browseBtn = new Button("Gözat");
        browseBtn.setStyle("-fx-padding: 4 10 4 10;");
        browseBtn.setOnAction(e -> browseFolder());
        inputGrid.add(browseBtn, 2, 1);

        // Aranacak metin
        inputGrid.add(styledLabel("Aranacak Metin:"), 0, 2);
        GridPane.setColumnSpan(searchStringField, 2);
        inputGrid.add(searchStringField, 1, 2);

        // Pattern
        inputGrid.add(styledLabel("Proje Pattern:"), 0, 3);
        inputGrid.add(patternField, 1, 3);
        Label patternHint = new Label("(*, his-api-*, *-service)");
        patternHint.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        inputGrid.add(patternHint, 2, 3);

        // Folder filter
        inputGrid.add(styledLabel("Üst Klasör Filtresi:"), 0, 4);
        inputGrid.add(folderFilterField, 1, 4);
        Label folderHint = new Label("boş = tümü");
        folderHint.setStyle("-fx-text-fill: #888888; -fx-font-size: 11px;");
        inputGrid.add(folderHint, 2, 4);

        // Buttons row
        HBox buttonRow = new HBox(10,
            testIncluded, caseSensitiveCheckBox, wholeWordCheckBox, useIndex,
            new Region(), progressBar,
            refreshIndexButton, deleteIndexButton, searchButton, stopButton, clearButton
        );
        HBox.setHgrow(buttonRow.getChildren().get(2), Priority.ALWAYS);
        HBox.setHgrow(progressBar, Priority.SOMETIMES);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);
        inputGrid.add(buttonRow, 0, 5, 3, 1);

        // === Sonuçlar Panel ===
        VBox resultsBox = new VBox(5);
        Label resultsLabel = new Label("Sonuçlar");
        resultsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #bbbbbb;");
        resultsBox.setStyle("-fx-border-color: #555555; -fx-border-radius: 4; -fx-background-color: #3c3f41; -fx-background-radius: 4;");
        resultsBox.setPadding(new Insets(8));

        // Client-side search field
        HBox searchFieldBox = new HBox(4, clientSideSearchField, clientSideSearchClearButton, clientSearchInfoButton);
        searchFieldBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(clientSideSearchField, Priority.ALWAYS);

        HBox searchBox = new HBox(8, searchFieldBox);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchFieldBox, Priority.ALWAYS);

        VBox.setVgrow(resultTable, Priority.ALWAYS);
        resultsBox.getChildren().addAll(resultsLabel, searchBox, resultTable);
        VBox.setVgrow(resultsBox, Priority.ALWAYS);

        // === Status Bar ===
        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(4, 0, 0, 0));

        VBox.setVgrow(resultsBox, Priority.ALWAYS);
        root.getChildren().addAll(inputGrid, resultsBox, statusBar);

        // Event handlers
        useIndex.setOnAction(e -> {
            boolean useIdx = useIndex.isSelected();
            refreshIndexButton.setVisible(useIdx);
            deleteIndexButton.setVisible(useIdx);
        });
        refreshIndexButton.setOnAction(e -> refreshIndex());
        deleteIndexButton.setOnAction(e -> deleteIndex());
        searchButton.setOnAction(e -> performSearch());
        stopButton.setOnAction(e -> stopSearch());
        clearButton.setOnAction(e -> clearAll());
        searchStringField.setOnAction(e -> performSearch());
        patternField.setOnAction(e -> performSearch());
        clientSideSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasText = newVal != null && !newVal.isBlank();
            clientSideSearchClearButton.setDisable(!hasText);
            applyClientFilter(newVal);
        });

        return root;
    }

    private Label styledLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #cccccc;");
        return l;
    }

    private void browseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Klasör Seçin");
        File selected = chooser.showDialog(ownerWindow);
        if (selected != null) {
            pathField.setText(selected.getAbsolutePath());
        }
    }

    private void refreshIndex() {
      String path = pathField.getText().trim();
      if (path.isEmpty()) {
        showError("Lütfen klasör yolu girin!");
        return;
      }

      var currentTask = new Task<>() {
        @Override
        protected Boolean call() throws IOException {
          progressBar.setVisible(true);
          EntitySearchService service = new EntitySearchService();
          return service.refreshIndex(path);
        }
      };

      currentTask.setOnSucceeded(ev -> {
        progressBar.setVisible(false);
        showToast("Index yenileme tamamlandı!");
      });

      currentTask.setOnFailed(ev -> {
        progressBar.setVisible(false);
        showError("Index yenileme sırasında hata oluştu: " + currentTask.getException().getMessage());
      });

      Thread t = new Thread(currentTask);
      t.setDaemon(true);
      t.start();
    }

    private void deleteIndex() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            showError("Lütfen klasör yolu girin!");
            return;
        }

        var currentTask = new Task<>() {
            @Override
            protected Boolean call() throws IOException {
                progressBar.setVisible(true);
                EntitySearchService service = new EntitySearchService();
                return service.deleteIndex(path);
            }
        };

        currentTask.setOnSucceeded(ev -> {
            progressBar.setVisible(false);
            showToast("Index silme tamamlandı!");
        });

        currentTask.setOnFailed(ev -> {
            progressBar.setVisible(false);
            showError("Index silme sırasında hata oluştu: " + currentTask.getException().getMessage());
        });

        Thread t = new Thread(currentTask);
        t.setDaemon(true);
        t.start();
    }

    private void performSearch() {
        String path = pathField.getText().trim();
        String searchString = searchStringField.getText().trim();
        String pattern = patternField.getText().trim();
        String folderFilter = folderFilterField.getText().trim();

        if (path.isEmpty()) { showError("Lütfen klasör yolu girin!"); return; }
        if (searchString.isEmpty()) { showError("Lütfen aranacak metni girin!"); return; }
        if (!new File(path).exists()) { showError("Belirtilen klasör bulunamadı!"); return; }
        if (pattern.isEmpty()) pattern = "*";

        searchButton.setDisable(true);
        stopButton.setDisable(false);
        progressBar.setVisible(true);
        resultTable.getItems().clear();
        summaryPopupTimer.stop();
        summaryPopup.hide();
        hasSummary = false;
        clientSearchInfoButton.setDisable(true);
        statusLabel.setText("Arama yapılıyor...");

        currentSearchString = searchString;
        currentCaseSensitive = caseSensitiveCheckBox.isSelected();
        currentWholeWord = wholeWordCheckBox.isSelected();

        final String finalPattern = pattern;
        final String finalFolderFilter = folderFilter.isEmpty() ? null : folderFilter;
        final boolean finalCaseSensitive = currentCaseSensitive;
        final boolean finalWholeWord = currentWholeWord;
        final boolean finalUseIndex = useIndex.isSelected();

        long startTime = System.currentTimeMillis();

        currentTask = new Task<>() {
            @Override
            protected List<SearchResultDTO> call() {
                EntitySearchService service = new EntitySearchService();
                service.setCaseSensitive(finalCaseSensitive);
                service.setWholeWord(finalWholeWord);
                service.setUseIndex(finalUseIndex);
                service.setTestIncluded(testIncluded.isSelected());
                return service.search(path, searchString, finalPattern, finalFolderFilter);
            }
        };

        currentTask.setOnSucceeded(ev -> {
            long duration = System.currentTimeMillis() - startTime;
            List<SearchResultDTO> results = currentTask.getValue();
            displayResults(results, duration);
            statusLabel.setText("Arama tamamlandı! " + results.size() + " sonuç bulundu. (" + duration + " ms)");
            searchButton.setDisable(false);
            stopButton.setDisable(true);
            progressBar.setVisible(false);
        });

        currentTask.setOnFailed(ev -> {
            summaryPopupTimer.stop();
            summaryPopup.hide();
            hasSummary = false;
            clientSearchInfoButton.setDisable(true);
            statusLabel.setText("Hata oluştu: " + currentTask.getException().getMessage());
            searchButton.setDisable(false);
            stopButton.setDisable(true);
            progressBar.setVisible(false);
        });

        currentTask.setOnCancelled(ev -> {
            summaryPopupTimer.stop();
            summaryPopup.hide();
            hasSummary = false;
            clientSearchInfoButton.setDisable(true);
            statusLabel.setText("Arama durduruldu.");
            searchButton.setDisable(false);
            stopButton.setDisable(true);
            progressBar.setVisible(false);
        });

        Thread t = new Thread(currentTask);
        t.setDaemon(true);
        t.start();
    }

    private void stopSearch() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
    }

    private void displayResults(List<SearchResultDTO> results, long duration) {
        clientSideSearchField.clear();
        allResults = results;

        Map<String, Map<String, List<SearchResultDTO>>> grouped = results.stream()
            .collect(Collectors.groupingBy(
                SearchResultDTO::getProjectName,
                LinkedHashMap::new,
                Collectors.groupingBy(SearchResultDTO::getFilePath, LinkedHashMap::new, Collectors.toList())
            ));

        summaryFileCount = grouped.values().stream().mapToInt(Map::size).sum();

        summaryProjectDetail = grouped.isEmpty() ? "" :
            grouped.entrySet().stream()
            .map(e -> {
                int matchCount = e.getValue().values().stream().mapToInt(List::size).sum();
                return new java.util.AbstractMap.SimpleEntry<>(e.getKey(), matchCount);
            })
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .map(e -> SUMMARY_LINE_INDENT + e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("\n"));
        if (!summaryProjectDetail.isEmpty()) {
            summaryProjectDetail = currentSearchString + "\n" + summaryProjectDetail;
        }

        resultTable.getItems().setAll(results);
        summaryProjectCount = grouped.size();
        summaryMatchCount = results.size();
        summaryDuration = duration;
        hasSummary = true;
        clientSearchInfoButton.setDisable(false);
        showSummaryPopup(true);

        statusLabel.setText("Arama tamamlandı! " + results.size() + " sonuç bulundu. (" + duration + " ms)");
    }

    private void applyClientFilter(String filter) {
        if (allResults == null || allResults.isEmpty()) {
            resultTable.getItems().clear();
            return;
        }

        List<SearchResultDTO> filtered;
        String normalized = filter == null ? "" : filter.trim();
        if (normalized.length() < 3) {
            filtered = allResults;
        } else {
            String filterLower = normalized.toLowerCase();
            filtered = allResults.stream()
                .filter(r -> r.getLineContent().toLowerCase().contains(filterLower) ||
                           r.getFileName().toLowerCase().contains(filterLower) ||
                           r.getProjectName().toLowerCase().contains(filterLower))
                .collect(Collectors.toList());
        }

        resultTable.getItems().setAll(filtered);
        if (normalized.length() >= 3) {
            statusLabel.setText("Filtreleme: \"" + normalized + "\" (" + filtered.size() + " sonuç)");
        } else {
            statusLabel.setText("Arama tamamlandı! " + allResults.size() + " sonuç bulundu. (" + this.summaryDuration + " ms)");
        }
    }

    private void setupTableColumns() {
        String cellStyle = "-fx-font-family: 'Consolas'; -fx-font-size: 12px;";

        TableColumn<SearchResultDTO, String> colProject = new TableColumn<>("Proje");
        colProject.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getProjectName()));
        colProject.setStyle(cellStyle);
        colProject.setPrefWidth(50);
        colProject.setCellFactory(col -> createCopyCell(
            SearchResultDTO::getProjectName,
            row -> "Proje",
            false
        ));

        TableColumn<SearchResultDTO, String> colFile = new TableColumn<>("Dosya");
        colFile.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getFileName()));
        colFile.setStyle(cellStyle);
        colFile.setPrefWidth(100);
        colFile.setCellFactory(col -> createCopyCell(
            SearchResultDTO::getFileName,
            row -> "Dosya",
            false
        ));

        TableColumn<SearchResultDTO, Number> colLine = new TableColumn<>("Satır");
        colLine.setCellValueFactory(cd -> new SimpleIntegerProperty(cd.getValue().getLineNumber()));
        colLine.setStyle(cellStyle);
        colLine.setPrefWidth(60);
        colLine.setMaxWidth(70);
        colLine.setCellFactory(col -> createCopyCell(
            row -> String.valueOf(row.getLineNumber()),
            row -> "Satır",
            false
        ));

        TableColumn<SearchResultDTO, String> colContent = new TableColumn<>("İçerik");
        colContent.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().getLineContent()));
        colContent.setStyle(cellStyle);
        colContent.setPrefWidth(280);
        colContent.setCellFactory(col -> createCopyCell(
            SearchResultDTO::getLineContent,
            row -> "İçerik",
            true
        ));

        resultTable.getColumns().add(colProject);
        resultTable.getColumns().add(colFile);
        resultTable.getColumns().add(colLine);
        resultTable.getColumns().add(colContent);
    }

    private <T> TableCell<SearchResultDTO, T> createCopyCell(Function<SearchResultDTO, String> textProvider, Function<SearchResultDTO, String> fieldNameProvider, boolean highlighted) {
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
                    SearchResultDTO row = getCurrentRowItem();
                    if (row == null) {
                        return;
                    }
                    String text = safeString(textProvider.apply(row));
                    String fieldName = safeString(fieldNameProvider.apply(row));
                    copyToClipboard(text, fieldName);
                });
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);

                SearchResultDTO row = getCurrentRowItem();
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                String value = safeString(textProvider.apply(row));
                setText(null);

                // Check if this is a Test.java file
                boolean isTestFile = row.getFileName() != null && row.getFileName().endsWith("Test.java");

                if (highlighted) {
                    TextFlow contentFlow = createHighlightedTextFlow(value);
                    box.getChildren().set(0, contentFlow);
                } else {
                    valueLabel.setText(value);
                    valueLabel.setStyle("-fx-text-fill: #e0e0e0; -fx-font-size: 12px;");
                    box.setStyle("");
                    // Apply green background for Test.java files (IntelliJ-like color)
                    if (isTestFile) {
                        box.setStyle("-fx-background-color: #2d5f3c; -fx-background-radius: 3;");
                    }
                    box.getChildren().set(0, valueLabel);
                }
                setGraphic(box);
            }

            private SearchResultDTO getCurrentRowItem() {
                int idx = getIndex();
                if (idx < 0 || getTableView() == null || idx >= getTableView().getItems().size()) {
                    return null;
                }
                return getTableView().getItems().get(idx);
            }
        };
    }

    private String safeString(String value) {
        return value == null ? "" : value;
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
        Stage appStage = (Stage) resultTable.getScene().getWindow();

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

    private void toggleSummaryPopup() {
        if (!hasSummary) {
            return;
        }

        if (summaryPopup.isShowing()) {
            summaryPopupTimer.stop();
            summaryPopup.hide();
            return;
        }

        showSummaryPopup(false);
    }

    private void showSummaryPopup(boolean autoHide) {
        if (!hasSummary) {
            return;
        }

        summaryPopupTimer.stop();
        summaryPopup.hide();
        VBox popupContent = buildSummaryPopupContent();
        popupContent.applyCss();
        popupContent.autosize();

        summaryPopup.getContent().setAll(popupContent);

        Bounds bounds = clientSearchInfoButton.localToScreen(clientSearchInfoButton.getBoundsInLocal());
        if (bounds == null) {
            return;
        }

        double x = bounds.getMinX();
        double y = bounds.getMaxY() + 6;
        summaryPopup.show(clientSearchInfoButton, x, y);

        if (autoHide) {
            summaryPopupTimer.playFromStart();
        }
    }

    private VBox buildSummaryPopupContent() {
        VBox popupBox = new VBox(6);
        popupBox.setPadding(new Insets(10));
        popupBox.setStyle("-fx-background-color: #3c3f41; -fx-border-color: #64c8ff; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label summaryTitle = new Label("ÖZET");
        summaryTitle.setStyle("-fx-text-fill: #64c8ff; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label proj = new Label(SUMMARY_LINE_INDENT + "Toplam proje: " + summaryProjectCount);
        proj.setStyle("-fx-text-fill: #ffffff; -fx-font-family: 'Consolas';");
        Label files = new Label(SUMMARY_LINE_INDENT + "Toplam dosya: " + summaryFileCount);
        files.setStyle("-fx-text-fill: #ffffff; -fx-font-family: 'Consolas';");
        Label matches = new Label(SUMMARY_LINE_INDENT + "Toplam eşleşme: " + summaryMatchCount);
        matches.setStyle("-fx-text-fill: #ffffff; -fx-font-family: 'Consolas';");
        Label durationLabel = new Label(SUMMARY_LINE_INDENT + "Süre: " + summaryDuration + " ms");
        durationLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-family: 'Consolas';");

        popupBox.getChildren().addAll(summaryTitle, proj, files, matches, durationLabel);

        if (summaryProjectDetail != null && !summaryProjectDetail.isBlank()) {
            Label detailTitle = new Label("── Proje Detayı ── (📋)");
            detailTitle.setStyle("-fx-text-fill: #64c8ff; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 4 0 2 0;");
            detailTitle.setTooltip(new Tooltip("Kopyalamak için tıklayın"));
            popupBox.getChildren().add(detailTitle);
            Label detailLine = new Label(summaryProjectDetail);
            detailLine.setStyle("-fx-text-fill: #ffffff; -fx-font-family: 'Consolas';");
            detailTitle.setOnMouseClicked(event -> copyToClipboard(detailLine.getText(), "Sonuç detayı"));

            popupBox.getChildren().add(detailLine);
        }

        return popupBox;
    }

    private TextFlow createHighlightedTextFlow(String text) {
        TextFlow flow = new TextFlow();
        String highlightText = getActiveHighlightText();

        if (highlightText == null || highlightText.isBlank()) {
            flow.getChildren().add(styledText(text, "#ffffff", 12, false));
            return flow;
        }

        appendHighlighted(flow, text, highlightText, currentCaseSensitive, currentWholeWord);
        return flow;
    }

    private String getActiveHighlightText() {
        String clientFilter = clientSideSearchField.getText();
        if (clientFilter != null && !clientFilter.isBlank() && clientFilter.trim().length() >= 3) {
            return clientFilter.trim();
        }
        return currentSearchString;
    }

    private void appendHighlighted(TextFlow target, String text, String search, boolean caseSensitive, boolean wholeWord) {
        if (search == null || search.isBlank()) {
            target.getChildren().add(styledText(text, "#ffffff", 12, false));
            return;
        }

        String compareText = caseSensitive ? text : text.toLowerCase();
        String compareSearch = caseSensitive ? search : search.toLowerCase();
        int lastEnd = 0;
        int index;

        while ((index = compareText.indexOf(compareSearch, lastEnd)) >= 0) {
            boolean shouldHighlight = true;
            if (wholeWord) {
                boolean startOk = (index == 0) || !Character.isLetterOrDigit(compareText.charAt(index - 1));
                int endIndex = index + compareSearch.length();
                boolean endOk = (endIndex >= compareText.length()) || !Character.isLetterOrDigit(compareText.charAt(endIndex));
                shouldHighlight = startOk && endOk;
            }

            if (shouldHighlight) {
                if (index > lastEnd) {
                    target.getChildren().add(styledText(text.substring(lastEnd, index), "#ffffff", 12, false));
                }
                Label hlLabel = new Label(text.substring(index, index + search.length()));
                hlLabel.setStyle("-fx-background-color: #FFD700; -fx-text-fill: #000000; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold;");
                target.getChildren().add(hlLabel);
                lastEnd = index + search.length();
            } else {
                lastEnd = index + 1;
            }
        }

        if (lastEnd < text.length()) {
            target.getChildren().add(styledText(text.substring(lastEnd), "#ffffff", 12, false));
        }
    }

    private Text styledText(String content, String color, int fontSize, boolean bold) {
        Text t = new Text(content);
        t.setStyle(String.format("-fx-fill: %s; -fx-font-family: 'Consolas'; -fx-font-size: %dpx;%s",
            color, fontSize, bold ? " -fx-font-weight: bold;" : ""));
        t.setFill(javafx.scene.paint.Color.web(color));
        t.setFont(bold
            ? javafx.scene.text.Font.font("Consolas", FontWeight.BOLD, fontSize)
            : javafx.scene.text.Font.font("Consolas", fontSize));
        return t;
    }

    private void clearAll() {
        pathField.clear();
        searchStringField.clear();
        patternField.setText("*");
        folderFilterField.clear();
        clientSideSearchField.clear();
        resultTable.getItems().clear();
        summaryPopupTimer.stop();
        summaryPopup.hide();
        hasSummary = false;
        clientSearchInfoButton.setDisable(true);
        statusLabel.setText("Hazır");
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Hata");
        alert.showAndWait();
    }
}

