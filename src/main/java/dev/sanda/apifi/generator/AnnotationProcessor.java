package dev.sanda.apifi.generator;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.apifi.annotations.WithCRUDResolvers;
import dev.sanda.apifi.generator.entity.CRUDResolvers;
import dev.sanda.apifi.generator.entity.EntityApiGenerator;
import dev.sanda.apifi.generator.entity.ServiceAndTest;
import lombok.val;
import lombok.var;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.ApifiStaticUtils.getGraphQLApiEntities;
import static dev.sanda.datafi.DatafiStaticUtils.getBasePackage;


/**
 * Iterates over all elements annotated with @GraphQLApiEntity and generates the complete api
 */
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@SupportedAnnotationTypes({"*"})
@AutoService(Processor.class)
public class AnnotationProcessor extends AbstractProcessor {
    private String basePackage;
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnvironment) {
        basePackage = getBasePackage(roundEnvironment);
        Set<? extends TypeElement> entities = getGraphQLApiEntities(annotations, roundEnvironment);
        Map<String, TypeElement> entitiesMap =
                entities
                .stream()
                .collect(
                        Collectors.toMap(type -> type.getQualifiedName().toString(), type -> type)
                );
        entities.forEach(entity -> generateApiForEntity(entity, entitiesMap));
        return false;
    }

    private void generateApiForEntity(TypeElement entity, Map<String, TypeElement> entitiesMap) {
        List<CRUDResolvers> crudResolvers = getCrudResolversOf(entity);
        var apiBuilder = new EntityApiGenerator.GraphQLApiBuilder(entity, entitiesMap);
        apiBuilder.setCrudResolvers(crudResolvers);
        var serviceAndTest = apiBuilder.build(processingEnv);
        writeServiceAndTestToJavaFiles(serviceAndTest);
    }

    private void writeServiceAndTestToJavaFiles(ServiceAndTest serviceAndTest) {
        final TypeSpec service = serviceAndTest.getService();
        final JavaFile serviceJavaFile = JavaFile.builder(basePackage + ".service", service).build();
        final TypeSpec test = serviceAndTest.getTest();
        final JavaFile testsJavaFile = JavaFile.builder(basePackage + ".test", test).build();
        try {
            serviceJavaFile.writeTo(System.out);
            serviceJavaFile.writeTo(processingEnv.getFiler());
            testsJavaFile.writeTo(System.out);
            testsJavaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
           processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
    }

    private List<CRUDResolvers> getCrudResolversOf(TypeElement entity) {
        val withCrudResolversAnnotation = entity.getAnnotation(WithCRUDResolvers.class);
        if(withCrudResolversAnnotation == null) return new ArrayList<>();
        return Arrays.asList(withCrudResolversAnnotation.value());
    }
}
