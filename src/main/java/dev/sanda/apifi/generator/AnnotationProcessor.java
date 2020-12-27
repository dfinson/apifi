package dev.sanda.apifi.generator;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import dev.sanda.apifi.annotations.WithCRUDEndpoints;
import dev.sanda.apifi.generator.client.ApifiClientFactory;
import dev.sanda.apifi.generator.entity.CRUDEndpoints;
import dev.sanda.apifi.generator.entity.EntityApiGenerator;
import dev.sanda.apifi.generator.entity.ServiceAndTestableService;
import lombok.val;
import lombok.var;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.getGraphQLApiEntities;
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
        val clientFactory = new ApifiClientFactory();
        Set<? extends TypeElement> entities = getGraphQLApiEntities(annotations, roundEnvironment);
        Map<String, TypeElement> entitiesMap =
                entities
                .stream()
                .collect(
                        Collectors.toMap(type -> type.getQualifiedName().toString(), type -> type)
                );
        List<String> services = new ArrayList<>();
        Map<String, ClassName> collectionsTypes = new HashMap<>();
        entities.forEach(entity -> {
            val service = generateApiForEntity(entity, entitiesMap, clientFactory, collectionsTypes);
            services.add(service);
        });
        if(!services.isEmpty()){
            val controller = GraphQLControllerFactory.generate(services);
            writeControllerToFile(controller);
            clientFactory.generate();
        }
        return false;
    }


    private void writeControllerToFile(TypeSpec controller) {
        try {
            val file = JavaFile.builder(basePackage + ".controller", controller).build();
            file.writeTo(System.out);
            file.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
    }

    private String generateApiForEntity(TypeElement entity, Map<String, TypeElement> entitiesMap, ApifiClientFactory clientFactory, Map<String, ClassName> collectionsTypes) {
        List<CRUDEndpoints> crudResolvers = getCrudResolversOf(entity);
        var apiBuilder = new EntityApiGenerator.GraphQLApiBuilder(entity, entitiesMap);
        apiBuilder.setCrudResolvers(crudResolvers);
        var serviceAndTest = apiBuilder.build(processingEnv, clientFactory, collectionsTypes);
        writeServiceAndTestToJavaFiles(serviceAndTest);
        return basePackage + ".service." + serviceAndTest.getService().name;
    }

    private void writeServiceAndTestToJavaFiles(ServiceAndTestableService serviceAndTestableService) {
        final TypeSpec service = serviceAndTestableService.getService();
        final JavaFile serviceJavaFile = JavaFile.builder(basePackage + ".service", service).build();

        final TypeSpec testableService = serviceAndTestableService.getTestableService();
        final JavaFile testableServiceJavaFile = JavaFile.builder(basePackage + ".testable_service", testableService).build();

        try {
            serviceJavaFile.writeTo(System.out);
            serviceJavaFile.writeTo(processingEnv.getFiler());
            testableServiceJavaFile.writeTo(System.out);
            testableServiceJavaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
           processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
    }

    private List<CRUDEndpoints> getCrudResolversOf(TypeElement entity) {
        val withCrudResolversAnnotation = entity.getAnnotation(WithCRUDEndpoints.class);
        if(withCrudResolversAnnotation == null) return new ArrayList<>();
        return Arrays.asList(withCrudResolversAnnotation.value());
    }
}
