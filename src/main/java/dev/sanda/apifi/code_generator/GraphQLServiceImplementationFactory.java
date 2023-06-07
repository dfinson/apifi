package dev.sanda.apifi.code_generator;

import com.squareup.javapoet.*;
import dev.sanda.apifi.code_generator.entity.CustomEndpointsAggregator;
import dev.sanda.apifi.service.graphql_config.GraphQLInstanceFactory;
import dev.sanda.apifi.service.graphql_config.GraphQLSubscriptionSupport;
import graphql.GraphQL;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.AsyncExecutionStrategy;
import graphql.execution.instrumentation.dataloader.DataLoaderDispatcherInstrumentation;
import io.leangen.graphql.GraphQLSchemaGenerator;
import lombok.Getter;
import lombok.val;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static dev.sanda.apifi.utils.ApifiStaticUtils.toSimpleCamelcaseName;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

public class GraphQLServiceImplementationFactory {

  public GraphQLServiceImplementationFactory(
    RoundEnvironment roundEnvironment,
    ProcessingEnvironment processingEnvironment
  ) {
    this.customEndpointServices =
      new CustomEndpointsAggregator(roundEnvironment, processingEnvironment)
        .customEndpointServices();
  }

  public TypeSpec generate(List<String> services) {
    return generateGraphQLService(services);
  }

  private final Set<FieldSpec> customEndpointServices;

  private TypeSpec generateGraphQLService(List<String> services) {
    val controller = TypeSpec
      .classBuilder("GraphQLServiceImplementation")
      .addModifiers(PUBLIC)
      .addAnnotation(Component.class)
      .addSuperinterface(GraphQLInstanceFactory.class)
      .addMethod(genGraphQLServiceConstructor(services))
      .addFields(genGraphQLServiceFields(services))
      .addMethod(genGraphQLServiceInit(services))
      .addMethod(graphQLInstanceGetter());
    return controller.build();
  }

  private MethodSpec graphQLInstanceGetter() {
    return MethodSpec
      .methodBuilder("getGraphQLInstance")
      .addModifiers(PUBLIC)
      .returns(ClassName.get(GraphQL.class))
      .addStatement("return graphQLInstanceBuilder.build()")
      .build();
  }

  private MethodSpec genGraphQLServiceInit(List<String> services) {
    MethodSpec.Builder builder = MethodSpec
      .methodBuilder("init")
      .addAnnotation(PostConstruct.class)
      .addModifiers(PRIVATE)
      .returns(void.class);
    builder.addCode(genInitGraphQLSchemaCodeBlock(services));
    builder.addStatement(
      "hasSubscriptionsSupport.setHasSubscriptions(schema.getSchema().getSubscriptionType() != null)"
    );
    return builder.build();
  }

  private CodeBlock genInitGraphQLSchemaCodeBlock(List<String> services) {
    StringBuilder code = new StringBuilder();
    code.append("$T schema = new $T()\n");
    List<Object> args = new ArrayList<>(
      Arrays.asList(val.class, GraphQLSchemaGenerator.class)
    );
    for (val service : services) {
      code
        .append("\t\t.withOperationsFromSingleton(")
        .append(toSimpleCamelcaseName(service))
        .append(", ")
        .append("$T")
        .append(".class")
        .append(")\n");
      args.add(ClassName.bestGuess(service));
    }
    for (val customServiceField : customEndpointServices) {
      code
        .append("\t\t.withOperationsFromSingleton(")
        .append(customServiceField.name)
        .append(", ")
        .append("$T")
        .append(".class")
        .append(")\n");
      args.add(customServiceField.type);
    }
    code.append("\t\t.generateExecutable();\n");

    // Add code to initialize DataLoaderRegistry
    code.append("this.dataLoaderRegistry = new $T();\n");
    args.add(DataLoaderRegistry.class);
    code.append(
      "schema.getBatchLoaders().forEach((loaderName, batchLoader) -> {\n"
    );
    code.append(
      "\tthis.dataLoaderRegistry.register(loaderName, $T.newDataLoader(batchLoader));\n"
    );
    code.append("});\n");
    args.add(DataLoader.class);

    val schemaInit = CodeBlock
      .builder()
      .add(code.toString(), args.toArray())
      .build();
    val graphQlInstanceInit = CodeBlock
      .builder()
      .add(
        "graphQLInstanceBuilder = $T\n\t" +
        ".newGraphQL(schema.getSchema())\n\t" +
        ".queryExecutionStrategy(new $T())\n\t" +
        ".instrumentation(new $T())\n\t" + // Use DataLoaderDispatcherInstrumentation
        ".instrumentation(new $T(maxQueryDepth));\n", // Use MaxQueryDepthInstrumentation
        GraphQL.class,
        AsyncExecutionStrategy.class, // Use AsyncExecutionStrategy
        DataLoaderDispatcherInstrumentation.class, // Use DataLoaderDispatcherInstrumentation
        MaxQueryDepthInstrumentation.class // Use MaxQueryDepthInstrumentation (to prevent DOS attacks via GraphQL queries with high depth)
      )
      .build();
    return CodeBlock.builder().add(schemaInit).add(graphQlInstanceInit).build();
  }

  private MethodSpec genGraphQLServiceConstructor(List<String> services) {
    MethodSpec.Builder constructorBuilder = MethodSpec
      .constructorBuilder()
      .addModifiers(PUBLIC)
      .addAnnotation(Autowired.class);

    for (val service : services) {
      constructorBuilder.addParameter(
        ParameterSpec
          .builder(ClassName.bestGuess(service), toSimpleCamelcaseName(service))
          .build()
      );
    }

    for (val customServiceField : customEndpointServices) {
      constructorBuilder.addParameter(
        ParameterSpec
          .builder(customServiceField.type, customServiceField.name)
          .build()
      );
    }

    constructorBuilder.addParameter(
      ParameterSpec
        .builder(Integer.class, "maxQueryDepth")
        .addAnnotation(
          AnnotationSpec
            .builder(Value.class)
            .addMember(
              "value",
              "$S",
              "#{new Integer('${apifi.max-query-depth:15}')}"
            )
            .build()
        )
        .build()
    );

    constructorBuilder.addParameter(
      ParameterSpec
        .builder(GraphQLSubscriptionSupport.class, "hasSubscriptionsSupport")
        .build()
    );

    constructorBuilder.addStatement(
      "this.graphQLInstanceBuilder = graphQLInstanceBuilder"
    );
    for (val service : services) {
      constructorBuilder.addStatement(
        "this." +
        toSimpleCamelcaseName(service) +
        " = " +
        toSimpleCamelcaseName(service)
      );
    }
    for (val customServiceField : customEndpointServices) {
      constructorBuilder.addStatement(
        "this." + customServiceField.name + " = " + customServiceField.name
      );
    }
    constructorBuilder.addStatement("this.maxQueryDepth = maxQueryDepth");

    constructorBuilder.addStatement(
      "this.hasSubscriptionsSupport = hasSubscriptionsSupport"
    );

    return constructorBuilder.build();
  }

  private List<FieldSpec> genGraphQLServiceFields(List<String> services) {
    val graphQLInstanceBuilderField = FieldSpec
      .builder(GraphQL.Builder.class, "graphQLInstanceBuilder", PRIVATE)
      .build();
    List<FieldSpec> fieldSpecs = new ArrayList<>();
    for (val service : services) {
      val field = FieldSpec
        .builder(
          ClassName.bestGuess(service),
          toSimpleCamelcaseName(service),
          PRIVATE
        )
        .build();
      fieldSpecs.add(field);
    }
    fieldSpecs.addAll(customEndpointServices);
    val maxQueryDepthField = FieldSpec
      .builder(Integer.class, "maxQueryDepth", PRIVATE)
      .build();

    val hasSubscriptionsSupportField = FieldSpec
      .builder(
        GraphQLSubscriptionSupport.class,
        "hasSubscriptionsSupport",
        PRIVATE
      )
      .build();

    val dataLoaderRegistryField = FieldSpec
      .builder(DataLoaderRegistry.class, "dataLoaderRegistry", PRIVATE)
      .addAnnotation(Getter.class)
      .build();

    fieldSpecs.add(graphQLInstanceBuilderField);
    fieldSpecs.add(maxQueryDepthField);
    fieldSpecs.add(hasSubscriptionsSupportField);
    fieldSpecs.add(dataLoaderRegistryField);
    return fieldSpecs;
  }
}
