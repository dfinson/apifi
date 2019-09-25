package org.sindaryn.apifi.generator;

import com.squareup.javapoet.*;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.var;

import org.sindaryn.apifi.annotations.ApiReadOnly;
import org.sindaryn.apifi.annotations.NonDirectlyExposable;
import org.sindaryn.apifi.security.SecurityAnnotationsHandler;
import org.sindaryn.datafi.annotations.*;
import org.springframework.stereotype.Service;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.persistence.*;
import javax.transaction.Transactional;
import java.util.*;

import static org.sindaryn.apifi.StaticUtils.getFields;
import static org.sindaryn.apifi.StaticUtils.isArchivable;
import static org.sindaryn.datafi.StaticUtils.writeToJavaFile;

/**
 * generates a single web-exposed graphql service bean for a given entity
 */
@RequiredArgsConstructor
public class GraphQLApiFactory {
    @NonNull
    private ProcessingEnvironment processingEnvironment;
    @NonNull
    private EntitiesInfoCache entitiesInfoCache;
    private FieldSpecs fieldSpecs;
    private MethodSpecs methodSpecs;
    private SecurityAnnotationsHandler securityAnnotationsHandler = new SecurityAnnotationsHandler();
    private Map<TypeName, FieldSpec> additionalDataManagers;

    protected void generateGraphQLService(TypeElement entity) {
        //we'll need these helpers
        fieldSpecs = new FieldSpecs(processingEnvironment, entitiesInfoCache);
        methodSpecs = new MethodSpecs(processingEnvironment, securityAnnotationsHandler);
        additionalDataManagers = new HashMap<>();
        //get package, class & file names for the graphql api bean to generate
        String className = entity.getQualifiedName().toString();
        int lastDot = className.lastIndexOf('.');
        String packageName = className.substring(0, lastDot);
        String simpleClassName = className.substring(lastDot + 1);
        String serviceName = simpleClassName + "GraphQLService";
        //lay out the skeletal structure..
        TypeSpec.Builder builder = TypeSpec
                .classBuilder(serviceName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Service.class)
                .addAnnotation(Transactional.class)
                .addAnnotation(GraphQLApi.class);

        //if this
        if(entity.getAnnotation(NonDirectlyExposable.class) == null){
            builder
                    .addMethod(methodSpecs.generateGetAllEndpoint(entity))
                    .addMethod(methodSpecs.generateGetByIdEndpoint(entity))
                    .addMethod(methodSpecs.generateGetCollectionByIdEndpoint(entity));

            if(isFuzzySearchable(entity)) addFuzzySearchResolver(builder, entity);

            if(entity.getAnnotation(ApiReadOnly.class) == null){
                builder
                        .addMethod(methodSpecs.generateAddEndpoint(entity))
                        .addMethod(methodSpecs.generateUpdateEndpoint(entity))
                        .addMethod(methodSpecs.generateDeleteEndpoint(entity))
                        .addMethod(methodSpecs.generateAddCollectionEndpoint(entity))
                        .addMethod(methodSpecs.generateUpdateCollectionEndpoint(entity))
                        .addMethod(methodSpecs.generateDeleteCollectionEndpoint(entity));

                if(isArchivable(entity, processingEnvironment)){
                    builder
                            .addMethod(methodSpecs.generateArchiveEndpoint(entity))
                            .addMethod(methodSpecs.generateDeArchiveEndpoint(entity))
                            .addMethod(methodSpecs.generateArchiveCollectionEndpoint(entity))
                            .addMethod(methodSpecs.generateDeArchiveCollectionEndpoint(entity));
                }
            }
        }
        builder
                .addField(fieldSpecs.metaOps(entity))
                .addField(fieldSpecs.reflectionCache())
                .addField(fieldSpecs.dataManager(entity));

        for(VariableElement field : getFields(entity)){
            if(isForeignKeyOrKeys(field))
                addEmbeddedFieldResolvers(entity, field, builder);
        }
        addGetByAndGetAllByResolvers(entity, builder);
        addCustomResolvers(entity, builder);
        handleSecurityAnnotations(builder, entity);
        writeToJavaFile(simpleClassName, packageName, builder, processingEnvironment, "GraphQL Api Service Bean");
    }

    private void addFuzzySearchResolver(TypeSpec.Builder builder, TypeElement entity) {
        builder.addMethod(methodSpecs.generateFuzzySearchEndpoint(entity));
    }

    private boolean isFuzzySearchable(TypeElement entity) {
        if(entity.getAnnotation(FuzzySearchByFields.class) != null) return true;
        for (Element enclosedElement : entity.getEnclosedElements())
            if (enclosedElement.getKind().isField() && enclosedElement.getAnnotation(FuzzySearchBy.class) != null)
                return true;
        return false;
    }

    private void addCustomResolvers(TypeElement entity, TypeSpec.Builder builder) {
        WithResolver[] resolvers = entity.getAnnotationsByType(WithResolver.class);
        if(resolvers == null || resolvers.length <= 0) return;
        for(WithResolver resolver : resolvers){
            builder.addMethod(methodSpecs.generateCustomResolverEndpoint(entity, resolver));
        }
    }

    private void addGetByAndGetAllByResolvers(TypeElement entity, TypeSpec.Builder builder) {
        for(VariableElement field : getFields(entity)){
            if(field.getAnnotation(GetAllBy.class) != null)
                builder.addMethod(methodSpecs.generateGetByAllEndpoint(entity, field));
            if(field.getAnnotation(GetBy.class) != null)
                builder.addMethod(methodSpecs.generateGetByEndpoint(entity, field));
            else if(field.getAnnotation(GetByUnique.class) != null)
                builder.addMethod(methodSpecs.generateGetByUniqueEndpoint(entity, field));
        }
    }

    private boolean isForeignKeyOrKeys(VariableElement field) {
        return
                field.getAnnotation(OneToMany.class) != null ||
                field.getAnnotation(ManyToOne.class) != null ||
                field.getAnnotation(OneToOne.class) != null ||
                field.getAnnotation(ManyToMany.class) != null;
    }

    private void addEmbeddedFieldResolvers(TypeElement entity, VariableElement field, TypeSpec.Builder builder) {
        //if field is iterable
        if(field.getAnnotation(OneToMany.class) != null || field.getAnnotation(ManyToMany.class) != null){
            //get as embedded entity collection
            builder.addMethod(methodSpecs.generateGetAsEmbeddedEntityCollection(field, entity));
            if(additionalDataManagers.get(ClassName.get(field.asType())) == null){
                var typeArgs = ((DeclaredType)field.asType()).getTypeArguments();
                addDataManager(field, builder, typeArgs);
            }
            //add to collection
            if(entitiesInfoCache.exposeDirectly(field)){
                builder.addMethod(methodSpecs.generateAttachExistingToEmbeddedCollection(field, entity));
            }else {
                builder.addMethod(methodSpecs.generateAddNewToEmbeddedCollection(field, entity));
            }
            //update in collection
            builder.addMethod(methodSpecs.generateUpdateEmbeddedCollection(field, entity));
            //remove from collection
            builder.addMethod(methodSpecs.generateRemoveFromEmbeddedCollection(field, entity));
            //inject autowired metaops for embedded entity collection type
            builder.addField(fieldSpecs.embeddedCollectionMetaOps(field));
        }else {
            addDataManager(field, builder, Collections.singletonList(field.asType()));
            builder.addMethod(methodSpecs.generateGetAsEmbeddedEntity(field, entity));
        }
    }

    private void addDataManager(VariableElement field, TypeSpec.Builder builder, List<? extends TypeMirror> typeArgs) {
        FieldSpec additionalDataManager = fieldSpecs.dataManager(typeArgs.iterator().next(), field.getSimpleName().toString());
        builder.addField(additionalDataManager);
        additionalDataManagers.put(ClassName.get(field.asType()), additionalDataManager);
    }

    private void handleSecurityAnnotations(TypeSpec.Builder builder, TypeElement element){
        val entry = securityAnnotationsHandler.handleCRUD(element, new ArrayList<>());
        List<AnnotationSpec> crudSecurityAnnotations = entry.getKey();
        if (!crudSecurityAnnotations.isEmpty())
            builder.addAnnotations(crudSecurityAnnotations);
    }
}
