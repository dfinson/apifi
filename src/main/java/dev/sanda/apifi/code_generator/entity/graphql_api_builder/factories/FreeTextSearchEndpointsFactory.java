package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.PAGE;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.QUERY;
import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static javax.lang.model.element.Modifier.PUBLIC;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import dev.sanda.apifi.annotations.WithApiFreeTextSearchByFields;
import dev.sanda.apifi.code_generator.client.ApifiClientFactory;
import dev.sanda.apifi.code_generator.client.GraphQLQueryBuilder;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.element_api_spec.FieldGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeElement;
import lombok.Getter;
import lombok.val;

public class FreeTextSearchEndpointsFactory {

  private final GraphQLApiBuilderParams params;

  @Getter
  private final List<MethodSpec> freeTextSearchApiEndpoints;

  private final EntityGraphQLApiSpec apiSpec;
  private final String entityName;
  private final Map<String, TypeElement> entitiesMap;

  public FreeTextSearchEndpointsFactory(GraphQLApiBuilderParams params) {
    this.params = params;
    this.apiSpec = params.getApiSpec();
    this.entityName = params.getEntityName();
    this.entitiesMap = params.getEntitiesMap();
    this.freeTextSearchApiEndpoints = new ArrayList<>();
    build();
  }

  private void build() {
    ApifiClientFactory clientFactory = params.getClientFactory();
    boolean hasFreeTextSearchFields = params
      .getFieldGraphQLApiSpecs()
      .stream()
      .anyMatch(this::isFreeTextSearchAnnotated);
    if (hasFreeTextSearchFields) {
      GraphQLQueryBuilder clientQueryBuilder = new GraphQLQueryBuilder(
        entitiesMap.values(),
        PAGE,
        entityName
      );

      MethodSpec freeTextSearchBy = genFreeTextSearchBy(clientQueryBuilder);
      freeTextSearchApiEndpoints.add(freeTextSearchBy);
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
}
