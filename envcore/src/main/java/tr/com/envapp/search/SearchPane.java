package tr.com.envapp.search;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Entity Search - JavaFX Dark Pane
 */
public class SearchPane {

    private TextField pathField;
    private TextField searchStringField;
    private TextField patternField;
    private TextField folderFilterField;
    private CheckBox caseSensitiveCheckBox;
    private CheckBox wholeWordCheckBox;
    private TextFlow resultArea;
    private ScrollPane resultScroll;
    private Button searchButton;
    private Button stopButton;
    private Button clearButton;
    private Label statusLabel;
    private ProgressBar progressBar;
    private Task<List<SearchResult>> currentTask;
    private String currentSearchString;
    private boolean currentCaseSensitive;
    private boolean currentWholeWord;
    private Window ownerWindow;

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

        caseSensitiveCheckBox = new CheckBox("Case Sensitive");
        wholeWordCheckBox = new CheckBox("Whole Word");

        searchButton = new Button("▶  Aramayı Başlat");
        searchButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 6 16 6 16;");

        stopButton = new Button("■  Durdur");
        stopButton.setStyle("-fx-background-color: #F44336; -fx-text-fill: white; -fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 6 16 6 16;");
        stopButton.setDisable(true);

        clearButton = new Button("Temizle");
        clearButton.setStyle("-fx-font-size: 13px; -fx-padding: 6 16 6 16;");

        progressBar = new ProgressBar();
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.setVisible(false);
        progressBar.setMaxWidth(Double.MAX_VALUE);

        statusLabel = new Label("Hazır");
        statusLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-style: italic;");

        resultArea = new TextFlow();
        resultArea.setStyle("-fx-background-color: #2b2b2b;");
        resultArea.setPadding(new Insets(10));

        resultScroll = new ScrollPane(resultArea);
        resultScroll.setFitToWidth(true);
        resultScroll.setFitToHeight(true);
        resultScroll.setStyle("-fx-background: #2b2b2b; -fx-background-color: #2b2b2b;");
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
            caseSensitiveCheckBox, wholeWordCheckBox,
            new Region(), progressBar,
            searchButton, stopButton, clearButton
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
        VBox.setVgrow(resultScroll, Priority.ALWAYS);
        resultsBox.getChildren().addAll(resultsLabel, resultScroll);
        VBox.setVgrow(resultsBox, Priority.ALWAYS);

        // === Status Bar ===
        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(4, 0, 0, 0));

        VBox.setVgrow(resultsBox, Priority.ALWAYS);
        root.getChildren().addAll(inputGrid, resultsBox, statusBar);

        // Event handlers
        searchButton.setOnAction(e -> performSearch());
        stopButton.setOnAction(e -> stopSearch());
        clearButton.setOnAction(e -> clearAll());
        searchStringField.setOnAction(e -> performSearch());
        patternField.setOnAction(e -> performSearch());

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
        resultArea.getChildren().clear();
        statusLabel.setText("Arama yapılıyor...");

        currentSearchString = searchString;
        currentCaseSensitive = caseSensitiveCheckBox.isSelected();
        currentWholeWord = wholeWordCheckBox.isSelected();

        final String finalPattern = pattern;
        final String finalFolderFilter = folderFilter.isEmpty() ? null : folderFilter;
        final boolean finalCaseSensitive = currentCaseSensitive;
        final boolean finalWholeWord = currentWholeWord;

        long startTime = System.currentTimeMillis();

        currentTask = new Task<>() {
            @Override
            protected List<SearchResult> call() {
                EntitySearchService service = new EntitySearchService();
                service.setCaseSensitive(finalCaseSensitive);
                service.setWholeWord(finalWholeWord);
                return service.search(path, searchString, finalPattern, finalFolderFilter);
            }
        };

        currentTask.setOnSucceeded(ev -> {
            long duration = System.currentTimeMillis() - startTime;
            List<SearchResult> results = currentTask.getValue();
            displayResults(results, duration, path);
            statusLabel.setText("Arama tamamlandı! " + results.size() + " sonuç bulundu. (" + duration + " ms)");
            searchButton.setDisable(false);
            stopButton.setDisable(true);
            progressBar.setVisible(false);
        });

        currentTask.setOnFailed(ev -> {
            statusLabel.setText("Hata oluştu: " + currentTask.getException().getMessage());
            searchButton.setDisable(false);
            stopButton.setDisable(true);
            progressBar.setVisible(false);
        });

        currentTask.setOnCancelled(ev -> {
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

    private void displayResults(List<SearchResult> results, long duration, String basePath) {
        resultArea.getChildren().clear();

        if (results.isEmpty()) {
            resultArea.getChildren().add(styledText("Sonuç bulunamadı!\n", "#ffffff", 12, false));
            return;
        }

        resultArea.getChildren().add(styledText("=".repeat(70) + "\n", "#64c8ff", 12, true));
        resultArea.getChildren().add(styledText("BULUNAN SONUÇLAR\n", "#64c8ff", 12, true));
        resultArea.getChildren().add(styledText("=".repeat(70) + "\n\n", "#64c8ff", 12, true));

        Map<String, Map<String, List<SearchResult>>> grouped = results.stream()
            .collect(Collectors.groupingBy(
                r -> extractProjectName(r.getFilePath(), basePath),
                Collectors.groupingBy(SearchResult::getFilePath)
            ));

        int totalFiles = 0;

        for (Map.Entry<String, Map<String, List<SearchResult>>> projectEntry : grouped.entrySet()) {
            String projectName = projectEntry.getKey();
            Map<String, List<SearchResult>> filesInProject = projectEntry.getValue();
            int projectResultCount = filesInProject.values().stream().mapToInt(List::size).sum();

            resultArea.getChildren().add(styledText("\n📁 ", "#ffffff", 12, false));
            resultArea.getChildren().add(styledText(projectName, "#ffc864", 14, true));
            resultArea.getChildren().add(styledText(" (" + projectResultCount + " sonuç, " + filesInProject.size() + " dosya)\n", "#ffffff", 12, false));
            resultArea.getChildren().add(styledText("│\n", "#ffffff", 12, false));

            int fileIndex = 0;
            for (Map.Entry<String, List<SearchResult>> fileEntry : filesInProject.entrySet()) {
                fileIndex++;
                totalFiles++;
                String filePath = fileEntry.getKey();
                List<SearchResult> fileResults = fileEntry.getValue();
                String shortPath = getShortFilePath(filePath, basePath, projectName);
                String filePrefix = (fileIndex == filesInProject.size()) ? "└── " : "├── ";
                String linePrefix = (fileIndex == filesInProject.size()) ? "    " : "│   ";

                resultArea.getChildren().add(styledText(filePrefix, "#ffffff", 12, false));
                resultArea.getChildren().add(styledText(shortPath, "#90ee90", 12, false));
                resultArea.getChildren().add(styledText(" (" + fileResults.size() + " eşleşme)\n", "#ffffff", 12, false));

                for (int i = 0; i < fileResults.size(); i++) {
                    SearchResult result = fileResults.get(i);
                    String lineContent = result.getLineContent().trim();
                    if (lineContent.length() > 100) lineContent = lineContent.substring(0, 97) + "...";
                    String resultPrefix = (i == fileResults.size() - 1) ? "└─ " : "├─ ";
                    resultArea.getChildren().add(styledText(linePrefix + resultPrefix + "Satır " + result.getLineNumber() + ": ", "#ffffff", 12, false));
                    appendHighlighted(lineContent, currentSearchString, currentCaseSensitive, currentWholeWord);
                    resultArea.getChildren().add(styledText("\n", "#ffffff", 12, false));
                }
            }
        }

        resultArea.getChildren().add(styledText("\n" + "=".repeat(70) + "\n", "#64c8ff", 12, true));
        resultArea.getChildren().add(styledText("ÖZET\n", "#64c8ff", 12, true));
        resultArea.getChildren().add(styledText("=".repeat(70) + "\n", "#64c8ff", 12, true));
        resultArea.getChildren().add(styledText("   Toplam proje: " + grouped.size() + "\n", "#ffffff", 12, false));
        resultArea.getChildren().add(styledText("   Toplam dosya: " + totalFiles + "\n", "#ffffff", 12, false));
        resultArea.getChildren().add(styledText("   Toplam eşleşme: " + results.size() + "\n", "#ffffff", 12, false));
        resultArea.getChildren().add(styledText("   Süre: " + duration + " ms\n", "#ffffff", 12, false));

        Platform.runLater(() -> resultScroll.setVvalue(0));
    }

    private void appendHighlighted(String text, String search, boolean caseSensitive, boolean wholeWord) {
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
                    resultArea.getChildren().add(styledText(text.substring(lastEnd, index), "#ffffff", 12, false));
                }
                Text hl = styledText(text.substring(index, index + search.length()), "#000000", 12, true);
                hl.setStyle("-fx-background-color: yellow;");
                // TextFlow'da background highlight için Label kullanıyoruz
                Label hlLabel = new Label(text.substring(index, index + search.length()));
                hlLabel.setStyle("-fx-background-color: #FFD700; -fx-text-fill: #000000; -fx-font-family: 'Consolas'; -fx-font-size: 12px; -fx-font-weight: bold;");
                resultArea.getChildren().add(hlLabel);
                lastEnd = index + search.length();
            } else {
                lastEnd = index + 1;
            }
        }

        if (lastEnd < text.length()) {
            resultArea.getChildren().add(styledText(text.substring(lastEnd), "#ffffff", 12, false));
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

    private String extractProjectName(String filePath, String basePath) {
        String nf = filePath.replace("\\", "/");
        String nb = basePath.replace("\\", "/");
        if (!nb.endsWith("/")) nb += "/";
        if (nf.startsWith(nb)) {
            String rel = nf.substring(nb.length());
            int slash = rel.indexOf("/");
            return slash > 0 ? rel.substring(0, slash) : rel;
        }
        return "(root)";
    }

    private String getShortFilePath(String filePath, String basePath, String projectName) {
        String nf = filePath.replace("\\", "/");
        String nb = basePath.replace("\\", "/");
        if (!nb.endsWith("/")) nb += "/";
        String prefix = nb + projectName + "/";
        return nf.startsWith(prefix) ? nf.substring(prefix.length()) : filePath;
    }

    private void clearAll() {
        pathField.clear();
        searchStringField.clear();
        patternField.setText("*");
        folderFilterField.clear();
        resultArea.getChildren().clear();
        statusLabel.setText("Hazır");
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Hata");
        alert.showAndWait();
    }
}

