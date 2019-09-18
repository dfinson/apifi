package org.sindaryn.apifi.generator;


import com.squareup.javapoet.*;
import graphql.execution.batched.Batched;
import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.var;
import org.sindaryn.apifi.security.SecurityAnnotationsHandler;
import org.sindaryn.apifi.service.ApiLogic;
import org.sindaryn.datafi.annotations.WithResolver;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.lang.annotation.Annotation;
import java.util.*;

import static org.reflections.util.ConfigurationBuilder.build;
import static org.sindaryn.apifi.StaticUtils.*;
import static org.sindaryn.datafi.StaticUtils.toPlural;
import static org.sindaryn.datafi.generator.DataLayerAnnotationsProcessor.getIdType;

/**
 * all api method templates are defined here as Javapoet MethodSpecs
 */

@SuppressWarnings({"unchecked", "deprecation"})
@RequiredArgsConstructor
public class MethodSpecs {
    @NonNull
    private ProcessingEnvironment processingEnvironment;
    @NonNull
    //spring security integration helper
    private SecurityAnnotationsHandler securityAnnotationsHandler;

    //methods

    //top level

    /**
     * Get all the entities of a given type
     * @param entity The type of class for which to build the resolver
     * @return The methodSpec
     */
    public MethodSpec generateGetAllEndpoint(TypeElement entity) {
        String queryName = "all" + toPlural(pascalCaseNameOf(entity));
        MethodSpec.Builder builder =
                MethodSpec.methodBuilder(queryName)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                                .addMember("name", "$S", queryName)
                                .build())
                        .addParameter(graphQLParameter(TypeName.INT, "offset", "0"))
                        .addParameter(graphQLParameter(TypeName.INT, "limit", "50"))
                        .addStatement("return $T.getAll($T.class, $L, $L, offset, limit)",
                                ClassName.get(ApiLogic.class),//$T
                                ClassName.get(entity),//$T
                                dataManagerName(entity),//$L
                                metaOpsName(entity)//$L
                        )
                        .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }

    public MethodSpec generateFuzzySearchEndpoint(TypeElement entity) {
        String queryName = toPlural(camelcaseNameOf(entity)) + "FuzzySearch";
        MethodSpec.Builder builder =
                MethodSpec.methodBuilder(queryName)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                                .addMember("name", "$S", queryName)
                                .build())
                        .addParameter(String.class, "searchTerm")
                        .addParameter(graphQLParameter(TypeName.INT, "offset", "0"))
                        .addParameter(graphQLParameter(TypeName.INT, "limit", "50"))
                        .addStatement("return $T.fuzzySearch($T.class, $L, $L, searchTerm, offset, limit)",
                                ClassName.get(ApiLogic.class),//$T
                                ClassName.get(entity),//$T
                                dataManagerName(entity),//$L
                                metaOpsName(entity)//$L
                        )
                        .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }

    /*public MethodSpec generateGetAllSortedByEndpoint(TypeElement entity) {
        String queryName = "all" + toPlural(pascalCaseNameOf(entity));
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build())
                .addParameter(TypeName.INT, "limit")
                .addParameter(TypeName.INT, "offset")
                .addParameter(String.class, "sortedBy")
                .addParameter(Sort.Direction.class, "sortingDirection")
                .addStatement("return $T.getAll($T.class, $L, limit, offset, sortedBy, sortingDirection)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        dataManagerName(entity)//$L
                )
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }*/


    public MethodSpec generateGetByIdEndpoint(TypeElement entity) {
        String queryName = "get" + pascalCaseNameOf(entity) + "ById";
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build())
                .addParameter(getIdType(entity, processingEnvironment), "input")
                .addStatement("return $T.getById($T.class, $L, $L, input)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity)//$L
                )
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }

    public MethodSpec generateGetByUniqueEndpoint(TypeElement entity, VariableElement field) {
        String queryName = "get" + pascalCaseNameOf(entity) + "ByUnique" + pascalCaseNameOf(field);
        String fieldName = field.getSimpleName().toString();
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build())
                .addParameter(ClassName.get(field.asType()), fieldName)
                .addStatement("return $T.getByUnique($T.class, $L, $L, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity),//$L
                        queryName,//$S
                        fieldName)//$L
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, field, READ);
        return builder.build();
    }

    public MethodSpec generateGetByEndpoint(TypeElement entity, VariableElement field) {
        String queryName = "get" + toPlural(pascalCaseNameOf(entity)) + "By" + pascalCaseNameOf(field);
        String fieldName = field.getSimpleName().toString();
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build())
                .addParameter(ClassName.get(field.asType()), fieldName)
                .addStatement("return $T.getBy($T.class, $L, $L, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity),//$L
                        field.getSimpleName(),//$S
                        fieldName)//$L
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, field, READ);
        return builder.build();
    }

    public MethodSpec generateGetByAllEndpoint(TypeElement entity, VariableElement field) {
        String queryName = "getAll" + toPlural(pascalCaseNameOf(entity)) + "By" + toPlural(pascalCaseNameOf(field));
        String fieldName = field.getSimpleName().toString();
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build())
                .addParameter(listOf(field), toPlural(fieldName))
                .addStatement("return $T.getAllBy($T.class, $L, $L, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity),//$L
                        fieldName,//$S
                        toPlural(fieldName))//$L
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, field, READ);
        return builder.build();
    }

    public MethodSpec generateCustomResolverEndpoint(TypeElement entity, WithResolver resolver) {
        String queryName = resolver.name();
        Map<String, TypeName> fieldTypes = mapFieldTypes(entity);
        String resolverParams = resolverParams(resolver);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build());

        for(String arg : resolver.args()){
            builder.addParameter(fieldTypes.get(arg), arg);
        }
        argsToResolver(resolverParams, builder);
        builder.addStatement("return $T.selectBy($T.class, $L, $L, $S, args)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity),//$L
                        resolver.name())//$S
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }


    public MethodSpec generateAddEndpoint(TypeElement entity) {
        String mutationName = "add" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(parameterizeType(entity))
                .addStatement("$T entity = $T.add($L, input, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, CREATE);
        return builder.build();
    }

    public MethodSpec generateUpdateEndpoint(TypeElement entity) {
        String mutationName = "update" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(parameterizeType(entity))
                .addStatement("$T entity = $T.update($L, input, $L, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        reflectionCache,//$L
                        metaOpsName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateArchiveEndpoint(TypeElement entity) {
        String mutationName = "archive" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(parameterizeType(entity))
                .addStatement("$T entity = $T.archive($L, input, $L, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        reflectionCache,//$L
                        metaOpsName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateDeArchiveEndpoint(TypeElement entity) {
        String mutationName = "deArchive" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(parameterizeType(entity))
                .addStatement("$T entity = $T.deArchive($L, input, $L, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        reflectionCache,//$L
                        metaOpsName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateDeleteEndpoint(TypeElement entity) {
        String mutationName = "delete" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(parameterizeType(entity))
                .addStatement("$T entity = $T.delete($L, $L, input, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        reflectionCache,//$L
                        metaOpsName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, DELETE);
        return builder.build();
    }

    public MethodSpec generateGetCollectionByIdEndpoint(TypeElement entity) {
        String queryName = "get" + toPlural(pascalCaseNameOf(entity)) + "ById";
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build())
                .addParameter(ParameterSpec.builder(listOf(getIdType(entity, processingEnvironment)), "input").build())
                .addStatement("return $T.getCollectionById($T.class, $L, input)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        dataManagerName(entity)//$L
                )
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }

    public MethodSpec generateAddCollectionEndpoint(TypeElement entity) {
        String mutationName = "add" + toPlural(pascalCaseNameOf(entity));
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.addCollection($L, input, $L)",
                        entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity)//$L
                )
                .addStatement("return entities")
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, CREATE);
        return builder.build();
    }

    public MethodSpec generateUpdateCollectionEndpoint(TypeElement entity) {
        String mutationName = "update" + toPlural(pascalCaseNameOf(entity));
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.updateCollection($L, input, $L)",
                        entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity)//$L
                )
                .addStatement("return entities")
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateArchiveCollectionEndpoint(TypeElement entity) {
        String mutationName = "archive" + toPlural(pascalCaseNameOf(entity));
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.archiveCollection($L, input, $L)",
                        entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity)//$L
                )
                .addStatement("return entities")
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateDeArchiveCollectionEndpoint(TypeElement entity) {
        String mutationName = "deArchive" + toPlural(pascalCaseNameOf(entity));
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.deArchiveCollection($L, input, $L)",
                        entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity)//$L
                )
                .addStatement("return entities")
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateDeleteCollectionEndpoint(TypeElement entity) {
        String mutationName = "delete" + toPlural(pascalCaseNameOf(entity));
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.deleteCollection($L, input, $L)",
                        entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(entity),//$L
                        metaOpsName(entity)//$L
                )
                .addStatement("return entities")
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, DELETE);
        return builder.build();
    }

    //embedded entity
    public MethodSpec generateGetAsEmbeddedEntity(VariableElement embedded, TypeElement owner) {
        String queryName = camelcaseNameOf(embedded);
        ParameterSpec input = asParamList(owner, GraphQLContext.class);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(suppressDeprecationWarning())
                .addAnnotation(Batched.class)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build())
                .addParameter(input)
                .addStatement("return $T.getAsEmbeddedEntity($T.class, $L, input, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(embedded.asType()),//$T
                        dataManagerName(embedded),//$L
                        embedded.getSimpleName(),//$S
                        reflectionCache//$L
                )
                .returns(listOf(embedded));
        handleSecurityAnnotations(builder, embedded, READ);
        return builder.build();
    }

    //embedded entity collection
    public MethodSpec generateGetAsEmbeddedEntityCollection(VariableElement embedded, TypeElement owner) {
        String queryName = camelcaseNameOf(embedded);
        ParameterSpec input = asParamList(owner, GraphQLContext.class);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(suppressDeprecationWarning())
                .addAnnotation(Batched.class)
                .addAnnotation(AnnotationSpec.builder(GraphQLQuery.class)
                        .addMember("name", "$S", queryName)
                        .build())
                .addParameter(input)
                .addStatement("return $T.getAsEmbeddedCollection($T.class, $L, input, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        collectionTypeName(embedded),//$T
                        dataManagerName(embedded),//$L
                        camelcaseNameOf(embedded),//$S
                        reflectionCache//$L
                )
                .returns(listOfLists(embedded));
        handleSecurityAnnotations(builder, embedded, READ);
        return builder.build();
    }

    public MethodSpec generateAddNewToEmbeddedCollection(VariableElement embedded, TypeElement owner) {
        String mutationName = "addNew" + pascalCaseNameOf(embedded) + "To" + pascalCaseNameOf(owner);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asEmbeddedCollectionParamList(embedded))
                .addParameter(ParameterSpec.builder(ClassName.get(owner), camelcaseNameOf(owner)).build())
                .addStatement("return $T.addNewToEmbeddedCollection($L, $L, $L, $S, input, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(owner),//$L
                        dataManagerName(embedded),//$L
                        camelcaseNameOf(owner),//$L
                        camelcaseNameOf(embedded),//$S
                        embeddedCollectionMetaOpsName(embedded),//$L
                        reflectionCache//$L
                )
                .returns(listOfEmbedded(embedded));
        handleSecurityAnnotations(builder, embedded, CREATE);
        return builder.build();
    }

    public MethodSpec generateAttachExistingToEmbeddedCollection(VariableElement embedded, TypeElement owner) {
        String mutationName = "attachExisting" + pascalCaseNameOf(embedded) + "To" + pascalCaseNameOf(owner);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asEmbeddedCollectionParamList(embedded))
                .addParameter(ParameterSpec.builder(ClassName.get(owner), camelcaseNameOf(owner)).build())
                .addStatement("return $T.attachExistingToEmbeddedCollection($L, $L, $L, $S, input, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(owner),//$L
                        dataManagerName(embedded),//$L
                        camelcaseNameOf(owner),//$L
                        camelcaseNameOf(embedded),//$S
                        embeddedCollectionMetaOpsName(embedded),//$L
                        reflectionCache//$L
                )
                .returns(listOfEmbedded(embedded));
        handleSecurityAnnotations(builder, embedded, UPDATE);
        return builder.build();
    }

    public MethodSpec generateUpdateEmbeddedCollection(VariableElement embedded, TypeElement owner) {
        String mutationName = "update" + pascalCaseNameOf(embedded) + "In" + pascalCaseNameOf(owner);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asEmbeddedCollectionParamList(embedded))
                .addParameter(ParameterSpec.builder(ClassName.get(owner), camelcaseNameOf(owner)).build())
                .addStatement("return $T.updateEmbeddedCollection($L, $L, $L, input, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(owner),//$L
                        dataManagerName(embedded),//$L
                        camelcaseNameOf(owner),//$L
                        /*camelcaseNameOf(embedded),//$S*/
                        embeddedCollectionMetaOpsName(embedded),//$L
                        reflectionCache//$L
                )
                .returns(listOfEmbedded(embedded));
        handleSecurityAnnotations(builder, embedded, UPDATE);
        return builder.build();
    }

    public MethodSpec generateRemoveFromEmbeddedCollection(VariableElement embedded, TypeElement owner) {
        String mutationName = "remove" + pascalCaseNameOf(embedded) + "From" + pascalCaseNameOf(owner);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(GraphQLMutation.class)
                        .addMember("name", "$S", mutationName)
                        .build())
                .addParameter(asEmbeddedCollectionParamList(embedded))
                .addParameter(ParameterSpec.builder(ClassName.get(owner), camelcaseNameOf(owner)).build())
                .addStatement("return $T.removeFromEmbeddedCollection($L, $L, $L, $S, input, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        dataManagerName(owner),//$L
                        dataManagerName(embedded),//$L
                        camelcaseNameOf(owner),//$L
                        camelcaseNameOf(embedded),//$S
                        embeddedCollectionMetaOpsName(embedded),//$L
                        reflectionCache//$L
                )
                .returns(listOfEmbedded(embedded));
        handleSecurityAnnotations(builder, embedded, DELETE);
        return builder.build();
    }

    private void handleSecurityAnnotations(MethodSpec.Builder builder, Element element, String operation) {
        Map.Entry<List<AnnotationSpec>, List<Class<? extends Annotation>>> entry =
                securityAnnotationsHandler.handleCRUD(element, new ArrayList<>());
        List<AnnotationSpec> operationSpecificSecurityAnnotations = new ArrayList<>();
        List<AnnotationSpec> assignedCRUDSecurityAnnotations;
        List<Class<? extends Annotation>> alreadyAssigned;
        if(element instanceof TypeElement){
            assignedCRUDSecurityAnnotations = new ArrayList<>();
            alreadyAssigned = new ArrayList<>();
        }else {
            assignedCRUDSecurityAnnotations = entry.getKey();
            alreadyAssigned = entry.getValue();
        }
        switch (operation) {
            case CREATE:
                operationSpecificSecurityAnnotations = securityAnnotationsHandler
                        .handleCreate(element, alreadyAssigned).getKey();
                break;
            case READ:
                operationSpecificSecurityAnnotations = securityAnnotationsHandler
                        .handleRead(element, alreadyAssigned).getKey();
                break;
            case UPDATE:
                operationSpecificSecurityAnnotations = securityAnnotationsHandler
                        .handleUpdate(element, alreadyAssigned).getKey();
                break;
            case DELETE:
                operationSpecificSecurityAnnotations = securityAnnotationsHandler
                        .handleDelete(element, alreadyAssigned).getKey();
                break;
        }

        if (!assignedCRUDSecurityAnnotations.isEmpty())
            builder.addAnnotations(assignedCRUDSecurityAnnotations);
        if (!operationSpecificSecurityAnnotations.isEmpty())
            builder.addAnnotations(operationSpecificSecurityAnnotations);
    }

    private final static String CREATE = "Create";
    private final static String READ = "Read";
    private final static String UPDATE = "Update";
    private final static String DELETE = "Delete";

    private String resolverParams(WithResolver resolverAnnotation){
        StringBuilder format = new StringBuilder("");
        String[] args = resolverAnnotation.args();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            format.append(arg);
            format.append((i + 1) >= args.length ? "" : ", ");
        }
        return format.toString();
    }

    private Map<String, TypeName> mapFieldTypes(TypeElement entity) {
        Map<String, TypeName> mapFieldTypes = new HashMap<>();
        for (VariableElement field : getFields(entity)){
            mapFieldTypes.put(field.getSimpleName().toString(), ClassName.get(field.asType()));
        }
        return mapFieldTypes;
    }

    private ParameterSpec graphQLParameter(TypeName typeName, String name, String defaultValue) {
        return ParameterSpec.builder(typeName, name)
                .addAnnotation(AnnotationSpec.builder(GraphQLArgument.class)
                        .addMember("name", "$S", name)
                        .addMember("defaultValue", "$S", defaultValue)
                        .build())
                .build();
    }
}

