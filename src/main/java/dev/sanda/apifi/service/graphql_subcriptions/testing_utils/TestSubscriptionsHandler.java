package dev.sanda.apifi.service.graphql_subcriptions.testing_utils;

import static dev.sanda.apifi.utils.ApifiStaticUtils.inQuotes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.sanda.apifi.dto.GraphQLRequest;
import dev.sanda.apifi.service.graphql_config.GraphQLRequestExecutor;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.AsyncExecutorService;
import dev.sanda.apifi.utils.ConfigValues;
import dev.sanda.datafi.reflection.cached_type_info.CachedEntityTypeInfo;
import dev.sanda.datafi.reflection.runtime_services.CollectionsTypeResolver;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import io.leangen.graphql.annotations.GraphQLSubscription;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class TestSubscriptionsHandler {

  private final AsyncExecutorService asyncExecutorService;
  private final PlatformTransactionManager transactionManager;
  private final CollectionsTypeResolver collectionsTypeResolver;
  private final ReflectionCache reflectionCache;
  private final GraphQLRequestExecutor requestExecutor;
  private final ConfigValues configValues;
  private final DataManager dataManager;

  public TestSubscriberImplementation handleSubscriptionRequest(
    Method subscriptionResolver,
    Class targetReturnType,
    Object... args
  ) {
    val request = getGraphQLRequest(
      subscriptionResolver,
      targetReturnType,
      args
    );
    val executionResult = getExecutionResult(request);
    return handleSubscription(executionResult);
  }

  private GraphQLRequest getGraphQLRequest(
    Method subscriptionResolver,
    Class targetType,
    Object[] args
  ) {
    return new GraphQLRequestBuilder()
      .setCollectionsTypeResolver(collectionsTypeResolver)
      .setTargetReturnType(targetType)
      .setEntityTypes(
        reflectionCache
          .getEntitiesCache()
          .values()
          .stream()
          .map(CachedEntityTypeInfo::getClazz)
          .collect(Collectors.toSet())
      )
      .setArgs(args)
      .setSubscriptionResolver(subscriptionResolver)
      .build();
  }

  private ExecutionResult getExecutionResult(GraphQLRequest request) {
    return requestExecutor
      .getGraphQLService()
      .getGraphQLInstance()
      .execute(
        ExecutionInput
          .newExecutionInput()
          .query(request.getQuery())
          .variables(request.getVariables())
          .build()
      );
  }

  private TestSubscriberImplementation handleSubscription(
    ExecutionResult result
  ) {
    val publisher = (Publisher<ExecutionResult>) result.getData();
    val subscriber = new TestSubscriberImplementation(
      dataManager,
      asyncExecutorService,
      transactionManager,
      configValues,
      reflectionCache
    );
    publisher.subscribe(subscriber);
    addSubscription(subscriber, publisher);
    return subscriber;
  }

  private final Map<String, TestSubscription> subscriptions = new ConcurrentHashMap<>();

  private void addSubscription(
    TestSubscriberImplementation subscriber,
    Publisher<ExecutionResult> publisher
  ) {
    val id = UUID.randomUUID().toString();
    subscriptions.put(id, new TestSubscription(id, subscriber, publisher));
  }

  private static class GraphQLRequestBuilder {

    public GraphQLRequestBuilder setTargetReturnType(Class targetReturnType) {
      this.targetReturnType = targetReturnType;
      return this;
    }

    public GraphQLRequestBuilder setEntityTypes(Set<Class> entityTypes) {
      this.entityTypes = entityTypes;
      return this;
    }

    public GraphQLRequestBuilder setCollectionsTypeResolver(
      CollectionsTypeResolver collectionsTypeResolver
    ) {
      this.collectionsTypeResolver = collectionsTypeResolver;
      return this;
    }

    public GraphQLRequestBuilder setSubscriptionResolver(
      Method subscriptionResolver
    ) {
      this.subscriptionResolver = subscriptionResolver;
      this.subscriptionName =
        subscriptionResolver
            .getAnnotation(GraphQLSubscription.class)
            .name()
            .equals("")
          ? subscriptionResolver.getName()
          : subscriptionResolver
            .getAnnotation(GraphQLSubscription.class)
            .name();
      return this;
    }

    public GraphQLRequestBuilder setArgs(Object[] args) {
      this.args = args;
      return this;
    }

    private String subscriptionName;
    private Class targetReturnType;
    private Set<Class> entityTypes;
    private CollectionsTypeResolver collectionsTypeResolver;
    private Method subscriptionResolver;
    private Object[] args;

    @SneakyThrows
    public GraphQLRequest build() {
      val queryString =
        "{ " +
        "\"query\"" +
        ": \"" +
        "subscription " +
        subscriptionName +
        varsDef() +
        " { " +
        subscriptionName +
        varsArgs() +
        expectedResultString() +
        "\", " +
        varsVals() +
        " }";
      val objectNode = (ObjectNode) new ObjectMapper().readTree(queryString);
      val request = GraphQLRequest.fromObjectNode(objectNode);
      request.setQuery(request.getQuery() + "}");
      return request;
    }

    @SneakyThrows
    private String varsVals() {
      val parameters = subscriptionResolver.getParameters();
      assert parameters.length == args.length;
      val builder = new StringBuilder();
      for (int i = 0; i < parameters.length; i++) {
        builder
          .append(inQuotes(parameters[i].getName()))
          .append(": ")
          .append(paramArgValue(args[i]))
          .append(", ");
      }
      builder.setLength(builder.length() - 2);
      return ("\"variables\"" + ": { " + builder + " }");
    }

    private String paramArgValue(Object arg) throws JsonProcessingException {
      val mapper = new ObjectMapper();
      if (arg instanceof Collection) {
        val items =
          ((Collection) arg).stream()
            .map(
              item -> {
                try {
                  return mapper.writeValueAsString(item);
                } catch (JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              }
            )
            .collect(Collectors.joining(", "));
        return ("[" + items + "]");
      } else return mapper.writeValueAsString(arg);
    }

    private String varsArgs() {
      val parameters = subscriptionResolver.getParameters();
      val builder = new StringBuilder();
      for (int i = 0; i < parameters.length; i++) {
        Parameter parameter = parameters[i];
        builder
          .append(parameter.getName())
          .append(": $")
          .append(parameter.getName());
        if (!(i + 1 == parameters.length)) builder.append(", ");
      }
      return "(" + builder + ")";
    }

    private String expectedResultString() {
      return (
        "{ " +
        fieldsMapToSelectionGraph(
          getFieldsMap(targetReturnType, new HashSet<>())
        ) +
        " }"
      );
    }

    private Map<Field, Object> getFieldsMap(Class type, Set<Class> visited) {
      visited.add(type);
      val result = new LinkedHashMap<Field, Object>();
      for (Field field : type.getDeclaredFields()) {
        val fieldType = getFieldType(type, field);
        val isEntityType = isEntityType(type, field);
        if (isEntityType && !visited.contains(fieldType)) {
          result.put(field, getFieldsMap(fieldType, visited));
        } else if (!isEntityType) {
          result.put(field, true);
        }
      }
      return result;
    }

    private String fieldsMapToSelectionGraph(Map<Field, Object> fieldsMap) {
      val builder = new StringBuilder();
      fieldsMap.forEach(
        (field, value) -> {
          if (value instanceof Boolean) builder
            .append(" ")
            .append(field.getName())
            .append(" "); else builder
            .append(field.getName())
            .append(" { ")
            .append(fieldsMapToSelectionGraph((Map<Field, Object>) value))
            .append(" } ");
        }
      );
      return builder.toString();
    }

    private boolean isEntityType(Class type, Field field) {
      return entityTypes.contains(getFieldType(type, field));
    }

    private Class getFieldType(Class type, Field field) {
      return Collection.class.isAssignableFrom(field.getType())
        ? collectionsTypeResolver.resolveFor(
          type.getSimpleName() + "." + field.getName()
        )
        : field.getType();
    }

    private String varsDef() {
      val builder = new StringBuilder();
      val parameters = subscriptionResolver.getParameters();
      for (int i = 0; i < parameters.length; i++) {
        val parameter = parameters[i];
        builder
          .append("$")
          .append(getParamTypeInputName(parameter.getName()))
          .append(": ")
          .append(
            (
              Collection.class.isAssignableFrom(parameter.getType())
                ? "[" +
                getParamTypeInputName(targetReturnType.getSimpleName()) +
                "]" //TODO - adapt for customization
                : getParamTypeInputName(parameter.getType().getSimpleName())
            )
          );
        if (!(i + 1 == parameters.length)) builder.append(", ");
      }
      return "(" + builder + ")";
    }

    private String getParamTypeInputName(String name) {
      if (
        entityTypes
          .stream()
          .map(Class::getSimpleName)
          .collect(Collectors.toSet())
          .contains(name)
      ) return name + "Input";
      return name;
    }
  }
}
