package dev.sanda.apifi.generator.custom_resolver;

import com.squareup.javapoet.*;
import dev.sanda.apifi.service.CustomMutationResolver;
import dev.sanda.apifi.service.CustomQueryResolver;
import dev.sanda.apifi.service.CustomResolverType;
import dev.sanda.datafi.code_generator.query.ReturnPlurality;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import lombok.*;

import javax.lang.model.element.TypeElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dev.sanda.apifi.ApifiStaticUtils.dataManagerName;
import static dev.sanda.apifi.ApifiStaticUtils.listOf;
import static dev.sanda.apifi.service.CustomResolverType.QUERY;
import static dev.sanda.datafi.code_generator.query.ReturnPlurality.BATCH;
import static javax.lang.model.element.Modifier.PUBLIC;

@Getter @Setter
@NoArgsConstructor
public class CustomResolverApiBuildInfo {
    private TypeElement entity;
    private String qualifier;
    private CustomResolverType customResolverType;
    private List<CustomResolverArgumentParameter> customResolverArgumentParameters;
    private TypeName returnType;
    private ReturnPlurality returnPlurality;



    public ParameterizedTypeName stringObjectTuple(Class<?> clazz) {
        return ParameterizedTypeName.get(clazz, String.class, Object.class);
    }

    public TypeName getCustomResolverTypeName() {
        Class<?> baseClazz = customResolverType.equals(QUERY) ? CustomQueryResolver.class : CustomMutationResolver.class;
        return ParameterizedTypeName.get(ClassName.get(baseClazz), ClassName.get(entity), returnType);
    }
}
