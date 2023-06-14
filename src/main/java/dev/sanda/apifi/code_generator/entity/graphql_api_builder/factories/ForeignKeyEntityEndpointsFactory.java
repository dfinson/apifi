package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.ARRAY;
import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.PAGE;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.*;
import static dev.sanda.apifi.code_generator.client.SubscriptionObservableType.COLLECTION_OWNER;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.EntityCollectionEndpointType.*;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.EntityCollectionEndpointType.PAGINATED__FREE_TEXT_SEARCH;
import static dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints.ON_ASSOCIATE_WITH;
import static dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints.ON_REMOVE_FROM;
import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static dev.sanda.apifi.utils.ApifiStaticUtils.getCollectionTypeSimpleName;
import static dev.sanda.datafi.DatafiStaticUtils.logCompilationError;
import static dev.sanda.datafi.DatafiStaticUtils.toPascalCase;
import static javax.lang.model.element.Modifier.*;

import com.squareup.javapoet.*;
import dev.sanda.apifi.annotations.EntityCollectionApi;
import dev.sanda.apifi.code_generator.client.GraphQLQueryBuilder;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.element_api_spec.FieldGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.EntityCollectionEndpointType;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import dev.sanda.apifi.service.api_hooks.NullEntityCollectionApiHooks;
import dev.sanda.apifi.service.api_logic.SubscriptionsLogicService;
import dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.PageRequest;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLIgnore;
import io.leangen.graphql.annotations.GraphQLSubscription;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

public class ForeignKeyEntityEndpointsFactory {

  private final GraphQLApiBuilderParams params;

  @Getter
  private final List<MethodSpec> entityCollectionEndpoints;

  @Getter
  private final List<FieldSpec> apiHooksFields;

  private final String entityName;
  private final EntityGraphQLApiSpec apiSpec;

  public ForeignKeyEntityEndpointsFactory(GraphQLApiBuilderParams params) {
    this.params = params;
    this.entityName = params.getEntityName();
    this.apiSpec = params.getApiSpec();
    this.entityCollectionEndpoints = new ArrayList<>();
    this.apiHooksFields = new ArrayList<>();
    build();
  }

  private void build() {
    val entitiesMap = params.getEntitiesMap();
    val clientFactory = params.getClientFactory();
    params
      .getFieldGraphQLApiSpecs()
      .stream()
      .filter(fieldApiSpec -> isForeignKeyOrKeys(fieldApiSpec.getElement()))
      .collect(Collectors.toList())
      .forEach(fieldApiSpec -> {
        val fieldElement = fieldApiSpec.getElement();
        if (isIterable(fieldElement.asType(), params.getProcessingEnv())) {
          val config = fieldApiSpec.getAnnotation(EntityCollectionApi.class);
          val resolvers = config != null
            ? Arrays.asList(config.endpoints())
            : new ArrayList<EntityCollectionEndpointType>();
          addApiHooksIfPresent(fieldElement, config);
          val fkTargetType = resolveTypescriptType(
            getCollectionTypeSimpleName(fieldElement),
            params.getAllEntityTypesSimpleNames()
          );

          //read
          if (
            !isGraphQLIgnored(fieldElement) &&
            !fieldApiSpec.hasAnnotation(GraphQLIgnore.class)
          ) {
            final MethodSpec getEntityCollection = genGetEntityCollection(
              fieldElement
            );
            entityCollectionEndpoints.add(getEntityCollection);
          }
          //associate
          if (resolvers.contains(ASSOCIATE_WITH)) {
            val clientQueryBuilder = new GraphQLQueryBuilder(
              entitiesMap.values(),
              ARRAY,
              fkTargetType
            );
            clientQueryBuilder.setOwnerEntityType(entityName);
            final MethodSpec associateWithEntityCollection =
              genAssociateWithEntityCollection(
                fieldApiSpec,
                clientQueryBuilder
              );
            entityCollectionEndpoints.add(associateWithEntityCollection);
            clientFactory.addQuery(clientQueryBuilder);
          }
          //remove
          if (resolvers.contains(REMOVE_FROM)) {
            val clientQueryBuilder = new GraphQLQueryBuilder(
              entitiesMap.values(),
              ARRAY,
              fkTargetType
            );
            clientQueryBuilder.setOwnerEntityType(entityName);
            val removeFromEntityCollection = genRemoveFromEntityCollection(
              fieldApiSpec,
              clientQueryBuilder
            );
            entityCollectionEndpoints.add(removeFromEntityCollection);
            clientFactory.addQuery(clientQueryBuilder);
          }
          if (resolvers.contains(GET_PAGINATED__BATCH)) {
            val clientQueryBuilder = new GraphQLQueryBuilder(
              entitiesMap.values(),
              PAGE,
              fkTargetType
            );
            clientQueryBuilder.setOwnerEntityType(entityName);
            val getPaginatedBatchInEntityCollection =
              genGetPaginatedBatchInEntityCollection(
                fieldApiSpec,
                clientQueryBuilder
              );
            entityCollectionEndpoints.add(getPaginatedBatchInEntityCollection);
            clientFactory.addQuery(clientQueryBuilder);
          }
          if (resolvers.contains(PAGINATED__FREE_TEXT_SEARCH)) {
            val clientQueryBuilder = new GraphQLQueryBuilder(
              entitiesMap.values(),
              PAGE,
              fkTargetType
            );
            clientQueryBuilder.setOwnerEntityType(entityName);
            val getPaginatedFreeTextSearchInEntityCollection =
              genGetPaginatedFreeTextSearchInEntityCollection(
                fieldApiSpec,
                clientQueryBuilder
              );
            entityCollectionEndpoints.add(
              getPaginatedFreeTextSearchInEntityCollection
            );
            clientFactory.addQuery(clientQueryBuilder);
          }

          val subscriptions = config != null
            ? Arrays.asList(config.subscriptions())
            : new ArrayList<EntityCollectionSubscriptionEndpoints>();

          if (subscriptions.contains(ON_ASSOCIATE_WITH)) {
            val clientQueryBuilder = new GraphQLQueryBuilder(
              entitiesMap.values(),
              ARRAY,
              fkTargetType
            );
            clientQueryBuilder.setSubscriptionObservableType(COLLECTION_OWNER);
            clientQueryBuilder.setOwnerEntityType(entityName);
            val onAssociateWithSubscription = genOnAssociateWithSubscription(
              fieldApiSpec,
              clientQueryBuilder
            );
            entityCollectionEndpoints.add(onAssociateWithSubscription);
            clientFactory.addQuery(clientQueryBuilder);
          }

          if (subscriptions.contains(ON_REMOVE_FROM)) {
            val clientQueryBuilder = new GraphQLQueryBuilder(
              entitiesMap.values(),
              ARRAY,
              fkTargetType
            );
            clientQueryBuilder.setSubscriptionObservableType(COLLECTION_OWNER);
            clientQueryBuilder.setOwnerEntityType(entityName);
            val onRemoveFromSubscription = genOnRemoveFromSubscription(
              fieldApiSpec,
              clientQueryBuilder
            );
            entityCollectionEndpoints.add(onRemoveFromSubscription);
            clientFactory.addQuery(clientQueryBuilder);
          }
        } else {
          val getForeignKeyEntity = genGetForeignKeyEntityMethodSpec(
            fieldApiSpec
          );
          entityCollectionEndpoints.add(getForeignKeyEntity);
        }
      });
  }

  private MethodSpec genGetEntityCollection(
    VariableElement entityCollectionField
  ) {
    String queryName = camelcaseNameOf(entityCollectionField);
    ParameterSpec input = asParamList(
      apiSpec.getElement(),
      GraphQLContext.class
    );
    String entityCollectionApiHooksName = entityCollectionApiHooksName(
      entityCollectionField
    );
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(io.leangen.graphql.annotations.Batched.class) // new Batched annotation
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(input)
      .addStatement(
        "return apiLogic.getEntityCollection(input, $S, $L, $L)",
        camelcaseNameOf(entityCollectionField), //$S
        entityCollectionApiHooksName, //$L
        dataManagerName(entityCollectionField) // $L
      )
      .returns(listOfLists(entityCollectionField));
    val config = entityCollectionField.getAnnotation(EntityCollectionApi.class);
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "Get"
      )
    ) builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Get"));
    return builder.build();
  }

  private MethodSpec genAssociateWithEntityCollection(
    FieldGraphQLApiSpec fkSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    String mutationName =
      "associate" +
      pascalCaseNameOf(fkSpec.getElement()) +
      "With" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = fkSpec.getAnnotation(EntityCollectionApi.class);
    boolean addPreExistingOnly =
      config != null && config.associatePreExistingOnly();
    String apiLogicBackingMethod = addPreExistingOnly
      ? "associatePreExistingWithEntityCollection"
      : "associateWithEntityCollection";
    final TypeName collectionTypeName = collectionTypeName(fkSpec.getElement());
    ParameterSpec input = asParamList(collectionTypeName);
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(input)
      .addStatement(
        "return apiLogic.$L(owner, $S, input, $L, $L, $L)",
        apiLogicBackingMethod,
        camelcaseNameOf(fkSpec.getElement()),
        dataManagerName(fkSpec.getElement()),
        isCustomEntityCollectionApiHooks(config)
          ? entityCollectionApiHooksName(fkSpec.getElement())
          : "null",
        fkSpec.getSimpleName() + SubscriptionsLogicService.class.getSimpleName()
      )
      .returns(listOf(collectionTypeName));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "AssociateWith"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "AssociateWith")
    );
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("owner", apiSpec.getSimpleName() + "Input");
          put(
            "input",
            inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input")
          );
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genRemoveFromEntityCollection(
    FieldGraphQLApiSpec fkSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    String mutationName =
      "remove" +
      pascalCaseNameOf(fkSpec.getElement()) +
      "From" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = fkSpec.getAnnotation(EntityCollectionApi.class);
    final TypeName collectionTypeName = collectionTypeName(fkSpec.getElement());
    ParameterSpec input = asParamList(collectionTypeName);
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(input)
      .addStatement(
        "return apiLogic.removeFromEntityCollection(owner, $S, input, $L, $L)",
        camelcaseNameOf(fkSpec.getElement()),
        dataManagerName(fkSpec.getElement()),
        isCustomEntityCollectionApiHooks(config)
          ? entityCollectionApiHooksName(fkSpec.getElement())
          : "null"
      )
      .returns(listOf(collectionTypeName));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "RemoveFrom"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "RemoveFrom")
    );
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("owner", apiSpec.getSimpleName() + "Input");
          put(
            "input",
            inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input")
          );
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genGetPaginatedBatchInEntityCollection(
    FieldGraphQLApiSpec fkSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    String queryName =
      camelcaseNameOf(fkSpec.getElement()) +
      "Of" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = fkSpec.getAnnotation(EntityCollectionApi.class);
    ParameterSpec input = ParameterSpec
      .builder(TypeName.get(PageRequest.class), "input")
      .build();
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(input)
      .addCode(
        initSortByIfNull(
          params.getEntitiesMap().get(getCollectionType(fkSpec.getElement()))
        )
      )
      //TODO - should be "apiLogic.getPaginatedBatchOfEntityCollection"
      .addStatement(
        "return apiLogic.getPaginatedBatchInEntityCollection(owner, input, $S, $L, $L)",
        camelcaseNameOf(fkSpec.getElement()),
        dataManagerName(fkSpec.getElement()),
        isCustomEntityCollectionApiHooks(config)
          ? entityCollectionApiHooksName(fkSpec.getElement())
          : "null"
      )
      .returns(pageType(fkSpec.getElement()));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "GetPaginated"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "GetPaginated")
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(queryName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("owner", apiSpec.getSimpleName() + "Input");
          put("input", "PageRequestInput");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genGetPaginatedFreeTextSearchInEntityCollection(
    FieldGraphQLApiSpec fkSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    String queryName =
      camelcaseNameOf(fkSpec.getElement()) +
      "Of" +
      pascalCaseNameOf(apiSpec.getElement()) +
      "FreeTextSearch";
    val config = fkSpec.getAnnotation(EntityCollectionApi.class);
    if (
      config.freeTextSearchFields().length == 1 &&
      config.freeTextSearchFields()[0].equals("")
    ) {
      logCompilationError(
        params.getProcessingEnv(),
        fkSpec.getElement(),
        "collection field " +
        fkSpec.getSimpleName() +
        " in " +
        apiSpec.getSimpleName().toString() +
        " has been marked " +
        "as free text searchable, but no field names of entity type " +
        getCollectionType(fkSpec.getElement()) +
        " have been specified in the " +
        "freeTextSearchFields parameter"
      );
    }
    ParameterSpec input = ParameterSpec
      .builder(TypeName.get(FreeTextSearchPageRequest.class), "input")
      .build();
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(input)
      .addCode(
        initSortByIfNull(
          params.getEntitiesMap().get(getCollectionType(fkSpec.getElement()))
        )
      )
      .addStatement(
        "return apiLogic.paginatedFreeTextSearchInEntityCollection(owner, input, $S, $L, $L)",
        camelcaseNameOf(fkSpec.getElement()),
        dataManagerName(fkSpec.getElement()),
        isCustomEntityCollectionApiHooks(config)
          ? entityCollectionApiHooksName(fkSpec.getElement())
          : "null"
      )
      .returns(pageType(fkSpec.getElement()));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "PaginatedFreeTextSearch"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "PaginatedFreeTextSearch")
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(queryName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("owner", apiSpec.getSimpleName() + "Input");
          put("input", "FreeTextSearchPageRequestInput");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genOnAssociateWithSubscription(
    FieldGraphQLApiSpec fieldApiSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val subscriptionName =
      "onAssociate" +
      toPascalCase(fieldApiSpec.getSimpleName()) +
      "With" +
      entityName;
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(subscriptionName)
      .addModifiers(PUBLIC)
      .addAnnotation(GraphQLSubscription.class)
      .addParameter(ClassName.get(apiSpec.getElement()), "owner")
      .addParameter(subscriptionBackPressureStrategyParam())
      .addStatement(
        "return apiLogic.onAssociateWithSubscription(owner, $S, backPressureStrategy, $L)",
        fieldApiSpec.getSimpleName(),
        dataManagerName(fieldApiSpec.getElement())
      )
      .returns(
        ParameterizedTypeName.get(
          ClassName.get(Flux.class),
          listOf(collectionTypeName(fieldApiSpec.getElement()))
        )
      );
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        fieldApiSpec.getAnnotation(EntityCollectionApi.class),
        "",
        "OnAssociateWith"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(
        fieldApiSpec.getAnnotation(EntityCollectionApi.class),
        "",
        "OnAssociateWith"
      )
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

  private MethodSpec genOnRemoveFromSubscription(
    FieldGraphQLApiSpec fieldApiSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val subscriptionName =
      "onRemove" +
      toPascalCase(fieldApiSpec.getSimpleName()) +
      "From" +
      entityName;
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(subscriptionName)
      .addModifiers(PUBLIC)
      .addAnnotation(GraphQLSubscription.class)
      .addParameter(ClassName.get(apiSpec.getElement()), "owner")
      .addParameter(subscriptionBackPressureStrategyParam())
      .addStatement(
        "return apiLogic.onRemoveFromSubscription(owner, $S, backPressureStrategy, $L)",
        fieldApiSpec.getSimpleName(),
        dataManagerName(fieldApiSpec.getElement())
      )
      .returns(
        ParameterizedTypeName.get(
          ClassName.get(Flux.class),
          listOf(collectionTypeName(fieldApiSpec.getElement()))
        )
      );
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        fieldApiSpec.getAnnotation(EntityCollectionApi.class),
        "",
        "OnRemoveFrom"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(
        fieldApiSpec.getAnnotation(EntityCollectionApi.class),
        "",
        "OnRemoveFrom"
      )
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

  private MethodSpec genGetForeignKeyEntityMethodSpec(
    FieldGraphQLApiSpec fieldSpec
  ) {
    String queryName = camelcaseNameOf(fieldSpec.getElement());
    ParameterSpec input = asParamList(
      apiSpec.getElement(),
      GraphQLContext.class
    );
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(input)
      .addStatement(
        "return apiLogic.getForeignKeyEntity(input, $S, $L)",
        camelcaseNameOf(fieldSpec.getElement()),
        dataManagerName(fieldSpec.getElement())
      )
      .returns(
        ParameterizedTypeName.get(
          ClassName.get(List.class),
          ClassName.get(fieldSpec.getElement().asType())
        )
      );
    return builder.build();
  }

  private void addApiHooksIfPresent(
    VariableElement entityCollectionField,
    EntityCollectionApi entityCollectionApi
  ) {
    if (!isCustomEntityCollectionApiHooks(entityCollectionApi)) return;
    final FieldSpec apiHooks = entityCollectionApiHooks(entityCollectionField);
    apiHooksFields.add(apiHooks);
  }

  private boolean isCustomEntityCollectionApiHooks(EntityCollectionApi config) {
    if (config == null) return false;
    return !getApiHooksTypeName(config)
      .toString()
      .equals(NullEntityCollectionApiHooks.class.getCanonicalName());
  }

  public FieldSpec entityCollectionApiHooks(VariableElement fk) {
    TypeName apiHooksType = null;
    val entityCollectionApi = fk.getAnnotation(EntityCollectionApi.class);
    if (entityCollectionApi != null) {
      apiHooksType = getApiHooksTypeName(entityCollectionApi);
    }
    assert apiHooksType != null;
    return FieldSpec
      .builder(apiHooksType, entityCollectionApiHooksName(fk), PRIVATE, FINAL)
      .addAnnotation(
        AnnotationSpec
          .builder(Setter.class)
          .addMember("onMethod_", "@$T", Autowired.class)
          .build()
      )
      .build();
  }

  private boolean isGraphQLIgnored(VariableElement fk) {
    val getter = apiSpec
      .getElement()
      .getEnclosedElements()
      .stream()
      .filter(elem ->
        elem.getKind().equals(ElementKind.METHOD) &&
        elem.getSimpleName().toString().equals("get" + pascalCaseNameOf(fk)) &&
        ((ExecutableElement) elem).getReturnType().equals(fk.asType())
      )
      .findFirst()
      .orElse(null);
    if (getter == null) return fk.getAnnotation(GraphQLIgnore.class) != null;
    return getter.getAnnotation(GraphQLIgnore.class) != null;
  }
}
