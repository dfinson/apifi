package dev.sanda.apifi.service.graphql_config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
public class GraphQLSubscriptionSupport {

  @Getter
  @Setter
  private Boolean hasSubscriptions;
}
