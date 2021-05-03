package dev.sanda.apifi.service.graphql_subcriptions.pubsub.redis_pubsub;

import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.data.redis.connection.*;
import org.springframework.stereotype.Component;

@Component
public class LoadDefaultRedisConfig implements Condition {

  @Autowired(required = false)
  private RedisConfiguration redisConfiguration;

  @Override
  public boolean matches(
    @NonNull ConditionContext conditionContext,
    @NonNull AnnotatedTypeMetadata annotatedTypeMetadata
  ) {
    return (
      System.getenv().containsKey("REDIS_PUBSUB_URL") ||
      redisConfiguration != null
    );
  }
}
