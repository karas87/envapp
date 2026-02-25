package tr.com.envapp.search;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Entity Search - Swing GUI (FlatLaf Dark Theme)
 */
public class EntitySearchGUI extends JFrame {

    private JTextField pathField;
    private JTextField searchStringField;
    private JTextField patternField;
    private JTextField folderFilterField;
    private JCheckBox caseSensitiveCheckBox;
    private JCheckBox wholeWordCheckBox;
    private JTextPane resultArea;
    private JButton searchButton;
    private JButton stopButton;
    private JButton clearButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private SwingWorker<List<SearchResult>, Void> currentWorker;
    private String currentSearchString;
    private boolean currentCaseSensitive;
    private boolean currentWholeWord;

    public EntitySearchGUI() {
        initLookAndFeel();
        initComponents();
        setupLayout();
        setupEventHandlers();
    }

    private void initLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Tema yuklenemedi, varsayilan tema kullanilacak.");
        }
    }

    private void initComponents() {
        setTitle("Search Tool");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        // Input fields
        pathField = new JTextField(40);
        pathField.setToolTipText("Arama yapilacak klasor yolu");

        searchStringField = new JTextField(40);
        searchStringField.setToolTipText("Aranacak metin");

        patternField = new JTextField("*", 20);
        patternField.setToolTipText("Proje pattern (* = tumu, his-api-* = his-api ile baslayanlar)");

        folderFilterField = new JTextField(20);
        folderFilterField.setToolTipText("Ust klasor filtresi (ornegin: domain, entity, model)");

        caseSensitiveCheckBox = new JCheckBox("Case Sensitive");
        caseSensitiveCheckBox.setToolTipText("Buyuk/kucuk harf duyarli arama");

        wholeWordCheckBox = new JCheckBox("Whole Word");
        wholeWordCheckBox.setToolTipText("Sadece tam kelime eslesmesi (Adres arandiginda kimlikAdresi bulunamaz)");

        // Text areas
        resultArea = new JTextPane();
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        resultArea.setBackground(new Color(43, 43, 43));

        // Buttons
        searchButton = new JButton("Aramayi Baslat");
        searchButton.setFont(new Font("Dialog", Font.BOLD, 14));
        searchButton.setBackground(new Color(76, 175, 80));
        searchButton.setForeground(Color.WHITE);

        stopButton = new JButton("Durdur");
        stopButton.setFont(new Font("Dialog", Font.BOLD, 14));
        stopButton.setBackground(new Color(244, 67, 54));
        stopButton.setForeground(Color.WHITE);
        stopButton.setEnabled(false);

        clearButton = new JButton("Temizle");
        clearButton.setFont(new Font("Dialog", Font.PLAIN, 14));

        // Progress & Status
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(false);

        statusLabel = new JLabel("Hazir");
        statusLabel.setFont(new Font("Dialog", Font.ITALIC, 12));
    }

    private void setupLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // === Ust Panel - Baslik ===
        JLabel titleLabel = new JLabel("Search Tool", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Dialog", Font.BOLD, 24));
        titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));

        // === Input Panel ===
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)),
            "Arama Parametreleri",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Dialog", Font.BOLD, 12)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Path row
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("Klasor Yolu:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        inputPanel.add(pathField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseButton = new JButton("Gozat");
        browseButton.addActionListener(e -> browseFolder());
        inputPanel.add(browseButton, gbc);

        // Search string row
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("Aranacak Metin:"), gbc);

        gbc.gridx = 1; gbc.weightx = 1;
        gbc.gridwidth = 2;
        inputPanel.add(searchStringField, gbc);

        // Pattern row
        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("Proje Pattern:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.5;
        inputPanel.add(patternField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JLabel patternHint = new JLabel("(*, his-api-*, *-service)");
        patternHint.setForeground(Color.GRAY);
        inputPanel.add(patternHint, gbc);

        // Folder filter row
        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        inputPanel.add(new JLabel("Ust Klasor Filtresi:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.5;
        inputPanel.add(folderFilterField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JLabel folderHint = new JLabel("(domain, entity, model - bos = tumu)");
        folderHint.setForeground(Color.GRAY);
        inputPanel.add(folderHint, gbc);

        // Button row
        gbc.gridx = 0; gbc.gridy = 4;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.add(caseSensitiveCheckBox);
        buttonPanel.add(wholeWordCheckBox);
        buttonPanel.add(progressBar);
        buttonPanel.add(searchButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(clearButton);
        inputPanel.add(buttonPanel, gbc);

        // === Center Panel - Results ===
        JPanel resultsPanel = new JPanel(new BorderLayout());
        resultsPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(100, 100, 100)),
            "Sonuclar",
            TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Dialog", Font.BOLD, 12)
        ));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        resultsPanel.add(resultScroll, BorderLayout.CENTER);

        // === Bottom Panel - Status Bar ===
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(new EmptyBorder(5, 0, 0, 0));
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        // Combine all
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(titleLabel, BorderLayout.NORTH);
        topPanel.add(inputPanel, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(resultsPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void setupEventHandlers() {
        searchButton.addActionListener(this::performSearch);
        stopButton.addActionListener(e -> stopSearch());
        clearButton.addActionListener(e -> clearAll());

        // Enter tusu ile arama
        searchStringField.addActionListener(this::performSearch);
        patternField.addActionListener(this::performSearch);
    }

    private void stopSearch() {
        if (currentWorker != null && !currentWorker.isDone()) {
            currentWorker.cancel(true);
            statusLabel.setText("Arama durduruldu!");
            searchButton.setEnabled(true);
            stopButton.setEnabled(false);
            progressBar.setVisible(false);
        }
    }

    private void browseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Klasor Secin");

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void performSearch(ActionEvent e) {
        String path = pathField.getText().trim();
        String searchString = searchStringField.getText().trim();
        String pattern = patternField.getText().trim();
        String folderFilter = folderFilterField.getText().trim();

        if (path.isEmpty()) {
            showError("Lutfen klasor yolu girin!");
            return;
        }

        if (searchString.isEmpty()) {
            showError("Lutfen aranacak metni girin!");
            return;
        }

        if (!new File(path).exists()) {
            showError("Belirtilen klasor bulunamadi!");
            return;
        }

        if (pattern.isEmpty()) {
            pattern = "*";
        }

        // UI hazirla
        searchButton.setEnabled(false);
        stopButton.setEnabled(true);
        progressBar.setVisible(true);
        resultArea.setText("");
        statusLabel.setText("Arama yapiliyor...");
        currentSearchString = searchString;
        currentCaseSensitive = caseSensitiveCheckBox.isSelected();
        currentWholeWord = wholeWordCheckBox.isSelected();

        final String finalPattern = pattern;
        final String finalFolderFilter = folderFilter.isEmpty() ? null : folderFilter;
        final boolean finalCaseSensitive = currentCaseSensitive;
        final boolean finalWholeWord = currentWholeWord;

        // Arka planda arama yap
        currentWorker = new SwingWorker<>() {
            long startTime;

            @Override
            protected List<SearchResult> doInBackground() {
                startTime = System.currentTimeMillis();
                EntitySearchService searchService = new EntitySearchService();
                searchService.setCaseSensitive(finalCaseSensitive);
                searchService.setWholeWord(finalWholeWord);
                return searchService.search(path, searchString, finalPattern, finalFolderFilter);
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        return;
                    }
                    List<SearchResult> results = get();
                    long duration = System.currentTimeMillis() - startTime;
                    displayResults(results, duration);
                    statusLabel.setText("Arama tamamlandi! " + results.size() + " sonuc bulundu. (" + duration + " ms)");
                } catch (Exception ex) {
                    if (!isCancelled()) {
                        showError("Arama sirasinda hata olustu: " + ex.getMessage());
                        statusLabel.setText("Hata olustu!");
                    }
                } finally {
                    searchButton.setEnabled(true);
                    stopButton.setEnabled(false);
                    progressBar.setVisible(false);
                }
            }
        };

        currentWorker.execute();
    }

    private void displayResults(List<SearchResult> results, long duration) {
        StyledDocument doc = resultArea.getStyledDocument();
        String basePath = pathField.getText().trim();

        // Stil tanımları
        Style defaultStyle = resultArea.addStyle("default", null);
        StyleConstants.setForeground(defaultStyle, Color.WHITE);
        StyleConstants.setFontFamily(defaultStyle, "Consolas");
        StyleConstants.setFontSize(defaultStyle, 12);

        Style highlightStyle = resultArea.addStyle("highlight", null);
        StyleConstants.setBackground(highlightStyle, Color.YELLOW);
        StyleConstants.setForeground(highlightStyle, Color.BLACK);
        StyleConstants.setFontFamily(highlightStyle, "Consolas");
        StyleConstants.setFontSize(highlightStyle, 12);
        StyleConstants.setBold(highlightStyle, true);

        Style headerStyle = resultArea.addStyle("header", null);
        StyleConstants.setForeground(headerStyle, new Color(100, 200, 255));
        StyleConstants.setFontFamily(headerStyle, "Consolas");
        StyleConstants.setFontSize(headerStyle, 12);
        StyleConstants.setBold(headerStyle, true);

        Style projectStyle = resultArea.addStyle("project", null);
        StyleConstants.setForeground(projectStyle, new Color(255, 200, 100));
        StyleConstants.setFontFamily(projectStyle, "Consolas");
        StyleConstants.setFontSize(projectStyle, 14);
        StyleConstants.setBold(projectStyle, true);

        Style fileStyle = resultArea.addStyle("file", null);
        StyleConstants.setForeground(fileStyle, new Color(144, 238, 144));
        StyleConstants.setFontFamily(fileStyle, "Consolas");
        StyleConstants.setFontSize(fileStyle, 12);

        try {
            if (results.isEmpty()) {
                doc.insertString(doc.getLength(), "Sonuc bulunamadi!\n", defaultStyle);
            } else {
                doc.insertString(doc.getLength(), "=".repeat(70) + "\n", headerStyle);
                doc.insertString(doc.getLength(), "BULUNAN SONUCLAR\n", headerStyle);
                doc.insertString(doc.getLength(), "=".repeat(70) + "\n\n", headerStyle);

                // Önce projeye göre grupla, sonra dosyaya göre
                Map<String, Map<String, List<SearchResult>>> groupedByProject = results.stream()
                    .collect(Collectors.groupingBy(
                        r -> extractProjectName(r.getFilePath(), basePath),
                        Collectors.groupingBy(SearchResult::getFilePath)
                    ));

                int projectCount = 0;
                int totalFiles = 0;

                for (Map.Entry<String, Map<String, List<SearchResult>>> projectEntry : groupedByProject.entrySet()) {
                    projectCount++;
                    String projectName = projectEntry.getKey();
                    Map<String, List<SearchResult>> filesInProject = projectEntry.getValue();

                    int projectResultCount = filesInProject.values().stream().mapToInt(List::size).sum();

                    // Proje başlığı
                    doc.insertString(doc.getLength(), "\n📁 ", defaultStyle);
                    doc.insertString(doc.getLength(), projectName, projectStyle);
                    doc.insertString(doc.getLength(), " (" + projectResultCount + " sonuc, " + filesInProject.size() + " dosya)\n", defaultStyle);
                    doc.insertString(doc.getLength(), "│\n", defaultStyle);

                    int fileIndex = 0;
                    for (Map.Entry<String, List<SearchResult>> fileEntry : filesInProject.entrySet()) {
                        fileIndex++;
                        totalFiles++;
                        String filePath = fileEntry.getKey();
                        List<SearchResult> fileResults = fileEntry.getValue();

                        // Dosya yolunu kısalt (proje adından sonraki kısmı göster)
                        String shortPath = getShortFilePath(filePath, basePath, projectName);

                        String filePrefix = (fileIndex == filesInProject.size()) ? "└── " : "├── ";
                        String linePrefix = (fileIndex == filesInProject.size()) ? "    " : "│   ";

                        doc.insertString(doc.getLength(), filePrefix, defaultStyle);
                        doc.insertString(doc.getLength(), shortPath, fileStyle);
                        doc.insertString(doc.getLength(), " (" + fileResults.size() + " eslesme)\n", defaultStyle);

                        for (int i = 0; i < fileResults.size(); i++) {
                            SearchResult result = fileResults.get(i);
                            String lineContent = result.getLineContent().trim();
                            if (lineContent.length() > 80) {
                                lineContent = lineContent.substring(0, 77) + "...";
                            }

                            String resultPrefix = (i == fileResults.size() - 1) ? "└─ " : "├─ ";
                            doc.insertString(doc.getLength(), linePrefix + resultPrefix + "Satir " + result.getLineNumber() + ": ", defaultStyle);

                            // Highlight aranan metni
                            appendHighlightedText(doc, lineContent, currentSearchString, defaultStyle, highlightStyle);
                            doc.insertString(doc.getLength(), "\n", defaultStyle);
                        }
                    }
                }

                // Ozet
                doc.insertString(doc.getLength(), "\n" + "=".repeat(70) + "\n", headerStyle);
                doc.insertString(doc.getLength(), "OZET\n", headerStyle);
                doc.insertString(doc.getLength(), "=".repeat(70) + "\n", headerStyle);
                doc.insertString(doc.getLength(), "   Toplam proje sayisi: " + groupedByProject.size() + "\n", defaultStyle);
                doc.insertString(doc.getLength(), "   Toplam dosya sayisi: " + totalFiles + "\n", defaultStyle);
                doc.insertString(doc.getLength(), "   Toplam eslesme sayisi: " + results.size() + "\n", defaultStyle);
                doc.insertString(doc.getLength(), "   Sure: " + duration + " ms\n", defaultStyle);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }

        resultArea.setCaretPosition(0);
    }

    private String extractProjectName(String filePath, String basePath) {
        // basePath'ten sonraki ilk klasörü proje adı olarak al
        String normalizedFile = filePath.replace("\\", "/");
        String normalizedBase = basePath.replace("\\", "/");

        if (!normalizedBase.endsWith("/")) {
            normalizedBase += "/";
        }

        if (normalizedFile.startsWith(normalizedBase)) {
            String relativePath = normalizedFile.substring(normalizedBase.length());
            int firstSlash = relativePath.indexOf("/");
            if (firstSlash > 0) {
                return relativePath.substring(0, firstSlash);
            }
            return relativePath;
        }
        return "(root)";
    }

    private String getShortFilePath(String filePath, String basePath, String projectName) {
        String normalizedFile = filePath.replace("\\", "/");
        String normalizedBase = basePath.replace("\\", "/");

        if (!normalizedBase.endsWith("/")) {
            normalizedBase += "/";
        }

        String fullPrefix = normalizedBase + projectName + "/";
        if (normalizedFile.startsWith(fullPrefix)) {
            return normalizedFile.substring(fullPrefix.length());
        }
        return filePath;
    }

    private void appendHighlightedText(StyledDocument doc, String text, String searchString, Style normalStyle, Style highlightStyle) throws BadLocationException {
        String compareText = currentCaseSensitive ? text : text.toLowerCase();
        String compareSearch = currentCaseSensitive ? searchString : searchString.toLowerCase();

        int lastEnd = 0;
        int index = 0;

        while ((index = compareText.indexOf(compareSearch, index)) >= 0) {
            boolean shouldHighlight = true;

            // Tam kelime kontrolü
            if (currentWholeWord) {
                boolean startOk = (index == 0) || !Character.isLetterOrDigit(compareText.charAt(index - 1));
                int endIndex = index + compareSearch.length();
                boolean endOk = (endIndex >= compareText.length()) || !Character.isLetterOrDigit(compareText.charAt(endIndex));
                shouldHighlight = startOk && endOk;
            }

            if (shouldHighlight) {
                // Normal metin (highlight öncesi)
                if (index > lastEnd) {
                    doc.insertString(doc.getLength(), text.substring(lastEnd, index), normalStyle);
                }
                // Highlight edilmiş metin
                doc.insertString(doc.getLength(), text.substring(index, index + searchString.length()), highlightStyle);
                lastEnd = index + searchString.length();
            }
            index++;
        }

        // Kalan metin
        if (lastEnd < text.length()) {
            doc.insertString(doc.getLength(), text.substring(lastEnd), normalStyle);
        }
    }

    private void clearAll() {
        pathField.setText("");
        searchStringField.setText("");
        patternField.setText("*");
        folderFilterField.setText("");
        resultArea.setText("");
        statusLabel.setText("Hazir");
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Hata", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            EntitySearchGUI gui = new EntitySearchGUI();
            gui.setVisible(true);
        });
    }
}
