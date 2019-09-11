package org.sindaryn.apifi.generator;

import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.squareup.javapoet.*;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import org.sindaryn.apifi.annotations.GraphQLApiEntity;
import org.sindaryn.datafi.annotations.GetBy;
import org.sindaryn.datafi.annotations.WithResolver;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.datafi.service.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.transaction.Transactional;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.*;

import static org.sindaryn.apifi.StaticUtils.getGraphQLApiEntities;
import static org.sindaryn.datafi.StaticUtils.toCamelCase;
import static org.sindaryn.datafi.StaticUtils.toPascalCase;
import static org.sindaryn.datafi.generator.DataLayerAnnotationsProcessor.resolveCustomResolvers;

/**
 * Iterates over all elements annotated with @GraphQLApiEntity and generates the complete api
 */
@SuppressWarnings("unchecked")
@SupportedAnnotationTypes({"org.sindaryn.*"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class AnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        //fetch all entities annotated with @GraphQLApiEntity

        Set<? extends TypeElement> entities = getGraphQLApiEntities(annotations, roundEnvironment);
        EntitiesInfoCache entitiesInfoCache = new EntitiesInfoCache(processingEnv);
        entitiesInfoCache.setTypeElementMap(entities);
        GraphQLApiFactory factory = new GraphQLApiFactory(processingEnv, entitiesInfoCache);
        //generate a custom graphqlApi service for each entity
        entities.forEach(factory::generateGraphQLService);
        return false;
    }
}
