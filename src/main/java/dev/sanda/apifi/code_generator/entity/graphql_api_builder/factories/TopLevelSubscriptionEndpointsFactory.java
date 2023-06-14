package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import com.squareup.javapoet.*;
import dev.sanda.apifi.annotations.WithSubscriptionEndpoints;
import dev.sanda.apifi.code_generator.client.ApifiClientFactory;
import dev.sanda.apifi.code_generator.client.GraphQLQueryBuilder;
import dev.sanda.apifi.code_generator.client.SubscriptionObservableType;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLSubscription;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import javax.lang.model.element.TypeElement;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.INSTANCE;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.SUBSCRIPTION;
import static dev.sanda.apifi.code_generator.client.SubscriptionObservableType.ENTITY_TYPE;
import static dev.sanda.apifi.code_generator.client.SubscriptionObservableType.LIST_TO_OBSERVE;
import static dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints.*;
import static dev.sanda.apifi.utils.ApifiStaticUtils.listOf;
import static dev.sanda.datafi.DatafiStaticUtils.toPlural;
import static javax.lang.model.element.Modifier.PUBLIC;

public class TopLevelSubscriptionEndpointsFactory {

  private final GraphQLApiBuilderParams params;
  private final Map<String, List<AnnotationSpec>> methodLevelSecuritiesMap;
  private final String entityName;
  private final EntityGraphQLApiSpec apiSpec;

  public TopLevelSubscriptionEndpointsFactory(GraphQLApiBuilderParams params) {
    this.params = params;
    this.methodLevelSecuritiesMap = params.getMethodLevelSecuritiesMap();
    this.entityName = params.getEntityName();
    this.apiSpec = params.getApiSpec();
  }

  public List<MethodSpec> generateSubscriptionEndpoints() {
    WithSubscriptionEndpoints subscriptionEndpoints = apiSpec.getAnnotation(
      WithSubscriptionEndpoints.class
    );
    if (subscriptionEndpoints == null) {
      return Collections.emptyList();
    }

    Set<SubscriptionEndpoints> subscriptionEndpointsSet = Arrays
      .stream(subscriptionEndpoints.value())
      .collect(Collectors.toSet());

    List<MethodSpec> result = new ArrayList<>();
    Map<String, TypeElement> entitiesMap = params.getEntitiesMap();
    ApifiClientFactory clientFactory = params.getClientFactory();
    String entityName = this.entityName;

    for (SubscriptionEndpoints endpointType : subscriptionEndpointsSet) {
      GraphQLQueryBuilder clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        INSTANCE,
        entityName
      );
      clientQueryBuilder.setSubscriptionObservableType(
        getObservableType(endpointType)
      );

      MethodSpec subscriptionMethod = genSubscriptionMethod(
        clientQueryBuilder,
        endpointType
      );
      result.add(subscriptionMethod);
      clientFactory.addQuery(clientQueryBuilder);
    }

    return result;
  }

  private MethodSpec genSubscriptionMethod(
    GraphQLQueryBuilder clientQueryBuilder,
    SubscriptionEndpoints endpointType
  ) {
    switch (endpointType) {
      case ON_CREATE:
        return genOnCreateSubscription(clientQueryBuilder);
      case ON_UPDATE:
        return genOnUpdateSubscription(clientQueryBuilder);
      case ON_DELETE:
        return genOnDeleteSubscription(clientQueryBuilder);
      case ON_ARCHIVE:
        return genOnArchiveSubscription(clientQueryBuilder);
      case ON_DE_ARCHIVE:
        return genOnDeArchiveSubscription(clientQueryBuilder);
      default:
        throw new IllegalArgumentException(
          "Unsupported endpoint type: " + endpointType
        );
    }
  }

  private SubscriptionObservableType getObservableType(
    SubscriptionEndpoints endpointType
  ) {
    return endpointType == ON_CREATE ? ENTITY_TYPE : LIST_TO_OBSERVE;
  }

  private MethodSpec genOnCreateSubscription(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val subscriptionName = "on" + toPlural(entityName) + "Created";
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(subscriptionName)
      .addModifiers(PUBLIC)
      .addAnnotation(GraphQLSubscription.class)
      .addParameter(subscriptionBackPressureStrategyParam())
      .addStatement(
        "return apiLogic.onCreateSubscription(backPressureStrategy)"
      )
      .returns(
        ParameterizedTypeName.get(
          ClassName.get(Flux.class),
          listOf(apiSpec.getElement())
        )
      );
    if (
      methodLevelSecuritiesMap.containsKey(ON_CREATE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(ON_CREATE.toString())
    );
    clientQueryBuilder.setQueryType(SUBSCRIPTION);
    clientQueryBuilder.setQueryName(subscriptionName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", "BaseSubscriptionRequestInput<T>");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genOnUpdateSubscription(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val subscriptionName = "on" + entityName + "Updated";
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(subscriptionName)
      .addModifiers(PUBLIC)
      .addAnnotation(GraphQLSubscription.class)
      .addParameter(
        ParameterSpec
          .builder(listOf(ClassName.get(apiSpec.getElement())), "toObserve")
          .build()
      )
      .addParameter(subscriptionBackPressureStrategyParam())
      .addStatement(
        "return apiLogic.onUpdateSubscription(toObserve, backPressureStrategy)"
      )
      .returns(
        ParameterizedTypeName.get(
          ClassName.get(Flux.class),
          ClassName.get(apiSpec.getElement())
        )
      );
    if (
      methodLevelSecuritiesMap.containsKey(ON_UPDATE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(ON_UPDATE.toString())
    );
    clientQueryBuilder.setQueryType(SUBSCRIPTION);
    clientQueryBuilder.setQueryName(subscriptionName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", "SubscriptionRequestInput<T>");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genOnDeleteSubscription(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val subscriptionName = "on" + entityName + "Deleted";
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(subscriptionName)
      .addModifiers(PUBLIC)
      .addAnnotation(GraphQLSubscription.class)
      .addParameter(
        ParameterSpec
          .builder(listOf(ClassName.get(apiSpec.getElement())), "toObserve")
          .build()
      )
      .addParameter(subscriptionBackPressureStrategyParam())
      .addStatement(
        "return apiLogic.onDeleteSubscription(toObserve, backPressureStrategy)"
      )
      .returns(
        ParameterizedTypeName.get(
          ClassName.get(Flux.class),
          ClassName.get(apiSpec.getElement())
        )
      );
    if (
      methodLevelSecuritiesMap.containsKey(ON_DELETE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(ON_DELETE.toString())
    );
    clientQueryBuilder.setQueryType(SUBSCRIPTION);
    clientQueryBuilder.setQueryName(subscriptionName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", "SubscriptionRequestInput<T>");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genOnArchiveSubscription(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val subscriptionName = "on" + entityName + "Archived";
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(subscriptionName)
      .addModifiers(PUBLIC)
      .addAnnotation(GraphQLSubscription.class)
      .addParameter(
        ParameterSpec
          .builder(listOf(ClassName.get(apiSpec.getElement())), "toObserve")
          .build()
      )
      .addParameter(subscriptionBackPressureStrategyParam())
      .addStatement(
        "return apiLogic.onArchiveSubscription(toObserve, backPressureStrategy)"
      )
      .returns(
        ParameterizedTypeName.get(
          ClassName.get(Flux.class),
          ClassName.get(apiSpec.getElement())
        )
      );
    if (
      methodLevelSecuritiesMap.containsKey(ON_ARCHIVE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(ON_ARCHIVE.toString())
    );
    clientQueryBuilder.setQueryType(SUBSCRIPTION);
    clientQueryBuilder.setQueryName(subscriptionName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", "SubscriptionRequestInput<T>");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genOnDeArchiveSubscription(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val subscriptionName = "on" + entityName + "DeArchived";
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(subscriptionName)
      .addModifiers(PUBLIC)
      .addAnnotation(GraphQLSubscription.class)
      .addParameter(
        ParameterSpec
          .builder(listOf(ClassName.get(apiSpec.getElement())), "toObserve")
          .build()
      )
      .addParameter(subscriptionBackPressureStrategyParam())
      .addStatement(
        "return apiLogic.onDeArchiveSubscription(toObserve, backPressureStrategy)"
      )
      .returns(
        ParameterizedTypeName.get(
          ClassName.get(Flux.class),
          ClassName.get(apiSpec.getElement())
        )
      );
    if (
      methodLevelSecuritiesMap.containsKey(ON_DE_ARCHIVE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(ON_DE_ARCHIVE.toString())
    );
    clientQueryBuilder.setQueryType(SUBSCRIPTION);
    clientQueryBuilder.setQueryName(subscriptionName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", "SubscriptionRequestInput<T>");
        }
      }
    );
    return builder.build();
  }

  private ParameterSpec subscriptionBackPressureStrategyParam() {
    return ParameterSpec
      .builder(FluxSink.OverflowStrategy.class, "backPressureStrategy")
      .addAnnotation(
        AnnotationSpec
          .builder(GraphQLArgument.class)
          .addMember("name", "$S", "backPressureStrategy")
          .addMember(
            "defaultValue",
            "$S",
            "\"" + FluxSink.OverflowStrategy.BUFFER + "\""
          )
          .build()
      )
      .build();
  }
}
