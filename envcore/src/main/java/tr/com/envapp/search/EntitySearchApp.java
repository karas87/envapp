package tr.com.envapp.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Entity Search Uygulaması
 *
 * Bu uygulama verilen path'teki projelerde belirtilen string'i arar
 * ve kullanıldığı yerlerin isimlerini çıktı olarak verir.
 *
 * Kullanım:
 *   java -jar EntitySearch.jar <path> <aranacak_string> [klasör_pattern]
 *   java -jar EntitySearch.jar (interaktif mod)
 *
 * Örnek:
 *   java -jar EntitySearch.jar "C:\Projects" "CustomerEntity"
 *   java -jar EntitySearch.jar "C:\Projects" "CustomerEntity" "his-api-*"
 *   java -jar EntitySearch.jar "C:\Projects" "CustomerEntity" "*-service"
 */
public class EntitySearchApp {

    private static final String BANNER =
        "╔═══════════════════════════════════════════════════════════╗\n" +
        "║                   ENTITY SEARCH TOOL                      ║\n" +
        "║         Proje Dosyalarında Metin Arama Aracı             ║\n" +
        "╚═══════════════════════════════════════════════════════════╝\n";

    public static void main(String[] args) {
        // Ctrl+C ile düzgün çıkış için shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n\n⛔ Uygulama kullanıcı tarafından durduruldu.");
            System.out.println("Güle güle! 👋");
        }));

        System.out.println(BANNER);
        System.out.println("💡 İpucu: Çıkmak için Ctrl+C tuşlarına basın.\n");

        String path;
        String searchString;
        String folderPattern;

        if (args.length >= 2) {
            // Komut satırından parametreler alındı
            path = args[0];
            searchString = args[1];
            folderPattern = args.length >= 3 ? args[2] : "*";
        } else {
            // İnteraktif mod
            Scanner scanner = new Scanner(System.in);

            System.out.print("Arama yapılacak klasör yolu: ");
            path = scanner.nextLine().trim();

            System.out.print("Aranacak string: ");
            searchString = scanner.nextLine().trim();

            System.out.print("Klasör pattern (* = tümü, his-api-* = his-api ile başlayanlar): ");
            folderPattern = scanner.nextLine().trim();
            if (folderPattern.isEmpty()) {
                folderPattern = "*";
            }
        }

        if (path.isEmpty() || searchString.isEmpty()) {
            System.err.println("Hata: Path ve aranacak string boş olamaz!");
            System.exit(1);
        }

        // Arama işlemini başlat
        performSearch(path, searchString, folderPattern);
    }

    private static void performSearch(String path, String searchString, String folderPattern) {
        System.out.println("\n🔍 Arama başlatılıyor...");
        System.out.println("   Path: " + path);
        System.out.println("   Aranan: \"" + searchString + "\"");
        System.out.println("   Klasör Pattern: " + folderPattern);
        System.out.println(repeatString("─", 60));

        EntitySearchService searchService = new EntitySearchService();


        // Tarama sırasında klasör durumunu göster
        System.out.println("\n📂 TARAMA DURUMU:");
        searchService.setStatusListener((directoryName, foundCount) -> {
            if (foundCount > 0) {
                System.out.println("   ✓ " + directoryName + " → " + foundCount + " sonuç bulundu");
            } else {
                System.out.println("   ○ " + directoryName);
            }
        });

        long startTime = System.currentTimeMillis();

        List<SearchResult> results = searchService.search(path, searchString, folderPattern);

        System.out.println();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        if (results.isEmpty()) {
            System.out.println("\n⚠️  Sonuç bulunamadı!");
        } else {
            printResults(results);
        }

        System.out.println("\n" + repeatString("─", 60));
        System.out.printf("✅ Arama tamamlandı! %d sonuç bulundu. (Süre: %d ms)%n",
            results.size(), duration);
    }

    private static void printResults(List<SearchResult> results) {
        System.out.println("\n📁 BULUNAN SONUÇLAR:\n");

        // Dosyalara göre grupla
        Map<String, List<SearchResult>> groupedByFile = results.stream()
            .collect(Collectors.groupingBy(SearchResult::getFilePath));

        int fileCount = 0;
        for (Map.Entry<String, List<SearchResult>> entry : groupedByFile.entrySet()) {
            fileCount++;
            String filePath = entry.getKey();
            List<SearchResult> fileResults = entry.getValue();

            System.out.println("┌─ Dosya #" + fileCount + ": " + filePath);
            System.out.println("│  Toplam " + fileResults.size() + " eşleşme bulundu");

            for (SearchResult result : fileResults) {
                String lineContent = result.getLineContent().trim();
                // Uzun satırları kısalt
                if (lineContent.length() > 100) {
                    lineContent = lineContent.substring(0, 97) + "...";
                }
                System.out.printf("│    ├─ Satır %d: %s%n", result.getLineNumber(), lineContent);
            }
            System.out.println("└" + repeatString("─", 59));
            System.out.println();
        }

        // Özet bilgi
        printSummary(groupedByFile);
    }

    private static void printSummary(Map<String, List<SearchResult>> groupedByFile) {
        System.out.println("\n📊 ÖZET:");
        System.out.println("   Toplam dosya sayısı: " + groupedByFile.size());

        int totalMatches = groupedByFile.values().stream()
            .mapToInt(List::size)
            .sum();
        System.out.println("   Toplam eşleşme sayısı: " + totalMatches);

        // Dosya türlerine göre dağılım
        Map<String, Long> extensionCount = new HashMap<>();
        for (String filePath : groupedByFile.keySet()) {
            String extension = getFileExtension(filePath);
            extensionCount.merge(extension, 1L, Long::sum);
        }

        System.out.println("\n   Dosya türlerine göre dağılım:");
        extensionCount.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
            .forEach(entry ->
                System.out.printf("      • %s: %d dosya%n", entry.getKey(), entry.getValue()));
    }

    private static String getFileExtension(String filePath) {
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filePath.length() - 1) {
            return filePath.substring(lastDot).toLowerCase();
        }
        return "(uzantısız)";
    }

    private static String repeatString(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }
}

