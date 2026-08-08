package tr.com.envapp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class HisExceptionDTO {

  private String errorType;
  private String errorCode;
  private String description;
  private String errorId;
  private String message;
  private String stackTrace;

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getErrorId() {
    return errorId;
  }

  public void setErrorId(String errorId) {
    this.errorId = errorId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }

  @Override
  public String toString() {
    return "Error Type: " + errorType + "\n"
        + "Error Code: " + errorCode + "\n"
        + "Description: " + description + "\n"
        + "Error ID: " + errorId + "\n"
        + "Message: " + message + "\n\n"
        + "Stack Trace:\n" + stackTrace;
  }
}
