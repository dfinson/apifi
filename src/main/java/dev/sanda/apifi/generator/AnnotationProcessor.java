package dev.sanda.apifi.generator;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.*;
import dev.sanda.apifi.annotations.WithCRUDEndpoints;
import dev.sanda.apifi.generator.client.ApifiClientFactory;
import dev.sanda.apifi.generator.entity.CRUDEndpoints;
import dev.sanda.apifi.generator.entity.CollectionsTypeResolver;
import dev.sanda.apifi.generator.entity.EntityApiGenerator;
import dev.sanda.apifi.generator.entity.ServiceTestableServiceAndTest;
import lombok.val;
import lombok.var;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static dev.sanda.datafi.DatafiStaticUtils.getBasePackage;
import static javax.lang.model.element.Modifier.PUBLIC;


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
        generateCollectionsTypesResolver(collectionsTypes);
        return false;
    }

    private void generateCollectionsTypesResolver(Map<String, ClassName> collectionsTypes) {
        if(collectionsTypes.isEmpty()) return;
        TypeName mapType = ParameterizedTypeName.get(ClassName.get(HashMap.class), ClassName.get(String.class), ClassName.get(Class.class));
        val builder = MethodSpec.methodBuilder("collectionsTypesResolver")
                .addAnnotation(Bean.class)
                .addModifiers(PUBLIC)
                .returns(ClassName.get(CollectionsTypeResolver.class));
        builder.addStatement("$T typeResolverMap = new $T()", mapType, mapType);
        collectionsTypes.forEach((key, type) -> builder.addStatement("typeResolverMap.put($S, $T.class)", key, type));
        builder.addStatement("return new $T(typeResolverMap)", CollectionsTypeResolver.class);
        TypeSpec typeResolverMapFactory = TypeSpec.classBuilder("TypeResolverMapFactory")
                .addModifiers(PUBLIC)
                .addAnnotation(Configuration.class)
                .addMethod(builder.build())
                .build();
        try {
            val file = JavaFile.builder(basePackage, typeResolverMapFactory).build();
            file.writeTo(System.out);
            file.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
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

    private void writeServiceAndTestToJavaFiles(ServiceTestableServiceAndTest serviceTestableServiceAndTest) {
        final TypeSpec service = serviceTestableServiceAndTest.getService();
        final JavaFile serviceJavaFile = JavaFile.builder(basePackage + ".service", service).build();

        final TypeSpec testableService = serviceTestableServiceAndTest.getTestableService();
        final JavaFile testableServiceJavaFile = JavaFile.builder(basePackage + ".testable_service", testableService).build();

        final TypeSpec test = serviceTestableServiceAndTest.getTest();
        final JavaFile testsJavaFile = JavaFile.builder(basePackage + ".test", test).build();
        try {
            serviceJavaFile.writeTo(System.out);
            serviceJavaFile.writeTo(processingEnv.getFiler());
            testableServiceJavaFile.writeTo(System.out);
            testableServiceJavaFile.writeTo(processingEnv.getFiler());
            testsJavaFile.writeTo(System.out);
            testsJavaFile.writeTo(processingEnv.getFiler());
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
