package dev.sanda.apifi.service.graphql_subcriptions.pubsub.redis_pubsub;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(DefaultRedisConfig.class)
@AllArgsConstructor(onConstructor_ = @Autowired)
public class RedisMessagePublisher implements MessagePublisher {

  private final RedisTemplate<String, Object> redisTemplate;

  @Override
  public synchronized void publish(String topic, String message) {
    log.info("publishing message \"{}\" on topic \"{}\"", message, topic);
    redisTemplate.convertAndSend(topic, message);
  }
}
