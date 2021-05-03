package dev.sanda.apifi.service.graphql_subcriptions.pubsub.redis_pubsub;

import static dev.sanda.datafi.DatafiStaticUtils.toSingular;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import java.util.ArrayList;
import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;

@Slf4j
@Data
public class RedisMessageSubscriber implements MessageListener {

  private final ChannelTopic topic;
  private final RedisPubSubMessagingService redisPubSubMessagingService;

  private Class targetEntityType;

  public RedisMessageSubscriber(
    String topic,
    RedisPubSubMessagingService redisPubSubMessagingService,
    ReflectionCache reflectionCache
  ) {
    this.topic = new ChannelTopic(topic);
    this.redisPubSubMessagingService = redisPubSubMessagingService;
    this.targetEntityType = getTargetEntityType(reflectionCache);
  }

  private Class getTargetEntityType(ReflectionCache reflectionCache) {
    try {
      val topicPrefix = topic
        .getTopic()
        .substring(0, topic.getTopic().indexOf("/"))
        .replaceFirst("\\([a-zA-z_][a-zA-Z_0-9]*=[^)]+\\)", "");
      return reflectionCache.getEntitiesCache().containsKey(topicPrefix)
        ? reflectionCache.getEntitiesCache().get(topicPrefix).getClazz()
        : reflectionCache
          .getEntitiesCache()
          .get(toSingular(topicPrefix))
          .getClazz();
    } catch (NullPointerException npe) {
      throw new RuntimeException(
        "Cannot determine target entity type for subscription topic \"" +
        topic +
        "\""
      );
    }
  }

  @Override
  public void onMessage(@NonNull Message message, byte[] pattern) {
    log.info(
      "received message \"{}\" on topic \"{}\"",
      message,
      topic.getTopic()
    );
    redisPubSubMessagingService.handleDataPayload(
      topic.getTopic(),
      deserializePayload(message)
    );
  }

  private Object deserializePayload(Message message) {
    try {
      val mapper = new ObjectMapper();
      val tree = mapper.readTree(getSerializedPayload(message));
      if (tree.isArray()) {
        val resultList = new ArrayList<>();
        for (val obj : tree) resultList.add(
          mapper.convertValue(obj, targetEntityType)
        );
        return resultList;
      } else return mapper.convertValue(tree, targetEntityType);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
  }

  private String getSerializedPayload(Message message) {
    val messageString = message.toString();
    val isArray =
      messageString.contains("[") &&
      messageString.indexOf("[") < messageString.indexOf("{");
    return isArray
      ? messageString.substring(messageString.indexOf("["))
      : messageString.substring(messageString.indexOf("{"));
  }
}
