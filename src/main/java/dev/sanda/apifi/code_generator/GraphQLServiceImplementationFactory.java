package dev.sanda.apifi.code_generator;

import com.squareup.javapoet.*;
import dev.sanda.apifi.code_generator.entity.CustomEndpointsAggregator;
import dev.sanda.apifi.service.graphql_config.GraphQLService;
import graphql.GraphQL;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.batched.BatchedExecutionStrategy;
import io.leangen.graphql.GraphQLSchemaGenerator;
import lombok.Getter;
import lombok.val;
import lombok.var;
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
    public GraphQLServiceImplementationFactory(RoundEnvironment roundEnvironment, ProcessingEnvironment processingEnvironment) {
        this.customEndpointServices = new CustomEndpointsAggregator(roundEnvironment, processingEnvironment).customEndpointServices();
    }

    public TypeSpec generate(List<String> services){
        return generateGraphQLService(services);
    }

    private final Set<FieldSpec> customEndpointServices;

    private TypeSpec generateGraphQLService(List<String> services) {
        val controller = TypeSpec.classBuilder("GraphQLServiceImplementation")
                .addModifiers(PUBLIC)
                .addAnnotation(Component.class)
                .addSuperinterface(GraphQLService.class)
                .addFields(genGraphQLServiceFields(services))
                .addMethod(genGraphQLServiceInit(services));
        return controller.build();
    }

    private MethodSpec genGraphQLServiceInit(List<String> services) {
        var builder = MethodSpec
                .methodBuilder("init")
                .addAnnotation(PostConstruct.class)
                .addModifiers(PRIVATE)
                .returns(void.class)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember("value", "$S", "deprecation")
                        .build());
        builder.addCode(genInitGraphQLSchemaCodeBlock(services));
        return builder.build();
    }

    @SuppressWarnings("deprecation")
    private CodeBlock genInitGraphQLSchemaCodeBlock(List<String> services) {
        StringBuilder code = new StringBuilder();
        code.append("$T schema = new $T()\n");
        List<Object> args = new ArrayList<>(Arrays.asList(val.class, GraphQLSchemaGenerator.class));
        for(val service : services){
            code
                    .append("\t\t.withOperationsFromSingleton(")
                    .append(toSimpleCamelcaseName(service)).append(", ")
                    .append("$T").append(".class")
                    .append(")\n");
            args.add(ClassName.bestGuess(service));
        }
        for(val customServiceField : customEndpointServices){
            code
                    .append("\t\t.withOperationsFromSingleton(")
                    .append(customServiceField.name).append(", ")
                    .append("$T").append(".class")
                    .append(")\n");
            args.add(customServiceField.type);
        }
        code.append("\t\t.generate();\n");
        val schemaInit = CodeBlock.builder().add(code.toString(), args.toArray()).build();
        val graphQlInstanceInit = CodeBlock.builder().add(
                "graphQLInstance = $T\n\t" +
                        ".newGraphQL(schema)\n\t" +
                        ".queryExecutionStrategy(new $T())\n\t" +
                        ".instrumentation(new $T(maxQueryDepth))\n\t" +
                        ".build();\n",
                GraphQL.class,
                BatchedExecutionStrategy.class,
                MaxQueryDepthInstrumentation.class).build();
        return CodeBlock.builder().add(schemaInit).add(graphQlInstanceInit).build();
    }

    private List<FieldSpec> genGraphQLServiceFields(List<String> services) {
        val graphQLInstanceField = FieldSpec.builder(GraphQL.class, "graphQLInstance", PRIVATE)
                .addAnnotation(Getter.class)
                .build();
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for(val service : services){
            val field = FieldSpec
                    .builder(ClassName.bestGuess(service), toSimpleCamelcaseName(service), PRIVATE)
                    .addAnnotation(Autowired.class)
                    .build();
            fieldSpecs.add(field);
        }
        fieldSpecs.addAll(customEndpointServices);
        val maxQueryDepthField = FieldSpec.builder(Integer.class, "maxQueryDepth", PRIVATE)
                .addAnnotation(AnnotationSpec.builder(Value.class)
                        .addMember("value", "$S",
                                "#{new Integer('${apifi.max-query-depth:15}')}")
                        .build())
                .build();
        fieldSpecs.add(graphQLInstanceField);
        fieldSpecs.add(maxQueryDepthField);
        return fieldSpecs;
    }
}
