package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import static dev.sanda.apifi.utils.ApifiStaticUtils.autowiredRequiredArgsConstructor;
import static javax.lang.model.element.Modifier.*;

import com.squareup.javapoet.*;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.service.graphql_subcriptions.testing_utils.TestSubscriptionsHandler;
import dev.sanda.apifi.test_utils.TestGraphQLService;
import dev.sanda.apifi.utils.ConfigValues;
import jakarta.transaction.Transactional;
import lombok.Getter;
import org.springframework.stereotype.Service;

public class GraphQLServicesBuilderFactory {

  private final EntityGraphQLApiSpec apiSpec;

  public GraphQLServicesBuilderFactory(GraphQLApiBuilderParams params) {
    this.apiSpec = params.getApiSpec();
  }

  public TypeSpec.Builder generateGraphQLServiceBuilder() {
    return TypeSpec
      .classBuilder(apiSpec.getSimpleName() + "GraphQLApiService")
      .addModifiers(PUBLIC)
      .addAnnotation(Service.class)
      .addAnnotation(Transactional.class)
      .addAnnotation(autowiredRequiredArgsConstructor());
  }

  public TypeSpec.Builder generateTestableGraphQLServiceBuilder() {
    return TypeSpec
      .classBuilder("Testable" + apiSpec.getSimpleName() + "GraphQLApiService")
      .addModifiers(PUBLIC)
      .addSuperinterface(testableGraphQLServiceInterface())
      .addAnnotation(Service.class)
      .addField(testSubscriptionsHandlerField())
      .addField(configValues())
      .addAnnotation(autowiredRequiredArgsConstructor());
  }

  private FieldSpec testSubscriptionsHandlerField() {
    return FieldSpec
      .builder(TestSubscriptionsHandler.class, "testSubscriptionsHandler")
      .addModifiers(PRIVATE, FINAL)
      .build();
  }

  private TypeName testableGraphQLServiceInterface() {
    return ParameterizedTypeName.get(
      ClassName.get(TestGraphQLService.class),
      ClassName.get(apiSpec.getElement())
    );
  }

  private FieldSpec configValues() {
    return FieldSpec
      .builder(ConfigValues.class, "configValues")
      .addAnnotation(Getter.class)
      .addModifiers(PRIVATE, FINAL)
      .build();
  }
}
