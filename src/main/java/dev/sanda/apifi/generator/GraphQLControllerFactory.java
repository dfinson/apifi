package dev.sanda.apifi.generator;

import com.squareup.javapoet.*;
import dev.sanda.apifi.service.GraphQLRequestExecutor;
import graphql.GraphQL;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.batched.BatchedExecutionStrategy;
import io.leangen.graphql.GraphQLSchemaGenerator;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.val;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.PostConstruct;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static dev.sanda.apifi.utils.ApifiStaticUtils.toSimpleCamelcaseName;
import static dev.sanda.apifi.utils.ApifiStaticUtils.toSimplePascalCaseName;
import static dev.sanda.datafi.DatafiStaticUtils.toCamelCase;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;


public class GraphQLControllerFactory {
    private static final GraphQLControllerFactory instance = new GraphQLControllerFactory();
    public static TypeSpec generate(List<String> services){
        return instance.generateGraphQLController(services);
    }

    private GraphQLControllerFactory(){

    }

    private TypeSpec generateGraphQLController(List<String> services){
        val controller = TypeSpec.classBuilder("GraphQLApiController")
                .addModifiers(PUBLIC)
                .addAnnotation(RestController.class)
                .addSuperinterface(GraphQLRequestExecutor.class)
                .addFields(genGraphQLControllerFields(services))
                .addMethod(genGraphQLControllerInit(services))
                .addMethod(genGraphQLControllerEndpoint());
        return controller.build();
    }

    private MethodSpec genGraphQLControllerEndpoint() {
        val mapString2Object = ParameterizedTypeName.get(Map.class, String.class, Object.class);
        var builder = MethodSpec.methodBuilder("graphqlEndPoint")
                .addAnnotation(AnnotationSpec.builder(PostMapping.class)
                        .addMember("value", "$S", "${apifi.endpoint:/graphql}")
                        .build())
                .addModifiers(PUBLIC)
                .addParameter(ParameterSpec.builder(mapString2Object, "request")
                        .addAnnotation(RequestBody.class)
                        .build())
                .addParameter(HttpServletRequest.class, "raw")
                .addStatement("return executeQuery(request, raw, graphQLInstance)")
                .returns(mapString2Object);
        return builder.build();
    }

    private MethodSpec genGraphQLControllerInit(List<String> services) {
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
                    .append("\t.withOperationsFromSingleton(")
                    .append(toSimpleCamelcaseName(service)).append(", ")
                    .append("$T").append(".class")
                    .append(")\n");
            args.add(ClassName.bestGuess(service));
        }
        code.append(".generate();\n");
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

    private Iterable<FieldSpec> genGraphQLControllerFields(List<String> services) {

        val graphQLInstanceField = FieldSpec.builder(GraphQL.class, "graphQLInstance", PRIVATE).build();
        List<FieldSpec> fieldSpecs = new ArrayList<>();
        for(val service : services){
            val field = FieldSpec
                    .builder(ClassName.bestGuess(service), toSimpleCamelcaseName(service), PRIVATE)
                    .addAnnotation(Autowired.class)
                    .build();
            fieldSpecs.add(field);
        }
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
