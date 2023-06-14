package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import com.squareup.javapoet.*;
import dev.sanda.apifi.annotations.WithApiFreeTextSearchByFields;
import dev.sanda.apifi.code_generator.client.ClientSideReturnType;
import dev.sanda.apifi.code_generator.client.GraphQLQueryBuilder;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.code_generator.entity.operation_types_enums.CRUDEndpoints;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.PageRequest;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import lombok.val;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.*;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.MUTATION;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.QUERY;
import static dev.sanda.apifi.code_generator.entity.operation_types_enums.CRUDEndpoints.*;
import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static dev.sanda.datafi.DatafiStaticUtils.getIdType;
import static dev.sanda.datafi.DatafiStaticUtils.toPlural;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * Factory class for generating top / entity level CRUD methods.
 */

public class TopLevelCRUDMethodsFactory {

  private final GraphQLApiBuilderParams params;
  private final EntityGraphQLApiSpec apiSpec;
  private final Map<String, List<AnnotationSpec>> methodLevelSecuritiesMap;

  public TopLevelCRUDMethodsFactory(GraphQLApiBuilderParams params) {
    this.params = params;
    this.apiSpec = params.getApiSpec();
    if (
      params.getMethodLevelSecuritiesMap() == null
    ) throw new IllegalArgumentException(
      "methodLevelSecuritiesMap cannot be null when instantiating CRUDMethodsFactory"
    ); else this.methodLevelSecuritiesMap =
      params.getMethodLevelSecuritiesMap();
  }

  /**
   * Generates a map of CRUD methods.
   *
   * @return a map of CRUD methods.
   */
  public List<MethodSpec> generateCrudMethods() {
    List<MethodSpec> crudMethods = new ArrayList<>();
    val crudResolvers = params.getCrudResolvers();
    val entitiesMap = params.getEntitiesMap();
    val clientFactory = params.getClientFactory();
    // Generate CRUD methods based on the provided resolvers
    for (CRUDEndpoints endpoint : crudResolvers.keySet()) {
      if (crudResolvers.get(endpoint)) {
        val clientQueryBuilder = new GraphQLQueryBuilder(
          entitiesMap.values(),
          getReturnType(endpoint),
          getReturnTypeName(endpoint)
        );
        MethodSpec methodSpec = generateMethodSpec(
          endpoint,
          clientQueryBuilder
        );
        crudMethods.add(methodSpec);
        clientFactory.addQuery(clientQueryBuilder);
      }
    }

    return crudMethods;
  }

  private String getReturnTypeName(CRUDEndpoints endpoint) {
    switch (endpoint) {
      case GET_TOTAL_COUNT:
      case GET_TOTAL_ARCHIVED_COUNT:
        return "number";
      default:
        return params.getEntityName();
    }
  }

  private MethodSpec generateMethodSpec(
    CRUDEndpoints endpoint,
    GraphQLQueryBuilder clientQueryBuilder
  ) {
    // Generate the method specification based on the endpoint
    switch (endpoint) {
      case GET_PAGINATED_BATCH:
        return genGetPaginatedBatch(clientQueryBuilder);
      case GET_TOTAL_COUNT:
        return genGetTotalNonArchivedCount(clientQueryBuilder);
      case GET_TOTAL_ARCHIVED_COUNT:
        return genGetTotalArchivedCount(clientQueryBuilder);
      case GET_BY_ID:
        return genGetById(params.getProcessingEnv(), clientQueryBuilder);
      case GET_BATCH_BY_IDS:
        return genGetBatchByIds(params.getProcessingEnv(), clientQueryBuilder);
      case CREATE:
        return genCreate(clientQueryBuilder);
      case BATCH_CREATE:
        return genBatchCreate(clientQueryBuilder);
      case UPDATE:
        return genUpdate(clientQueryBuilder);
      case BATCH_UPDATE:
        return genBatchUpdate(clientQueryBuilder);
      case DELETE:
        return genDelete(clientQueryBuilder);
      case BATCH_DELETE:
        return genBatchDelete(clientQueryBuilder);
      case ARCHIVE:
        return genArchive(clientQueryBuilder);
      case BATCH_ARCHIVE:
        return genBatchArchive(clientQueryBuilder);
      case DE_ARCHIVE:
        return genDeArchive(clientQueryBuilder);
      case BATCH_DE_ARCHIVE:
        return genBatchDeArchive(clientQueryBuilder);
      case GET_ARCHIVED_PAGINATED_BATCH:
        return genGetArchivedPaginatedBatch(clientQueryBuilder);
      default:
        throw new IllegalArgumentException("Unsupported endpoint: " + endpoint);
    }
  }

  /**
   * Determines the return type for a given CRUD endpoint.
   *
   * @param endpoint the CRUD endpoint.
   * @return the return type.
   */
  private ClientSideReturnType getReturnType(CRUDEndpoints endpoint) {
    switch (endpoint) {
      case GET_PAGINATED_BATCH:
      case GET_ARCHIVED_PAGINATED_BATCH:
        return PAGE;
      case GET_TOTAL_COUNT:
      case GET_TOTAL_ARCHIVED_COUNT:
        return NUMBER;
      case GET_BY_ID:
      case CREATE:
      case UPDATE:
      case DELETE:
      case ARCHIVE:
      case DE_ARCHIVE:
        return INSTANCE;
      case GET_BATCH_BY_IDS:
      case BATCH_CREATE:
      case BATCH_UPDATE:
      case BATCH_DELETE:
      case BATCH_ARCHIVE:
      case BATCH_DE_ARCHIVE:
        return ARRAY;
      default:
        throw new IllegalArgumentException("Unsupported endpoint: " + endpoint);
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

  private AnnotationSpec graphqlQueryAnnotation() {
    return AnnotationSpec.builder(GraphQLQuery.class).build();
  }

  private CodeBlock initSortByIfNull(TypeElement entityType) {
    return CodeBlock
      .builder()
      .beginControlFlow("if(input.getSortBy() == null)")
      .addStatement("input.setSortBy($S)", getIdFieldName(entityType))
      .endControlFlow()
      .build();
  }

  private AnnotationSpec namedGraphQLQuery(String queryName) {
    return AnnotationSpec
      .builder(GraphQLQuery.class)
      .addMember("name", "$S", queryName)
      .build();
  }

  private String getInputTypeSimpleName(String simpleName, String packageName) {
    return isPrimitive(packageName)
      ? resolveSimpleTypeName(simpleName)
      /*+ "!" */: simpleName + "Input";
  }

  private static boolean isPrimitive(String packageName) {
    return packageName.contains("java.lang");
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

  private AnnotationSpec graphqlMutationAnnotation() {
    return AnnotationSpec.builder(GraphQLMutation.class).build();
  }
}
