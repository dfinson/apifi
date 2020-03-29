package dev.sanda.apifi.generator.entity;

import com.squareup.javapoet.*;
import dev.sanda.apifi.utils.ApifiStaticUtils;
import dev.sanda.apifi.annotations.*;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import dev.sanda.apifi.service.ApiHooks;
import dev.sanda.apifi.service.ApiLogic;
import dev.sanda.apifi.service.NullEmbeddedCollectionApiHooks;
import dev.sanda.datafi.service.DataManager;
import dev.sanda.testifi.TestLogic;
import graphql.execution.batched.Batched;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLIgnore;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.spqr.spring.annotations.GraphQLApi;
import lombok.val;
import lombok.var;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.tools.Diagnostic;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static dev.sanda.apifi.generator.entity.CRUDEndpoints.*;
import static dev.sanda.apifi.generator.entity.ForeignKeyCollectionResolverType.*;
import static dev.sanda.datafi.DatafiStaticUtils.getIdType;
import static dev.sanda.datafi.DatafiStaticUtils.toPlural;
import static dev.sanda.testifi.TestifiStaticUtils.pluralCamelCaseName;
import static dev.sanda.testifi.TestifiStaticUtils.pluralPascalCaseName;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;

@SuppressWarnings("deprecation")
public class EntityApiGenerator {
    public static class GraphQLApiBuilder {
        private TypeElement entity;
        private List<VariableElement> fields;
        private Map<CRUDEndpoints, Boolean> crudResolvers;
        private ProcessingEnvironment processingEnv;
        private Map<String, TypeElement> entitiesMap;
        private Map<CRUDEndpoints, List<AnnotationSpec>> methodLevelSecuritiesMap;

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

        public GraphQLApiBuilder setCrudResolvers(List<CRUDEndpoints> crudResolvers) {
            this.crudResolvers = crudResolvers.stream().collect(Collectors.toMap(cr -> cr, cr -> true));
            return this;
        }

        public ServiceAndTest build(ProcessingEnvironment processingEnv){
            this.processingEnv = processingEnv;
            SecurityAnnotationsFactory.setProcessingEnv(processingEnv);
            //init builders
            var serviceBuilder =
                    TypeSpec.classBuilder(entity.getSimpleName().toString() + "GraphQLApiService").addModifiers(PUBLIC)
                    .addAnnotation(Service.class).addAnnotation(Transactional.class).addAnnotation(GraphQLApi.class);
            var testBuilder = TypeSpec
                    .classBuilder(entity.getSimpleName().toString() + "GraphQLApiServiceTest").addModifiers(PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(RunWith.class)
                        .addMember("value", "$T.class", SpringRunner.class)
                        .build())
                    .addAnnotation(SpringBootTest.class).addAnnotation(Transactional.class)
                    .addAnnotation(AnnotationSpec.builder(Scope.class).build());

            //generate fields:

            //fields 1,2 - ApiLogic & testLogic

            serviceBuilder.addField(apiLogic());
            serviceBuilder.addField(defaultDataManager());
            testBuilder.addField(defaultDataManager());
            testBuilder.addField(testLogic());
            serviceBuilder.addField(defaultApiHooks());
            testBuilder.addField(defaultApiHooks());
            testBuilder.addField(apiLogic());
            serviceBuilder.addMethod(postConstructInitApiLogic());
            testBuilder.addMethod(postConstructInitTestLogic());

            //field(s) 4 - foreign key data managers
            fields.forEach(field -> {
                        String typeNameKey = isIterable(field.asType(), processingEnv) ? getCollectionType(field) : field.asType().toString();
                        TypeElement type = entitiesMap.get(typeNameKey);
                        if(type != null) {
                            final FieldSpec typeDataManager = dataManager(type, dataManagerName(field));
                            serviceBuilder.addField(typeDataManager);
                            testBuilder.addField(typeDataManager);
                        }
                    });

            // generate class level security annotations
            val serviceLevelSecurity = entity.getAnnotation(WithServiceLevelSecurity.class);
            if(serviceLevelSecurity != null){
                val securityAnnotations = SecurityAnnotationsFactory.of(serviceLevelSecurity);
                if(!securityAnnotations.isEmpty()) serviceBuilder.addAnnotations(securityAnnotations);
            }

            // prepare the groundwork for method level security annotations
            methodLevelSecuritiesMap = new HashMap<>();
            val methodLevelSecurities = entity.getAnnotationsByType(WithMethodLevelSecurity.class);
            if(methodLevelSecurities != null){
                for (var security : methodLevelSecurities)
                    handleTargetMethodsMapping(security, methodLevelSecuritiesMap);
            }

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

            //generate foreign key endpoints
            fields.stream().filter(ApifiStaticUtils::isForeignKeyOrKeys).collect(Collectors.toList())
            .forEach(fk -> {
                if(isIterable(fk.asType(), processingEnv)){

                    val config = fk.getAnnotation(EmbeddedCollectionApi.class);
                    val resolvers = config != null ? Arrays.asList(config.resolvers()) : new ArrayList<>();
                    addApiHooksIfPresent(fk, serviceBuilder, testBuilder, config);

                    //read
                    if(!isGraphQLIgnored(fk)){
                        serviceBuilder.addMethod(genGetEmbeddedCollection(fk, serviceBuilder, testBuilder));
                        testBuilder.addMethod(genGetEmbeddedCollectionTest(fk));
                    }
                    //associate
                    if(resolvers.contains(ASSOCIATE_WITH) || resolvers.contains(ALL)){
                        serviceBuilder.addMethod(genAssociateWithEmbeddedCollection(fk));
                        testBuilder.addMethod(genAssociateWithEmbeddedCollectionTest(fk));
                    }
                    //update
                    if(resolvers.contains(ForeignKeyCollectionResolverType.UPDATE_IN) || resolvers.contains(ALL)){
                        serviceBuilder.addMethod(genUpdateInEmbeddedCollection(fk));
                        testBuilder.addMethod(genUpdateInEmbeddedCollectionTest(fk));
                    }
                    //remove
                    if(resolvers.contains(REMOVE_FROM) || resolvers.contains(ALL)){
                        serviceBuilder.addMethod(genRemoveFromEmbeddedCollection(fk));
                        testBuilder.addMethod(genRemoveFromEmbeddedCollectionTest(fk));
                    }
                }else {
                    serviceBuilder.addMethod(genGetEmbedded(fk));
                    testBuilder.addMethod(genGetEmbeddedTest(fk));
                }
            });

            // generate custom endpoints
            fields
                    .stream()
                    .filter(this::isApiFindByAnnotated)
                    .map(this::toApiFindByEndpoints)
                    .forEach(serviceBuilder::addMethods);
            //return result
            return new ServiceAndTest(serviceBuilder.build(), testBuilder.build());
        }

        private FieldSpec defaultDataManager() {
            return FieldSpec
                    .builder(ParameterizedTypeName.get(ClassName.get(DataManager.class), ClassName.get(entity)), "dataManager")
                    .addAnnotation(Autowired.class)
                    .addModifiers(PRIVATE)
                    .build();
        }

        private MethodSpec postConstructInitTestLogic() {
            return MethodSpec.methodBuilder("postConstructInit")
                    .addAnnotation(PostConstruct.class)
                    .addModifiers(PRIVATE)
                    .returns(void.class)
                    .addStatement("apiLogic.setApiHooks(apiHooks)")
                    .addStatement("apiLogic.setDataManager(dataManager)")
                    .addStatement("testLogic.setApiHooks(apiHooks)")
                    .addStatement("testLogic.setDataManager(dataManager)")
                    .addStatement("testLogic.setApiLogic(apiLogic)")
                    .addStatement("testLogic.setClazz($T.class)", ClassName.get(entity))
                    .addStatement("testLogic.setClazzSimpleName($T.class.getSimpleName())", ClassName.get(entity))
                    .build();
        }

        private MethodSpec postConstructInitApiLogic() {
            return MethodSpec.methodBuilder("postConstructInit")
                    .addAnnotation(PostConstruct.class)
                    .addModifiers(PRIVATE)
                    .returns(void.class)
                    .addStatement("apiLogic.setApiHooks(apiHooks)")
                    .addStatement("apiLogic.setDataManager(dataManager)")
                    .build();
        }

        private void handleTargetMethodsMapping(WithMethodLevelSecurity security, Map<CRUDEndpoints, List<AnnotationSpec>> methodLevelSecuritiesMap) {
            val targetMethods = security.targets();
            val securityAnnotations = SecurityAnnotationsFactory.of(security);
            for (val targetMethod : targetMethods) {
                if(!methodLevelSecuritiesMap.containsKey(targetMethod))
                    methodLevelSecuritiesMap.put(targetMethod, new ArrayList<>());
                var securities = methodLevelSecuritiesMap.get(targetMethod);
                for (var securityAnnotation : securityAnnotations) {
                    if (!securities.contains(securityAnnotation)) securities.add(securityAnnotation);
                    else {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                "Illegal attempt to repeat non repeatable security " +
                                        "annotation of type: " + securityAnnotation.type.toString() +
                                        " on or in entity of type: " + pascalCaseNameOf(entity));
                        return;
                    }
                }
                methodLevelSecuritiesMap.replace(targetMethod, securities);
            }
        }

        //method specs

        private MethodSpec genGetPaginatedBatch() {
            var builder = MethodSpec.methodBuilder(pluralCamelCaseName(entity))
                    .addModifiers(PUBLIC)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(ParameterSpec.builder(TypeName.INT, "offset").build())
                    .addParameter(graphQLParameter(TypeName.INT, "limit", "50"))
                    .addParameter(graphQLParameter(ClassName.get(String.class), "sortBy", getIdFieldName(entity)))
                    .addParameter(graphQLParameter(ClassName.get(Sort.Direction.class), "sortDirection", "\"ASC\""))
                    .addStatement("return apiLogic.getPaginatedBatch(offset, limit, sortBy, sortDirection)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(GET_PAGINATED_BATCH))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_PAGINATED_BATCH));
            return builder.build();
        }
        private MethodSpec genGetById(ProcessingEnvironment processingEnv) {
            String queryName = "get" + pascalCaseNameOf(entity) + "ById";
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(getIdType(entity, processingEnv), "input")
                    .addStatement("return apiLogic.getById(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(GET_BY_ID))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_BY_ID));
            return builder.build();
        }
        private MethodSpec genGetBatchByIds(ProcessingEnvironment processingEnv) {
            String queryName = "get" + toPlural(pascalCaseNameOf(entity)) + "ById";
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(ParameterSpec.builder(listOf(getIdType(entity, processingEnv)), "input").build())
                    .addStatement("return apiLogic.getCollectionById(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(GET_BATCH_BY_IDS))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_BATCH_BY_IDS));
            return builder.build();
        }
        private MethodSpec genCreate() {
            String mutationName = "create" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(ApifiStaticUtils.parameterizeType(entity))
                    .addStatement("return apiLogic.create(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(CREATE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(CREATE));
            return builder.build();
        }
        private MethodSpec genBatchCreate() {
            String mutationName = "create" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchCreate(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_CREATE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_CREATE));
            return builder.build();
        }
        private MethodSpec genUpdate() {
            String mutationName = "update" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.update(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(UPDATE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(UPDATE));
            return builder.build();
        }
        private MethodSpec genBatchUpdate() {
            String mutationName = "update" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchUpdate(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_UPDATE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_UPDATE));
            return builder.build();
        }
        private MethodSpec genDelete() {
            String mutationName = "delete" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.delete(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(DELETE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(DELETE));
            return builder.build();
        }
        private MethodSpec genBatchDelete() {
            String mutationName = "delete" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchDelete(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_DELETE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_DELETE));
            return builder.build();
        }
        private MethodSpec genArchive() {
            String mutationName = "archive" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.archive(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(ARCHIVE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(ARCHIVE));
            return builder.build();
        }
        private MethodSpec genBatchArchive() {
            String mutationName = "archive" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchArchive(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_ARCHIVE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_ARCHIVE));
            return builder.build();
        }
        private MethodSpec genDeArchive() {
            String mutationName = "deArchive" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.deArchive(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(DE_ARCHIVE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(DE_ARCHIVE));
            return builder.build();
        }
        private MethodSpec genBatchDeArchive() {
            String mutationName = "deArchive" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchDeArchive(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_DE_ARCHIVE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_DE_ARCHIVE));
            return builder.build();
        }
        private MethodSpec genGetArchivedPaginatedBatch() {
            var builder = MethodSpec.methodBuilder(toPlural(pascalCaseNameOf(entity)))
                    .addModifiers(PUBLIC)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(ParameterSpec.builder(TypeName.INT, "offset").build())
                    .addParameter(graphQLParameter(TypeName.INT, "limit", "50"))
                    .addParameter(graphQLParameter(ClassName.get(String.class), "sortBy", getIdFieldName(entity)))
                    .addParameter(graphQLParameter(ClassName.get(Sort.Direction.class), "sortDirection", "\"ASC\""))
                    .addStatement("return apiLogic.getArchivedPaginatedBatch(offset, limit, sortBy, sortDirection)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(GET_ARCHIVED_PAGINATED_BATCH))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_ARCHIVED_PAGINATED_BATCH));
            return builder.build();
        }
        @SuppressWarnings("deprecation")
        private MethodSpec genGetEmbeddedCollection(VariableElement embedded, TypeSpec.Builder serviceBuilder, TypeSpec.Builder testBuilder) {
            String queryName = camelcaseNameOf(embedded);
            ParameterSpec input = asParamList(entity, GraphQLContext.class);
            String embeddedCollectionApiHooksName = embeddedCollectionApiHooksName(embedded);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(suppressDeprecationWarning())
                    .addAnnotation(Batched.class)
                    .addAnnotation(GraphQLQuery.class)
                    .addParameter(input)
                    .addStatement("return apiLogic.getEmbeddedCollection(input, $S, $L, $L)",
                            camelcaseNameOf(embedded),//$S
                            embeddedCollectionApiHooksName, //$L
                            dataManagerName(embedded)       // $L
                    ).returns(listOfLists(embedded));
            val config = embedded.getAnnotation(EmbeddedCollectionApi.class);
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "Get"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Get"));
            return builder.build();
        }
        @SuppressWarnings("deprecation")
        private MethodSpec genGetEmbedded(VariableElement embedded) {
            String queryName = camelcaseNameOf(embedded);
            ParameterSpec input = asParamList(entity, GraphQLContext.class);
            var builder = MethodSpec.methodBuilder(queryName)
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
        private MethodSpec genAssociateWithEmbeddedCollection(VariableElement fk) {
            String mutationName = "associate" + pascalCaseNameOf(fk) + "With" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(EmbeddedCollectionApi.class);
            Class<?> apiHooks = null;
            if(config != null && isCustomEmbeddedCollectionApiHooks(config))
                apiHooks = config.apiHooks();
            boolean addPreExistingOnly = config != null && config.associatePreExistingOnly();
            String apiLogicBackingMethod = addPreExistingOnly ? "associatePreExistingWithEmbeddedCollection" : "associateWithEmbeddedCollection";
            ParameterSpec input = asParamList(collectionTypeName(fk));
            var builder = MethodSpec.methodBuilder(mutationName)
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
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "AssociateWith"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "AssociateWith"));
            return builder.build();
        }

        private MethodSpec genRemoveFromEmbeddedCollection(VariableElement fk) {
            String mutationName = "remove" + pascalCaseNameOf(fk) + "From" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(EmbeddedCollectionApi.class);
            Class<?> apiHooks = null;
            if(config != null && isCustomEmbeddedCollectionApiHooks(config))
                apiHooks = config.apiHooks();
            ParameterSpec input = asParamList(collectionTypeName(fk));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addStatement("return apiLogic.removeFromEmbeddedCollection(owner, $S, input, $L)",
                            camelcaseNameOf(fk),
                            apiHooks != null ? embeddedCollectionApiHooksName(fk) : "null")
                    .returns(listOf(collectionTypeName(fk)));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "RemoveFrom"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "RemoveFrom"));
            return builder.build();
        }
        private MethodSpec genUpdateInEmbeddedCollection(VariableElement fk) {
            String mutationName = "update" + pascalCaseNameOf(fk) + "In" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(EmbeddedCollectionApi.class);
            Class<?> apiHooks = null;
            if(isCustomEmbeddedCollectionApiHooks(config))
                apiHooks = config.apiHooks();
            ParameterSpec input = asParamList(collectionTypeName(fk));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(GraphQLMutation.class)
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addStatement("return apiLogic.updateEmbeddedCollection(owner, $L, input, $L)",
                            dataManagerName(fk),
                            apiHooks != null ? embeddedCollectionApiHooksName(fk) : "null")
                    .returns(listOf(collectionTypeName(fk)));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "UpdateIn"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "UpdateIn"));
            return builder.build();
        }

        private List<MethodSpec> toApiFindByEndpoints(VariableElement field) {

            var methodsToAdd = new ArrayList<MethodSpec>();
            val fieldName = camelcaseNameOf(field);
            val fieldPascalCaseName = pascalCaseNameOf(field);
            val fieldType = ClassName.get(field.asType());

            val apiFindByAnnotation = field.getAnnotation(ApiFindBy.class);
            val apiFindByUniqueAnnotation = field.getAnnotation(ApiFindByUnique.class);
            val apiFindAllByAnnotation = field.getAnnotation(ApiFindAllBy.class);

            if(apiFindByAnnotation != null) {
                val apiFindBy = MethodSpec
                        .methodBuilder("find" + toPlural(pascalCaseNameOf(entity)) + "By" + fieldPascalCaseName)
                        .returns(listOf(entity))
                        .addAnnotation(GraphQLQuery.class)
                        .addAnnotations(SecurityAnnotationsFactory.of(apiFindByAnnotation))
                        .addModifiers(PUBLIC)
                        .addParameter(ParameterSpec.builder(fieldType, fieldName).build())
                        .addStatement("return apiLogic.apiFindBy($S, $L)", fieldName, fieldName);
                methodsToAdd.add(apiFindBy
                    .build());
            } else if(apiFindByUniqueAnnotation != null) {
                methodsToAdd.add(MethodSpec
                    .methodBuilder("find" + pascalCaseNameOf(entity) + "ByUnique" + fieldPascalCaseName)
                    .returns(ClassName.get(entity))
                    .addAnnotation(GraphQLQuery.class)
                    .addAnnotations(SecurityAnnotationsFactory.of(apiFindByUniqueAnnotation))
                    .addModifiers(PUBLIC)
                    .addParameter(fieldType, fieldName)
                    .addStatement("return apiLogic.apiFindByUnique($S, $L)", fieldName, fieldName)
                    .build());
            }

            if(apiFindAllByAnnotation != null)
                methodsToAdd.add(MethodSpec
                        .methodBuilder("find" + toPlural(pascalCaseNameOf(entity)) + "By" + toPlural(fieldPascalCaseName))
                        .returns(listOf(entity))
                        .addAnnotation(GraphQLQuery.class)
                        .addAnnotations(SecurityAnnotationsFactory.of(apiFindAllByAnnotation))
                        .addModifiers(PUBLIC)
                        .addParameter(listOf(fieldType), toPlural(fieldName))
                        .addStatement("return apiLogic.apiFindAllBy($S, $L)", fieldName, toPlural(fieldName))
                        .build());
            return methodsToAdd;
        }

        //test method specs

        private MethodSpec genGetEmbeddedTest(VariableElement embedded) {
            String testName = "get" + pascalCaseNameOf(embedded) + "From" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getEmbeddedTest($L, $S)",
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
                    .addStatement("testLogic.getEmbeddedCollectionTest($L, $S, $L)",
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
            String suffix = namePrefix.endsWith("DataManager") ? "" : "DataManager";
            return FieldSpec
                    .builder(ParameterizedTypeName.get(ClassName.get(DataManager.class), ClassName.get(entity)), namePrefix + suffix)
                    .addModifiers(Modifier.PRIVATE)
                    .addAnnotation(Autowired.class)
                    .build();
        }

        private FieldSpec defaultApiHooks(){
            return FieldSpec
                    .builder(ParameterizedTypeName.get(ClassName.get(ApiHooks.class), ClassName.get(entity)), "apiHooks")
                    .addAnnotation(AnnotationSpec.builder(Autowired.class)
                            .addMember("required", "false")
                            .build())
                    .addModifiers(PRIVATE)
                    .build();
        }

        private FieldSpec apiLogic() {
            return FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(ApiLogic.class), ClassName.get(entity)), "apiLogic")
                    .addAnnotation(Autowired.class)
                    .addModifiers(PRIVATE)
                    .build();
        }

        private FieldSpec testLogic() {
            return FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(TestLogic.class), ClassName.get(entity)), "testLogic")
                    .addAnnotation(Autowired.class)
                    .addModifiers(PRIVATE)
                    .build();
        }

        public FieldSpec embeddedCollectionApiHooks(VariableElement field, VariableElement fk) {
            TypeName apiHooksType = null;
            val embeddedCollectionApi = field.getAnnotation(EmbeddedCollectionApi.class);
            if(embeddedCollectionApi != null){
                apiHooksType = getApiHooksTypeName(embeddedCollectionApi);
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

        private boolean isGraphQLIgnored(VariableElement fk) {
            val getter =
                    entity
                    .getEnclosedElements()
                    .stream()
                    .filter(elem ->
                            elem.getKind().equals(ElementKind.METHOD) &&
                            elem.getSimpleName().toString().equals("get" + pascalCaseNameOf(fk)) &&
                            ((ExecutableElement)elem).getReturnType().equals(fk.asType()))
                    .findFirst().orElse(null);
            if(getter == null) return fk.getAnnotation(GraphQLIgnore.class) != null;
            return getter.getAnnotation(GraphQLIgnore.class) != null;
        }

        private void addApiHooksIfPresent(VariableElement embedded, TypeSpec.Builder serviceBuilder, TypeSpec.Builder testBuilder, EmbeddedCollectionApi embeddedCollectionApi) {
            if(!isCustomEmbeddedCollectionApiHooks(embeddedCollectionApi)) return;
            final FieldSpec apiHooks = embeddedCollectionApiHooks(embedded, embedded);
            serviceBuilder.addField(apiHooks);
            testBuilder.addField(apiHooks);
        }

        private boolean isApiFindByAnnotated(VariableElement variableElement) {
            return
                    variableElement.getAnnotation(ApiFindBy.class) != null ||
                    variableElement.getAnnotation(ApiFindAllBy.class) != null ||
                    variableElement.getAnnotation(ApiFindByUnique.class) != null;
        }
        private boolean isCustomEmbeddedCollectionApiHooks(EmbeddedCollectionApi config) {
            if(config == null) return false;
            return !getApiHooksTypeName(config)
                    .toString()
                    .equals(NullEmbeddedCollectionApiHooks.class.getCanonicalName());
        }
    }
}
