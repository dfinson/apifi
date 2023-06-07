package dev.sanda.apifi.code_generator.entity;

import com.squareup.javapoet.*;
import dev.sanda.apifi.annotations.*;
import dev.sanda.apifi.code_generator.client.ApifiClientFactory;
import dev.sanda.apifi.code_generator.client.GraphQLQueryBuilder;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.element_api_spec.FieldGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.CRUDEndpoints;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.ElementCollectionEndpointType;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.EntityCollectionEndpointType;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.MapElementCollectionEndpointType;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import dev.sanda.apifi.service.api_hooks.ApiHooks;
import dev.sanda.apifi.service.api_hooks.NullElementCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.NullEntityCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.NullMapElementCollectionApiHooks;
import dev.sanda.apifi.service.api_logic.ApiLogic;
import dev.sanda.apifi.service.api_logic.SubscriptionsLogicService;
import dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints;
import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.TestSubscriptionsHandler;
import dev.sanda.apifi.test_utils.TestGraphQLService;
import dev.sanda.apifi.utils.ConfigValues;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.PageRequest;
import dev.sanda.datafi.service.DataManager;
import io.leangen.graphql.annotations.*;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.ElementCollection;
import jakarta.transaction.Transactional;
import lombok.Getter;
import lombok.Setter;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.*;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.*;
import static dev.sanda.apifi.code_generator.client.SubscriptionObservableType.*;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.CRUDEndpoints.*;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.ElementCollectionEndpointType.ADD_TO;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.ElementCollectionEndpointType.REMOVE__FROM;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.EntityCollectionEndpointType.*;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.MapElementCollectionEndpointType.*;
import static dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints.ON_ASSOCIATE_WITH;
import static dev.sanda.apifi.service.graphql_subcriptions.EntityCollectionSubscriptionEndpoints.ON_REMOVE_FROM;
import static dev.sanda.apifi.service.graphql_subcriptions.SubscriptionEndpoints.*;
import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static dev.sanda.datafi.DatafiStaticUtils.*;
import static javax.lang.model.element.Modifier.*;

public class GraphQLApiBuilder {

  private EntityGraphQLApiSpec apiSpec;
  private List<FieldGraphQLApiSpec> fieldGraphQLApiSpecs;
  private Map<CRUDEndpoints, Boolean> crudResolvers;
  private ProcessingEnvironment processingEnv;
  private Map<String, TypeElement> entitiesMap;
  private Map<String, List<AnnotationSpec>> methodLevelSecuritiesMap;
  private ApifiClientFactory clientFactory;
  private String entityName;
  private Set<String> enumTypes;
  private Set<String> allEntityTypesSimpleNames;

  public GraphQLApiBuilder(
    EntityGraphQLApiSpec apiSpec,
    Map<String, TypeElement> entitiesMap,
    List<CRUDEndpoints> crudResolvers,
    Set<TypeElement> enumTypes
  ) {
    this.enumTypes =
      enumTypes
        .stream()
        .map(TypeElement::getSimpleName)
        .map(Objects::toString)
        .map(String::toLowerCase)
        .collect(Collectors.toSet());
    this.allEntityTypesSimpleNames = new HashSet<>(this.enumTypes);
    this.allEntityTypesSimpleNames.addAll(
        entitiesMap
          .values()
          .stream()
          .map(TypeElement::getSimpleName)
          .map(Objects::toString)
          .map(String::toLowerCase)
          .collect(Collectors.toSet())
      );
    this.apiSpec = apiSpec;
    this.entityName = apiSpec.getSimpleName();
    this.crudResolvers =
      crudResolvers.stream().collect(Collectors.toMap(cr -> cr, cr -> true));
    this.entitiesMap = entitiesMap;
    this.fieldGraphQLApiSpecs = apiSpec.getFieldGraphQLApiSpecs();
  }

  public ServiceAndTestableService build(
    ProcessingEnvironment processingEnv,
    ApifiClientFactory clientFactory,
    Map<String, ClassName> collectionsTypes
  ) {
    this.clientFactory = clientFactory;
    this.processingEnv = processingEnv;
    registerCollectionsTypes(collectionsTypes);
    SecurityAnnotationsFactory.setProcessingEnv(processingEnv);
    //init builders
    TypeSpec.Builder serviceBuilder = TypeSpec
      .classBuilder(apiSpec.getSimpleName() + "GraphQLApiService")
      .addModifiers(PUBLIC)
      .addAnnotation(Service.class)
      .addAnnotation(Transactional.class)
      .addAnnotation(autowiredRequiredArgsConstructor());
    TypeSpec.Builder testableServiceBuilder = TypeSpec
      .classBuilder("Testable" + apiSpec.getSimpleName() + "GraphQLApiService")
      .addModifiers(PUBLIC)
      .addSuperinterface(testableGraphQLServiceInterface())
      .addAnnotation(Service.class)
      .addField(testSubscriptionsHandlerField())
      .addField(configValues())
      .addAnnotation(autowiredRequiredArgsConstructor());
    //generate fields:

    //fields 1,2 - ApiLogic & testLogic

    val apiLogic = apiLogic();
    serviceBuilder.addField(apiLogic);
    testableServiceBuilder.addField(apiLogic);

    val subscriptionsLogicService = subscriptionsLogicService();
    serviceBuilder.addField(subscriptionsLogicService);
    testableServiceBuilder.addField(subscriptionsLogicService);

    val defaultDataManager = defaultDataManager();
    serviceBuilder.addField(defaultDataManager);
    testableServiceBuilder.addField(defaultDataManager);

    val defaultApiHooks = defaultApiHooks();
    serviceBuilder.addField(defaultApiHooks);
    testableServiceBuilder.addField(defaultApiHooks);

    serviceBuilder.addMethod(postConstructInit());
    testableServiceBuilder.addMethod(postConstructInit());

    //field(s) 4 - foreign key data managers
    fieldGraphQLApiSpecs.forEach(fieldGraphQLApiSpec -> {
      val field = fieldGraphQLApiSpec.getElement();
      String typeNameKey = isIterable(field.asType(), processingEnv)
        ? getCollectionType(field)
        : field.asType().toString();
      val type = entitiesMap.get(typeNameKey);
      if (type != null) {
        val typeDataManager = dataManager(type, dataManagerName(field));
        serviceBuilder.addField(typeDataManager);
        testableServiceBuilder.addField(typeDataManager);
      }
    });

    //field(s) 4 - foreign key subscription logic services
    fieldGraphQLApiSpecs.forEach(fieldGraphQLApiSpec -> {
      val field = fieldGraphQLApiSpec.getElement();
      String typeNameKey = isIterable(field.asType(), processingEnv)
        ? getCollectionType(field)
        : field.asType().toString();
      val type = entitiesMap.get(typeNameKey);
      if (type != null) {
        val typeSubscriptionLogicService = FieldSpec
          .builder(
            ParameterizedTypeName.get(
              ClassName.get(SubscriptionsLogicService.class),
              ClassName.get(type)
            ),
            field.getSimpleName() +
            SubscriptionsLogicService.class.getSimpleName(),
            PRIVATE,
            FINAL
          )
          .build();
        serviceBuilder.addField(typeSubscriptionLogicService);
        testableServiceBuilder.addField(typeSubscriptionLogicService);
      }
    });

    // generate class level security annotations
    val serviceLevelSecurity =
      this.apiSpec.getAnnotation(WithServiceLevelSecurity.class);
    if (serviceLevelSecurity != null) {
      val securityAnnotations = SecurityAnnotationsFactory.of(
        serviceLevelSecurity
      );
      if (!securityAnnotations.isEmpty()) {
        serviceBuilder.addAnnotations(securityAnnotations);
        testableServiceBuilder.addAnnotations(securityAnnotations);
      }
    }

    // prepare the groundwork for method level security annotations
    methodLevelSecuritiesMap = new HashMap<>();
    val methodLevelSecurities = apiSpec.getAnnotationsByType(
      WithMethodLevelSecurity.class
    );
    if (
      !methodLevelSecurities.isEmpty()
    ) methodLevelSecurities.forEach(security ->
      handleTargetMethodsMapping(security, methodLevelSecuritiesMap)
    );

    //generate crud methods:

    //GET_PAGINATED_BATCH
    if (crudResolvers.containsKey(GET_PAGINATED_BATCH)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        entityName
      );
      serviceBuilder.addMethod(genGetPaginatedBatch(clientQueryBuilder));
      testableServiceBuilder.addMethod(
        genGetPaginatedBatch(clientQueryBuilder)
      );
      clientFactory.addQuery(clientQueryBuilder);
    }

    //GET_TOTAL_NON_ARCHIVED_COUNT
    if (crudResolvers.containsKey(GET_TOTAL_COUNT)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        NUMBER,
        "number"
      );
      serviceBuilder.addMethod(genGetTotalNonArchivedCount(clientQueryBuilder));
      testableServiceBuilder.addMethod(
        genGetTotalNonArchivedCount(clientQueryBuilder)
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
    //GET_TOTAL_ARCHIVED_COUNT
    if (crudResolvers.containsKey(GET_TOTAL_ARCHIVED_COUNT)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        NUMBER,
        "number"
      );
      serviceBuilder.addMethod(genGetTotalArchivedCount(clientQueryBuilder));
      testableServiceBuilder.addMethod(
        genGetTotalArchivedCount(clientQueryBuilder)
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
    //GET_BY_ID
    if (crudResolvers.containsKey(GET_BY_ID)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        INSTANCE,
        entityName
      );
      serviceBuilder.addMethod(genGetById(processingEnv, clientQueryBuilder));
      testableServiceBuilder.addMethod(
        genGetById(processingEnv, clientQueryBuilder)
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
    //GET_BATCH_BY_IDS
    if (crudResolvers.containsKey(GET_BATCH_BY_IDS)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        entityName
      );
      serviceBuilder.addMethod(
        genGetBatchByIds(processingEnv, clientQueryBuilder)
      );
      testableServiceBuilder.addMethod(
        genGetBatchByIds(processingEnv, clientQueryBuilder)
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
    //CREATE
    if (crudResolvers.containsKey(CREATE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        INSTANCE,
        entityName
      );
      serviceBuilder.addMethod(genCreate(clientQueryBuilder));
      testableServiceBuilder.addMethod(genCreate(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //BATCH_CREATE
    if (crudResolvers.containsKey(BATCH_CREATE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        entityName
      );
      serviceBuilder.addMethod(genBatchCreate(clientQueryBuilder));
      testableServiceBuilder.addMethod(genBatchCreate(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //UPDATE
    if (crudResolvers.containsKey(UPDATE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        INSTANCE,
        entityName
      );
      serviceBuilder.addMethod(genUpdate(clientQueryBuilder));
      testableServiceBuilder.addMethod(genUpdate(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //BATCH_UPDATE
    if (crudResolvers.containsKey(BATCH_UPDATE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        entityName
      );
      serviceBuilder.addMethod(genBatchUpdate(clientQueryBuilder));
      testableServiceBuilder.addMethod(genBatchUpdate(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //DELETE
    if (crudResolvers.containsKey(DELETE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        INSTANCE,
        entityName
      );
      serviceBuilder.addMethod(genDelete(clientQueryBuilder));
      testableServiceBuilder.addMethod(genDelete(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //BATCH_DELETE
    if (crudResolvers.containsKey(BATCH_DELETE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        entityName
      );
      serviceBuilder.addMethod(genBatchDelete(clientQueryBuilder));
      testableServiceBuilder.addMethod(genBatchDelete(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //ARCHIVE
    if (crudResolvers.containsKey(ARCHIVE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        INSTANCE,
        entityName
      );
      serviceBuilder.addMethod(genArchive(clientQueryBuilder));
      testableServiceBuilder.addMethod(genArchive(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //BATCH_ARCHIVE
    if (crudResolvers.containsKey(BATCH_ARCHIVE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        entityName
      );
      serviceBuilder.addMethod(genBatchArchive(clientQueryBuilder));
      testableServiceBuilder.addMethod(genBatchArchive(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //DE_ARCHIVE
    if (crudResolvers.containsKey(DE_ARCHIVE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        INSTANCE,
        entityName
      );
      serviceBuilder.addMethod(genDeArchive(clientQueryBuilder));
      testableServiceBuilder.addMethod(genDeArchive(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //BATCH_DE_ARCHIVE
    if (crudResolvers.containsKey(BATCH_DE_ARCHIVE)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        entityName
      );
      serviceBuilder.addMethod(genBatchDeArchive(clientQueryBuilder));
      testableServiceBuilder.addMethod(genBatchDeArchive(clientQueryBuilder));
      clientFactory.addQuery(clientQueryBuilder);
    }
    //GET_ARCHIVED_PAGINATED_BATCH
    if (crudResolvers.containsKey(GET_ARCHIVED_PAGINATED_BATCH)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        entityName
      );
      serviceBuilder.addMethod(
        genGetArchivedPaginatedBatch(clientQueryBuilder)
      );
      testableServiceBuilder.addMethod(
        genGetArchivedPaginatedBatch(clientQueryBuilder)
      );
      clientFactory.addQuery(clientQueryBuilder);
    }

    // generate subscription endpoints
    val subscriptionEndpoints = apiSpec.getAnnotation(
      WithSubscriptionEndpoints.class
    );
    if (subscriptionEndpoints != null) {
      val subscriptionEndpointsSet = Arrays
        .stream(subscriptionEndpoints.value())
        .collect(Collectors.toSet());
      // ON_CREATE
      if (subscriptionEndpointsSet.contains(ON_CREATE)) {
        val clientQueryBuilder = new GraphQLQueryBuilder(
          entitiesMap.values(),
          ARRAY,
          entityName
        );
        clientQueryBuilder.setSubscriptionObservableType(ENTITY_TYPE);
        val onCreateSubscription = genOnCreateSubscription(clientQueryBuilder);
        serviceBuilder.addMethod(onCreateSubscription);
        testableServiceBuilder.addMethod(onCreateSubscription);
        clientFactory.addQuery(clientQueryBuilder);
      }
      // ON_UPDATE
      if (subscriptionEndpointsSet.contains(ON_UPDATE)) {
        val clientQueryBuilder = new GraphQLQueryBuilder(
          entitiesMap.values(),
          INSTANCE,
          entityName
        );
        clientQueryBuilder.setSubscriptionObservableType(LIST_TO_OBSERVE);
        val onUpdateSubscription = genOnUpdateSubscription(clientQueryBuilder);
        serviceBuilder.addMethod(onUpdateSubscription);
        testableServiceBuilder.addMethod(onUpdateSubscription);
        clientFactory.addQuery(clientQueryBuilder);
      }
      // ON_DELETE
      if (subscriptionEndpointsSet.contains(ON_DELETE)) {
        val clientQueryBuilder = new GraphQLQueryBuilder(
          entitiesMap.values(),
          INSTANCE,
          entityName
        );
        clientQueryBuilder.setSubscriptionObservableType(LIST_TO_OBSERVE);
        val onDeleteSubscription = genOnDeleteSubscription(clientQueryBuilder);
        serviceBuilder.addMethod(onDeleteSubscription);
        testableServiceBuilder.addMethod(onDeleteSubscription);
        clientFactory.addQuery(clientQueryBuilder);
      }
      // ON_ARCHIVE
      if (subscriptionEndpointsSet.contains(ON_ARCHIVE)) {
        val clientQueryBuilder = new GraphQLQueryBuilder(
          entitiesMap.values(),
          INSTANCE,
          entityName
        );
        clientQueryBuilder.setSubscriptionObservableType(LIST_TO_OBSERVE);
        val onArchiveSubscription = genOnArchiveSubscription(
          clientQueryBuilder
        );
        serviceBuilder.addMethod(onArchiveSubscription);
        testableServiceBuilder.addMethod(onArchiveSubscription);
        clientFactory.addQuery(clientQueryBuilder);
      }
      // ON_DE_ARCHIVE
      if (subscriptionEndpointsSet.contains(ON_DE_ARCHIVE)) {
        val clientQueryBuilder = new GraphQLQueryBuilder(
          entitiesMap.values(),
          INSTANCE,
          entityName
        );
        clientQueryBuilder.setSubscriptionObservableType(LIST_TO_OBSERVE);
        val onDeArchiveSubscription = genOnDeArchiveSubscription(
          clientQueryBuilder
        );
        serviceBuilder.addMethod(onDeArchiveSubscription);
        testableServiceBuilder.addMethod(onDeArchiveSubscription);
        clientFactory.addQuery(clientQueryBuilder);
      }
    }

    //generate foreign key endpoints
    fieldGraphQLApiSpecs
      .stream()
      .filter(fieldApiSpec -> isForeignKeyOrKeys(fieldApiSpec.getElement()))
      .collect(Collectors.toList())
      .forEach(fieldApiSpec -> {
        val fieldElement = fieldApiSpec.getElement();
        if (isIterable(fieldElement.asType(), processingEnv)) {
          val config = fieldApiSpec.getAnnotation(EntityCollectionApi.class);
          val resolvers = config != null
            ? Arrays.asList(config.endpoints())
            : new ArrayList<EntityCollectionEndpointType>();
          addApiHooksIfPresent(
            fieldElement,
            testableServiceBuilder,
            serviceBuilder,
            config
          );
          val fkTargetType = resolveTypescriptType(
            getCollectionTypeSimpleName(fieldElement),
            allEntityTypesSimpleNames
          );

          //read
          if (
            !isGraphQLIgnored(fieldElement) &&
            !fieldApiSpec.hasAnnotation(GraphQLIgnore.class)
          ) {
            final MethodSpec getEntityCollection = genGetEntityCollection(
              fieldElement
            );
            serviceBuilder.addMethod(getEntityCollection);
            testableServiceBuilder.addMethod(getEntityCollection);
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
            serviceBuilder.addMethod(associateWithEntityCollection);
            testableServiceBuilder.addMethod(associateWithEntityCollection);
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
            serviceBuilder.addMethod(removeFromEntityCollection);
            testableServiceBuilder.addMethod(removeFromEntityCollection);
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
            serviceBuilder.addMethod(getPaginatedBatchInEntityCollection);
            testableServiceBuilder.addMethod(
              getPaginatedBatchInEntityCollection
            );
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
            serviceBuilder.addMethod(
              getPaginatedFreeTextSearchInEntityCollection
            );
            testableServiceBuilder.addMethod(
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
            serviceBuilder.addMethod(onAssociateWithSubscription);
            testableServiceBuilder.addMethod(onAssociateWithSubscription);
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
            serviceBuilder.addMethod(onRemoveFromSubscription);
            testableServiceBuilder.addMethod(onRemoveFromSubscription);
            clientFactory.addQuery(clientQueryBuilder);
          }
        } else {
          val getEmbedded = genGetEmbedded(fieldApiSpec);
          serviceBuilder.addMethod(getEmbedded);
          testableServiceBuilder.addMethod(getEmbedded);
        }
      });

    //generate element collection endpoints
    fieldGraphQLApiSpecs
      .stream()
      .filter(f -> f.getAnnotation(ElementCollection.class) != null)
      .collect(Collectors.toList())
      .forEach(elemCollectionSpec -> {
        val elementCollectionApiConfig = elemCollectionSpec.getAnnotation(
          ElementCollectionApi.class
        );
        if (
          elementCollectionApiConfig != null
        ) generateElementCollectionMethods(
          clientFactory,
          testableServiceBuilder,
          serviceBuilder,
          elemCollectionSpec,
          elementCollectionApiConfig
        ); else {
          val mapElementCollectionApiConfig = elemCollectionSpec.getAnnotation(
            MapElementCollectionApi.class
          );
          if (
            mapElementCollectionApiConfig != null
          ) generateMapElementCollectionMethods(
            clientFactory,
            testableServiceBuilder,
            serviceBuilder,
            elemCollectionSpec,
            mapElementCollectionApiConfig
          );
        }
      });

    //generate api free text search endpoints
    val hasFreeTextSearchFields = fieldGraphQLApiSpecs
      .stream()
      .anyMatch(this::isFreeTextSearchAnnotated);
    if (hasFreeTextSearchFields) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        entityName
      );

      val freeTextSearchBy = genFreeTextSearchBy(clientQueryBuilder);
      serviceBuilder.addMethod(freeTextSearchBy);
      testableServiceBuilder.addMethod(freeTextSearchBy);
      clientFactory.addQuery(clientQueryBuilder);
    }

    // generate api find by endpoints
    fieldGraphQLApiSpecs
      .stream()
      .filter(this::isApiFindByAnnotated)
      .map(fieldSpec -> toApiFindByEndpoints(fieldSpec, clientFactory))
      .forEach(methodSpecs -> {
        serviceBuilder.addMethods(methodSpecs);
        testableServiceBuilder.addMethods(methodSpecs);
      });

    //return result
    return new ServiceAndTestableService(
      serviceBuilder.build(),
      testableServiceBuilder.build()
    );
  }

  private void generateElementCollectionMethods(
    ApifiClientFactory clientFactory,
    TypeSpec.Builder testableServiceBuilder,
    TypeSpec.Builder serviceBuilder,
    FieldGraphQLApiSpec elemCollectionSpec,
    ElementCollectionApi config
  ) {
    val endpoints = config != null
      ? Arrays.asList(config.endpoints())
      : new ArrayList<ElementCollectionEndpointType>();
    val fkTargetType = getTypeScriptElementCollectionType(
      elemCollectionSpec.getElement(),
      enumTypes
    );
    if (endpoints.contains(ADD_TO)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        fkTargetType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      serviceBuilder.addMethod(
        genAddToElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        )
      );
      testableServiceBuilder.addMethod(
        genAddToElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        )
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
    if (endpoints.contains(REMOVE__FROM)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        fkTargetType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      serviceBuilder.addMethod(
        genRemoveFromElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        )
      );
      testableServiceBuilder.addMethod(
        genRemoveFromElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        )
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
    if (endpoints.contains(ElementCollectionEndpointType.PAGINATED__BATCH_)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        fkTargetType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      serviceBuilder.addMethod(
        genGetPaginatedBatchInElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        )
      );
      testableServiceBuilder.addMethod(
        genGetPaginatedBatchInElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        )
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
    if (
      endpoints.contains(
        ElementCollectionEndpointType.PAGINATED__FREE__TEXT_SEARCH
      )
    ) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        fkTargetType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      serviceBuilder.addMethod(
        genFreeTextSearchInElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        )
      );
      testableServiceBuilder.addMethod(
        genFreeTextSearchInElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        )
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
  }

  private void generateMapElementCollectionMethods(
    ApifiClientFactory clientFactory,
    TypeSpec.Builder testableServiceBuilder,
    TypeSpec.Builder serviceBuilder,
    FieldGraphQLApiSpec elemCollectionSpec,
    MapElementCollectionApi config
  ) {
    val endpoints = config != null
      ? Arrays.asList(config.endpoints())
      : new ArrayList<MapElementCollectionEndpointType>();
    val mapKeyType = resolveTypescriptType(
      toSimpleName(getMapKeyType(elemCollectionSpec.getElement())),
      allEntityTypesSimpleNames
    );
    val mapValueType = resolveTypescriptType(
      toSimpleName(getMapValueType(elemCollectionSpec.getElement())),
      allEntityTypesSimpleNames
    );
    val typeScriptReturnType = mapKeyType + ", " + mapValueType;
    if (endpoints.contains(PUT_ALL)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        MAP,
        typeScriptReturnType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      val genAddToMapElementCollection = genAddToMapElementCollection(
        elemCollectionSpec,
        clientQueryBuilder
      );
      serviceBuilder.addMethod(genAddToMapElementCollection);
      testableServiceBuilder.addMethod(genAddToMapElementCollection);
      clientFactory.addQuery(clientQueryBuilder);
    }
    if (endpoints.contains(REMOVE_ALL)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        MAP,
        typeScriptReturnType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      val genRemoveFromMapElementCollection = genRemoveFromMapElementCollection(
        elemCollectionSpec,
        clientQueryBuilder
      );
      serviceBuilder.addMethod(genRemoveFromMapElementCollection);
      testableServiceBuilder.addMethod(genRemoveFromMapElementCollection);
      clientFactory.addQuery(clientQueryBuilder);
    }
    if (endpoints.contains(PAGINATED__BATCH__)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        typeScriptReturnType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      final MethodSpec getPaginatedBatchInMapElementCollection =
        genGetPaginatedBatchInMapElementCollection(
          elemCollectionSpec,
          clientQueryBuilder
        );
      serviceBuilder.addMethod(getPaginatedBatchInMapElementCollection);
      testableServiceBuilder.addMethod(getPaginatedBatchInMapElementCollection);
      clientFactory.addQuery(clientQueryBuilder);
    }
  }

  private boolean isFreeTextSearchAnnotated(
    FieldGraphQLApiSpec fieldGraphQLApiSpec
  ) {
    val freeTextSearchByFields = fieldGraphQLApiSpec
      .getElement()
      .getEnclosingElement()
      .getAnnotation(WithApiFreeTextSearchByFields.class);
    return (
      freeTextSearchByFields != null &&
      containsFieldName(freeTextSearchByFields, fieldGraphQLApiSpec)
    );
  }

  private FieldSpec defaultDataManager() {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(DataManager.class),
          ClassName.get(apiSpec.getElement())
        ),
        "dataManager"
      )
      .addAnnotation(Getter.class)
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private MethodSpec postConstructInit() {
    return MethodSpec
      .methodBuilder("postConstructInit")
      .addAnnotation(PostConstruct.class)
      .addModifiers(PRIVATE)
      .returns(void.class)
      .addStatement("subscriptionsLogicService.setApiHooks(apiHooks)")
      .addStatement(
        "apiLogic.init(dataManager, apiHooks, subscriptionsLogicService)"
      )
      .build();
  }

  private void handleTargetMethodsMapping(
    WithMethodLevelSecurity security,
    Map<String, List<AnnotationSpec>> methodLevelSecuritiesMap
  ) {
    val targetMethods = Arrays
      .stream(security.crudEndpointTargets())
      .map(Object::toString)
      .collect(Collectors.toList());
    targetMethods.addAll(
      Arrays
        .stream(security.subscriptionEndpointTargets())
        .map(Object::toString)
        .collect(Collectors.toList())
    );

    val securityAnnotations = SecurityAnnotationsFactory.of(security);
    for (val targetMethod : targetMethods) {
      if (
        !methodLevelSecuritiesMap.containsKey(targetMethod)
      ) methodLevelSecuritiesMap.put(targetMethod, new ArrayList<>());
      List<AnnotationSpec> securities = methodLevelSecuritiesMap.get(
        targetMethod
      );
      for (AnnotationSpec securityAnnotation : securityAnnotations) {
        if (!securities.contains(securityAnnotation)) securities.add(
          securityAnnotation
        ); else {
          processingEnv
            .getMessager()
            .printMessage(
              Diagnostic.Kind.ERROR,
              "Illegal attempt to repeat non repeatable security " +
              "annotation of type: " +
              securityAnnotation.type.toString() +
              " on or in entity of type: " +
              pascalCaseNameOf(apiSpec.getElement())
            );
          return;
        }
      }
      methodLevelSecuritiesMap.replace(targetMethod, securities);
    }
  }

  //method specs
  private MethodSpec genGetPaginatedBatch(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    final String name = toPlural(camelcaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(name)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(
        ParameterSpec.builder(ClassName.get(PageRequest.class), "input").build()
      )
      .addCode(initSortByIfNull(apiSpec.getElement()))
      .addStatement("return apiLogic.getPaginatedBatch(input)")
      .returns(pageType(apiSpec.getElement()));
    if (
      methodLevelSecuritiesMap.containsKey(GET_PAGINATED_BATCH.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(GET_PAGINATED_BATCH.toString())
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(name);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", "PageRequestInput");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genGetTotalNonArchivedCount(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    String queryName =
      "countTotal" + toPlural(pascalCaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addStatement("return apiLogic.getTotalNonArchivedCount()")
      .returns(TypeName.LONG);
    if (
      methodLevelSecuritiesMap.containsKey(GET_TOTAL_COUNT.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(GET_TOTAL_COUNT.toString())
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(queryName);
    clientQueryBuilder.setPrimitiveReturnType(true);
    return builder.build();
  }

  private MethodSpec genGetTotalArchivedCount(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    String queryName =
      "countTotalArchived" + toPlural(pascalCaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addStatement("return apiLogic.getTotalArchivedCount()")
      .returns(TypeName.LONG);
    if (
      methodLevelSecuritiesMap.containsKey(GET_TOTAL_ARCHIVED_COUNT.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(GET_TOTAL_ARCHIVED_COUNT.toString())
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(queryName);
    clientQueryBuilder.setPrimitiveReturnType(true);
    return builder.build();
  }

  private MethodSpec genGetById(
    ProcessingEnvironment processingEnv,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    String queryName = "get" + pascalCaseNameOf(apiSpec.getElement()) + "ById";
    final ClassName idType = getIdType(apiSpec.getElement(), processingEnv);
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(namedGraphQLQuery(queryName))
      .addParameter(idType, "input")
      .addStatement("return apiLogic.getById(input)")
      .returns(TypeName.get(apiSpec.getElement().asType()));
    if (
      methodLevelSecuritiesMap.containsKey(GET_BY_ID.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(GET_BY_ID.toString())
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(queryName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put(
            "input",
            getInputTypeSimpleName(idType.simpleName(), idType.packageName())
          );
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genGetBatchByIds(
    ProcessingEnvironment processingEnv,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    String queryName =
      "get" + toPlural(pascalCaseNameOf(apiSpec.getElement())) + "ByIds";
    final ClassName idType = getIdType(apiSpec.getElement(), processingEnv);
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(namedGraphQLQuery(queryName))
      .addParameter(ParameterSpec.builder(listOf(idType), "input").build())
      .addStatement("return apiLogic.getBatchByIds(input)")
      .returns(listOf(apiSpec.getElement()));
    if (
      methodLevelSecuritiesMap.containsKey(GET_BATCH_BY_IDS.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(GET_BATCH_BY_IDS.toString())
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(queryName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", inBrackets(idType.simpleName() + "Input"));
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genCreate(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName = "create" + pascalCaseNameOf(apiSpec.getElement());
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(parameterizeType(apiSpec.getElement()))
      .addStatement("return apiLogic.create(input)")
      .returns(TypeName.get(apiSpec.getElement().asType()));
    if (
      methodLevelSecuritiesMap.containsKey(CREATE.toString())
    ) builder.addAnnotations(methodLevelSecuritiesMap.get(CREATE.toString()));
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", apiSpec.getSimpleName() + "Input");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genBatchCreate(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName =
      "create" + toPlural(pascalCaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(asParamList(apiSpec.getElement()))
      .addStatement("return apiLogic.batchCreate(input)")
      .returns(listOf(apiSpec.getElement()));
    if (
      methodLevelSecuritiesMap.containsKey(BATCH_CREATE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(BATCH_CREATE.toString())
    );
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", inBrackets(apiSpec.getSimpleName() + "Input"));
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genUpdate(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName = "update" + pascalCaseNameOf(apiSpec.getElement());
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(parameterizeType(apiSpec.getElement()))
      .addStatement("return apiLogic.update(input)")
      .returns(TypeName.get(apiSpec.getElement().asType()));
    if (
      methodLevelSecuritiesMap.containsKey(UPDATE.toString())
    ) builder.addAnnotations(methodLevelSecuritiesMap.get(UPDATE.toString()));
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", apiSpec.getSimpleName() + "Input");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genBatchUpdate(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName =
      "update" + toPlural(pascalCaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(asParamList(apiSpec.getElement()))
      .addStatement("return apiLogic.batchUpdate(input)")
      .returns(listOf(apiSpec.getElement()));
    if (
      methodLevelSecuritiesMap.containsKey(BATCH_UPDATE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(BATCH_UPDATE.toString())
    );
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", inBrackets(apiSpec.getSimpleName() + "Input"));
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genDelete(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName = "delete" + pascalCaseNameOf(apiSpec.getElement());
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(parameterizeType(apiSpec.getElement()))
      .addStatement("return apiLogic.delete(input)")
      .returns(TypeName.get(apiSpec.getElement().asType()));
    if (
      methodLevelSecuritiesMap.containsKey(DELETE.toString())
    ) builder.addAnnotations(methodLevelSecuritiesMap.get(DELETE.toString()));
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", apiSpec.getSimpleName() + "Input");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genBatchDelete(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName =
      "delete" + toPlural(pascalCaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(asParamList(apiSpec.getElement()))
      .addStatement("return apiLogic.batchDelete(input)")
      .returns(listOf(apiSpec.getElement()));
    if (
      methodLevelSecuritiesMap.containsKey(BATCH_DELETE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(BATCH_DELETE.toString())
    );
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", inBrackets(apiSpec.getSimpleName() + "Input"));
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genArchive(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName = "archive" + pascalCaseNameOf(apiSpec.getElement());
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(parameterizeType(apiSpec.getElement()))
      .addStatement("return apiLogic.archive(input)")
      .returns(TypeName.get(apiSpec.getElement().asType()));
    if (
      methodLevelSecuritiesMap.containsKey(ARCHIVE.toString())
    ) builder.addAnnotations(methodLevelSecuritiesMap.get(ARCHIVE.toString()));
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", apiSpec.getSimpleName() + "Input");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genBatchArchive(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName =
      "archive" + toPlural(pascalCaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(asParamList(apiSpec.getElement()))
      .addStatement("return apiLogic.batchArchive(input)")
      .returns(listOf(apiSpec.getElement()));
    if (
      methodLevelSecuritiesMap.containsKey(BATCH_ARCHIVE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(BATCH_ARCHIVE.toString())
    );
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", inBrackets(apiSpec.getSimpleName() + "Input"));
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genDeArchive(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName = "deArchive" + pascalCaseNameOf(apiSpec.getElement());
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(parameterizeType(apiSpec.getElement()))
      .addStatement("return apiLogic.deArchive(input)")
      .returns(TypeName.get(apiSpec.getElement().asType()));
    if (
      methodLevelSecuritiesMap.containsKey(DE_ARCHIVE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(DE_ARCHIVE.toString())
    );
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", apiSpec.getSimpleName() + "Input");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genBatchDeArchive(GraphQLQueryBuilder clientQueryBuilder) {
    String mutationName =
      "deArchive" + toPlural(pascalCaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(Modifier.PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(asParamList(apiSpec.getElement()))
      .addStatement("return apiLogic.batchDeArchive(input)")
      .returns(listOf(apiSpec.getElement()));
    if (
      methodLevelSecuritiesMap.containsKey(BATCH_DE_ARCHIVE.toString())
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(BATCH_DE_ARCHIVE.toString())
    );
    clientQueryBuilder.setQueryType(MUTATION);
    clientQueryBuilder.setQueryName(mutationName);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", inBrackets(apiSpec.getSimpleName() + "Input"));
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genGetArchivedPaginatedBatch(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    final String name =
      "archived" + toPlural(pascalCaseNameOf(apiSpec.getElement()));
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(name)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(
        ParameterSpec.builder(ClassName.get(PageRequest.class), "input").build()
      )
      .addCode(initSortByIfNull(apiSpec.getElement()))
      .addStatement("return apiLogic.getArchivedPaginatedBatch(input)")
      .returns(pageType(apiSpec.getElement()));
    if (
      methodLevelSecuritiesMap.containsKey(
        GET_ARCHIVED_PAGINATED_BATCH.toString()
      )
    ) builder.addAnnotations(
      methodLevelSecuritiesMap.get(GET_ARCHIVED_PAGINATED_BATCH.toString())
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(name);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", "PageRequestInput");
        }
      }
    );
    return builder.build();
  }

  private MethodSpec genFreeTextSearchBy(
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    final String name =
      camelcaseNameOf(apiSpec.getElement()) + "FreeTextSearch";
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(name)
      .addAnnotation(graphqlQueryAnnotation())
      .addModifiers(PUBLIC)
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(FreeTextSearchPageRequest.class), "input")
          .build()
      )
      .addCode(initSortByIfNull(apiSpec.getElement()))
      .addStatement("return apiLogic.freeTextSearch(input)")
      .returns(pageType(apiSpec.getElement()));
    val textSearchBySecurity = apiSpec.getAnnotation(
      WithApiFreeTextSearchByFields.class
    );

    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        textSearchBySecurity,
        ""
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(textSearchBySecurity, "")
    );
    clientQueryBuilder.setQueryType(QUERY);
    clientQueryBuilder.setQueryName(name);
    clientQueryBuilder.setVars(
      new LinkedHashMap<String, String>() {
        {
          put("input", "FreeTextSearchPageRequestInput");
        }
      }
    );
    return builder.build();
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

  private MethodSpec genFreeTextSearchInElementCollection(
    VariableElement elemCollection,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val queryName =
      "freeTextSearch" +
      pascalCaseNameOf(elemCollection) +
      "Of" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = elemCollection.getAnnotation(ElementCollectionApi.class);
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(FreeTextSearchPageRequest.class), "input")
          .build()
      )
      .addStatement(
        "return apiLogic.getFreeTextSearchPaginatedBatchInElementCollection(" +
        "owner, input, $S, $L" +
        ")",
        camelcaseNameOf(elemCollection),
        isCustomElementCollectionApiHooks(config)
          ? elementCollectionApiHooksName(elemCollection)
          : "null"
      )
      .returns(pageType(elemCollection));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "FreeTextSearch"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "FreeTextSearch")
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

  private MethodSpec genGetPaginatedBatchInElementCollection(
    VariableElement elemCollection,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val queryName =
      camelcaseNameOf(elemCollection) +
      "Of" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = elemCollection.getAnnotation(ElementCollectionApi.class);
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(
        ParameterSpec.builder(ClassName.get(PageRequest.class), "input").build()
      )
      .addStatement(
        "return apiLogic.getPaginatedBatchInElementCollection(" +
        "owner, input, $S, $L" +
        ")",
        camelcaseNameOf(elemCollection),
        isCustomElementCollectionApiHooks(config)
          ? elementCollectionApiHooksName(elemCollection)
          : "null"
      )
      .returns(pageType(elemCollection));
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

  private MethodSpec genRemoveFromElementCollection(
    VariableElement elemCollection,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val mutationName =
      "remove" +
      pascalCaseNameOf(elemCollection) +
      "From" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = elemCollection.getAnnotation(ElementCollectionApi.class);
    val collectionTypeName = collectionTypeName(elemCollection);
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(asParamList(collectionTypeName))
      .addStatement(
        "return apiLogic.removeFromElementCollection(" +
        "owner, $S, input, $L" +
        ")",
        camelcaseNameOf(elemCollection),
        isCustomElementCollectionApiHooks(config)
          ? elementCollectionApiHooksName(elemCollection)
          : "null"
      )
      .returns(listOf(collectionTypeName));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "Remove"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "Remove")
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

  private MethodSpec genAddToElementCollection(
    VariableElement elemCollection,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val mutationName =
      "add" +
      pascalCaseNameOf(elemCollection) +
      "To" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = elemCollection.getAnnotation(ElementCollectionApi.class);
    val collectionTypeName = collectionTypeName(elemCollection);
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(asParamList(collectionTypeName))
      .addStatement(
        "return apiLogic.addToElementCollection(" +
        "owner, $S, input, $L" +
        ")",
        camelcaseNameOf(elemCollection),
        isCustomElementCollectionApiHooks(config)
          ? elementCollectionApiHooksName(elemCollection)
          : "null"
      )
      .returns(listOf(collectionTypeName));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "Add"
      )
    ) builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Add"));
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

  private MethodSpec genFreeTextSearchInMapElementCollection(
    FieldGraphQLApiSpec elemCollectionSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val queryName =
      "freeTextSearch" +
      pascalCaseNameOf(elemCollectionSpec.getElement()) +
      "Of" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = elemCollectionSpec.getAnnotation(
      MapElementCollectionApi.class
    );
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(FreeTextSearchPageRequest.class), "input")
          .build()
      )
      .addStatement(
        "return apiLogic.getFreeTextSearchPaginatedBatchInMapElementCollection(" +
        "owner, input, $S, $L" +
        ")",
        camelcaseNameOf(elemCollectionSpec.getElement()),
        isCustomElementCollectionApiHooks(config)
          ? mapElementCollectionApiHooksName(elemCollectionSpec.getElement())
          : "null"
      )
      .returns(mapEntryListPageType(elemCollectionSpec.getElement()));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "FreeTextSearch"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "FreeTextSearch")
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

  private MethodSpec genGetPaginatedBatchInMapElementCollection(
    FieldGraphQLApiSpec elemCollectionSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val queryName =
      camelcaseNameOf(elemCollectionSpec.getElement()) +
      "Of" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = elemCollectionSpec.getAnnotation(
      MapElementCollectionApi.class
    );
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(queryName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlQueryAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(ParameterSpec.builder(PageRequest.class, "input").build())
      .addStatement(
        "return apiLogic.getPaginatedBatchInMapElementCollection(" +
        "owner, input, $S, $L" +
        ")",
        camelcaseNameOf(elemCollectionSpec.getElement()),
        isCustomElementCollectionApiHooks(config)
          ? mapElementCollectionApiHooksName(elemCollectionSpec.getElement())
          : "null"
      )
      .returns(mapEntryListPageType(elemCollectionSpec.getElement()));
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

  private MethodSpec genRemoveFromMapElementCollection(
    FieldGraphQLApiSpec elemCollectionSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val mutationName =
      "remove" +
      pascalCaseNameOf(elemCollectionSpec.getElement()) +
      "From" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = elemCollectionSpec.getAnnotation(
      MapElementCollectionApi.class
    );
    val collectionTypeName = mapOf(elemCollectionSpec.getElement());
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(asParamMapKeyList(elemCollectionSpec.getElement())) //TODO - validate
      .addStatement(
        "return apiLogic.removeFromMapElementCollection(" +
        "owner, $S, input, $L" +
        ")",
        camelcaseNameOf(elemCollectionSpec.getElement()),
        isCustomElementCollectionApiHooks(config)
          ? mapElementCollectionApiHooksName(elemCollectionSpec.getElement())
          : "null"
      )
      .returns(mapOf(elemCollectionSpec.getElement()));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "Remove"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "Remove")
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

  private MethodSpec genAddToMapElementCollection(
    FieldGraphQLApiSpec elemCollectionSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val mutationName =
      "add" +
      pascalCaseNameOf(elemCollectionSpec.getElement()) +
      "To" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = elemCollectionSpec.getAnnotation(
      MapElementCollectionApi.class
    );
    val collectionTypeName = collectionTypeName(
      elemCollectionSpec.getElement()
    );
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder(mutationName)
      .addModifiers(PUBLIC)
      .addAnnotation(graphqlMutationAnnotation())
      .addParameter(
        ParameterSpec
          .builder(ClassName.get(apiSpec.getElement()), "owner")
          .build()
      )
      .addParameter(asParamMap(elemCollectionSpec.getElement()))
      .addStatement(
        "return apiLogic.addToMapElementCollection(" +
        "owner, $S, input, $L" +
        ")",
        camelcaseNameOf(elemCollectionSpec.getElement()),
        isCustomElementCollectionApiHooks(config)
          ? elementCollectionApiHooksName(elemCollectionSpec.getElement())
          : "null"
      )
      .returns(mapOf(elemCollectionSpec.getElement()));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "Put"
      )
    ) builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Put"));
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

  private MethodSpec genGetEmbedded(FieldGraphQLApiSpec fieldSpec) {
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
        "return apiLogic.getEmbedded(input, $S, $L)",
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

  private String collectionTypeSimpleName(TypeName collectionTypeName) {
    final String name = collectionTypeName.toString();
    return name.substring(name.lastIndexOf(".") + 1);
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
        processingEnv,
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
          entitiesMap.get(getCollectionType(fkSpec.getElement()))
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
          entitiesMap.get(getCollectionType(fkSpec.getElement()))
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

  private MethodSpec genUpdateInEntityCollection(
    FieldGraphQLApiSpec fkSpec,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    val mutationName =
      "update" +
      pascalCaseNameOf(fkSpec.getElement()) +
      "Of" +
      pascalCaseNameOf(apiSpec.getElement());
    val config = fkSpec.getAnnotation(EntityCollectionApi.class);
    val hasApiHooks = isCustomEntityCollectionApiHooks(config);
    final TypeName collectionTypeName = collectionTypeName(fkSpec.getElement());
    val input = asParamList(collectionTypeName);
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
        "return apiLogic.updateEntityCollection(owner, $L, input, $L, $S, $L)",
        dataManagerName(fkSpec.getElement()),
        hasApiHooks
          ? entityCollectionApiHooksName(fkSpec.getElement())
          : "null",
        fkSpec.getSimpleName(),
        fkSpec.getSimpleName() + SubscriptionsLogicService.class.getSimpleName()
      )
      .returns(listOf(collectionTypeName));
    if (
      SecurityAnnotationsFactory.areSecurityAnnotationsPresent(
        config,
        "",
        "UpdateIn"
      )
    ) builder.addAnnotations(
      SecurityAnnotationsFactory.of(config, "", "UpdateIn")
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

  private List<MethodSpec> toApiFindByEndpoints(
    FieldGraphQLApiSpec fieldSpec,
    ApifiClientFactory clientFactory
  ) {
    List<MethodSpec> methodsToAdd = new ArrayList<MethodSpec>();
    val fieldName = camelcaseNameOf(fieldSpec.getElement());
    val fieldPascalCaseName = pascalCaseNameOf(fieldSpec.getElement());
    val fieldType = ClassName.get(fieldSpec.getElement().asType());

    val apiFindByAnnotation = fieldSpec.getAnnotation(ApiFindBy.class);
    val apiFindByUniqueAnnotation = fieldSpec.getAnnotation(
      ApiFindByUnique.class
    );
    val apiFindAllByAnnotation = fieldSpec.getAnnotation(ApiFindAllBy.class);
    final String inputTypeSimpleName = getInputTypeSimpleName(
      fieldSpec.getSimpleName(),
      fieldSpec.getElement().asType().toString()
    );

    if (apiFindByAnnotation != null) {
      val clientBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        entityName
      );
      final String name =
        "find" +
        toPlural(pascalCaseNameOf(apiSpec.getElement())) +
        "By" +
        fieldPascalCaseName;
      val apiFindBy = MethodSpec
        .methodBuilder(name)
        .returns(listOf(apiSpec.getElement()))
        .addAnnotation(graphqlQueryAnnotation())
        .addAnnotations(SecurityAnnotationsFactory.of(apiFindByAnnotation))
        .addModifiers(PUBLIC)
        .addParameter(ParameterSpec.builder(fieldType, fieldName).build())
        .addStatement(
          "return apiLogic.apiFindBy($S, $L)",
          fieldName,
          fieldName
        );
      methodsToAdd.add(apiFindBy.build());
      clientBuilder.setQueryType(QUERY);
      clientBuilder.setQueryName(name);
      clientBuilder.setVars(
        new LinkedHashMap<String, String>() {
          {
            put(fieldName, inputTypeSimpleName);
          }
        }
      );
      clientFactory.addQuery(clientBuilder);
    } else if (apiFindByUniqueAnnotation != null) {
      val clientBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        INSTANCE,
        entityName
      );
      clientBuilder.setFindByUniqueFieldType(
        resolveTypescriptType(
          toSimpleName(fieldType.toString()),
          allEntityTypesSimpleNames
        )
      );
      final String name =
        "find" +
        pascalCaseNameOf(apiSpec.getElement()) +
        "ByUnique" +
        fieldPascalCaseName;
      methodsToAdd.add(
        MethodSpec
          .methodBuilder(name)
          .returns(ClassName.get(apiSpec.getElement()))
          .addAnnotation(graphqlQueryAnnotation())
          .addAnnotations(
            SecurityAnnotationsFactory.of(apiFindByUniqueAnnotation)
          )
          .addModifiers(PUBLIC)
          .addParameter(fieldType, fieldName)
          .addStatement(
            "return apiLogic.apiFindByUnique($S, $L)",
            fieldName,
            fieldName
          )
          .build()
      );
      clientBuilder.setQueryType(QUERY);
      clientBuilder.setQueryName(name);
      clientBuilder.setVars(
        new LinkedHashMap<String, String>() {
          {
            put(fieldName, inputTypeSimpleName);
          }
        }
      );
      clientFactory.addQuery(clientBuilder);
    }

    if (apiFindAllByAnnotation != null) {
      val clientBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        entityName
      );
      final String name =
        "find" +
        toPlural(pascalCaseNameOf(apiSpec.getElement())) +
        "By" +
        toPlural(fieldPascalCaseName);
      methodsToAdd.add(
        MethodSpec
          .methodBuilder(name)
          .returns(listOf(apiSpec.getElement()))
          .addAnnotation(graphqlQueryAnnotation())
          .addAnnotations(SecurityAnnotationsFactory.of(apiFindAllByAnnotation))
          .addModifiers(PUBLIC)
          .addParameter(listOf(fieldType), toPlural(fieldName))
          .addStatement(
            "return apiLogic.apiFindAllBy($S, $L)",
            fieldName,
            toPlural(fieldName)
          )
          .build()
      );
      clientBuilder.setQueryType(QUERY);
      clientBuilder.setQueryName(name);
      clientBuilder.setVars(
        new LinkedHashMap<String, String>() {
          {
            put(fieldName, inputTypeSimpleName);
          }
        }
      );
      clientFactory.addQuery(clientBuilder);
    }
    return methodsToAdd;
  }

  private String getInputTypeSimpleName(String simpleName, String packageName) {
    return isPrimitive(packageName)
      ? resolveSimpleTypeName(simpleName)
      /*+ "!" */: simpleName + "Input";
  }

  private String resolveSimpleTypeName(String simpleName) {
    /*val n = simpleName.toLowerCase();
            switch (n){
                case "long":;
                case "Long": return "Long";
                case "in"
            }
            if(n.equals("long") || n.equals("integer") || n.equals("int") || n.equals("short") || n.equals("byte"))
                return "Int";
            else if(n.equals("float") || n.equals("double"))
                return "Float";
            else
                return "String";*/
    return simpleName;
  }

  //field spec helpers
  private FieldSpec dataManager(TypeElement entity, String namePrefix) {
    String suffix = namePrefix.endsWith("DataManager") ? "" : "DataManager";
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(DataManager.class),
          ClassName.get(entity)
        ),
        namePrefix + suffix
      )
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private FieldSpec defaultApiHooks() {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(ApiHooks.class),
          ClassName.get(apiSpec.getElement())
        ),
        "apiHooks"
      )
      .addAnnotation(
        AnnotationSpec
          .builder(Setter.class)
          .addMember("onMethod_", "@$T(required = false)", Autowired.class)
          .build()
      )
      .addModifiers(PRIVATE)
      .build();
  }

  private FieldSpec apiLogic() {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(ApiLogic.class),
          ClassName.get(apiSpec.getElement())
        ),
        "apiLogic"
      )
      .addAnnotation(Getter.class)
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private FieldSpec subscriptionsLogicService() {
    return FieldSpec
      .builder(
        ParameterizedTypeName.get(
          ClassName.get(SubscriptionsLogicService.class),
          ClassName.get(apiSpec.getElement())
        ),
        "subscriptionsLogicService"
      )
      .addAnnotation(Getter.class)
      .addModifiers(PRIVATE, FINAL)
      .build();
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

  private FieldSpec testSubscriptionsHandlerField() {
    return FieldSpec
      .builder(TestSubscriptionsHandler.class, "testSubscriptionsHandler")
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private FieldSpec configValues() {
    return FieldSpec
      .builder(ConfigValues.class, "configValues")
      .addAnnotation(Getter.class)
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  //misc util methods
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

  private void addApiHooksIfPresent(
    VariableElement entityCollectionField,
    TypeSpec.Builder testableServiceBuilder,
    TypeSpec.Builder serviceBuilder,
    EntityCollectionApi entityCollectionApi
  ) {
    if (!isCustomEntityCollectionApiHooks(entityCollectionApi)) return;
    final FieldSpec apiHooks = entityCollectionApiHooks(entityCollectionField);
    serviceBuilder.addField(apiHooks);
    testableServiceBuilder.addField(apiHooks);
  }

  private boolean isApiFindByAnnotated(FieldGraphQLApiSpec fieldSpec) {
    return (
      fieldSpec.getAnnotation(ApiFindBy.class) != null ||
      fieldSpec.getAnnotation(ApiFindAllBy.class) != null ||
      fieldSpec.getAnnotation(ApiFindByUnique.class) != null
    );
  }

  private boolean isCustomEntityCollectionApiHooks(EntityCollectionApi config) {
    if (config == null) return false;
    return !getApiHooksTypeName(config)
      .toString()
      .equals(NullEntityCollectionApiHooks.class.getCanonicalName());
  }

  private boolean isCustomElementCollectionApiHooks(
    ElementCollectionApi config
  ) {
    if (config == null) return false;
    return !getApiHooksTypeName(config)
      .toString()
      .equals(NullElementCollectionApiHooks.class.getCanonicalName());
  }

  private boolean isCustomElementCollectionApiHooks(
    MapElementCollectionApi config
  ) {
    if (config == null) return false;
    return !getApiHooksTypeName(config)
      .toString()
      .equals(NullMapElementCollectionApiHooks.class.getCanonicalName());
  }

  private boolean containsFieldName(
    WithApiFreeTextSearchByFields freeTextSearchByFields,
    FieldGraphQLApiSpec fieldSpec
  ) {
    for (int i = 0; i < freeTextSearchByFields.value().length; i++) {
      if (
        freeTextSearchByFields.value()[i].equals(fieldSpec.getSimpleName())
      ) return true;
    }
    return false;
  }

  private AnnotationSpec graphqlQueryAnnotation() {
    return AnnotationSpec.builder(GraphQLQuery.class).build();
  }

  private AnnotationSpec graphqlMutationAnnotation() {
    return AnnotationSpec.builder(GraphQLMutation.class).build();
  }

  private CodeBlock initSortByIfNull(TypeElement entityType) {
    return CodeBlock
      .builder()
      .beginControlFlow("if(input.getSortBy() == null)")
      .addStatement("input.setSortBy($S)", getIdFieldName(entityType))
      .endControlFlow()
      .build();
  }

  private static AnnotationSpec namedGraphQLQuery(String queryName) {
    return AnnotationSpec
      .builder(GraphQLQuery.class)
      .addMember("name", "$S", queryName)
      .build();
  }

  private TypeName testableGraphQLServiceInterface() {
    return ParameterizedTypeName.get(
      ClassName.get(TestGraphQLService.class),
      ClassName.get(apiSpec.getElement())
    );
  }

  private static boolean isPrimitive(String packageName) {
    return packageName.contains("java.lang");
  }

  private void registerCollectionsTypes(
    Map<String, ClassName> collectionsTypes
  ) {
    this.fieldGraphQLApiSpecs.forEach(fieldGraphQLApiSpec -> {
        val fieldElement = fieldGraphQLApiSpec.getElement();
        if (isIterable(fieldElement.asType(), processingEnv)) {
          String key =
            apiSpec.getSimpleName() + "." + fieldGraphQLApiSpec.getSimpleName();
          ClassName value = ClassName.bestGuess(
            fieldElement
              .asType()
              .toString()
              .replaceAll(".+<", "")
              .replaceAll(">", "")
          );
          collectionsTypes.put(key, value);
        }
      });
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
