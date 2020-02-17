package dev.sanda.apifi.generator;

import com.google.auto.service.AutoService;
import dev.sanda.apifi.ApifiStaticUtils;
import dev.sanda.apifi.generator.entity.EntitiesInfoCache;


import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Iterates over all elements annotated with @GraphQLApiEntity and generates the complete api
 */
@SupportedAnnotationTypes({"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class AnnotationProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        Set<? extends TypeElement> entities = ApifiStaticUtils.getGraphQLApiEntities(annotations, roundEnvironment);
        EntitiesInfoCache entitiesInfoCache = new EntitiesInfoCache(processingEnv);
        entitiesInfoCache.setAllTypeElements(annotations.stream().map(a -> (TypeElement)a).collect(Collectors.toList()));
        entitiesInfoCache.setTypeElementMap(entities);
        GraphQLApiFactory factory = new GraphQLApiFactory(processingEnv, entitiesInfoCache);
        //generate a custom graphqlApi service for each entity
        entities.forEach(factory::generateGraphQLService);
        return false;
    }
}
