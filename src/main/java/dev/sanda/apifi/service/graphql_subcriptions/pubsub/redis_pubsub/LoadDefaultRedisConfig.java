package dev.sanda.apifi.service.graphql_subcriptions.pubsub.redis_pubsub;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;

@Component
public class LoadDefaultRedisConfig implements Condition {

  // redis config properties
  @Getter(AccessLevel.NONE)
  @Value("${apifi.subscriptions.redis-pubsub-url:@null}")
  private String redisPubSubUrlConfigProp;

  @Getter(AccessLevel.NONE)
  @Value("${REDIS_PUBSUB_URL:@null}")
  private String redisPubSubUrlEnvVar;

  public String getRedisPubSubUrl() {
    return redisPubSubUrlEnvVar != null
      ? redisPubSubUrlEnvVar
      : redisPubSubUrlConfigProp;
  }

  @Override
  public boolean matches(
    @NonNull ConditionContext conditionContext,
    @NonNull AnnotatedTypeMetadata annotatedTypeMetadata
  ) {
    return getRedisPubSubUrl() != null;
  }
}
