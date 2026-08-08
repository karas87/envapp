package tr.com.envapp.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TokenResponseDTO {

  private String hazelcastSessionId;
  private String tokenResult;

  public TokenResponseDTO(String hazelcastSessionId, String tokenResult) {
    this.hazelcastSessionId = hazelcastSessionId;
    this.tokenResult = tokenResult;
  }

  public String getHazelcastSessionId() {
    return hazelcastSessionId;
  }

  public void setHazelcastSessionId(String hazelcastSessionId) {
    this.hazelcastSessionId = hazelcastSessionId;
  }

  public String getTokenResult() {
    return tokenResult;
  }

  public void setTokenResult(String tokenResult) {
    this.tokenResult = tokenResult;
  }
}
