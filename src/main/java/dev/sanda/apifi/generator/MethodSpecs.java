package dev.sanda.apifi.generator;


import com.squareup.javapoet.*;
import dev.sanda.apifi.ApifiStaticUtils;
import dev.sanda.apifi.service.ApiLogic;
import graphql.execution.batched.Batched;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import dev.sanda.apifi.security.SecurityAnnotationsHandler;

import org.springframework.data.domain.Sort;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.lang.annotation.Annotation;
import java.util.*;

import static dev.sanda.apifi.ApifiStaticUtils.*;
import static dev.sanda.datafi.DatafiStaticUtils.*;

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
                        .addAnnotation(GraphQLQuery.class)
                        .addParameter(graphQLParameter(TypeName.INT, "offset", "0"))
                        .addParameter(graphQLParameter(TypeName.INT, "limit", "50"))
                        .addParameter(graphQLParameter(ClassName.get(String.class), "sortBy", null))
                        .addParameter(graphQLParameter(ClassName.get(Sort.Direction.class), "sortDirection", "\"ASC\""))
                        .addStatement("return $T.getAll($T.class, $L, $L, $L, offset, limit, sortBy, sortDirection)",
                                ClassName.get(ApiLogic.class),//$T
                                ClassName.get(entity),//$T
                                ApifiStaticUtils.dataManagerName(entity),//$L
                                ApifiStaticUtils.reflectionCache,//$L
                                ApifiStaticUtils.apiHooksName(entity)//$L
                        )
                        .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }

    public MethodSpec generateFuzzySearchEndpoint(TypeElement entity) {
        String queryName = toPlural(ApifiStaticUtils.camelcaseNameOf(entity)) + "FuzzySearch";
        MethodSpec.Builder builder =
                MethodSpec.methodBuilder(queryName)
                        .addModifiers(Modifier.PUBLIC)
                        .addAnnotation(GraphQLQuery.class)
                        .addParameter(String.class, "searchTerm")
                        .addParameter(graphQLParameter(TypeName.INT, "offset", "0"))
                        .addParameter(graphQLParameter(TypeName.INT, "limit", "50"))
                        .addParameter(graphQLParameter(ClassName.get(String.class), "sortBy", null))
                        .addParameter(graphQLParameter(ClassName.get(Sort.Direction.class), "sortDirection", "\"ASC\""))
                        .addStatement("return $T.freeTextSearch($T.class, $L, $L, offset, limit, searchTerm, sortBy, sortDirection)",
                                ClassName.get(ApiLogic.class),//$T
                                ClassName.get(entity),//$T
                                ApifiStaticUtils.dataManagerName(entity),//$L
                                ApifiStaticUtils.apiHooksName(entity)//$L
                        )
                        .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }

    public MethodSpec generateGetByIdEndpoint(TypeElement entity) {
        String queryName = "get" + pascalCaseNameOf(entity) + "ById";
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLQuery.class)
                .addParameter(getIdType(entity, processingEnvironment), "input")
                .addStatement("return $T.getById($T.class, $L, $L, input)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity)//$L
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
                .addAnnotation(GraphQLQuery.class)
                .addParameter(ClassName.get(field.asType()), fieldName)
                .addStatement("return $T.getByUnique($T.class, $L, $L, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ClassName.get(entity),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity),//$L
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
                .addAnnotation(GraphQLQuery.class)
                .addParameter(ClassName.get(field.asType()), fieldName)
                .addStatement("return $T.getBy($L, $L, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity),//$L
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
                .addAnnotation(GraphQLQuery.class)
                .addParameter(listOf(field), toPlural(fieldName))
                .addStatement("return $T.getAllBy($L, $L, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity),//$L
                        fieldName,//$S
                        toPlural(fieldName))//$L
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, field, READ);
        return builder.build();
    }

    public MethodSpec generateAddEndpoint(TypeElement entity) {
        String mutationName = "add" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.parameterizeType(entity))
                .addStatement("$T entity = $T.add($L, input, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, CREATE);
        return builder.build();
    }

    public MethodSpec generateUpdateEndpoint(TypeElement entity) {
        String mutationName = "update" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.parameterizeType(entity))
                .addStatement("$T entity = $T.update($L, input, $L, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.reflectionCache,//$L
                        ApifiStaticUtils.apiHooksName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateArchiveEndpoint(TypeElement entity) {
        String mutationName = "archive" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.parameterizeType(entity))
                .addStatement("$T entity = $T.archive($L, input, $L, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.reflectionCache,//$L
                        ApifiStaticUtils.apiHooksName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateDeArchiveEndpoint(TypeElement entity) {
        String mutationName = "deArchive" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.parameterizeType(entity))
                .addStatement("$T entity = $T.deArchive($L, input, $L, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.reflectionCache,//$L
                        ApifiStaticUtils.apiHooksName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, UPDATE);
        return builder.build();
    }

    public MethodSpec generateDeleteEndpoint(TypeElement entity) {
        String mutationName = "delete" + pascalCaseNameOf(entity);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.parameterizeType(entity))
                .addStatement("$T entity = $T.delete($L, $L, input, $L)",
                        ClassName.get(entity),//$T
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.reflectionCache,//$L
                        ApifiStaticUtils.apiHooksName(entity))//$L
                .addStatement("return entity")
                .returns(TypeName.get(entity.asType()));
        handleSecurityAnnotations(builder, entity, DELETE);
        return builder.build();
    }

    public MethodSpec generateGetCollectionByIdEndpoint(TypeElement entity) {
        String queryName = "get" + toPlural(pascalCaseNameOf(entity)) + "ById";
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLQuery.class)
                .addParameter(ParameterSpec.builder(listOf(getIdType(entity, processingEnvironment)), "input").build())
                .addStatement("return $T.getCollectionById($L, input)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity)//$L
                )
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, READ);
        return builder.build();
    }

    public MethodSpec generateAddCollectionEndpoint(TypeElement entity) {
        String mutationName = "add" + toPlural(pascalCaseNameOf(entity));
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.addCollection($L, input, $L)",
                        ApifiStaticUtils.entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity)//$L
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
                .addAnnotation(GraphQLMutation.class)
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.updateCollection($L, input, $L)",
                        ApifiStaticUtils.entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity)//$L
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
                .addAnnotation(GraphQLMutation.class)
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.archiveCollection($L, input, $L)",
                        ApifiStaticUtils.entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity)//$L
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
                .addAnnotation(GraphQLMutation.class)
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.deArchiveCollection($L, input, $L)",
                        ApifiStaticUtils.entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity)//$L
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
                .addAnnotation(GraphQLMutation.class)
                .addParameter(asParamList(entity))
                .addStatement("$L = $T.deleteCollection($L, input, $L)",
                        ApifiStaticUtils.entitiesList(entity),//$L
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(entity),//$L
                        ApifiStaticUtils.apiHooksName(entity)//$L
                )
                .addStatement("return entities")
                .returns(listOf(entity));
        handleSecurityAnnotations(builder, entity, DELETE);
        return builder.build();
    }

    //embedded entity
    public MethodSpec generateGetAsEmbeddedEntity(VariableElement embedded, TypeElement owner) {
        String queryName = ApifiStaticUtils.camelcaseNameOf(embedded);
        ParameterSpec input = asParamList(owner, GraphQLContext.class);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ApifiStaticUtils.suppressDeprecationWarning())
                .addAnnotation(Batched.class)
                .addAnnotation(GraphQLQuery.class)
                .addParameter(input)
                .addStatement("return $T.getAsEmbeddedEntity($L, input, $S, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(embedded),//$L
                        embedded.getSimpleName(),//$S
                        ApifiStaticUtils.reflectionCache//$L
                )
                .returns(listOf(embedded));
        handleSecurityAnnotations(builder, embedded, READ);
        return builder.build();
    }

    //embedded entity collection
    public MethodSpec generateGetAsEmbeddedEntityCollection(VariableElement embedded, TypeElement owner) {
        String queryName = ApifiStaticUtils.camelcaseNameOf(embedded);
        ParameterSpec input = asParamList(owner, GraphQLContext.class);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ApifiStaticUtils.suppressDeprecationWarning())
                .addAnnotation(Batched.class)
                .addAnnotation(GraphQLQuery.class)
                .addParameter(input)
                .addStatement("return $T.getEmbeddedCollection($L, input, $S, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(embedded),//$L
                        ApifiStaticUtils.camelcaseNameOf(embedded),//$S
                        ApifiStaticUtils.embeddedCollectionApiHooksName(embedded),//$T
                        ApifiStaticUtils.reflectionCache//$L
                )
                .returns(ApifiStaticUtils.listOfLists(embedded));
        handleSecurityAnnotations(builder, embedded, READ);
        return builder.build();
    }

    public MethodSpec generateAddNewToEmbeddedCollection(VariableElement embedded, TypeElement owner) {
        String mutationName = "addNew" + pascalCaseNameOf(embedded) + "To" + pascalCaseNameOf(owner);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.asEmbeddedCollectionParamList(embedded))
                .addParameter(ParameterSpec.builder(ClassName.get(owner), ApifiStaticUtils.camelcaseNameOf(owner)).build())
                .addStatement("return $T.addNewToEmbeddedCollection($L, $L, $L, $S, input, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(owner),//$L
                        ApifiStaticUtils.dataManagerName(embedded),//$L
                        ApifiStaticUtils.camelcaseNameOf(owner),//$L
                        ApifiStaticUtils.camelcaseNameOf(embedded),//$S
                        ApifiStaticUtils.embeddedCollectionApiHooksName(embedded),//$L
                        ApifiStaticUtils.reflectionCache//$L
                )
                .returns(ApifiStaticUtils.listOfEmbedded(embedded));
        handleSecurityAnnotations(builder, embedded, CREATE);
        return builder.build();
    }

    public MethodSpec generateAttachExistingToEmbeddedCollection(VariableElement embedded, TypeElement owner) {
        String mutationName = "attachExisting" + pascalCaseNameOf(embedded) + "To" + pascalCaseNameOf(owner);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.asEmbeddedCollectionParamList(embedded))
                .addParameter(ParameterSpec.builder(ClassName.get(owner), ApifiStaticUtils.camelcaseNameOf(owner)).build())
                .addStatement("return $T.attachExistingToEmbeddedCollection($L, $L, $L, $S, input, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(owner),//$L
                        ApifiStaticUtils.dataManagerName(embedded),//$L
                        ApifiStaticUtils.camelcaseNameOf(owner),//$L
                        ApifiStaticUtils.camelcaseNameOf(embedded),//$S
                        ApifiStaticUtils.embeddedCollectionApiHooksName(embedded),//$L
                        ApifiStaticUtils.reflectionCache//$L
                )
                .returns(ApifiStaticUtils.listOfEmbedded(embedded));
        handleSecurityAnnotations(builder, embedded, UPDATE);
        return builder.build();
    }

    public MethodSpec generateUpdateEmbeddedCollection(VariableElement embedded, TypeElement owner) {
        String mutationName = "update" + pascalCaseNameOf(embedded) + "In" + pascalCaseNameOf(owner);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.asEmbeddedCollectionParamList(embedded))
                .addParameter(ParameterSpec.builder(ClassName.get(owner), ApifiStaticUtils.camelcaseNameOf(owner)).build())
                .addStatement("return $T.updateEmbeddedCollection($L, $L, $L, input, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(owner),//$L
                        ApifiStaticUtils.dataManagerName(embedded),//$L
                        ApifiStaticUtils.camelcaseNameOf(owner),//$L
                        /*camelcaseNameOf(embedded),//$S*/
                        ApifiStaticUtils.embeddedCollectionApiHooksName(embedded),//$L
                        ApifiStaticUtils.reflectionCache//$L
                )
                .returns(ApifiStaticUtils.listOfEmbedded(embedded));
        handleSecurityAnnotations(builder, embedded, UPDATE);
        return builder.build();
    }

    public MethodSpec generateRemoveFromEmbeddedCollection(VariableElement embedded, TypeElement owner) {
        String mutationName = "remove" + pascalCaseNameOf(embedded) + "From" + pascalCaseNameOf(owner);
        MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(GraphQLMutation.class)
                .addParameter(ApifiStaticUtils.asEmbeddedCollectionParamList(embedded))
                .addParameter(ParameterSpec.builder(ClassName.get(owner), ApifiStaticUtils.camelcaseNameOf(owner)).build())
                .addStatement("return $T.removeFromEmbeddedCollection($L, $L, $S, input, $L, $L)",
                        ClassName.get(ApiLogic.class),//$T
                        ApifiStaticUtils.dataManagerName(owner),//$L
                        ApifiStaticUtils.camelcaseNameOf(owner),//$L
                        ApifiStaticUtils.camelcaseNameOf(embedded),//$S
                        ApifiStaticUtils.embeddedCollectionApiHooksName(embedded),//$L
                        ApifiStaticUtils.reflectionCache//$L
                )
                .returns(ApifiStaticUtils.listOfEmbedded(embedded));
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
}

