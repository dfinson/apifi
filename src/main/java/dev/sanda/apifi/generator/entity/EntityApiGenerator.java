package dev.sanda.apifi.generator.entity;

import com.squareup.javapoet.*;
import dev.sanda.apifi.ApifiStaticUtils;
import dev.sanda.apifi.annotations.ForeignKeyCollectionApi;
import dev.sanda.apifi.generator.custom_resolver.CustomResolverApiBuildInfo;
import dev.sanda.apifi.generator.custom_resolver.CustomResolverArgumentsMap;
import dev.sanda.apifi.service.ApiLogic;
import dev.sanda.apifi.service.EmbeddedCollectionApiHooks;
import dev.sanda.apifi.service.NullEmbeddedCollectionApiHooks;
import dev.sanda.datafi.service.DataManager;
import graphql.execution.batched.Batched;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import lombok.val;
import lombok.var;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Types;
import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.sanda.apifi.ApifiStaticUtils.*;
import static dev.sanda.apifi.generator.entity.CRUDResolvers.*;
import static dev.sanda.apifi.generator.entity.CRUDResolvers.UPDATE;
import static dev.sanda.apifi.generator.entity.ForeignKeyCollectionResolverType.*;
import static dev.sanda.apifi.service.CustomResolverType.QUERY;
import static dev.sanda.datafi.DatafiStaticUtils.getIdType;
import static dev.sanda.datafi.DatafiStaticUtils.toPlural;
import static dev.sanda.datafi.code_generator.query.ReturnPlurality.BATCH;
import static dev.sanda.testifi.TestifiStaticUtils.*;
import static javax.lang.model.element.Modifier.PUBLIC;

//TODO security, findBys, tests
@SuppressWarnings("deprecation")
public class EntityApiGenerator {
    public static class GraphQLApiBuilder {
        private TypeElement entity;
        private List<VariableElement> fields;
        private Map<CRUDResolvers, Boolean> crudResolvers;
        private List<CustomResolverApiBuildInfo> customResolvers;
        private ProcessingEnvironment processingEnv;
        private Map<String, TypeElement> entitiesMap;

        public GraphQLApiBuilder(TypeElement entity, Map<String, TypeElement> entitiesMap){
            this.entity = entity;
            this.entitiesMap = entitiesMap;
            this.fields = entity
                    .getEnclosedElements()
                    .stream()
                    .filter(elem -> elem.getKind().isField())
                    .map(elem -> (VariableElement)elem)
                    .collect(Collectors.toList());
        }

        public GraphQLApiBuilder setCrudResolvers(List<CRUDResolvers> crudResolvers) {
            this.crudResolvers = crudResolvers.stream().collect(Collectors.toMap(cr -> cr, cr -> true));
            return this;
        }

        public GraphQLApiBuilder setCustomResolvers(List<CustomResolverApiBuildInfo> customResolvers) {
            this.customResolvers = customResolvers;
            return this;
        }
        public ServiceAndTest build(ProcessingEnvironment processingEnv){
            this.processingEnv = processingEnv;
            //init builders
            var serviceBuilder =
                    TypeSpec.classBuilder(entity.getSimpleName().toString() + "GraphQLApiService").addModifiers(PUBLIC)
                    .addAnnotation(Service.class).addAnnotation(Transactional.class).addAnnotation(GraphQLApi.class);
            var testBuilder =
                    TypeSpec.classBuilder(entity.getSimpleName().toString() + "GraphQLApiServiceTest").addModifiers(PUBLIC)
                    .addAnnotation(SpringBootTest.class).addAnnotation(Transactional.class);

            //generate fields:

            //field 1 - ApiLogic
            val apiLogic = apiLogic();
            serviceBuilder.addField(apiLogic);
            testBuilder.addField(apiLogic);

            //field(s) 2 - foreign key data managers
            fields.forEach(field -> {
                        String typeNameKey = isIterable(field.asType(), processingEnv) ? getCollectionType(field) : field.asType().toString();
                        TypeElement type = entitiesMap.get(typeNameKey);
                        if(type != null) {
                            final FieldSpec typeDataManager = dataManager(type, dataManagerName(field));
                            serviceBuilder.addField(typeDataManager);
                            testBuilder.addField(typeDataManager);
                        }
                    });

            //field(s) 3 - custom resolvers
            customResolvers.forEach(customResolver -> serviceBuilder.addField(customResolverField(customResolver)));

            //generate crud methods:

            //GET_PAGINATED_BATCH
            if(crudResolvers.containsKey(GET_PAGINATED_BATCH)) {
                serviceBuilder.addMethod(genGetPaginatedBatch());
                testBuilder.addMethod(genGetPaginatedBatchTest());
            }
            //GET_BY_ID
            if(crudResolvers.containsKey(GET_BY_ID)) {
                serviceBuilder.addMethod(genGetById(processingEnv));
                testBuilder.addMethod(genGetByIdTest());
            }
            //GET_BATCH_BY_IDS
            if(crudResolvers.containsKey(GET_BATCH_BY_IDS)) {
                serviceBuilder.addMethod(genGetBatchByIds(processingEnv));
                testBuilder.addMethod(genGetBatchByIdsTest());
            }
            //CREATE
            if(crudResolvers.containsKey(CREATE)) {
                serviceBuilder.addMethod(genCreate());
                testBuilder.addMethod(genCreateTest());
            }
            //BATCH_CREATE
            if(crudResolvers.containsKey(BATCH_CREATE)) {
                serviceBuilder.addMethod(genBatchCreate());
                testBuilder.addMethod(genBatchCreateTest());
            }
            //UPDATE
            if(crudResolvers.containsKey(UPDATE)) {
                serviceBuilder.addMethod(genUpdate());
                testBuilder.addMethod(genUpdateTest());
            }
            //BATCH_UPDATE
            if(crudResolvers.containsKey(BATCH_UPDATE)) {
                serviceBuilder.addMethod(genBatchUpdate());
                testBuilder.addMethod(genBatchUpdateTest());
            }
            //DELETE
            if(crudResolvers.containsKey(DELETE)) {
                serviceBuilder.addMethod(genDelete());
                testBuilder.addMethod(genDeleteTest());
            }
            //BATCH_DELETE
            if(crudResolvers.containsKey(BATCH_DELETE)) {
                serviceBuilder.addMethod(genBatchDelete());
                testBuilder.addMethod(genBatchDeleteTest());
            }
            //ARCHIVE
            if(crudResolvers.containsKey(ARCHIVE)) {
                serviceBuilder.addMethod(genArchive());
                testBuilder.addMethod(genArchiveTest());
            }
            //BATCH_ARCHIVE
            if(crudResolvers.containsKey(BATCH_ARCHIVE)) {
                serviceBuilder.addMethod(genBatchArchive());
                testBuilder.addMethod(genBatchArchiveTest());
            }
            //DE_ARCHIVE
            if(crudResolvers.containsKey(DE_ARCHIVE)) {
                serviceBuilder.addMethod(genDeArchive());
                testBuilder.addMethod(genDeArchiveTest());
            }
            //BATCH_DE_ARCHIVE
            if(crudResolvers.containsKey(BATCH_DE_ARCHIVE)) {
                serviceBuilder.addMethod(genBatchDeArchive());
                testBuilder.addMethod(genBatchDeArchiveTest());
            }
            //GET_ARCHIVED_PAGINATED_BATCH
            if(crudResolvers.containsKey(GET_ARCHIVED_PAGINATED_BATCH)) {
                serviceBuilder.addMethod(genGetArchivedPaginatedBatch());
                testBuilder.addMethod(genGetArchivedPaginatedBatchTest());
            }

            //generate custom resolver endpoints:
            customResolvers.forEach(customResolver -> serviceBuilder.addMethod(genCustomResolver(customResolver)));

            //generate foreign key endpoints
            fields.stream().filter(ApifiStaticUtils::isForeignKeyOrKeys).collect(Collectors.toList())
            .forEach(fk -> {
                if(isIterable(fk.asType(), processingEnv)){
                    //read
                    serviceBuilder.addMethod(genGetEmbeddedCollection(fk, serviceBuilder, testBuilder));
                    testBuilder.addMethod(genGetEmbeddedCollectionTest(fk));

                    val config = fk.getAnnotation(ForeignKeyCollectionApi.class);
                    val resolvers = Arrays.asList(config.resolvers());
                    //associate
                    if(resolvers.contains(ASSOCIATE) || resolvers.contains(ALL)){
                        serviceBuilder.addMethod(genAssociateWithEmbeddedCollection(fk));
                        testBuilder.addMethod(genAssociateWithEmbeddedCollectionTest(fk));
                    }
                    //update
                    if(resolvers.contains(ForeignKeyCollectionResolverType.UPDATE) || resolvers.contains(ALL)){
                        serviceBuilder.addMethod(genUpdateInEmbeddedCollection(fk));
                        testBuilder.addMethod(genUpdateInEmbeddedCollectionTest(fk));
                    }
                    //remove
                    if(resolvers.contains(REMOVE) || resolvers.contains(ALL)){
                        serviceBuilder.addMethod(genRemoveFromEmbeddedCollection(fk));
                        testBuilder.addMethod(genRemoveFromEmbeddedCollectionTest(fk));
                    }
                }else {
                    serviceBuilder.addMethod(genGetEmbedded(fk));
                    testBuilder.addMethod(genGetEmbeddedTest(fk));
                }
            });

            //return result
            return new ServiceAndTest(serviceBuilder.build(), testBuilder.build());
        }

        //method specs

        private MethodSpec genGetPaginatedBatch() {
            return MethodSpec.methodBuilder(pluralCamelCaseName(entity))
                            .addModifiers(PUBLIC)
                            .addAnnotation(GraphQLQuery.class)
                            .addParameter(ParameterSpec.builder(TypeName.INT, "offset").build())
                            .addParameter(graphQLParameter(TypeName.INT, "limit", "50"))
                            .addParameter(graphQLParameter(ClassName.get(String.class), "sortBy", null))
                            .addParameter(graphQLParameter(ClassName.get(Sort.Direction.class), "sortDirection", "\"ASC\""))
                            .addStatement("return apiLogic.getPaginatedBatch(offset, limit, sortBy, sortDirection)")
                            .returns(listOf(entity))
                    .build();
        }
        private MethodSpec genGetById(ProcessingEnvironment processingEnv) {
            String queryName = "get" + pascalCaseNameOf(entity) + "ById";
            MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(getIdType(entity, processingEnv), "input")
                    .addStatement("return apiLogic.getById(input)")
                    .returns(TypeName.get(entity.asType()));
            return builder.build();
        }
        private MethodSpec genGetBatchByIds(ProcessingEnvironment processingEnv) {
            String queryName = "get" + toPlural(pascalCaseNameOf(entity)) + "ById";
            MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(ParameterSpec.builder(listOf(getIdType(entity, processingEnv)), "input").build())
                    .addStatement("return apiLogic.getCollectionById(input)")
                    .returns(listOf(entity));
            return builder.build();
        }
        private MethodSpec genCreate() {
            String mutationName = "create" + pascalCaseNameOf(entity);
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(ApifiStaticUtils.parameterizeType(entity))
                    .addStatement("return apiLogic.create(input)")
                    .returns(TypeName.get(entity.asType()));
            return builder.build();
        }
        private MethodSpec genBatchCreate() {
            String mutationName = "create" + toPlural(pascalCaseNameOf(entity));
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchCreate(input)")
                    .returns(listOf(entity));
            return builder.build();
        }
        private MethodSpec genUpdate() {
            String mutationName = "update" + pascalCaseNameOf(entity);
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.update(input)")
                    .returns(TypeName.get(entity.asType()));
            return builder.build();
        }
        private MethodSpec genBatchUpdate() {
            String mutationName = "update" + toPlural(pascalCaseNameOf(entity));
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchUpdate(input)")
                    .returns(listOf(entity));
            return builder.build();
        }
        private MethodSpec genDelete() {
            String mutationName = "delete" + pascalCaseNameOf(entity);
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.delete(input)")
                    .returns(TypeName.get(entity.asType()));
            return builder.build();
        }
        private MethodSpec genBatchDelete() {
            String mutationName = "delete" + toPlural(pascalCaseNameOf(entity));
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchDelete(input)")
                    .returns(listOf(entity));
            return builder.build();
        }
        private MethodSpec genArchive() {
            String mutationName = "archive" + pascalCaseNameOf(entity);
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.archive(input)")
                    .returns(TypeName.get(entity.asType()));
            return builder.build();
        }
        private MethodSpec genBatchArchive() {
            String mutationName = "archive" + toPlural(pascalCaseNameOf(entity));
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchArchive(input)")
                    .returns(listOf(entity));
            return builder.build();
        }
        private MethodSpec genDeArchive() {
            String mutationName = "deArchive" + pascalCaseNameOf(entity);
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.deArchive(input)")
                    .returns(TypeName.get(entity.asType()));
            return builder.build();
        }
        private MethodSpec genBatchDeArchive() {
            String mutationName = "deArchive" + toPlural(pascalCaseNameOf(entity));
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchDeArchive(input)")
                    .returns(listOf(entity));
            return builder.build();
        }
        private MethodSpec genGetArchivedPaginatedBatch() {
            return MethodSpec.methodBuilder(toPlural(pascalCaseNameOf(entity)))
                    .addModifiers(PUBLIC)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(ParameterSpec.builder(TypeName.INT, "offset").build())
                    .addParameter(graphQLParameter(TypeName.INT, "limit", "50"))
                    .addParameter(graphQLParameter(ClassName.get(String.class), "sortBy", null))
                    .addParameter(graphQLParameter(ClassName.get(Sort.Direction.class), "sortDirection", "\"ASC\""))
                    .addStatement("return apiLogic.getArchivedPaginatedBatch(offset, limit, sortBy, sortDirection)")
                    .returns(listOf(entity))
                    .build();
        }
        @SuppressWarnings("deprecation")
        private MethodSpec genGetEmbeddedCollection(
                VariableElement embedded,
                TypeSpec.Builder serviceBuilder,
                TypeSpec.Builder testBuilder) {
            String queryName = camelcaseNameOf(embedded);
            ParameterSpec input = asParamList(entity, GraphQLContext.class);
            String embeddedCollectionApiHooksName = embeddedCollectionApiHooksName(embedded);
            addApiHooksIfPresent(embedded, serviceBuilder, testBuilder, embeddedCollectionApiHooksName);
            MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(suppressDeprecationWarning())
                    .addAnnotation(Batched.class)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(input)
                    .addStatement("return apiLogic.getEmbeddedCollection(input, $S, $L, $L)",
                            camelcaseNameOf(embedded),//$S
                            embeddedCollectionApiHooksName, //$L
                            dataManagerName(embedded)       // $L
                    )
                    .returns(listOfLists(embedded));
            return builder.build();
        }
        @SuppressWarnings("deprecation")
        private MethodSpec genGetEmbedded(VariableElement embedded) {
            String queryName = camelcaseNameOf(embedded);
            ParameterSpec input = asParamList(entity, GraphQLContext.class);
            MethodSpec.Builder builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(suppressDeprecationWarning())
                    .addAnnotation(Batched.class)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(input)
                    .addStatement("return apiLogic.getEmbedded(input, $S, $L)",
                            camelcaseNameOf(embedded),
                            dataManagerName(embedded))
                    .returns(listOf(embedded));
            return builder.build();
        }
        private MethodSpec genCustomResolver(CustomResolverApiBuildInfo info){
            var builder =
                    MethodSpec.methodBuilder(info.getQualifier())
                            .addModifiers(PUBLIC)
                            .addAnnotation(info.getCustomResolverType().equals(QUERY) ? GraphQLQuery.class : GraphQLMutation.class)
                            .returns(info.getReturnPlurality().equals(BATCH) ? listOf(info.getReturnType()) : info.getReturnType())
                            .addStatement("$T arguments = new $T()", var.class, info.stringObjectTuple(LinkedHashMap.class));
            info.getCustomResolverArgumentParameters().forEach(arg -> {
                builder.addParameter(ParameterSpec.builder(arg.getType(), arg.getName()).build());
                builder.addStatement("arguments.put($S, $L)", arg.getNameWithType(), arg.getName());
            });
            builder.addStatement("$T argumentsMap = new $T(arguments)", val.class, CustomResolverArgumentsMap.class);
            if(!isFromType(info.getReturnType(), ClassName.get(void.class)))
                builder.addStatement("return $L.handleRequest(argumentsMap)", info.getQualifier());
            else
                builder.addStatement("$L.handleRequest(argumentsMap)", info.getQualifier());
            return builder.build();
        }

        private MethodSpec genAssociateWithEmbeddedCollection(VariableElement fk) {
            String mutationName = "associate" + pascalCaseNameOf(fk) + "With" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(ForeignKeyCollectionApi.class);
            Class<?> apiHooks = null;
            if(config != null && !config.apiHooks().equals(NullEmbeddedCollectionApiHooks.class))
                apiHooks = config.apiHooks();
            boolean addPreExistingOnly = config != null && config.addPreExistingOnly();
            String apiLogicBackingMethod =
                    addPreExistingOnly ? "associatePreExistingWithEmbeddedCollection" : "associateWithEmbeddedCollection";
            ParameterSpec input = asParamList(collectionTypeName(fk));
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addStatement("return apiLogic.$L(owner, $S, input, $L, $L)",
                            apiLogicBackingMethod,
                            camelcaseNameOf(fk),
                            dataManagerName(fk),
                            apiHooks != null ? embeddedCollectionApiHooksName(fk) : "null")
                    .returns(listOf(collectionTypeName(fk)));
            return builder.build();
        }

        private MethodSpec genRemoveFromEmbeddedCollection(VariableElement fk) {
            String mutationName = "remove" + pascalCaseNameOf(fk) + "From" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(ForeignKeyCollectionApi.class);
            Class<?> apiHooks = null;
            if(config != null && !config.apiHooks().equals(NullEmbeddedCollectionApiHooks.class))
                apiHooks = config.apiHooks();
            ParameterSpec input = asParamList(collectionTypeName(fk));
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addStatement("return apiLogic.removeFromEmbeddedCollection(owner, $S, input, $L, $L)",
                            camelcaseNameOf(fk),
                            dataManagerName(fk),
                            apiHooks != null ? embeddedCollectionApiHooksName(fk) : "null")
                    .returns(listOf(collectionTypeName(fk)));
            return builder.build();
        }

        private MethodSpec genUpdateInEmbeddedCollection(VariableElement fk) {
            String mutationName = "update" + pascalCaseNameOf(fk) + "In" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(ForeignKeyCollectionApi.class);
            Class<?> apiHooks = null;
            if(config != null && !config.apiHooks().equals(NullEmbeddedCollectionApiHooks.class))
                apiHooks = config.apiHooks();
            ParameterSpec input = asParamList(collectionTypeName(fk));
            MethodSpec.Builder builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addStatement("return apiLogic.updateEmbeddedCollection(owner, $L, input, $L)",
                            dataManagerName(fk),
                            camelcaseNameOf(fk),
                            apiHooks != null ? embeddedCollectionApiHooksName(fk) : "null")
                    .returns(listOf(collectionTypeName(fk)));
            return builder.build();
        }

        //test method specs

        private MethodSpec genGetEmbeddedTest(VariableElement embedded) {
            String testName = "get" + pascalCaseNameOf(embedded) + "From" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getEmbeddedTest($L, $L)",
                            dataManagerName(embedded),
                            embedded.getSimpleName().toString())
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genGetEmbeddedCollectionTest(VariableElement embedded) {
            String testName = "get" + toPlural(pascalCaseNameOf(embedded)) + "From" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getEmbeddedCollectionTest($L, $L, $L)",
                            dataManagerName(embedded),
                            embedded.getSimpleName().toString(),
                            embeddedCollectionApiHooksName(embedded))
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genGetArchivedPaginatedBatchTest() {
            String testName = "archived" + pluralPascalCaseName(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getArchivedPaginatedBatchTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genBatchDeArchiveTest() {
            String testName = "deArchive" + toPlural(pascalCaseNameOf(entity)) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.batchDeArchiveTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genDeArchiveTest() {
            String testName = "deArchive" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.deArchiveTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genBatchArchiveTest() {
            String testName = "archive" + toPlural(pascalCaseNameOf(entity)) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.batchArchiveTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genArchiveTest() {
            String testName = "archive" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.archiveTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genBatchDeleteTest() {
            String testName = "delete" + pluralPascalCaseName(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.batchDeleteTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genDeleteTest() {
            String testName = "delete" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.deleteTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genBatchUpdateTest() {
            String testName = "update" + pluralPascalCaseName(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.batchUpdateTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genUpdateTest() {
            String testName = "update" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.updateTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genBatchCreateTest() {
            String testName = "create" + pluralPascalCaseName(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.batchCreateTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genCreateTest() {
            String testName = "create" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.createTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genGetBatchByIdsTest() {
            String testName = "get" + pluralPascalCaseName(entity) + "ByIdTest";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getBatchByIdsTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genGetByIdTest() {
            String testName = "get" + pascalCaseNameOf(entity) + "ByIdTest";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getByIdTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genGetPaginatedBatchTest() {
            String testName = pluralCamelCaseName(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getPaginatedBatchTest()")
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genAssociateWithEmbeddedCollectionTest(VariableElement fk) {
            String testName = "associate" + pascalCaseNameOf(fk) + "With" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.associateWithEmbeddedCollectionTest($L, $S, $L)",
                            dataManagerName(fk), fk.getSimpleName(), embeddedCollectionApiHooksName(fk))
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genUpdateInEmbeddedCollectionTest(VariableElement fk) {
            String testName = "update" + pascalCaseNameOf(fk) + "In" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.updateEmbeddedCollectionTest($L, $S, $L)",
                            dataManagerName(fk), fk.getSimpleName(), embeddedCollectionApiHooksName(fk))
                    .returns(void.class)
                    .build();
        }

        private MethodSpec genRemoveFromEmbeddedCollectionTest(VariableElement fk) {
            String testName = "remove" + pascalCaseNameOf(fk) + "From" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.removeFromEmbeddedCollectionTest($L, $S, $L)",
                            dataManagerName(fk), fk.getSimpleName(), embeddedCollectionApiHooksName(fk))
                    .returns(void.class)
                    .build();
        }

        //field spec helpers
        private FieldSpec dataManager(TypeElement entity, String namePrefix) {
            return FieldSpec
                    .builder(ParameterizedTypeName.get(ClassName.get(DataManager.class), ClassName.get(entity)), namePrefix + "DataManager")
                    .addModifiers(Modifier.PRIVATE)
                    .addAnnotation(Autowired.class)
                    .build();
        }
        private FieldSpec customResolverField(CustomResolverApiBuildInfo info) {
            return FieldSpec
                    .builder(info.getCustomResolverTypeName(), info.getQualifier())
                    .addAnnotation(Autowired.class)
                    .addAnnotation(AnnotationSpec.builder(Qualifier.class)
                            .addMember("value", "$S", info.getQualifier())
                            .build())
                    .addModifiers(Modifier.PRIVATE)
                    .build();
        }
        private FieldSpec apiLogic() {
            return FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(ApiLogic.class), ClassName.get(entity)), "apiLogic").build();
        }

        public FieldSpec embeddedCollectionApiHooks(VariableElement field) {
            ParameterizedTypeName apiHooksType = null;
            ForeignKeyCollectionApi foreignKeyCollectionApi = field.getAnnotation(ForeignKeyCollectionApi.class);
            if(foreignKeyCollectionApi != null){
                try{
                    foreignKeyCollectionApi.apiHooks();
                }catch (MirroredTypeException mte){
                    Types TypeUtils = this.processingEnv.getTypeUtils();
                    apiHooksType = ParameterizedTypeName.get(ClassName.get((TypeElement)TypeUtils.asElement(mte.getTypeMirror())));
                }
            }
            assert apiHooksType != null;
            return
                    FieldSpec
                            .builder(apiHooksType,
                                    embeddedCollectionApiHooksName(field),
                                    Modifier.PRIVATE)
                            .addAnnotation(Autowired.class)
                            .build();
        }

        //misc util methods
        private void addApiHooksIfPresent(VariableElement embedded, TypeSpec.Builder serviceBuilder, TypeSpec.Builder testBuilder, String embeddedCollectionApiHooksName) {
            if(embeddedCollectionApiHooksName.equals("null")) return;
            final FieldSpec apiHooks = embeddedCollectionApiHooks(embedded);
            serviceBuilder.addField(apiHooks);
            testBuilder.addField(apiHooks);
        }
    }
}