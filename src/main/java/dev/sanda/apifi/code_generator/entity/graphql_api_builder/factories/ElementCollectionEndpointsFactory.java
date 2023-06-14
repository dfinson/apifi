package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import dev.sanda.apifi.annotations.ElementCollectionApi;
import dev.sanda.apifi.annotations.MapElementCollectionApi;
import dev.sanda.apifi.code_generator.client.ApifiClientFactory;
import dev.sanda.apifi.code_generator.client.GraphQLQueryBuilder;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.element_api_spec.FieldGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.ElementCollectionEndpointType;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.MapElementCollectionEndpointType;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import dev.sanda.apifi.service.api_hooks.NullElementCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.NullMapElementCollectionApiHooks;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.PageRequest;
import jakarta.persistence.ElementCollection;
import lombok.Getter;
import lombok.val;

import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.*;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.MUTATION;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.QUERY;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.ElementCollectionEndpointType.ADD_TO;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.ElementCollectionEndpointType.REMOVE__FROM;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.MapElementCollectionEndpointType.*;
import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static javax.lang.model.element.Modifier.PUBLIC;

public class ElementCollectionEndpointsFactory {

  private final GraphQLApiBuilderParams params;

  @Getter
  private final List<MethodSpec> elementCollectionApiEndpoints;

  private final EntityGraphQLApiSpec apiSpec;
  private final String entityName;
  private final Map<String, TypeElement> entitiesMap;
  private final Set<String> allEntityTypesSimpleNames;

  public ElementCollectionEndpointsFactory(GraphQLApiBuilderParams params) {
    this.params = params;
    this.apiSpec = params.getApiSpec();
    this.entityName = params.getEntityName();
    this.entitiesMap = params.getEntitiesMap();
    this.allEntityTypesSimpleNames = params.getAllEntityTypesSimpleNames();
    this.elementCollectionApiEndpoints = new ArrayList<>();
    build();
  }

  private void build() {
    val clientFactory = params.getClientFactory();
    params
      .getFieldGraphQLApiSpecs()
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
            elemCollectionSpec,
            mapElementCollectionApiConfig
          );
        }
      });
  }

  private void generateElementCollectionMethods(
    ApifiClientFactory clientFactory,
    FieldGraphQLApiSpec elemCollectionSpec,
    ElementCollectionApi config
  ) {
    val endpoints = config != null
      ? Arrays.asList(config.endpoints())
      : new ArrayList<ElementCollectionEndpointType>();
    val fkTargetType = getTypeScriptElementCollectionType(
      elemCollectionSpec.getElement(),
      params.getEnumTypes()
    );
    if (endpoints.contains(ADD_TO)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        fkTargetType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      val addToElementCollection = genAddToElementCollection(
        elemCollectionSpec.getElement(),
        clientQueryBuilder
      );
      elementCollectionApiEndpoints.add(addToElementCollection);
      clientFactory.addQuery(clientQueryBuilder);
    }
    if (endpoints.contains(REMOVE__FROM)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        ARRAY,
        fkTargetType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      val removeFromElementCollection = genRemoveFromElementCollection(
        elemCollectionSpec.getElement(),
        clientQueryBuilder
      );
      elementCollectionApiEndpoints.add(removeFromElementCollection);
      clientFactory.addQuery(clientQueryBuilder);
    }
    if (endpoints.contains(ElementCollectionEndpointType.PAGINATED__BATCH_)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        fkTargetType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      val getPaginatedBatchInElementCollection =
        genGetPaginatedBatchInElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        );
      elementCollectionApiEndpoints.add(getPaginatedBatchInElementCollection);
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
      val freeTextSearchInElementCollection =
        genFreeTextSearchInElementCollection(
          elemCollectionSpec.getElement(),
          clientQueryBuilder
        );
      elementCollectionApiEndpoints.add(freeTextSearchInElementCollection);
      clientFactory.addQuery(clientQueryBuilder);
    }
  }

  private void generateMapElementCollectionMethods(
    ApifiClientFactory clientFactory,
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
      val addToMapElementCollection = genAddToMapElementCollection(
        elemCollectionSpec,
        clientQueryBuilder
      );
      elementCollectionApiEndpoints.add(addToMapElementCollection);
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
      elementCollectionApiEndpoints.add(genRemoveFromMapElementCollection);
      clientFactory.addQuery(clientQueryBuilder);
    }
    if (endpoints.contains(PAGINATED__BATCH__)) {
      val clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        typeScriptReturnType
      );
      clientQueryBuilder.setOwnerEntityType(entityName);
      val getPaginatedBatchInMapElementCollection =
        genGetPaginatedBatchInMapElementCollection(
          elemCollectionSpec,
          clientQueryBuilder
        );
      elementCollectionApiEndpoints.add(
        getPaginatedBatchInMapElementCollection
      );
      clientFactory.addQuery(clientQueryBuilder);
    }
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
        isCustomMapElementCollectionApiHooks(config)
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
        isCustomMapElementCollectionApiHooks(config)
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
        isCustomMapElementCollectionApiHooks(config)
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

  private boolean isCustomElementCollectionApiHooks(
    ElementCollectionApi config
  ) {
    if (config == null) return false;
    return !getApiHooksTypeName(config)
      .toString()
      .equals(NullElementCollectionApiHooks.class.getCanonicalName());
  }

  private boolean isCustomMapElementCollectionApiHooks(
    MapElementCollectionApi config
  ) {
    if (config == null) return false;
    return !getApiHooksTypeName(config)
      .toString()
      .equals(NullMapElementCollectionApiHooks.class.getCanonicalName());
  }
}
