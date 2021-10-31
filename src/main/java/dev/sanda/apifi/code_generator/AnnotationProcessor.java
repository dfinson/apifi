package dev.sanda.apifi.code_generator;

import static dev.sanda.apifi.utils.ApifiStaticUtils.getGraphQLApiSpecs;
import static dev.sanda.datafi.DatafiStaticUtils.getBasePackage;
import static dev.sanda.datafi.DatafiStaticUtils.getEntityApiSpecs;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.apifi.code_generator.client.ApifiClientFactory;
import dev.sanda.apifi.code_generator.entity.CRUDEndpoints;
import dev.sanda.apifi.code_generator.entity.GraphQLApiBuilder;
import dev.sanda.apifi.code_generator.entity.ServiceAndTestableService;
import dev.sanda.apifi.code_generator.entity.SubscriptionsLogicServicesFactory;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.annotations.TransientModule;
import dev.sanda.datafi.code_generator.annotated_element_specs.AnnotatedElementSpec;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import lombok.val;

/**
 * Iterates over all elements annotated with @GraphQLApiEntity and generates the complete api
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({ "*" })
@AutoService(Processor.class)
public class AnnotationProcessor extends AbstractProcessor {

  private String basePackage;

  @Override
  public boolean process(
    Set<? extends TypeElement> annotations,
    RoundEnvironment roundEnvironment
  ) {
    if (
      !roundEnvironment
        .getElementsAnnotatedWith(TransientModule.class)
        .isEmpty()
    ) return false;
    basePackage = getBasePackage(roundEnvironment);
    val clientFactory = new ApifiClientFactory();
    List<EntityGraphQLApiSpec> entityGraphQLApiSpecs = getGraphQLApiSpecs(
      roundEnvironment,
      processingEnv
    );
    //Set<? extends TypeElement> entities = getGraphQLApiEntities(roundEnvironment);
    Map<String, TypeElement> entitiesMap = getEntitiesMap(
      entityGraphQLApiSpecs
    );
    List<String> services;
    val enums = extractEnums(roundEnvironment.getRootElements());
    Map<String, ClassName> collectionsTypes = new HashMap<>();
    services =
      entityGraphQLApiSpecs
        .stream()
        .map(
          graphQLApiSpec ->
            generateApiForEntity(
              graphQLApiSpec,
              entitiesMap,
              clientFactory,
              collectionsTypes,
              enums
            )
        )
        .collect(Collectors.toList());
    if (!services.isEmpty()) {
      val graphQLServiceImplementation = new GraphQLServiceImplementationFactory(
        roundEnvironment,
        processingEnv
      )
        .generate(services);
      writeGraphQLServiceImplementationToFile(graphQLServiceImplementation);
      clientFactory.setProcessingEnv(processingEnv);
      clientFactory.setEntities(new HashSet<>(entitiesMap.values()));
      clientFactory.setEnums(enums);
      clientFactory.setTypescriptMode(false);
      clientFactory.generate();
      clientFactory.setTypescriptMode(true);
      clientFactory.generate();
    }
    generateSubscriptionLogicServicesConfigFactory(roundEnvironment);
    return false;
  }

  private void generateSubscriptionLogicServicesConfigFactory(
    RoundEnvironment roundEnvironment
  ) {
    val entitySpecs = getEntityApiSpecs(roundEnvironment, processingEnv);
    if (entitySpecs.isEmpty()) return;
    SubscriptionsLogicServicesFactory subscriptionsLogicServicesFactory = new SubscriptionsLogicServicesFactory(
      processingEnv,
      DatafiStaticUtils.getBasePackage(roundEnvironment)
    );
    entitySpecs.forEach(
      subscriptionsLogicServicesFactory::addSubscriptionLogicService
    );
    subscriptionsLogicServicesFactory.writeToFile();
  }

  private Map<String, TypeElement> getEntitiesMap(
    List<EntityGraphQLApiSpec> entityGraphQLApiSpecs
  ) {
    return entityGraphQLApiSpecs
      .stream()
      .collect(
        Collectors.toMap(
          type -> type.getElement().getQualifiedName().toString(),
          AnnotatedElementSpec::getElement
        )
      );
  }

  private Set<TypeElement> extractEnums(Set<? extends Element> rootElements) {
    return rootElements
      .stream()
      .filter(element -> element.getKind() == ElementKind.ENUM)
      .map(element -> (TypeElement) element)
      .collect(Collectors.toSet());
  }

  private void writeGraphQLServiceImplementationToFile(
    TypeSpec graphQLServiceType
  ) {
    try {
      val file = JavaFile
        .builder(basePackage + ".graphql", graphQLServiceType)
        .build();
      file.writeTo(System.out);
      file.writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      processingEnv
        .getMessager()
        .printMessage(Diagnostic.Kind.ERROR, e.toString());
    }
  }

  private String generateApiForEntity(
    EntityGraphQLApiSpec entityGraphQLApiSpec,
    Map<String, TypeElement> entitiesMap,
    ApifiClientFactory clientFactory,
    Map<String, ClassName> collectionsTypes,
    Set<TypeElement> enumTypes
  ) {
    List<CRUDEndpoints> crudResolvers = entityGraphQLApiSpec.getMergedCrudEndpoints();
    GraphQLApiBuilder apiBuilder = new GraphQLApiBuilder(
      entityGraphQLApiSpec,
      entitiesMap,
      crudResolvers,
      enumTypes
    );
    ServiceAndTestableService serviceAndTest = apiBuilder.build(
      processingEnv,
      clientFactory,
      collectionsTypes
    );
    writeServiceAndTestToJavaFiles(serviceAndTest);
    return basePackage + ".service." + serviceAndTest.getService().name;
  }

  private void writeServiceAndTestToJavaFiles(
    ServiceAndTestableService serviceAndTestableService
  ) {
    final TypeSpec service = serviceAndTestableService.getService();
    final JavaFile serviceJavaFile = JavaFile
      .builder(basePackage + ".service", service)
      .build();

    final TypeSpec testableService = serviceAndTestableService.getTestableService();
    final JavaFile testableServiceJavaFile = JavaFile
      .builder(basePackage + ".testable_service", testableService)
      .build();

    try {
      serviceJavaFile.writeTo(System.out);
      serviceJavaFile.writeTo(processingEnv.getFiler());
      testableServiceJavaFile.writeTo(System.out);
      testableServiceJavaFile.writeTo(processingEnv.getFiler());
    } catch (IOException e) {
      processingEnv
        .getMessager()
        .printMessage(Diagnostic.Kind.ERROR, e.toString());
    }
  }
}
