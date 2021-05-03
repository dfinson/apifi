package dev.sanda.apifi.utils.spqr_fixes;

import graphql.schema.GraphQLScalarType;
import io.leangen.graphql.generator.BuildContext;
import io.leangen.graphql.generator.OperationMapper;
import io.leangen.graphql.generator.mapping.common.CachingMapper;
import java.lang.reflect.AnnotatedType;

public class JodaScalarMapper
  extends CachingMapper<GraphQLScalarType, GraphQLScalarType> {

  @Override
  protected GraphQLScalarType toGraphQLType(
    String typeName,
    AnnotatedType javaType,
    OperationMapper operationMapper,
    BuildContext buildContext
  ) {
    buildContext.typeCache.register(typeName);
    return JodaTimeScalars.toGraphQLScalarType(javaType.getType());
  }

  @Override
  protected GraphQLScalarType toGraphQLInputType(
    String typeName,
    AnnotatedType javaType,
    OperationMapper operationMapper,
    BuildContext buildContext
  ) {
    buildContext.typeCache.register(typeName);
    return toGraphQLType(typeName, javaType, operationMapper, buildContext);
  }

  @Override
  public boolean supports(AnnotatedType type) {
    return JodaTimeScalars.isScalar(type.getType());
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
}
