package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import dev.sanda.apifi.service.graphql_subcriptions.pubsub.redis_pubsub.RedisPubSubMessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PubSubMessagingServiceFactory {

  private final CustomPubSubMessagingService customPubSubMessagingService;
  private final RedisPubSubMessagingService redisPubSubMessagingService;

  @Autowired
  public PubSubMessagingServiceFactory(
    @Autowired(
      required = false
    ) CustomPubSubMessagingService customPubSubMessagingService,
    @Autowired(
      required = false
    ) RedisPubSubMessagingService redisPubSubMessagingService
  ) {
    this.customPubSubMessagingService = customPubSubMessagingService;
    this.redisPubSubMessagingService = redisPubSubMessagingService;
  }

  @Bean
  public PubSubMessagingService pubSubMessagingService() {
    if (customPubSubMessagingService != null) {
      return customPubSubMessagingService;
    } else if (redisPubSubMessagingService != null) {
      return redisPubSubMessagingService;
    } else {
      return new InMemoryPubSubMessagingService();
    }
  }
}
