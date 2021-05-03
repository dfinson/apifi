package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.messages;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationMessage {

  private String id;
  private String type;

  public OperationMessage(String type) {
    this.type = type;
  }
}
