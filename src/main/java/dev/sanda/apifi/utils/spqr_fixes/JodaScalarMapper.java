package dev.sanda.apifi.utils.spqr_fixes;

import graphql.schema.GraphQLScalarType;
import io.leangen.graphql.generator.BuildContext;
import io.leangen.graphql.generator.mapping.TypeMappingEnvironment;
import io.leangen.graphql.generator.mapping.common.CachingMapper;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;

public class JodaScalarMapper
        extends CachingMapper<GraphQLScalarType, GraphQLScalarType> {

    @Override
    protected GraphQLScalarType toGraphQLType(String typeName, AnnotatedType javaType, TypeMappingEnvironment env) {
        return JodaTimeScalars.toGraphQLScalarType(javaType.getType());
    }

    @Override
    protected GraphQLScalarType toGraphQLInputType(String typeName, AnnotatedType javaType, TypeMappingEnvironment env) {
        return toGraphQLType(typeName, javaType, env);
    }

    @Override
    protected String getTypeName(AnnotatedType type, BuildContext buildContext) {
        return JodaTimeScalars.toGraphQLScalarType(type.getType()).getName();
    }

    @Override
    protected String getInputTypeName(
            AnnotatedType type,
            BuildContext buildContext
    ) {
        return getTypeName(type, buildContext);
    }

    @Override
    public boolean supports(AnnotatedElement element, AnnotatedType type) {
        return JodaTimeScalars.isScalar(type.getType());
    }
}
