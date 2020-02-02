package dev.sanda.apifi.generator.api_builder;

import com.squareup.javapoet.*;
import dev.sanda.apifi.ApifiStaticUtils;
import dev.sanda.datafi.code_generator.query.ReturnPlurality;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import lombok.*;

import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static dev.sanda.apifi.ApifiStaticUtils.dataManagerName;
import static dev.sanda.apifi.ApifiStaticUtils.listOf;
import static dev.sanda.apifi.generator.api_builder.CustomResolverType.QUERY;
import static dev.sanda.datafi.code_generator.query.ReturnPlurality.BATCH;
import static javax.lang.model.element.Modifier.PUBLIC;

@Getter @Setter
@NoArgsConstructor
public class CustomResolverApiBuildInfo {
    private TypeElement entity;
    private String qualifier;
    private CustomResolverType customResolverType;
    private List<ArgumentParameter> argumentParameters;
    private TypeName returnType;
    private ReturnPlurality returnPlurality;

    public MethodSpec toMethodSpec(){
        var mb =
                MethodSpec.methodBuilder(qualifier)
                .addModifiers(PUBLIC)
                .addAnnotation(customResolverType.equals(QUERY) ? GraphQLQuery.class : GraphQLMutation.class)
                .returns(returnPlurality.equals(BATCH) ? listOf(returnType) : returnType)
                .addStatement("$T arguments = new $T()", stringObjectTuple(Map.class), stringObjectTuple(LinkedHashMap.class));
        argumentParameters.forEach(arg -> {
            mb.addParameter(ParameterSpec.builder(arg.getType(), arg.getName()).build());
            mb.addStatement("arguments.put($S, $L)", arg.getRawName(), arg.getName());
        });
        mb.addStatement("$T argumentsMap = new $T(arguments)", CustomResolverArgumentsMap.class, CustomResolverArgumentsMap.class);
        mb.addStatement("return $L.handleRequest(argumentsMap, $L, customResolverContext)", qualifier, dataManagerName(entity));
        return mb.build();
    }

    public ParameterizedTypeName stringObjectTuple(Class<?> clazz) {
        return ParameterizedTypeName.get(clazz, String.class, Object.class);
    }
}
