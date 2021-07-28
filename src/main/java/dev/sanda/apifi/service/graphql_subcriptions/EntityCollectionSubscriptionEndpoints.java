package dev.sanda.apifi.service.graphql_subcriptions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EntityCollectionSubscriptionEndpoints {
  ON_ASSOCIATE_WITH("AssociateWith"),
  ON_REMOVE_FROM("RemoveFrom"),

  NONE("None");

  private final String stringValue;
}
