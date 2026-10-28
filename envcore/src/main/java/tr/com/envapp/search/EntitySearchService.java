package tr.com.envapp.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import tr.com.envapp.model.IndexMetadataDTO;
import tr.com.envapp.model.SearchResultDTO;

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

    // Binary dosya uzantıları (HashSet ile O(1) kontrol)
    private static final Set<String> BINARY_EXTENSIONS = new HashSet<>(Arrays.asList(
        ".class", ".jar", ".war", ".ear", ".zip", ".png", ".jpg",
        ".jpeg", ".gif", ".ico", ".pdf", ".exe", ".dll", ".so",
        ".dylib", ".bin", ".dat", ".db", ".sqlite", ".tar", ".gz",
        ".7z", ".rar", ".iso", ".dmg"
    ));

    // Atlanacak klasörler
    private static final Set<String> SKIP_DIRECTORIES = new HashSet<>(Arrays.asList(
        "target", "build", "node_modules", ".git", ".idea", ".mvn",
        "bin", "out", ".gradle", ".settings", "tests", "test-classes"
    ));

    private boolean testIncluded = false;
    private boolean caseSensitive = false;
    private boolean searchInAllFiles = false;
    private boolean wholeWord = false;
    private boolean useIndex = false;
    private StatusListener statusListener;

    // Parallelization için thread pool
    private final int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors()); // Minimum 4 thread, maksimum CPU çekirdeği sayısının 2 katı

    // Index için sabitler
    private static final String INDEX_BASE_DIR = "data/search-index";
    private static final String INDEX_METADATA_FILE = "data/search-index/index-metadata.json";
    private final ObjectMapper objectMapper;

    public EntitySearchService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Boolean refreshIndex(String basePath) throws IOException {
        String indexPath = getIndexPathForBasePath(basePath);
        checkAndCreateIndex(basePath, indexPath);
        return true;
    }

    public Boolean deleteIndex(String basePath) throws IOException {
        String indexPath = getIndexPathForBasePath(basePath);
        Map<String, IndexMetadataDTO> metadataMap = loadIndexMetadata();
        IndexMetadataDTO metadata = metadataMap.get(basePath);
        if (metadata != null) {
            metadataMap.remove(basePath);
            saveIndexMetadata(metadataMap);
            Files.walkFileTree(Paths.get(indexPath), new SimpleFileVisitor<>() {
              @Override
              public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file); // Önce dosyaları siler
                return FileVisitResult.CONTINUE;
              }

              @Override
              public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir); // Klasörün içi boşaldıktan sonra klasörü siler
                return FileVisitResult.CONTINUE;
              }
            });
        }
        return true;
    }

    // Status listener interface
    public interface StatusListener {
        void onDirectoryCompleted(String directoryName, int foundCount);
    }

  /**
     * Belirtilen path'te verilen string'i arar
     * @param basePath Arama yapılacak kök dizin
     * @param searchString Aranacak string
     * @return Bulunan sonuçların listesi
     */
    public List<SearchResultDTO> search(String basePath, String searchString) {
        return search(basePath, searchString, "*", null);
    }

    /**
     * Belirtilen path'te verilen string'i, belirli pattern'e uyan klasörlerde arar
     * @param basePath Arama yapılacak kök dizin
     * @param searchString Aranacak string
     * @param folderPattern Klasör pattern'i (örn: "*", "his-api-*", "*-service")
     * @return Bulunan sonuçların listesi
     */
    public List<SearchResultDTO> search(String basePath, String searchString, String folderPattern) {
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
    public List<SearchResultDTO> search(String basePath, String searchString, String folderPattern, String parentFolderFilter) {
        if (useIndex) {
            return searchWithIndex(basePath, searchString, folderPattern, parentFolderFilter);
        } else {
            return searchInFiles(basePath, searchString, folderPattern, parentFolderFilter);
        }
    }

    private List<SearchResultDTO> searchInFiles(String basePath, String searchString, String folderPattern, String parentFolderFilter) {
        ConcurrentLinkedQueue<SearchResultDTO> results = new ConcurrentLinkedQueue<>();
        File baseDir = new File(basePath);

        if (!baseDir.exists()) {
            System.err.println("Hata: Belirtilen path bulunamadı: " + basePath);
            return new ArrayList<>(results);
        }

        if (!baseDir.isDirectory()) {
            System.err.println("Hata: Belirtilen path bir klasör değil: " + basePath);
            return new ArrayList<>(results);
        }

        // Parent folder filter'ı normalize et
        String normalizedFilter = (parentFolderFilter != null && !parentFolderFilter.trim().isEmpty())
            ? parentFolderFilter.trim().toLowerCase()
            : null;

        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        AtomicInteger activeTaskCount = new AtomicInteger(0);

        try {
            // Pattern "*" ise tüm klasörleri tara
            if ("*".equals(folderPattern)) {
                searchInDirectory(basePath, baseDir, searchString, results, normalizedFilter, executor, activeTaskCount);
            } else {
                // Üst seviye klasörleri filtrele
                File[] topLevelDirs = baseDir.listFiles(File::isDirectory);
                if (topLevelDirs != null) {
                    for (File dir : topLevelDirs) {
                        if (matchesPattern(dir.getName(), folderPattern)) {
                            searchInDirectory(basePath, dir, searchString, results, normalizedFilter, executor, activeTaskCount);
                        }
                    }
                }
            }

            // Executor'u kapat ve tüm task'lerin bitmesini bekle
            executor.shutdown();
            boolean terminated = executor.awaitTermination(1, TimeUnit.HOURS);

            if (!terminated) {
                System.err.println("Uyarı: Arama işlemi 1 saat içinde tamamlanamadı. İşlem zorla sonlandırılıyor.");
                executor.shutdownNow();
                // Kalan task'lerin sonlanmasını kısa bir süre bekle
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.err.println("Uyarı: Bazı task'ler hala sonlanamadı.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Arama işlemi kesintiye uğradı: " + e.getMessage());
            executor.shutdownNow();
        } finally {
            executor.shutdownNow();
            executor.close();
        }

        return new ArrayList<>(results);
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
    private void searchInDirectory(String basePath, File directory, String searchString, ConcurrentLinkedQueue<SearchResultDTO> results, String parentFolderFilter, ExecutorService executor,  AtomicInteger activeTaskCount) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (shouldSearchDirectory(file.getName())) {
                    // Alt dizinleri recursive olarak tara
                    searchInDirectory(basePath, file, searchString, results, parentFolderFilter, executor, activeTaskCount);
                }
            } else {
                if (shouldSearchFile(file.getName()) && (parentFolderFilter == null || matchesParentFolder(file, parentFolderFilter))) {
                    // Her dosya için paralel task oluştur
                    final File fileToSearch = file;
                    activeTaskCount.incrementAndGet();
                    executor.submit(() -> {
                        try {
                            searchInFileParallel(basePath, fileToSearch, searchString, results);
                        } finally {
                            activeTaskCount.decrementAndGet();
                        }
                    });
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
    private void searchInFileParallel(String basePath, File file, String searchString, ConcurrentLinkedQueue<SearchResultDTO> results) {
        Path filePath = file.toPath();

        try {
            // Java NIO - Files.lines() daha hızlı okuma sağlar
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lineNumber = i + 1;

                boolean found = isFound(searchString, line);

                if (found) {
                    results.add(new SearchResultDTO(file.getAbsolutePath(), lineNumber, line.trim(), extractProjectName(file.getAbsolutePath(), basePath)));
                }
            }
        } catch (IOException e) {
            // Fallback - eğer encoding sorunu olursa BufferedReader kullan
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                int lineNumber = 0;

                while ((line = reader.readLine()) != null) {
                    lineNumber++;

                    boolean found = isFound(searchString, line);

                    if (found) {
                        results.add(new SearchResultDTO(file.getAbsolutePath(), lineNumber, line.trim(), extractProjectName(file.getAbsolutePath(), basePath)));
                    }
                }
            } catch (IOException ex) {
                System.err.println("Dosya okunamadı: " + file.getAbsolutePath() + " - " + ex.getMessage());
            }
        }
    }

    private boolean isFound(String searchString, String line) {
        boolean found;
        if (wholeWord) {
            found = containsWholeWord(line, searchString, caseSensitive);
        } else if (caseSensitive) {
            found = line.contains(searchString);
        } else {
            found = line.toLowerCase().contains(searchString.toLowerCase());
        }
        return found;
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

    private String extractProjectName(String filePath, String basePath) {
        String projectName = "(root)";
        String nf = filePath.replace("\\", "/");
        String nb = basePath.replace("\\", "/");
        if (!nb.endsWith("/")) nb += "/";
        if (nf.startsWith(nb)) {
            String rel = nf.substring(nb.length());
            int slash = rel.indexOf("/");
            projectName = slash > 0 ? rel.substring(0, slash) : rel;
        }
        return projectName;
    }

    /**
     * Dosyanın aranabilir olup olmadığını kontrol eder
     */
    private boolean shouldSearchFile(String fileName) {
        if (searchInAllFiles) {
            return !isBinaryFile(fileName);
        }

        // Optimizasyon: Dosya uzantısını bir kez extract et
        String extension = getFileExtension(fileName);
        return SEARCHABLE_EXTENSIONS.contains(extension);
    }

    /**
     * Dosya uzantısını al (lowercase)
     * Optimizasyon: Dosya adından uzantı çıkart, lowercase'e çevir
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0 && lastDot < fileName.length() - 1) {
            return fileName.substring(lastDot).toLowerCase();
        }
        return "";
    }

    /**
     * Binary dosya olup olmadığını kontrol eder
     */
    private boolean isBinaryFile(String fileName) {
        String extension = getFileExtension(fileName);
        return BINARY_EXTENSIONS.contains(extension);
    }

    /**
     * Klasörün atlanıp atlanmayacağını kontrol eder
     */
    private boolean shouldSearchDirectory(String dirName) {
        if (!testIncluded && "test".equalsIgnoreCase(dirName)) {
            return false;
        }
        return !SKIP_DIRECTORIES.contains(dirName);
    }

    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    /**
     * Index kullanarak arama yapar
     * @param basePath Arama yapılacak kök dizin
     * @param searchString Aranacak string
     * @param folderPattern Klasör pattern'i (örn: "*", "his-api-*", "*-service")
     * @param parentFolderFilter Üst klasör filtresi (örn: "domain", "entity") - null veya boş = filtre yok
     * @return Bulunan sonuçların listesi
     */
    public List<SearchResultDTO> searchWithIndex(String basePath, String searchString, String folderPattern, String parentFolderFilter) {
        try {
            if (basePath.endsWith("\\")) {
                basePath = basePath.substring(0, basePath.length() - 1);
            }
            // 1. Index var mı kontrol et
            String indexPath = getIndexPathForBasePath(basePath);
            Map<String, IndexMetadataDTO> metadataMap = loadIndexMetadata();
            IndexMetadataDTO metadata = metadataMap.get(basePath);
            if (metadata == null || !checkIndexExists(indexPath)) {
                checkAndCreateIndex(basePath, indexPath);
            }

            // 3. Index'te arama yap
            return searchInIndex(basePath, indexPath, searchString, folderPattern, parentFolderFilter);

        } catch (Exception e) {
            System.err.println("Index ile arama hatası: " + e.getMessage());
            e.printStackTrace();
            // Hata durumunda normal arama yap
            return search(basePath, searchString, folderPattern, parentFolderFilter);
        }
    }

    private void checkAndCreateIndex(String basePath, String indexPath) throws IOException {
        Map<String, IndexMetadataDTO> metadataMap = loadIndexMetadata();
        IndexMetadataDTO metadata = createIndex(basePath, indexPath);

        // Metadata'yı kaydet
        metadataMap.put(basePath, metadata);
        saveIndexMetadata(metadataMap);
    }

    /**
     * Base path için unique bir index path oluşturur
     */
    private String getIndexPathForBasePath(String basePath) {
        try {
            // Path'i hash'le, böylece her path için unique bir index directory olur
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(basePath.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return INDEX_BASE_DIR + File.separator + hexString;
        } catch (NoSuchAlgorithmException e) {
            // Fallback: path'i encode et
            return INDEX_BASE_DIR + File.separator + basePath.hashCode();
        }
    }

    /**
     * Index'in var olup olmadığını kontrol eder
     */
    private boolean checkIndexExists(String indexPath) {
        File indexDir = new File(indexPath);
        if (!indexDir.exists() || !indexDir.isDirectory()) {
            return false;
        }

        // Index directory'de dosya var mı kontrol et
        File[] files = indexDir.listFiles();
        return files != null && files.length > 0;
    }

    /**
     * Belirtilen path için index oluşturur.
     * Dosya tarama ve indexleme ayrılmıştır:
     *   1. Tüm indexlenecek dosyalar hızlıca toplanır (senkron tarama)
     *   2. Toplanan dosyalar parallel thread pool ile indexlenir (IndexWriter thread-safe)
     * @return İndexlenen dosya sayısı
     */
    private IndexMetadataDTO createIndex(String basePath, String indexPath) throws IOException {
        File baseDir = new File(basePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            throw new IOException("Geçersiz base path: " + basePath);
        }

        // Index directory oluştur
        Path indexDir = Paths.get(indexPath);
        Files.createDirectories(indexDir);

        // Lucene index writer oluştur
        Directory directory = FSDirectory.open(indexDir);
        Analyzer codeAnalyzer = new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                // 1. StandardTokenizer yerine WhitespaceTokenizer kullanıyoruz.
                // Böylece "/", "-", ";", "\"" gibi karakterler yok olmaz.
                WhitespaceTokenizer tokenizer = new WhitespaceTokenizer();

                // IDE tarzı parçalama yapan sihirli filtre
                int flags = WordDelimiterGraphFilter.GENERATE_WORD_PARTS |
                    WordDelimiterGraphFilter.GENERATE_NUMBER_PARTS |
                    WordDelimiterGraphFilter.SPLIT_ON_CASE_CHANGE | // camelCase ayrımı
                    WordDelimiterGraphFilter.PRESERVE_ORIGINAL;     // Orijinal kelimeyi de bozmadan sakla (PersonelDTO)

                TokenStream filter = new WordDelimiterGraphFilter(tokenizer, flags, null);

                // Son olarak her şeyi küçük harfe çevir
                filter = new LowerCaseFilter(filter);

                return new TokenStreamComponents(tokenizer, filter);
            }
        };
        IndexWriterConfig config = new IndexWriterConfig(codeAnalyzer);
        config.setOpenMode(OpenMode.CREATE);
        // Daha büyük RAM buffer → daha az disk flush → daha hızlı indexleme
        config.setRAMBufferSizeMB(4096);

        // 1. Adım: indexlenecek tüm dosyaları topla (hızlı senkron tarama)
        System.out.println("Index oluşturuluyor, dosyalar taranıyor: " + basePath);
        List<File> filesToIndex = new ArrayList<>();
        collectFilesForIndexing(baseDir, filesToIndex);
        System.out.println("Tarama tamamlandı: " + filesToIndex.size() + " dosya bulundu, indexleme başlıyor...");
        Integer indexedLineCount = 0;

        // 2. Adım: dosyaları paralel olarak indexle (IndexWriter thread-safe)
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

            for (File file : filesToIndex) {
                executor.submit(() -> {
                    try {
                        indexFile(writer, file);
                    } catch (Exception e) {
                        System.err.println("Dosya indexlenemedi: " + file.getAbsolutePath() + " - " + e.getMessage());
                    }
                });
            }

            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.MINUTES)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }

            writer.commit();
            indexedLineCount = writer.getDocStats().numDocs;
            System.out.println("Index oluşturma tamamlandı: " + filesToIndex.size() + " dosya, " + indexedLineCount + " satır indexlendi");
        }

        return new IndexMetadataDTO(basePath, indexPath, LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")), filesToIndex.size(), indexedLineCount);
    }

    /**
     * Dizini recursive olarak tarar ve indexlenecek dosyaları listeye ekler.
     * Indexleme işlemini yapmaz — sadece dosya toplama sorumluluğu vardır.
     */
    private void collectFilesForIndexing(File directory, List<File> result) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (shouldSearchDirectory(file.getName())) {
                    collectFilesForIndexing(file, result);
                }
            } else {
                if (shouldSearchFile(file.getName())) {
                    result.add(file);
                }
            }
        }
    }

    /**
     * Tek bir dosyayı index'ler
     */
    private void indexFile(IndexWriter writer, File file) {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lineNumber = i + 1;

                // Her satır için bir document oluştur
                Document doc = new Document();
                doc.add(new StringField("path", file.getAbsolutePath(), Field.Store.YES));
                doc.add(new StringField("lineNumber", String.valueOf(lineNumber), Field.Store.YES));
                doc.add(new TextField("content", line, Field.Store.YES));

                writer.addDocument(doc);
            }
        } catch (IOException e) {
            // Encoding sorunu olursa bu dosyayı atla
            System.err.println("Dosya indexlenemedi: " + file.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    /**
     * Index'te arama yapar
     */
    private List<SearchResultDTO> searchInIndex(String basePath, String indexPath, String searchString, String folderPattern, String parentFolderFilter) throws IOException {
        List<SearchResultDTO> results = new ArrayList<>();

        Path indexDir = Paths.get(indexPath);
        Directory directory = FSDirectory.open(indexDir);

        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // WildcardQuery ile substring arama: StandardAnalyzer camelCase kelimeleri
            // tek token'a düşürdüğünden (örn. "findAllByName" → "findallbyname"),
            // "findAllBy" gibi bir query token olan "findallby" ile eşleşmez.
            // *searchstring* wildcard pattern'i ile tüm substring'ler yakalanır.
            String lowerSearch = searchString.toLowerCase();
            // Wildcard özel karakterlerini escape et
            String escapedSearch = lowerSearch
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("?", "\\?")
                .replace("(", "\\(");

            // BooleanQuery Builder oluşturuyoruz (Birden fazla koşulu birleştirmek için)
            BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();

            // Aranan metni boşluklardan böl (Örn: "import", "his.module.personel...")
            String[] searchTerms = escapedSearch.split("\\s+");

            for (String term : searchTerms) {
                if (term.trim().isEmpty()) continue; // Boşlukları atla

                // ARTIK BURADA "if (wholeWord)" KONTROLÜ YAPMIYORUZ!
                // Lucene her zaman substring (*term*) arayıp potansiyel dökümanları bulmalı.
                // Çünkü kelimenin sağında solunda "-", "/", "\"" gibi karakterler bitişik olabilir.
                Query termQuery = new WildcardQuery(new Term("content", "*" + term + "*"));
                booleanQueryBuilder.add(termQuery, BooleanClause.Occur.MUST);

                // Her bir kelimenin o dökümanda geçmesi ZORUNLU (Occur.MUST = AND mantığı)
                booleanQueryBuilder.add(termQuery, BooleanClause.Occur.MUST);
            }

            Query query = booleanQueryBuilder.build();

            // Arama yap (maksimum 1.000.000 sonuç)
            TopDocs topDocs = searcher.search(query, 1000000);

            // Normalize edilmiş basePath (karşılaştırma için)
            String normalizedBasePath = basePath.replace("\\", "/");
            if (!normalizedBasePath.endsWith("/")) normalizedBasePath += "/";

            // Sonuçları işle
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                String path = doc.get("path");
                int lineNumber = Integer.parseInt(doc.get("lineNumber"));
                String content = doc.get("content");

                // FolderPattern filter varsa üst seviye klasörü kontrol et
                if (folderPattern != null && !"*".equals(folderPattern)) {
                    String normalizedPath = path.replace("\\", "/");
                    if (normalizedPath.startsWith(normalizedBasePath)) {
                        String relativePath = normalizedPath.substring(normalizedBasePath.length());
                        int slashIndex = relativePath.indexOf("/");
                        String topLevelDir = slashIndex > 0 ? relativePath.substring(0, slashIndex) : relativePath;
                        if (!matchesPattern(topLevelDir, folderPattern)) {
                            continue;
                        }
                    }
                }

                // Parent folder filter varsa kontrol et
                if (parentFolderFilter != null) {
                    File file = new File(path);
                    if (!matchesParentFolder(file, parentFolderFilter)) {
                        continue;
                    }
                }

                // Manuel substring (contains) matching - case sensitive/whole word kontrolü
                boolean matches = isFound(searchString, content);

                if (matches) {
                    results.add(new SearchResultDTO(path, lineNumber, content.trim(), extractProjectName(path, basePath)));
                }
            }
        }

        return results;
    }

    /**
     * Index metadata'yı JSON dosyasından yükler
     */
    private Map<String, IndexMetadataDTO> loadIndexMetadata() {
        File metadataFile = new File(INDEX_METADATA_FILE);
        if (!metadataFile.exists()) {
            return new HashMap<>();
        }

        try {
            return objectMapper.readValue(metadataFile, new TypeReference<>() {});
        } catch (IOException e) {
            System.err.println("Metadata dosyası okunamadı: " + e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Index metadata'yı JSON dosyasına kaydeder
     */
    private void saveIndexMetadata(Map<String, IndexMetadataDTO> metadataMap) {
        try {
            // Dizin yoksa oluştur
            File metadataFile = new File(INDEX_METADATA_FILE);
            File parentDir = metadataFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // JSON'a yaz
            objectMapper.writeValue(metadataFile, metadataMap);
            System.out.println("Index metadata kaydedildi: " + INDEX_METADATA_FILE);
        } catch (IOException e) {
            System.err.println("Metadata dosyası kaydedilemedi: " + e.getMessage());
        }
    }

    public void setWholeWord(boolean wholeWord) {
        this.wholeWord = wholeWord;
    }

    public void setSearchInAllFiles(boolean searchInAllFiles) {
        this.searchInAllFiles = searchInAllFiles;
    }

    public void setUseIndex(boolean useIndex) {
        this.useIndex = useIndex;
    }

    public void setTestIncluded(boolean testIncluded) {
        this.testIncluded = testIncluded;
    }

}

