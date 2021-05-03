package dev.sanda.apifi.service.graphql_subcriptions.pubsub.redis_pubsub;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubMessagingService;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.PubSubTopicHandler;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnBean(DefaultRedisConfig.class)
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class RedisPubSubMessagingService implements PubSubMessagingService {

  private final RedisMessagePublisher redisPublisher;
  private final RedisMessageListenerContainer redisListeners;
  private final ReflectionCache reflectionCache;

  private final Map<String, Map<String, PubSubTopicHandler>> topicHandlers = new ConcurrentHashMap<>();
  private final Map<String, MessageListener> topicListeners = new ConcurrentHashMap<>();

  @Transactional
  public void handleDataPayload(String topic, Object deserializedPayload) {
    synchronized (topicHandlers.get(topic)) {
      topicHandlers
        .get(topic)
        .values()
        .forEach(
          handler -> handler.handleDataInTransaction(deserializedPayload)
        );
    }
  }

  @Override
  @SneakyThrows
  public void publishToTopic(String topic, Object payload) {
    redisPublisher.publish(
      topic,
      publicationMapper().writeValueAsString(payload)
    );
  }

  private ObjectMapper publicationMapper() {
    val mapper = new ObjectMapper();
    mapper.disable(
      MapperFeature.AUTO_DETECT_CREATORS,
      MapperFeature.AUTO_DETECT_GETTERS,
      MapperFeature.AUTO_DETECT_IS_GETTERS
    );
    mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    return mapper;
  }

  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

  @Override
  public void registerTopicHandler(
    String topic,
    PubSubTopicHandler messageHandler
  ) {
    lock.writeLock().lock();
    log.info("registering handler for topic \"{}\"", topic);
    topicHandlers.putIfAbsent(topic, new ConcurrentHashMap<>());
    topicHandlers.get(topic).put(messageHandler.getId(), messageHandler);
    if (!topicListeners.containsKey(topic)) {
      val listener = new RedisMessageSubscriber(topic, this, reflectionCache);
      topicListeners.put(topic, listener);
      try {
        redisListeners.addMessageListener(listener, listener.getTopic());
      } catch (Exception e) {
        log.info(e.toString());
      }
    }
    lock.writeLock().unlock();
  }

  @Override
  public void removeTopicHandler(String topic, String handlerId) {
    lock.readLock().lock();
    log.info(
      "removing handler with id \"{}\" from topic \"{}\"",
      handlerId,
      topic
    );
    topicHandlers.get(topic).remove(handlerId);
    if (topicHandlers.get(topic).isEmpty()) {
      log.info(
        "no listeners left on topic \"{}\", unsubscribing from redis channel \"{}\"",
        topic,
        topic
      );
      redisListeners.removeMessageListener(topicListeners.get(topic));
      topicListeners.remove(topic);
    }
    lock.readLock().unlock();
  }
}
