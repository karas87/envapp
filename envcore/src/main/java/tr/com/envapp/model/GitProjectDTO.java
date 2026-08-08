package tr.com.envapp.model;

import java.io.File;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class GitProjectDTO {

  private final File file;
  private final BooleanProperty selected = new SimpleBooleanProperty(false);
  private final StringProperty currentBranch = new SimpleStringProperty("-");
  private final StringProperty version = new SimpleStringProperty("-");
  private final StringProperty projectType = new SimpleStringProperty("-");
  private final StringProperty status = new SimpleStringProperty("-");

  public GitProjectDTO(File file) {
    this.file = file;
  }

  public File getFile() {
    return file;
  }

  public BooleanProperty selectedProperty() {
    return selected;
  }

  public boolean isSelected() {
    return selected.get();
  }

  public void setSelected(boolean selected) {
    this.selected.set(selected);
  }

  public StringProperty currentBranchProperty() {
    return currentBranch;
  }

  public String getCurrentBranch() {
    return currentBranch.get();
  }

  public void setCurrentBranch(String currentBranch) {
    this.currentBranch.set(currentBranch);
  }

  public StringProperty versionProperty() {
    return version;
  }

  public String getVersion() {
    return version.get();
  }

  public void setVersion(String version) {
    this.version.set(version);
  }

  public StringProperty projectTypeProperty() {
    return projectType;
  }

  public String getProjectType() {
    return projectType.get();
  }

  public void setProjectType(String projectType) {
    this.projectType.set(projectType);
  }

  public StringProperty statusProperty() {
    return status;
  }

  public String getStatus() {
    return status.get();
  }

  public void setStatus(String status) {
    this.status.set(status);
  }
}
