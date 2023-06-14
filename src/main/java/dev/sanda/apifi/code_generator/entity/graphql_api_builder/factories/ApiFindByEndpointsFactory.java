package dev.sanda.apifi.code_generator.entity.graphql_api_builder.factories;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import dev.sanda.apifi.annotations.ApiFindAllBy;
import dev.sanda.apifi.annotations.ApiFindBy;
import dev.sanda.apifi.annotations.ApiFindByUnique;
import dev.sanda.apifi.code_generator.client.ApifiClientFactory;
import dev.sanda.apifi.code_generator.client.GraphQLQueryBuilder;
import dev.sanda.apifi.code_generator.entity.element_api_spec.EntityGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.element_api_spec.FieldGraphQLApiSpec;
import dev.sanda.apifi.code_generator.entity.graphql_api_builder.GraphQLApiBuilderParams;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import lombok.Getter;
import lombok.val;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.ARRAY;
import static dev.sanda.apifi.code_generator.client.ClientSideReturnType.INSTANCE;
import static dev.sanda.apifi.code_generator.client.GraphQLQueryType.QUERY;
import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static dev.sanda.datafi.DatafiStaticUtils.toPlural;
import static javax.lang.model.element.Modifier.PUBLIC;

public class ApiFindByEndpointsFactory {

    private final GraphQLApiBuilderParams params;
    @Getter
    private final List<MethodSpec> apiFindByEndpoints;
    private final EntityGraphQLApiSpec apiSpec;
    private final ApifiClientFactory clientFactory;

    public ApiFindByEndpointsFactory(GraphQLApiBuilderParams params) {
        this.params = params;
        this.apiSpec = params.getApiSpec();
        this.clientFactory = params.getClientFactory();
        this.apiFindByEndpoints = new ArrayList<>();
        build();
    }

    private void build() {
        params.getFieldGraphQLApiSpecs()
                .stream()
                .filter(this::isApiFindByAnnotated)
                .forEach(fieldSpec -> apiFindByEndpoints.addAll(
                        toApiFindByEndpoints(fieldSpec, clientFactory)
                ));
    }

    private boolean isApiFindByAnnotated(FieldGraphQLApiSpec fieldSpec) {
        return (
                fieldSpec.getAnnotation(ApiFindBy.class) != null ||
                        fieldSpec.getAnnotation(ApiFindAllBy.class) != null ||
                        fieldSpec.getAnnotation(ApiFindByUnique.class) != null
        );
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
        val inputTypeSimpleName = getInputTypeSimpleName(
                fieldSpec.getSimpleName(),
                fieldSpec.getElement().asType().toString()
        );

        if (apiFindByAnnotation != null) {
            val clientBuilder = new GraphQLQueryBuilder(
                    params.getEntitiesMap().values(),
                    ARRAY,
                    params.getEntityName()
            );
            val name =
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
                    params.getEntitiesMap().values(),
                    INSTANCE,
                    params.getEntityName()
            );
            clientBuilder.setFindByUniqueFieldType(
                    resolveTypescriptType(
                            toSimpleName(fieldType.toString()),
                            params.getAllEntityTypesSimpleNames()
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
                    params.getEntitiesMap().values(),
                    ARRAY,
                    params.getEntityName()
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

}

