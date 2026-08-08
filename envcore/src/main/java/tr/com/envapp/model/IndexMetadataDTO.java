package tr.com.envapp.model;

public class IndexMetadataDTO {

  private String basePath;
  private String indexPath;
  private String indexedAt;
  private Integer indexedFileCount = 0;
  private Integer indexedLineCount = 0;

  public IndexMetadataDTO() {
  }

  public IndexMetadataDTO(String basePath, String indexPath, String indexedAt, Integer indexedFileCount, Integer indexedLineCount) {
    this.basePath = basePath;
    this.indexPath = indexPath;
    this.indexedAt = indexedAt;
    this.indexedFileCount = indexedFileCount;
    this.indexedLineCount = indexedLineCount;
  }

  public String getBasePath() {
    return basePath;
  }

  public void setBasePath(String basePath) {
    this.basePath = basePath;
  }

  public String getIndexPath() {
    return indexPath;
  }

  public void setIndexPath(String indexPath) {
    this.indexPath = indexPath;
  }

  public String getIndexedAt() {
    return indexedAt;
  }

  public void setIndexedAt(String indexedAt) {
    this.indexedAt = indexedAt;
  }

  public Integer getIndexedFileCount() {
    return indexedFileCount;
  }

  public void setIndexedFileCount(Integer indexedFileCount) {
    this.indexedFileCount = indexedFileCount;
  }

  public Integer getIndexedLineCount() {
    return indexedLineCount;
  }

  public void setIndexedLineCount(Integer indexedLineCount) {
    this.indexedLineCount = indexedLineCount;
  }
}

