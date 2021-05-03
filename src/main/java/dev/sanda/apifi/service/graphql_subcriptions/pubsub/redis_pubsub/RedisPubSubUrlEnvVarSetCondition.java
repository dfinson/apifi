package dev.sanda.apifi.service.graphql_subcriptions.pubsub.redis_pubsub;

import lombok.NonNull;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class RedisPubSubUrlEnvVarSetCondition implements Condition {

  @Override
  public boolean matches(
    @NonNull ConditionContext conditionContext,
    @NonNull AnnotatedTypeMetadata annotatedTypeMetadata
  ) {
    return System.getenv().containsKey("REDIS_PUBSUB_URL");
  }
}
