package tr.com.envapp.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Proje dosyalarında arama yapan servis sınıfı
 */
public class EntitySearchService {

    // Arama yapılacak dosya uzantıları
    private static final Set<String> SEARCHABLE_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".java", ".xml", ".properties", ".yml", ".yaml", ".json",
        ".sql", ".txt", ".md", ".html", ".css", ".js", ".ts",
        ".kt", ".gradle", ".groovy", ".scala"
    ));

    // Atlanacak klasörler
    private static final Set<String> SKIP_DIRECTORIES = new HashSet<>(Arrays.asList(
        "target", "build", "node_modules", ".git", ".idea", ".mvn",
        "bin", "out", ".gradle", ".settings", "test", "tests", "test-classes"
    ));

    private boolean caseSensitive = false;
    private boolean searchInAllFiles = false;
    private boolean wholeWord = false;
    private StatusListener statusListener;

    // Status listener interface
    public interface StatusListener {
        void onDirectoryCompleted(String directoryName, int foundCount);
    }

    public EntitySearchService() {
    }

    public EntitySearchService(boolean caseSensitive, boolean searchInAllFiles) {
        this.caseSensitive = caseSensitive;
        this.searchInAllFiles = searchInAllFiles;
    }

    public void setStatusListener(StatusListener listener) {
        this.statusListener = listener;
    }


    /**
     * Belirtilen path'te verilen string'i arar
     * @param basePath Arama yapılacak kök dizin
     * @param searchString Aranacak string
     * @return Bulunan sonuçların listesi
     */
    public List<SearchResult> search(String basePath, String searchString) {
        return search(basePath, searchString, "*", null);
    }

    /**
     * Belirtilen path'te verilen string'i, belirli pattern'e uyan klasörlerde arar
     * @param basePath Arama yapılacak kök dizin
     * @param searchString Aranacak string
     * @param folderPattern Klasör pattern'i (örn: "*", "his-api-*", "*-service")
     * @return Bulunan sonuçların listesi
     */
    public List<SearchResult> search(String basePath, String searchString, String folderPattern) {
        return search(basePath, searchString, folderPattern, null);
    }

    /**
     * Belirtilen path'te verilen string'i, belirli pattern'e uyan klasörlerde ve
     * belirli üst klasör ismine sahip dosyalarda arar
     * @param basePath Arama yapılacak kök dizin
     * @param searchString Aranacak string
     * @param folderPattern Klasör pattern'i (örn: "*", "his-api-*", "*-service")
     * @param parentFolderFilter Üst klasör filtresi (örn: "domain", "entity") - null veya boş = filtre yok
     * @return Bulunan sonuçların listesi
     */
    public List<SearchResult> search(String basePath, String searchString, String folderPattern, String parentFolderFilter) {
        List<SearchResult> results = new ArrayList<>();
        File baseDir = new File(basePath);

        if (!baseDir.exists()) {
            System.err.println("Hata: Belirtilen path bulunamadı: " + basePath);
            return results;
        }

        if (!baseDir.isDirectory()) {
            System.err.println("Hata: Belirtilen path bir klasör değil: " + basePath);
            return results;
        }

        // Parent folder filter'ı normalize et
        String normalizedFilter = (parentFolderFilter != null && !parentFolderFilter.trim().isEmpty())
            ? parentFolderFilter.trim().toLowerCase()
            : null;

        // Pattern "*" ise tüm klasörleri tara
        if ("*".equals(folderPattern)) {
            searchInDirectory(baseDir, searchString, results, normalizedFilter);
        } else {
            // Üst seviye klasörleri filtrele
            File[] topLevelDirs = baseDir.listFiles(File::isDirectory);
            if (topLevelDirs != null) {
                for (File dir : topLevelDirs) {
                    if (matchesPattern(dir.getName(), folderPattern)) {
                        searchInDirectory(dir, searchString, results, normalizedFilter);
                    }
                }
            }
        }

        return results;
    }

    /**
     * Klasör adının verilen pattern'e uyup uymadığını kontrol eder
     * Pattern'de * karakteri wildcard olarak kullanılır
     * Örnek: "his-api-*" -> "his-api-gateway", "his-api-auth" ile eşleşir
     * @param folderName Klasör adı
     * @param pattern Pattern (örn: "his-*", "*-service", "*api*")
     * @return Eşleşme durumu
     */
    private boolean matchesPattern(String folderName, String pattern) {
        if (pattern == null || pattern.isEmpty() || "*".equals(pattern)) {
            return true;
        }

        // Pattern'i regex'e çevir
        // * -> .* (herhangi bir karakter dizisi)
        // Diğer özel karakterleri escape et
        String regex = pattern
            .replace(".", "\\.")
            .replace("?", ".")
            .replace("*", ".*");

        return folderName.toLowerCase().matches(regex.toLowerCase());
    }

    /**
     * Recursive olarak dizin içinde arama yapar
     * @param directory Aranacak dizin
     * @param searchString Aranacak string
     * @param results Sonuç listesi
     * @param parentFolderFilter Üst klasör filtresi (null = filtre yok)
     */
    private void searchInDirectory(File directory, String searchString, List<SearchResult> results, String parentFolderFilter) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!shouldSkipDirectory(file.getName())) {
                    searchInDirectory(file, searchString, results, parentFolderFilter);
                }
            } else {
                if (shouldSearchFile(file.getName())) {
                    // Eğer parentFolderFilter varsa, dosyanın üst klasörünü kontrol et
                    if (parentFolderFilter == null || matchesParentFolder(file, parentFolderFilter)) {
                        searchInFile(file, searchString, results);
                    }
                }
            }
        }
    }

    /**
     * Dosyanın üst klasörlerinden birinin filtre ile eşleşip eşleşmediğini kontrol eder
     */
    private boolean matchesParentFolder(File file, String folderFilter) {
        File parent = file.getParentFile();
        while (parent != null) {
            if (parent.getName().toLowerCase().equals(folderFilter)) {
                return true;
            }
            parent = parent.getParentFile();
        }
        return false;
    }

    /**
     * Tek bir dosya içinde arama yapar
     */
    private void searchInFile(File file, String searchString, List<SearchResult> results) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                boolean found;
                if (wholeWord) {
                    found = containsWholeWord(line, searchString, caseSensitive);
                } else if (caseSensitive) {
                    found = line.contains(searchString);
                } else {
                    found = line.toLowerCase().contains(searchString.toLowerCase());
                }

                if (found) {
                    results.add(new SearchResult(file.getAbsolutePath(), lineNumber, line));
                }
            }
        } catch (IOException e) {
            System.err.println("Dosya okunamadı: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    /**
     * Tam kelime eşleşmesi kontrolü yapar
     */
    private boolean containsWholeWord(String line, String searchString, boolean caseSensitive) {
        String compareLine = caseSensitive ? line : line.toLowerCase();
        String compareSearch = caseSensitive ? searchString : searchString.toLowerCase();

        int index = 0;
        while ((index = compareLine.indexOf(compareSearch, index)) >= 0) {
            // Kelimenin başında mı kontrol et
            boolean startOk = (index == 0) || !Character.isLetterOrDigit(compareLine.charAt(index - 1));
            // Kelimenin sonunda mı kontrol et
            int endIndex = index + compareSearch.length();
            boolean endOk = (endIndex >= compareLine.length()) || !Character.isLetterOrDigit(compareLine.charAt(endIndex));

            if (startOk && endOk) {
                return true;
            }
            index++;
        }
        return false;
    }

    /**
     * Dosyanın aranabilir olup olmadığını kontrol eder
     */
    private boolean shouldSearchFile(String fileName) {
        if (searchInAllFiles) {
            return !isBinaryFile(fileName);
        }

        String lowerName = fileName.toLowerCase();
        for (String ext : SEARCHABLE_EXTENSIONS) {
            if (lowerName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Binary dosya olup olmadığını kontrol eder
     */
    private boolean isBinaryFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".class") ||
               lowerName.endsWith(".jar") ||
               lowerName.endsWith(".war") ||
               lowerName.endsWith(".ear") ||
               lowerName.endsWith(".zip") ||
               lowerName.endsWith(".png") ||
               lowerName.endsWith(".jpg") ||
               lowerName.endsWith(".jpeg") ||
               lowerName.endsWith(".gif") ||
               lowerName.endsWith(".ico") ||
               lowerName.endsWith(".pdf") ||
               lowerName.endsWith(".exe") ||
               lowerName.endsWith(".dll");
    }

    /**
     * Klasörün atlanıp atlanmayacağını kontrol eder
     */
    private boolean shouldSkipDirectory(String dirName) {
        return SKIP_DIRECTORIES.contains(dirName);
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public void setWholeWord(boolean wholeWord) {
        this.wholeWord = wholeWord;
    }

    public void setSearchInAllFiles(boolean searchInAllFiles) {
        this.searchInAllFiles = searchInAllFiles;
    }
}

