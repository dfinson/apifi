package dev.sanda.apifi.service.graphql_config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "apifi.subscriptions.ws-allowed-origins")
public class WsAllowedOriginsConfig {

  @Getter
  private List<String> allowedOrigins = new ArrayList<>();

  public String[] originsArray() {
    return allowedOrigins.isEmpty()
      ? new String[] { "*" }
      : allowedOrigins.stream().toArray(String[]::new);
  }
}
