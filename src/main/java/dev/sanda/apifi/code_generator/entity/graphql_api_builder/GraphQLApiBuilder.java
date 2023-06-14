package dev.sanda.apifi.code_generator.entity.graphql_api_builder;

import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.apifi.code_generator.entity.ServiceAndTestableService;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.val;

import static javax.lang.model.element.Modifier.PRIVATE;

/**
 * This class is responsible for building the GraphQL API.
 * It uses various factories to generate different parts of the API.
 */
@RequiredArgsConstructor
public class GraphQLApiBuilder {

  private final GraphQLApiBuilderParams params;

  /**
   * Builds the GraphQL API.
   *
   * @return A ServiceAndTestableService object containing the built GraphQL service and testable service.
   */
  public ServiceAndTestableService build() {
    // Generate GraphQL service builder
    val servicesBuilderFactory = new GraphQLServicesBuilderFactory(params);
    TypeSpec.Builder serviceBuilder =
      servicesBuilderFactory.generateGraphQLServiceBuilder();
    TypeSpec.Builder testableServiceBuilder =
      servicesBuilderFactory.generateTestableGraphQLServiceBuilder();

    // Generate GraphQL service fields
    val fieldsFactory = new GraphQLServiceFieldsFactory(params);
    fieldsFactory
      .generateGraphQLServiceFields()
      .forEach(fieldSpec -> {
        serviceBuilder.addField(fieldSpec);
        testableServiceBuilder.addField(fieldSpec);
      });

    // Add post construct init method
    val postConstructInitMethodSpec = postConstructInitMethodSpec();
    serviceBuilder.addMethod(postConstructInitMethodSpec);
    testableServiceBuilder.addMethod(postConstructInitMethodSpec);

    // Generate security annotations
    val graphQLApiSecurityAnnotationsFactory =
      new GraphQLAPISecurityAnnotationsFactory(params);
    graphQLApiSecurityAnnotationsFactory.generateSecurityAnnotations();

    // Generate CRUD methods
    val topLevelCrudMethodsFactory = new TopLevelCRUDMethodsFactory(params);
    topLevelCrudMethodsFactory
      .generateCrudMethods()
      .forEach(methodSpec -> {
        serviceBuilder.addMethod(methodSpec);
        testableServiceBuilder.addMethod(methodSpec);
      });

    // Generate subscription endpoints
    val topLevelSubscriptionEndpointsFactory =
      new TopLevelSubscriptionEndpointsFactory(params);
    topLevelSubscriptionEndpointsFactory
      .generateSubscriptionEndpoints()
      .forEach(methodSpec -> {
        serviceBuilder.addMethod(methodSpec);
        testableServiceBuilder.addMethod(methodSpec);
      });

    // Generate foreign key entity endpoints
    val foreignKeyEntityEndpointsFactory = new ForeignKeyEntityEndpointsFactory(
      params
    );
    foreignKeyEntityEndpointsFactory
      .getEntityCollectionEndpoints()
      .forEach(methodSpec -> {
        serviceBuilder.addMethod(methodSpec);
        testableServiceBuilder.addMethod(methodSpec);
      });
    foreignKeyEntityEndpointsFactory
      .getApiHooksFields()
      .forEach(fieldSpec -> {
        serviceBuilder.addField(fieldSpec);
        testableServiceBuilder.addField(fieldSpec);
      });

    // Generate element collection endpoints
    val elementCollectionEndpointsFactory =
      new ElementCollectionEndpointsFactory(params);
    elementCollectionEndpointsFactory
      .getElementCollectionApiEndpoints()
      .forEach(methodSpec -> {
        serviceBuilder.addMethod(methodSpec);
        testableServiceBuilder.addMethod(methodSpec);
      });

    // Generate free text search endpoints
    val freeTextSearchEndpointsFactory = new FreeTextSearchEndpointsFactory(
      params
    );
    freeTextSearchEndpointsFactory
      .getFreeTextSearchApiEndpoints()
      .forEach(methodSpec -> {
        serviceBuilder.addMethod(methodSpec);
        testableServiceBuilder.addMethod(methodSpec);
      });

    // Generate API find by endpoints
    val apiFindByEndpointsFactory = new ApiFindByEndpointsFactory(params);
    apiFindByEndpointsFactory
      .getApiFindByEndpoints()
      .forEach(methodSpec -> {
        serviceBuilder.addMethod(methodSpec);
        testableServiceBuilder.addMethod(methodSpec);
      });

    // Return the built GraphQL service and testable service
    return new ServiceAndTestableService(
      serviceBuilder.build(),
      testableServiceBuilder.build()
    );
  }

  /**
   * Generates the post construct init method.
   *
   * @return A MethodSpec object representing the post construct init method.
   */
  private MethodSpec postConstructInitMethodSpec() {
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
}
