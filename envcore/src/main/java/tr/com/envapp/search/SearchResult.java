package tr.com.envapp.search;

/**
 * Arama sonuçlarını tutan model sınıfı
 */
public class SearchResult {
    private final String filePath;
    private final int lineNumber;
    private final String lineContent;
    private final String fileName;

    public SearchResult(String filePath, int lineNumber, String lineContent) {
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.lineContent = lineContent;
        this.fileName = extractFileName(filePath);
    }

    private String extractFileName(String path) {
        int lastSeparator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return lastSeparator >= 0 ? path.substring(lastSeparator + 1) : path;
    }

    public String getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getLineContent() {
        return lineContent;
    }

    public String getFileName() {
        return fileName;
    }

    @Override
    public String toString() {
        return String.format("Dosya: %s | Satır: %d | İçerik: %s",
            filePath, lineNumber, lineContent.trim());
    }
}

