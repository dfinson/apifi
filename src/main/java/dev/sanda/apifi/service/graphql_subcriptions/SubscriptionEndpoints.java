package dev.sanda.apifi.service.graphql_subcriptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SubscriptionEndpoints {
  ON_CREATE("Create"),
  ON_UPDATE("Update"),
  ON_DELETE("Delete"),
  ON_ARCHIVE("Archive"),
  ON_DE_ARCHIVE("DeArchive");

  private final String stringValue;
}
