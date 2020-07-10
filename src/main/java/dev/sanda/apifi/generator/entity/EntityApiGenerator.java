package dev.sanda.apifi.generator.entity;

import com.squareup.javapoet.*;
import dev.sanda.apifi.generator.client.ApifiClientFactory;
import dev.sanda.apifi.generator.client.GraphQLQueryBuilder;
import dev.sanda.apifi.service.*;
import dev.sanda.apifi.test_utils.TestableGraphQLService;
import dev.sanda.apifi.utils.ApifiStaticUtils;
import dev.sanda.apifi.annotations.*;
import dev.sanda.apifi.security.SecurityAnnotationsFactory;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.PageRequest;
import dev.sanda.datafi.service.DataManager;
import dev.sanda.testifi.TestLogic;
import graphql.execution.batched.Batched;
import io.leangen.graphql.annotations.GraphQLContext;
import io.leangen.graphql.annotations.GraphQLIgnore;
import io.leangen.graphql.annotations.GraphQLMutation;
import io.leangen.graphql.annotations.GraphQLQuery;
import lombok.Getter;
import lombok.val;
import lombok.var;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.PostConstruct;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.*;
import javax.persistence.ElementCollection;
import javax.tools.Diagnostic;
import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

import static dev.sanda.apifi.generator.client.GraphQLQueryType.MUTATION;
import static dev.sanda.apifi.generator.client.GraphQLQueryType.QUERY;
import static dev.sanda.apifi.generator.entity.ElementCollectionEndpointType.ADD_TO;
import static dev.sanda.apifi.generator.entity.ElementCollectionEndpointType.REMOVE__FROM;
import static dev.sanda.apifi.generator.entity.MapElementCollectionEndpointType.*;
import static dev.sanda.apifi.utils.ApifiStaticUtils.*;
import static dev.sanda.apifi.generator.entity.CRUDEndpoints.*;
import static dev.sanda.apifi.generator.entity.EntityCollectionEndpointType.*;
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
        private ApifiClientFactory clientFactory;
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

        public ServiceTestableServiceAndTest build(ProcessingEnvironment processingEnv, ApifiClientFactory clientFactory){
            this.clientFactory = clientFactory;
            this.processingEnv = processingEnv;
            SecurityAnnotationsFactory.setProcessingEnv(processingEnv);
            //init builders
            var serviceBuilder =
                    TypeSpec.classBuilder(entity.getSimpleName().toString() + "GraphQLApiService").addModifiers(PUBLIC)
                    .addAnnotation(Service.class).addAnnotation(Transactional.class);
            var testableServiceBuilder =
                    TypeSpec
                    .classBuilder("Testable" + entity.getSimpleName().toString() + "GraphQLApiService").addModifiers(PUBLIC)
                    .addSuperinterface(testableGraphQLServiceInterface())
                    .addAnnotation(Service.class);
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
            testableServiceBuilder.addField(apiLogic());

            serviceBuilder.addField(defaultDataManager());
            testableServiceBuilder.addField(defaultDataManager());
            testBuilder.addField(defaultDataManager());

            testBuilder.addField(testLogic());
            serviceBuilder.addField(defaultApiHooks());
            testableServiceBuilder.addField(defaultApiHooks());

            testBuilder.addField(defaultApiHooks());
            testBuilder.addField(apiLogic());
            serviceBuilder.addMethod(postConstructInitApiLogic());
            testableServiceBuilder.addMethod(postConstructInitApiLogic());
            testBuilder.addMethod(postConstructInitTestLogic());

            //field(s) 4 - foreign key data managers
            fields.forEach(field -> {
                        String typeNameKey = isIterable(field.asType(), processingEnv) ? getCollectionType(field) : field.asType().toString();
                        TypeElement type = entitiesMap.get(typeNameKey);
                        if(type != null) {
                            final FieldSpec typeDataManager = dataManager(type, dataManagerName(field));
                            serviceBuilder.addField(typeDataManager);
                            testableServiceBuilder.addField(typeDataManager);
                            testBuilder.addField(typeDataManager);
                        }
                    });

            // generate class level security annotations
            val serviceLevelSecurity = entity.getAnnotation(WithServiceLevelSecurity.class);
            if(serviceLevelSecurity != null){
                val securityAnnotations = SecurityAnnotationsFactory.of(serviceLevelSecurity);
                if(!securityAnnotations.isEmpty()) {
                    serviceBuilder.addAnnotations(securityAnnotations);
                    testableServiceBuilder.addAnnotations(securityAnnotations);
                }
            }

            // prepare the groundwork for method level security annotations
            methodLevelSecuritiesMap = new HashMap<>();
            val methodLevelSecurities = entity.getAnnotationsByType(WithMethodLevelSecurity.class);
            if(methodLevelSecurities.length > 0){
                for (var security : methodLevelSecurities)
                    handleTargetMethodsMapping(security, methodLevelSecuritiesMap);
            }

            //generate crud methods:
            
            //GET_PAGINATED_BATCH
            if(crudResolvers.containsKey(GET_PAGINATED_BATCH)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genGetPaginatedBatch(clientQueryBuilder));
                testableServiceBuilder.addMethod(genGetPaginatedBatch(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genGetPaginatedBatchTest());
            }

            //GET_TOTAL_NON_ARCHIVED_COUNT
            if(crudResolvers.containsKey(GET_TOTAL_COUNT)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genGetTotalNonArchivedCount(clientQueryBuilder));
                testableServiceBuilder.addMethod(genGetTotalNonArchivedCount(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                //TODO test method
            }

            //GET_TOTAL_ARCHIVED_COUNT
            if(crudResolvers.containsKey(GET_TOTAL_ARCHIVED_COUNT)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genGetTotalArchivedCount(clientQueryBuilder));
                testableServiceBuilder.addMethod(genGetTotalArchivedCount(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                //TODO test method
            }

            //GET_BY_ID
            if(crudResolvers.containsKey(GET_BY_ID)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genGetById(processingEnv, clientQueryBuilder));
                testableServiceBuilder.addMethod(genGetById(processingEnv, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genGetByIdTest());
            }
            //GET_BATCH_BY_IDS
            if(crudResolvers.containsKey(GET_BATCH_BY_IDS)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genGetBatchByIds(processingEnv, clientQueryBuilder));
                testableServiceBuilder.addMethod(genGetBatchByIds(processingEnv, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genGetBatchByIdsTest());
            }
            //CREATE
            if(crudResolvers.containsKey(CREATE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genCreate(clientQueryBuilder));
                testableServiceBuilder.addMethod(genCreate(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genCreateTest());
            }
            //BATCH_CREATE
            if(crudResolvers.containsKey(BATCH_CREATE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genBatchCreate(clientQueryBuilder));
                testableServiceBuilder.addMethod(genBatchCreate(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genBatchCreateTest());
            }
            //UPDATE
            if(crudResolvers.containsKey(UPDATE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genUpdate(clientQueryBuilder));
                testableServiceBuilder.addMethod(genUpdate(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genUpdateTest());
            }
            //BATCH_UPDATE
            if(crudResolvers.containsKey(BATCH_UPDATE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genBatchUpdate(clientQueryBuilder));
                testableServiceBuilder.addMethod(genBatchUpdate(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genBatchUpdateTest());
            }
            //DELETE
            if(crudResolvers.containsKey(DELETE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genDelete(clientQueryBuilder));
                testableServiceBuilder.addMethod(genDelete(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genDeleteTest());
            }
            //BATCH_DELETE
            if(crudResolvers.containsKey(BATCH_DELETE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genBatchDelete(clientQueryBuilder));
                testableServiceBuilder.addMethod(genBatchDelete(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genBatchDeleteTest());
            }
            //ARCHIVE
            if(crudResolvers.containsKey(ARCHIVE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genArchive(clientQueryBuilder));
                testableServiceBuilder.addMethod(genArchive(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genArchiveTest());
            }
            //BATCH_ARCHIVE
            if(crudResolvers.containsKey(BATCH_ARCHIVE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genBatchArchive(clientQueryBuilder));
                testableServiceBuilder.addMethod(genBatchArchive(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genBatchArchiveTest());
            }
            //DE_ARCHIVE
            if(crudResolvers.containsKey(DE_ARCHIVE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genDeArchive(clientQueryBuilder));
                testableServiceBuilder.addMethod(genDeArchive(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genDeArchiveTest());
            }
            //BATCH_DE_ARCHIVE
            if(crudResolvers.containsKey(BATCH_DE_ARCHIVE)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genBatchDeArchive(clientQueryBuilder));
                testableServiceBuilder.addMethod(genBatchDeArchive(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genBatchDeArchiveTest());
            }
            //GET_ARCHIVED_PAGINATED_BATCH
            if(crudResolvers.containsKey(GET_ARCHIVED_PAGINATED_BATCH)) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genGetArchivedPaginatedBatch(clientQueryBuilder));
                testableServiceBuilder.addMethod(genGetArchivedPaginatedBatch(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
                testBuilder.addMethod(genGetArchivedPaginatedBatchTest());
            }

            //generate foreign key endpoints
            fields.stream().filter(ApifiStaticUtils::isForeignKeyOrKeys).collect(Collectors.toList())
            .forEach(fk -> {
                if(isIterable(fk.asType(), processingEnv)){

                    val config = fk.getAnnotation(EntityCollectionApi.class);
                    val resolvers = config != null ? Arrays.asList(config.endpoints()) : new ArrayList<EntityCollectionEndpointType>();
                    addApiHooksIfPresent(fk, testableServiceBuilder, serviceBuilder, testBuilder, config);

                    //read
                    if(!isGraphQLIgnored(fk)){
                        serviceBuilder.addMethod(genGetEntityCollection(fk, serviceBuilder, testBuilder));
                        testableServiceBuilder.addMethod(genGetEntityCollection(fk, serviceBuilder, testBuilder));
                        testBuilder.addMethod(genGetEntityCollectionTest(fk));
                    }
                    //associate
                    if(resolvers.contains(ASSOCIATE_WITH)){
                        val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                        serviceBuilder.addMethod(genAssociateWithEntityCollection(fk, clientQueryBuilder));
                        testableServiceBuilder.addMethod(genAssociateWithEntityCollection(fk, clientQueryBuilder));
                        clientFactory.addQuery(clientQueryBuilder);
                        testBuilder.addMethod(genAssociateWithEntityCollectionTest(fk));
                    }
                    //update
                    if(resolvers.contains(EntityCollectionEndpointType.UPDATE_IN)){
                        val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                        serviceBuilder.addMethod(genUpdateInEntityCollection(fk, clientQueryBuilder));
                        testableServiceBuilder.addMethod(genUpdateInEntityCollection(fk, clientQueryBuilder));
                        clientFactory.addQuery(clientQueryBuilder);
                        testBuilder.addMethod(genUpdateInEntityCollectionTest(fk));
                    }
                    //remove
                    if(resolvers.contains(REMOVE_FROM)){
                        val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                        serviceBuilder.addMethod(genRemoveFromEntityCollection(fk, clientQueryBuilder));
                        testableServiceBuilder.addMethod(genRemoveFromEntityCollection(fk, clientQueryBuilder));
                        clientFactory.addQuery(clientQueryBuilder);
                        testBuilder.addMethod(genRemoveFromEntityCollectionTest(fk));
                    }
                    if(resolvers.contains(GET_PAGINATED__BATCH)){
                        val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                        serviceBuilder.addMethod(genGetPaginatedBatchInEntityCollection(fk, clientQueryBuilder));
                        testableServiceBuilder.addMethod(genGetPaginatedBatchInEntityCollection(fk, clientQueryBuilder));
                        clientFactory.addQuery(clientQueryBuilder);
                        /*testBuilder.addMethod(genGetPaginatedBatchInEntityCollectionTest(fk));TODO*/
                    }
                    if(resolvers.contains(PAGINATED__FREE_TEXT_SEARCH)){
                        val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                        serviceBuilder.addMethod(genGetPaginatedFreeTextSearchInEntityCollection(fk, clientQueryBuilder));
                        testableServiceBuilder.addMethod(genGetPaginatedFreeTextSearchInEntityCollection(fk, clientQueryBuilder));
                        clientFactory.addQuery(clientQueryBuilder);
                        /*testBuilder.addMethod(genGetPaginatedFreeTextSearchInEntityCollectionTest(fk));TODO*/
                    }
                }else {
                    serviceBuilder.addMethod(genGetEmbedded(fk));
                    testableServiceBuilder.addMethod(genGetEmbedded(fk));
                    testBuilder.addMethod(genGetEmbeddedTest(fk));
                }
            });

            //generate element collection endpoints
            fields.stream().filter(f -> f.getAnnotation(ElementCollection.class) != null).collect(Collectors.toList())
            .forEach(elemCollection -> {
                val elementCollectionApiConfig = elemCollection.getAnnotation(ElementCollectionApi.class);
                if(elementCollectionApiConfig != null)
                    generateElementCollectionMethods(clientFactory, testableServiceBuilder, serviceBuilder, elemCollection, elementCollectionApiConfig);
                else {
                    val mapElementCollectionApiConfig = elemCollection.getAnnotation(MapElementCollectionApi.class);
                    if(mapElementCollectionApiConfig != null)
                        generateMapElementCollectionMethods(clientFactory, testableServiceBuilder, serviceBuilder, elemCollection, mapElementCollectionApiConfig);
                }
            });

            //generate api free text search endpoints
            val hasFreeTextSearchFields = fields
                    .stream()
                    .anyMatch(this::isFreeTextSearchAnnotated);
            if(hasFreeTextSearchFields) {
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genFreeTextSearchBy(clientQueryBuilder));
                testableServiceBuilder.addMethod(genFreeTextSearchBy(clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }

            // generate api find by endpoints
            fields
                    .stream()
                    .filter(this::isApiFindByAnnotated)
                    .map(field -> toApiFindByEndpoints(field, clientFactory))
                    .forEach(serviceBuilder::addMethods);

            //return result
            return new ServiceTestableServiceAndTest(serviceBuilder.build(), testableServiceBuilder.build(), testBuilder.build());
        }

        private void generateElementCollectionMethods(ApifiClientFactory clientFactory, TypeSpec.Builder testableServiceBuilder, TypeSpec.Builder serviceBuilder, VariableElement elemCollection, ElementCollectionApi config) {
            val endpoints = config != null ? Arrays.asList(config.endpoints()) : new ArrayList<ElementCollectionEndpointType>();
            if(endpoints.contains(ADD_TO)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genAddToElementCollection(elemCollection, clientQueryBuilder));
                testableServiceBuilder.addMethod(genAddToElementCollection(elemCollection, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }
            if(endpoints.contains(REMOVE__FROM)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genRemoveFromElementCollection(elemCollection, clientQueryBuilder));
                testableServiceBuilder.addMethod(genRemoveFromElementCollection(elemCollection, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }
            if(endpoints.contains(ElementCollectionEndpointType.PAGINATED__BATCH_)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genGetPaginatedBatchInElementCollection(elemCollection, clientQueryBuilder));
                testableServiceBuilder.addMethod(genGetPaginatedBatchInElementCollection(elemCollection, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }
            if(endpoints.contains(ElementCollectionEndpointType.PAGINATED__FREE__TEXT_SEARCH)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genFreeTextSearchInElementCollection(elemCollection, clientQueryBuilder));
                testableServiceBuilder.addMethod(genFreeTextSearchInElementCollection(elemCollection, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }
        }

        private void generateMapElementCollectionMethods(ApifiClientFactory clientFactory,
                                                         TypeSpec.Builder testableServiceBuilder,
                                                         TypeSpec.Builder serviceBuilder,
                                                         VariableElement elemCollection, MapElementCollectionApi config) {
            val endpoints = config != null ? Arrays.asList(config.endpoints()) : new ArrayList<MapElementCollectionEndpointType>();
            if(endpoints.contains(PUT_ALL)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genAddToMapElementCollection(elemCollection, clientQueryBuilder));
                testableServiceBuilder.addMethod(genAddToMapElementCollection(elemCollection, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }
            if(endpoints.contains(REMOVE_ALL)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genRemoveFromMapElementCollection(elemCollection, clientQueryBuilder));
                testableServiceBuilder.addMethod(genRemoveFromMapElementCollection(elemCollection, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }
            if(endpoints.contains(PAGINATED__BATCH__)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genGetPaginatedBatchInMapElementCollection(elemCollection, clientQueryBuilder));
                testableServiceBuilder.addMethod(genGetPaginatedBatchInMapElementCollection(elemCollection, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }
            if(endpoints.contains(PAGINATED__FREE__TEXT__SEARCH)){
                val clientQueryBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                serviceBuilder.addMethod(genFreeTextSearchInMapElementCollection(elemCollection, clientQueryBuilder));
                testableServiceBuilder.addMethod(genFreeTextSearchInMapElementCollection(elemCollection, clientQueryBuilder));
                clientFactory.addQuery(clientQueryBuilder);
            }
        }

        private boolean isFreeTextSearchAnnotated(VariableElement variableElement) {
            val freeTextSearchByFields = variableElement.getEnclosingElement().getAnnotation(WithApiFreeTextSearchByFields.class);
            return freeTextSearchByFields != null && containsFieldName(freeTextSearchByFields, variableElement);
        }
        private FieldSpec defaultDataManager() {
            return FieldSpec
                    .builder(ParameterizedTypeName.get(ClassName.get(DataManager.class), ClassName.get(entity)), "dataManager")
                    .addAnnotation(Autowired.class)
                    .addAnnotation(Getter.class)
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
        private MethodSpec genGetPaginatedBatch(GraphQLQueryBuilder clientQueryBuilder) {
            final String name = pluralCamelCaseName(entity);
            var builder = MethodSpec.methodBuilder(name)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(PageRequest.class), "input").build())
                    .addCode(initSortByIfNull(entity))
                    .addStatement("return apiLogic.getPaginatedBatch(input)")
                    .returns(pageType(entity));
            if(methodLevelSecuritiesMap.containsKey(GET_PAGINATED_BATCH))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_PAGINATED_BATCH));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(name);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", "PageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genGetTotalNonArchivedCount(GraphQLQueryBuilder clientQueryBuilder) {
            String queryName = "countTotal" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addStatement("return apiLogic.getTotalNonArchivedCount()")
                    .returns(TypeName.LONG);
            if(methodLevelSecuritiesMap.containsKey(GET_TOTAL_COUNT))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_TOTAL_COUNT));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setPrimitiveReturnType(true);
            return builder.build();
        }
        private MethodSpec genGetTotalArchivedCount(GraphQLQueryBuilder clientQueryBuilder) {
            String queryName = "countTotalArchived" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addStatement("return apiLogic.getTotalArchivedCount()")
                    .returns(TypeName.LONG);
            if(methodLevelSecuritiesMap.containsKey(GET_TOTAL_ARCHIVED_COUNT))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_TOTAL_ARCHIVED_COUNT));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setPrimitiveReturnType(true);
            return builder.build();
        }
        private MethodSpec genGetById(ProcessingEnvironment processingEnv, GraphQLQueryBuilder clientQueryBuilder) {
            String queryName = "get" + pascalCaseNameOf(entity) + "ById";
            final ClassName idType = getIdType(entity, processingEnv);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(idType, "input")
                    .addStatement("return apiLogic.getById(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(GET_BY_ID))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_BY_ID));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", getInputTypeSimpleName(idType.simpleName(), idType.packageName()));
            }});
            return builder.build();
        }
        private MethodSpec genGetBatchByIds(ProcessingEnvironment processingEnv, GraphQLQueryBuilder clientQueryBuilder) {
            String queryName = "get" + toPlural(pascalCaseNameOf(entity)) + "ById";
            final ClassName idType = getIdType(entity, processingEnv);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(listOf(idType), "input").build())
                    .addStatement("return apiLogic.getBatchByIds(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(GET_BATCH_BY_IDS))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_BATCH_BY_IDS));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", inBrackets(idType.simpleName() + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genCreate(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "create" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(ApifiStaticUtils.parameterizeType(entity))
                    .addStatement("return apiLogic.create(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(CREATE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(CREATE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", entity.getSimpleName().toString() + "Input");
            }});
            return builder.build();
        }
        private MethodSpec genBatchCreate(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "create" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchCreate(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_CREATE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_CREATE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", inBrackets(entity.getSimpleName().toString() + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genUpdate(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "update" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.update(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(UPDATE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(UPDATE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", entity.getSimpleName().toString() + "Input");
            }});
            return builder.build();
        }
        private MethodSpec genBatchUpdate(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "update" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchUpdate(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_UPDATE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_UPDATE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", inBrackets(entity.getSimpleName().toString() + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genDelete(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "delete" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.delete(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(DELETE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(DELETE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", entity.getSimpleName().toString() + "Input");
            }});
            return builder.build();
        }
        private MethodSpec genBatchDelete(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "delete" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchDelete(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_DELETE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_DELETE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", inBrackets(entity.getSimpleName().toString() + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genArchive(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "archive" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.archive(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(ARCHIVE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(ARCHIVE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", entity.getSimpleName().toString() + "Input");
            }});
            return builder.build();
        }
        private MethodSpec genBatchArchive(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "archive" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchArchive(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_ARCHIVE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_ARCHIVE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", inBrackets(entity.getSimpleName().toString() + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genDeArchive(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "deArchive" + pascalCaseNameOf(entity);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(parameterizeType(entity))
                    .addStatement("return apiLogic.deArchive(input)")
                    .returns(TypeName.get(entity.asType()));
            if(methodLevelSecuritiesMap.containsKey(DE_ARCHIVE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(DE_ARCHIVE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", entity.getSimpleName().toString() + "Input");
            }});
            return builder.build();
        }
        private MethodSpec genBatchDeArchive(GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "deArchive" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(asParamList(entity))
                    .addStatement("return apiLogic.batchDeArchive(input)")
                    .returns(listOf(entity));
            if(methodLevelSecuritiesMap.containsKey(BATCH_DE_ARCHIVE))
                builder.addAnnotations(methodLevelSecuritiesMap.get(BATCH_DE_ARCHIVE));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", inBrackets(entity.getSimpleName().toString() + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genGetArchivedPaginatedBatch(GraphQLQueryBuilder clientQueryBuilder) {
            final String name = "archived" + toPlural(pascalCaseNameOf(entity));
            var builder = MethodSpec.methodBuilder(name)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(PageRequest.class), "input").build())
                    .addCode(initSortByIfNull(entity))
                    .addStatement("return apiLogic.getArchivedPaginatedBatch(input)")
                    .returns(pageType(entity));
            if(methodLevelSecuritiesMap.containsKey(GET_ARCHIVED_PAGINATED_BATCH))
                builder.addAnnotations(methodLevelSecuritiesMap.get(GET_ARCHIVED_PAGINATED_BATCH));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(name);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", "PageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genFreeTextSearchBy(GraphQLQueryBuilder clientQueryBuilder) {
            final String name = camelcaseNameOf(entity) + "FreeTextSearch";
            var builder = MethodSpec
                    .methodBuilder(name)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addModifiers(PUBLIC)
                    .addParameter(ParameterSpec.builder(ClassName.get(FreeTextSearchPageRequest.class), "input").build())
                    .addCode(initSortByIfNull(entity))
                    .addStatement("return apiLogic.freeTextSearch(input)")
                    .returns(pageType(entity));
            val textSearchBySecurity = entity.getAnnotation(WithApiFreeTextSearchByFields.class);

            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(textSearchBySecurity, ""))
                builder.addAnnotations(SecurityAnnotationsFactory.of(textSearchBySecurity, ""));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(name);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("input", "FreeTextSearchPageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genFreeTextSearchInElementCollection(VariableElement elemCollection, GraphQLQueryBuilder clientQueryBuilder) {
            val queryName = "freeTextSearch" + pascalCaseNameOf(elemCollection) + "In" + pascalCaseNameOf(entity);
            val config = elemCollection.getAnnotation(ElementCollectionApi.class);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(ParameterSpec.builder(ClassName.get(FreeTextSearchPageRequest.class), "input").build())
                    .addStatement(
                            "return apiLogic.getFreeTextSearchPaginatedBatchInElementCollection(" +
                                    "owner, input, $S, $L" +
                                    ")",
                            camelcaseNameOf(elemCollection),
                            isCustomElementCollectionApiHooks(config) ?
                                    elementCollectionApiHooksName(elemCollection) : "null")
                    .returns(pageType(elemCollection));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "FreeTextSearch"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "FreeTextSearch"));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", "FreeTextSearchPageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genGetPaginatedBatchInElementCollection(VariableElement elemCollection, GraphQLQueryBuilder clientQueryBuilder) {
            val queryName = camelcaseNameOf(elemCollection) + "In" + pascalCaseNameOf(entity);
            val config = elemCollection.getAnnotation(ElementCollectionApi.class);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(ParameterSpec.builder(ClassName.get(PageRequest.class), "input").build())
                    .addStatement(
                            "return apiLogic.getPaginatedBatchInElementCollection(" +
                                    "owner, input, $S, $L" +
                                    ")",
                            camelcaseNameOf(elemCollection),
                            isCustomElementCollectionApiHooks(config) ?
                                    elementCollectionApiHooksName(elemCollection) : "null")
                    .returns(pageType(elemCollection));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "GetPaginated"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "GetPaginated"));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", "PageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genRemoveFromElementCollection(VariableElement elemCollection, GraphQLQueryBuilder clientQueryBuilder) {
            val mutationName = "remove" + pascalCaseNameOf(elemCollection) + "From" + pascalCaseNameOf(entity);
            val config = elemCollection.getAnnotation(ElementCollectionApi.class);
            val collectionTypeName = collectionTypeName(elemCollection);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(asParamList(collectionTypeName))
                    .addStatement(
                            "return apiLogic.removeFromElementCollection(" +
                                    "owner, $S, input, $L" +
                                    ")",
                            camelcaseNameOf(elemCollection),
                            isCustomElementCollectionApiHooks(config) ?
                                    elementCollectionApiHooksName(elemCollection) : "null")
                    .returns(listOf(collectionTypeName));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "Remove"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Remove"));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genAddToElementCollection(VariableElement elemCollection, GraphQLQueryBuilder clientQueryBuilder) {
            val mutationName = "add" + pascalCaseNameOf(elemCollection) + "To" + pascalCaseNameOf(entity);
            val config = elemCollection.getAnnotation(ElementCollectionApi.class);
            val collectionTypeName = collectionTypeName(elemCollection);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(asParamList(collectionTypeName))
                    .addStatement(
                            "return apiLogic.addToElementCollection(" +
                                    "owner, $S, input, $L" +
                                    ")",
                            camelcaseNameOf(elemCollection),
                            isCustomElementCollectionApiHooks(config) ?
                                    elementCollectionApiHooksName(elemCollection) : "null")
                    .returns(listOf(collectionTypeName));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "Add"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Add"));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genFreeTextSearchInMapElementCollection(VariableElement elemCollection, GraphQLQueryBuilder clientQueryBuilder) {
            val queryName = "freeTextSearch" + pascalCaseNameOf(elemCollection) + "In" + pascalCaseNameOf(entity);
            val config = elemCollection.getAnnotation(MapElementCollectionApi.class);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(ParameterSpec.builder(ClassName.get(FreeTextSearchPageRequest.class), "input").build())
                    .addStatement(
                            "return apiLogic.getFreeTextSearchPaginatedBatchInMapElementCollection(" +
                                    "owner, input, $S, $L" +
                                    ")",
                            camelcaseNameOf(elemCollection),
                            isCustomElementCollectionApiHooks(config) ?
                                    mapElementCollectionApiHooksName(elemCollection) : "null")
                    .returns(mapEntryListPageType(elemCollection));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "FreeTextSearch"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "FreeTextSearch"));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", "FreeTextSearchPageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genGetPaginatedBatchInMapElementCollection(VariableElement elemCollection, GraphQLQueryBuilder clientQueryBuilder) {
            val queryName = camelcaseNameOf(elemCollection) + "In" + pascalCaseNameOf(entity);
            val config = elemCollection.getAnnotation(MapElementCollectionApi.class);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(ParameterSpec.builder(PageRequest.class, "input").build())
                    .addStatement(
                            "return apiLogic.getPaginatedBatchInMapElementCollection(" +
                                    "owner, input, $S, $L" +
                                    ")",
                            camelcaseNameOf(elemCollection),
                            isCustomElementCollectionApiHooks(config) ?
                                    mapElementCollectionApiHooksName(elemCollection) : "null")
                    .returns(mapEntryListPageType(elemCollection));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "GetPaginated"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "GetPaginated"));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", "PageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genRemoveFromMapElementCollection(VariableElement elemCollection, GraphQLQueryBuilder clientQueryBuilder) {
            val mutationName = "remove" + pascalCaseNameOf(elemCollection) + "From" + pascalCaseNameOf(entity);
            val config = elemCollection.getAnnotation(MapElementCollectionApi.class);
            val collectionTypeName = mapOf(elemCollection);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(asParamMapKeyList(elemCollection))//TODO - validate
                    .addStatement(
                            "return apiLogic.removeFromMapElementCollection(" +
                                    "owner, $S, input, $L" +
                                    ")",
                            camelcaseNameOf(elemCollection),
                            isCustomElementCollectionApiHooks(config) ?
                                    mapElementCollectionApiHooksName(elemCollection) : "null")
                    .returns(mapOf(elemCollection));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "Remove"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Remove"));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genAddToMapElementCollection(VariableElement elemCollection, GraphQLQueryBuilder clientQueryBuilder) {
            val mutationName = "add" + pascalCaseNameOf(elemCollection) + "To" + pascalCaseNameOf(entity);
            val config = elemCollection.getAnnotation(MapElementCollectionApi.class);
            val collectionTypeName = collectionTypeName(elemCollection);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(asParamMap(elemCollection))
                    .addStatement(
                            "return apiLogic.addToMapElementCollection(" +
                                    "owner, $S, input, $L" +
                                    ")",
                            camelcaseNameOf(elemCollection),
                            isCustomElementCollectionApiHooks(config) ?
                                    elementCollectionApiHooksName(elemCollection) : "null")
                    .returns(mapOf(elemCollection));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "Put"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Put"));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input"));
            }});
            return builder.build();
        }

        @SuppressWarnings("deprecation")
        private MethodSpec genGetEntityCollection(VariableElement entityCollectionField, TypeSpec.Builder serviceBuilder, TypeSpec.Builder testBuilder) {
            String queryName = camelcaseNameOf(entityCollectionField);
            ParameterSpec input = asParamList(entity, GraphQLContext.class);
            String entityCollectionApiHooksName = entityCollectionApiHooksName(entityCollectionField);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(suppressDeprecationWarning())
                    .addAnnotation(Batched.class)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(input)
                    .addStatement("return apiLogic.getEntityCollection(input, $S, $L, $L)",
                            camelcaseNameOf(entityCollectionField),//$S
                            entityCollectionApiHooksName, //$L
                            dataManagerName(entityCollectionField)       // $L
                    ).returns(listOfLists(entityCollectionField));
            val config = entityCollectionField.getAnnotation(EntityCollectionApi.class);
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "Get"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "Get"));
            return builder.build();
        }
        @SuppressWarnings("deprecation")
        private MethodSpec genGetEmbedded(VariableElement entityCollectionField) {
            String queryName = camelcaseNameOf(entityCollectionField);
            ParameterSpec input = asParamList(entity, GraphQLContext.class);
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(suppressDeprecationWarning())
                    .addAnnotation(Batched.class)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(input)
                    .addStatement("return apiLogic.getEmbedded(input, $S, $L)",
                            camelcaseNameOf(entityCollectionField),
                            dataManagerName(entityCollectionField))
                    .returns(listOf(entityCollectionField));
            return builder.build();
        }
        private MethodSpec genAssociateWithEntityCollection(VariableElement fk, GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "associate" + pascalCaseNameOf(fk) + "With" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(EntityCollectionApi.class);
            boolean addPreExistingOnly = config != null && config.associatePreExistingOnly();
            String apiLogicBackingMethod = addPreExistingOnly ? "associatePreExistingWithEntityCollection" : "associateWithEntityCollection";
            final TypeName collectionTypeName = collectionTypeName(fk);
            ParameterSpec input = asParamList(collectionTypeName);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addStatement("return apiLogic.$L(owner, $S, input, $L, $L)",
                            apiLogicBackingMethod,
                            camelcaseNameOf(fk),
                            dataManagerName(fk),
                            isCustomEntityCollectionApiHooks(config) ?
                                    entityCollectionApiHooksName(fk) : "null")
                    .returns(listOf(collectionTypeName));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "AssociateWith"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "AssociateWith"));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input"));
            }});
            return builder.build();
        }
        private String collectionTypeSimpleName(TypeName collectionTypeName) {
            final String name = collectionTypeName.toString();
            return name.substring(name.lastIndexOf(".") + 1);
        }
        private MethodSpec genRemoveFromEntityCollection(VariableElement fk, GraphQLQueryBuilder clientQueryBuilder) {
            String mutationName = "remove" + pascalCaseNameOf(fk) + "From" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(EntityCollectionApi.class);
            final TypeName collectionTypeName = collectionTypeName(fk);
            ParameterSpec input = asParamList(collectionTypeName);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addStatement("return apiLogic.removeFromEntityCollection(owner, $S, input, $L, $L)",
                            camelcaseNameOf(fk),
                            dataManagerName(fk),
                            isCustomEntityCollectionApiHooks(config) ?
                                    entityCollectionApiHooksName(fk) : "null")
                    .returns(listOf(collectionTypeName));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "RemoveFrom"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "RemoveFrom"));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input"));
            }});
            return builder.build();
        }
        private MethodSpec genGetPaginatedFreeTextSearchInEntityCollection(VariableElement fk, GraphQLQueryBuilder clientQueryBuilder) {
            String queryName = camelcaseNameOf(fk) + "In" + pascalCaseNameOf(entity) + "FreeTextSearch";
            val config = fk.getAnnotation(EntityCollectionApi.class);
            ParameterSpec input = ParameterSpec.builder(TypeName.get(FreeTextSearchPageRequest.class), "input").build();
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addCode(initSortByIfNull(entitiesMap.get(getCollectionType(fk))))
                    .addStatement("return apiLogic.paginatedFreeTextSearchInEntityCollection(owner, input, $S, $L, $L)",
                            camelcaseNameOf(fk),
                            dataManagerName(fk),
                            isCustomEntityCollectionApiHooks(config) ?
                                    entityCollectionApiHooksName(fk) : "null")
                    .returns(pageType(fk));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "PaginatedFreeTextSearch"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "PaginatedFreeTextSearch"));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", "FreeTextSearchPageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genGetPaginatedBatchInEntityCollection(VariableElement fk, GraphQLQueryBuilder clientQueryBuilder) {
            String queryName = camelcaseNameOf(fk) + "In" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(EntityCollectionApi.class);
            ParameterSpec input = ParameterSpec.builder(TypeName.get(PageRequest.class), "input").build();
            var builder = MethodSpec.methodBuilder(queryName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlQueryAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addCode(initSortByIfNull(entitiesMap.get(getCollectionType(fk))))
                    .addStatement("return apiLogic.getPaginatedBatchInEntityCollection(owner, input, $S, $L, $L)",
                            camelcaseNameOf(fk),
                            dataManagerName(fk),
                            isCustomEntityCollectionApiHooks(config) ?
                                    entityCollectionApiHooksName(fk) : "null")
                    .returns(pageType(fk));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "GetPaginated"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "GetPaginated"));
            clientQueryBuilder.setQueryType(QUERY);
            clientQueryBuilder.setQueryName(queryName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", "PageRequestInput");
            }});
            return builder.build();
        }
        private MethodSpec genUpdateInEntityCollection(VariableElement fk, GraphQLQueryBuilder clientQueryBuilder) {
            val mutationName = "update" + pascalCaseNameOf(fk) + "In" + pascalCaseNameOf(entity);
            val config = fk.getAnnotation(EntityCollectionApi.class);
            val hasApiHooks = isCustomEntityCollectionApiHooks(config);
            final TypeName collectionTypeName = collectionTypeName(fk);
            val input = asParamList(collectionTypeName);
            var builder = MethodSpec.methodBuilder(mutationName)
                    .addModifiers(PUBLIC)
                    .addAnnotation(graphqlMutationAnnotation())
                    .addParameter(ParameterSpec.builder(ClassName.get(entity), "owner").build())
                    .addParameter(input)
                    .addStatement("return apiLogic.updateEntityCollection(owner, $L, input, $L)",
                            dataManagerName(fk),
                            hasApiHooks ? entityCollectionApiHooksName(fk) : "null")
                    .returns(listOf(collectionTypeName));
            if(SecurityAnnotationsFactory.areSecurityAnnotationsPresent(config, "", "UpdateIn"))
                builder.addAnnotations(SecurityAnnotationsFactory.of(config, "", "UpdateIn"));
            clientQueryBuilder.setQueryType(MUTATION);
            clientQueryBuilder.setQueryName(mutationName);
            clientQueryBuilder.setVars(new LinkedHashMap<String, String>(){{
                put("owner", entity.getSimpleName() + "Input");
                put("input", inBrackets(collectionTypeSimpleName(collectionTypeName) + "Input"));
            }});
            return builder.build();
        }
        private List<MethodSpec> toApiFindByEndpoints(VariableElement field, ApifiClientFactory clientFactory) {

            var methodsToAdd = new ArrayList<MethodSpec>();
            val fieldName = camelcaseNameOf(field);
            val fieldPascalCaseName = pascalCaseNameOf(field);
            val fieldType = ClassName.get(field.asType());

            val apiFindByAnnotation = field.getAnnotation(ApiFindBy.class);
            val apiFindByUniqueAnnotation = field.getAnnotation(ApiFindByUnique.class);
            val apiFindAllByAnnotation = field.getAnnotation(ApiFindAllBy.class);
            final String inputTypeSimpleName = getInputTypeSimpleName(field.getSimpleName().toString(), field.asType().toString());

            if(apiFindByAnnotation != null) {
                val clientBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                final String name = "find" + toPlural(pascalCaseNameOf(entity)) + "By" + fieldPascalCaseName;
                val apiFindBy = MethodSpec
                        .methodBuilder(name)
                        .returns(listOf(entity))
                        .addAnnotation(graphqlQueryAnnotation())
                        .addAnnotations(SecurityAnnotationsFactory.of(apiFindByAnnotation))
                        .addModifiers(PUBLIC)
                        .addParameter(ParameterSpec.builder(fieldType, fieldName).build())
                        .addStatement("return apiLogic.apiFindBy($S, $L)", fieldName, fieldName);
                methodsToAdd.add(apiFindBy
                    .build());
                clientBuilder.setQueryType(QUERY);
                clientBuilder.setQueryName(name);
                clientBuilder.setVars(new LinkedHashMap<String, String>(){{
                    put(fieldName, inputTypeSimpleName);
                }});
                clientFactory.addQuery(clientBuilder);
            } else if(apiFindByUniqueAnnotation != null) {
                val clientBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                final String name = "find" + pascalCaseNameOf(entity) + "ByUnique" + fieldPascalCaseName;
                methodsToAdd.add(MethodSpec
                    .methodBuilder(name)
                    .returns(ClassName.get(entity))
                    .addAnnotation(graphqlQueryAnnotation())
                    .addAnnotations(SecurityAnnotationsFactory.of(apiFindByUniqueAnnotation))
                    .addModifiers(PUBLIC)
                    .addParameter(fieldType, fieldName)
                    .addStatement("return apiLogic.apiFindByUnique($S, $L)", fieldName, fieldName)
                    .build());
                clientBuilder.setQueryType(QUERY);
                clientBuilder.setQueryName(name);
                clientBuilder.setVars(new LinkedHashMap<String, String>(){{
                    put(fieldName, inputTypeSimpleName);
                }});
                clientFactory.addQuery(clientBuilder);
            }

            if(apiFindAllByAnnotation != null) {
                val clientBuilder = new GraphQLQueryBuilder(entitiesMap.values());
                final String name = "find" + toPlural(pascalCaseNameOf(entity)) + "By" + toPlural(fieldPascalCaseName);
                methodsToAdd.add(MethodSpec
                        .methodBuilder(name)
                        .returns(listOf(entity))
                        .addAnnotation(graphqlQueryAnnotation())
                        .addAnnotations(SecurityAnnotationsFactory.of(apiFindAllByAnnotation))
                        .addModifiers(PUBLIC)
                        .addParameter(listOf(fieldType), toPlural(fieldName))
                        .addStatement("return apiLogic.apiFindAllBy($S, $L)", fieldName, toPlural(fieldName))
                        .build());
                clientBuilder.setQueryType(QUERY);
                clientBuilder.setQueryName(name);
                clientBuilder.setVars(new LinkedHashMap<String, String>(){{
                    put(fieldName, inputTypeSimpleName);
                }});
                clientFactory.addQuery(clientBuilder);
            }
            return methodsToAdd;
        }
        private String getInputTypeSimpleName(String simpleName, String packageName) {
            return isPrimitive(packageName) ? resolveSimpleTypeName(simpleName) + "!" : simpleName + "Input";
        }
        private String resolveSimpleTypeName(String simpleName) {
            val n = simpleName.toLowerCase();
            if(n.equals("long") || n.equals("integer") || n.equals("int") || n.equals("short") || n.equals("byte"))
                return "Int";
            else if(n.equals("float") || n.equals("double"))
                return "Float";
            else
                return "String";
        }

        //test method specs
        private MethodSpec genGetEmbeddedTest(VariableElement entityCollectionField) {
            String testName = "get" + pascalCaseNameOf(entityCollectionField) + "From" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getEmbeddedTest($L, $S)",
                            dataManagerName(entityCollectionField),
                            entityCollectionField.getSimpleName().toString())
                    .returns(void.class)
                    .build();
        }
        private MethodSpec genGetEntityCollectionTest(VariableElement entityCollectionField) {
            String testName = "get" + toPlural(pascalCaseNameOf(entityCollectionField)) + "From" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.getEntityCollectionTest($L, $S, $L)",
                            dataManagerName(entityCollectionField),
                            entityCollectionField.getSimpleName().toString(),
                            entityCollectionApiHooksName(entityCollectionField))
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
        private MethodSpec genAssociateWithEntityCollectionTest(VariableElement fk) {
            String testName = "associate" + pascalCaseNameOf(fk) + "With" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.associateWithEntityCollectionTest($L, $S, $L)",
                            dataManagerName(fk), fk.getSimpleName(), entityCollectionApiHooksName(fk))
                    .returns(void.class)
                    .build();
        }
        private MethodSpec genUpdateInEntityCollectionTest(VariableElement fk) {
            String testName = "update" + pascalCaseNameOf(fk) + "In" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.updateEntityCollectionTest($L, $S, $L)",
                            dataManagerName(fk), fk.getSimpleName(), entityCollectionApiHooksName(fk))
                    .returns(void.class)
                    .build();
        }
        private MethodSpec genRemoveFromEntityCollectionTest(VariableElement fk) {
            String testName = "remove" + pascalCaseNameOf(fk) + "From" + pascalCaseNameOf(entity) + "Test";
            return MethodSpec.methodBuilder(testName)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Test.class)
                    .addStatement("testLogic.removeFromEntityCollectionTest($L, $S, $L)",
                            dataManagerName(fk), fk.getSimpleName(), entityCollectionApiHooksName(fk))
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
                    .addAnnotation(Getter.class)
                    .addModifiers(PRIVATE)
                    .build();
        }
        private FieldSpec apiLogic() {
            return FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(ApiLogic.class), ClassName.get(entity)), "apiLogic")
                    .addAnnotation(Autowired.class)
                    .addAnnotation(Getter.class)
                    .addModifiers(PRIVATE)
                    .build();
        }
        private FieldSpec testLogic() {
            return FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(TestLogic.class), ClassName.get(entity)), "testLogic")
                    .addAnnotation(Autowired.class)
                    .addModifiers(PRIVATE)
                    .build();
        }
        public FieldSpec entityCollectionApiHooks(VariableElement fk) {
            TypeName apiHooksType = null;
            val entityCollectionApi = fk.getAnnotation(EntityCollectionApi.class);
            if(entityCollectionApi != null){
                apiHooksType = getApiHooksTypeName(entityCollectionApi);
            }
            assert apiHooksType != null;
            return
                    FieldSpec
                            .builder(apiHooksType,
                                    entityCollectionApiHooksName(fk),
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
        private void addApiHooksIfPresent(VariableElement entityCollectionField,
                                          TypeSpec.Builder testableServiceBuilder,
                                          TypeSpec.Builder serviceBuilder, TypeSpec.Builder testBuilder, EntityCollectionApi entityCollectionApi) {
            if(!isCustomEntityCollectionApiHooks(entityCollectionApi)) return;
            final FieldSpec apiHooks = entityCollectionApiHooks(entityCollectionField);
            serviceBuilder.addField(apiHooks);
            testableServiceBuilder.addField(apiHooks);
            testBuilder.addField(apiHooks);
        }
        private boolean isApiFindByAnnotated(VariableElement variableElement) {
            return
                    variableElement.getAnnotation(ApiFindBy.class) != null ||
                    variableElement.getAnnotation(ApiFindAllBy.class) != null ||
                    variableElement.getAnnotation(ApiFindByUnique.class) != null;
        }
        private boolean isCustomEntityCollectionApiHooks(EntityCollectionApi config) {
            if(config == null) return false;
            return !getApiHooksTypeName(config)
                    .toString()
                    .equals(NullEntityCollectionApiHooks.class.getCanonicalName());
        }
        private boolean isCustomElementCollectionApiHooks(ElementCollectionApi config) {
            if(config == null) return false;
            return !getApiHooksTypeName(config)
                    .toString()
                    .equals(NullElementCollectionApiHooks.class.getCanonicalName());
        }
        private boolean isCustomElementCollectionApiHooks(MapElementCollectionApi config) {
            if(config == null) return false;
            return !getApiHooksTypeName(config)
                    .toString()
                    .equals(NullMapElementCollectionApiHooks.class.getCanonicalName());
        }
        private boolean containsFieldName(WithApiFreeTextSearchByFields freeTextSearchByFields, VariableElement variableElement) {
            for (int i = 0; i < freeTextSearchByFields.value().length; i++) {
                if(freeTextSearchByFields.value()[i].equals(variableElement.getSimpleName().toString()))
                    return true;
            }
            return false;
        }
        private AnnotationSpec graphqlQueryAnnotation(){
            return AnnotationSpec.builder(GraphQLQuery.class).build();
        }
        private AnnotationSpec graphqlMutationAnnotation(){
            return AnnotationSpec.builder(GraphQLMutation.class).build();
        }
        private CodeBlock initSortByIfNull(TypeElement entityType){
            return CodeBlock.builder()
                    .beginControlFlow("if(input.getSortBy() == null)")
                    .addStatement("input.setSortBy($S)", getIdFieldName(entityType))
                    .endControlFlow()
                    .build();
        }

        private TypeName testableGraphQLServiceInterface() {
            return ParameterizedTypeName.get(ClassName.get(TestableGraphQLService.class), ClassName.get(entity));
        }

        private static boolean isPrimitive(String packageName) {
            return packageName.contains("java.lang");
        }

    }
}
