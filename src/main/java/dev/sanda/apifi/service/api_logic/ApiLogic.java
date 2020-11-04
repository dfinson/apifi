package dev.sanda.apifi.service.api_logic;
import dev.sanda.apifi.annotations.EntityCollectionApi;
import dev.sanda.apifi.dto.KeyAndValue;
import dev.sanda.apifi.generator.entity.CollectionsTypeResolver;
import dev.sanda.apifi.service.*;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.persistence.Archivable;
import dev.sanda.datafi.reflection.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.isClazzArchivable;
import static dev.sanda.datafi.DatafiStaticUtils.*;
import static dev.sanda.datafi.reflection.CachedEntityTypeInfo.genDefaultInstance;


@Service
@Scope("prototype")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public final class ApiLogic<T> {

    @Value("#{new Boolean('${datafi.logging-enabled:false}')}")
    private Boolean datafiLoggingEnabled;
    @Setter
    private ApiHooks<T> apiHooks;
    @NonNull
    private final ReflectionCache reflectionCache;
    @NonNull
    private final CollectionInstantiator collectionInstantiator;
    @NonNull
    private final CollectionsTypeResolver collectionsTypeResolver;

    private DataManager<T> dataManager;
    public void setDataManager(DataManager<T> dataManager){
        this.dataManager = dataManager;
        this.entityName = dataManager.getClazzSimpleName();
        this.dataManager.setLoggingEnabled(datafiLoggingEnabled);
        this.idFieldName = reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).getIdField().getName();
    }
    private String entityName;
    private String idFieldName;

    public Page<T> getPaginatedBatch(dev.sanda.datafi.dto.PageRequest request) {
        validateSortByIfNonNull(dataManager.getClazz(), request.getSortBy(), reflectionCache);
        if(apiHooks != null) apiHooks.preGetPaginatedBatch(request, dataManager);
        org.springframework.data.domain.Page result;
        if(request.getFetchAll()) request.setPageNumber(0);
        val pageRequest = generatePageRequest(request, getTotalNonArchivedCount());
        if(isClazzArchivable(dataManager.getClazz(), reflectionCache))
            result = dataManager.findAll((Specification<T>)
                    (root, query, cb) -> cb.isFalse(root.get("isArchived")), pageRequest);
        else result = dataManager.findAll(pageRequest);
        val pageResult = new Page<>(result);
        if(apiHooks != null) apiHooks.postGetPaginatedBatch(request, pageResult, dataManager);
        logInfo("getPaginatedBatch: Got {} {}", result.getContent().size(), toPlural(dataManager.getClazzSimpleName()));
        return pageResult;
    }


    public Long getTotalNonArchivedCount(){
        if (!reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).isArchivable())
            return dataManager.count();
        else return dataManager.count(((Specification<T>) (root, query, cb) -> cb.isFalse(root.get("isArchived"))));
    }

    public Long getTotalArchivedCount(){
        if(reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).isArchivable())
            return dataManager.count(((Specification<T>) (root, query, cb) -> cb.isTrue(root.get("isArchived"))));
        else
            throw new RuntimeException("Entity " + dataManager.getClazzSimpleName() + " does not implement Archivable");
    }

    public Page<T> getArchivedPaginatedBatch(dev.sanda.datafi.dto.PageRequest request) {
        validateSortByIfNonNull(dataManager.getClazz(), request.getSortBy(), reflectionCache);
        if(request.getFetchAll()) request.setPageNumber(0);
        if(apiHooks != null)
            apiHooks.preGetArchivedPaginatedBatch(request, dataManager);
        org.springframework.data.domain.Page<T> result;
        val pageRequest = generatePageRequest(request, getTotalArchivedCount());
        if(isClazzArchivable(dataManager.getClazz(), reflectionCache))
            result = dataManager.findAll((Specification<T>)
                            (root, query, cb) -> cb.isTrue(root.get("isArchived")), pageRequest);
        else result = dataManager.findAll(pageRequest);
        val pageResult = new Page<>(result);
        if(apiHooks != null)
            apiHooks.postGetArchivedPaginatedBatch(request, pageResult, dataManager);
        logInfo("getArchivedPaginatedBatch: Got {} {}", result.getContent().size(), toPlural(dataManager.getClazzSimpleName()));
        return pageResult;
    }

    public Page<T> freeTextSearch(FreeTextSearchPageRequest request){
        try{

            String clazzSimpleNamePlural = toPlural(dataManager.getClazzSimpleName());

            if(request.getSearchTerm() == null || request.getSearchTerm().equals(""))
                throw new IllegalArgumentException("Illegal attempt to search for " + clazzSimpleNamePlural + " with null or blank string");
            validateSortByIfNonNull(dataManager.getClazz(), request.getSortBy(), reflectionCache);

            if(request.getFetchAll())
                request.setPageNumber(0);
            Page<T> result;

            if(apiHooks != null && (result = apiHooks.executeCustomFreeTextSearch(request, dataManager)) != null)
                return result;

            result = ApiFreeTextSearchByImpl.freeTextSearch(dataManager, request, apiHooks, reflectionCache);
            logInfo("freeTextSearchBy(String searchTerm)", "found {} {} by searchTerm '{}'",
                    result.getTotalItemsCount(), toPlural(dataManager.getClazzSimpleName()), request.getSearchTerm());
            return result;
        }catch (Exception e){
            logError("freeTextSearchBy(String searchTerm, int offset, int limit, String sortBy, Sort.Direction sortDirection)", e.toString());
            throw new RuntimeException(e);
        }
    }

    public T getById(Object id) {
        if(apiHooks != null) apiHooks.preGetById(id, dataManager);
        var result = dataManager.findById(id).orElse(null);
        if(result == null) throwEntityNotFoundException(dataManager.getClazzSimpleName(), id);
        if(apiHooks != null) apiHooks.postGetById(result, dataManager);
        logInfo("getById: Got {} by id #{}", dataManager.getClazzSimpleName(), id);
        return result;


    }

    public T apiFindByUnique(String fieldName, Object fieldValue) {
        if(apiHooks != null) apiHooks.preApiFindByUnique(fieldName, fieldValue, dataManager);
        T result = dataManager.findByUnique(fieldName, fieldValue).orElse(null);
        if(result == null) throwEntityNotFoundException(dataManager.getClazzSimpleName(), fieldValue);
        if(apiHooks != null) apiHooks.postApiFindByUnique(fieldName, fieldValue, result, dataManager);
        logInfo("apiFindByUnique: Found {} with id {} by {} == {}",
                dataManager.getClazzSimpleName(),
                getId(result, reflectionCache),
                fieldName,
                fieldValue);
        return result;
    }

    public List<T> apiFindBy(String fieldName, Object argument) {
        if(apiHooks != null) apiHooks.preApiFindBy(fieldName, argument, dataManager);
        List<T> result = dataManager.findBy(fieldName, argument);
        if(apiHooks != null) apiHooks.postApiFindBy(fieldName, argument, result, dataManager);
        logInfo("apiFindBy: found {} {} by {} == {}",
                result.size(),
                dataManager.getClazzSimpleName(),
                fieldName,
                argument);
        return result;
    }

    public List<T> apiFindAllBy(String fieldName, List<?> arguments) {
        if(apiHooks != null) apiHooks.preApiFindAllBy(fieldName, arguments, dataManager);
        List<T> result = dataManager.findAllBy(fieldName, arguments.toArray());
        if(apiHooks != null) apiHooks.postApiFindAllBy(fieldName, arguments, result, dataManager);
        logInfo("apiFindAllBy: found {} {} by [{}]",
                result.size(),
                dataManager.getClazzSimpleName(),
                arguments.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public T create(T input) {
        if(apiHooks != null) apiHooks.preCreate(input, dataManager);
        val result = dataManager.save(input);
        if(apiHooks != null) apiHooks.postCreate(input, result, dataManager);
        logInfo("create: Created {} with id #{}",
                dataManager.getClazzSimpleName(),
                getId(result, reflectionCache));
        return result;
    }

    public T update(T input) {
        if(input == null){
            throw new IllegalArgumentException(
                    String.format("Illegal attempt to update %s instance with null input", entityName)
            );
        }
        val id = getId(input, reflectionCache);
        T toUpdate = getById(id);
        if (toUpdate == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preUpdate(input, toUpdate, dataManager);
        dataManager.cascadeUpdate(toUpdate, input);
        val result = dataManager.save(toUpdate);
        if(apiHooks != null) apiHooks.postUpdate(input, toUpdate, result, dataManager);
        logInfo("update: Updated {} with id #{}", dataManager.getClazzSimpleName(), getId(result, reflectionCache));
        return result;
    }

    public T delete(T input) {
        val id = getId(input, reflectionCache);
        T toDelete = getById(id);
        if(apiHooks != null) apiHooks.preDelete(input, toDelete, dataManager);
        dataManager.deleteById(id);
        if(apiHooks != null) apiHooks.postDelete(input, toDelete, dataManager);
        logInfo("delete: deleted {} with id #{}", dataManager.getClazzSimpleName(), id);
        return toDelete;
    }

    public <A extends Archivable> T archive(A input) {
        val id = getId(input, reflectionCache);
        T toArchive = getById(id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preArchive((T) input, toArchive, dataManager);
        input.setIsArchived(true);
        val result = dataManager.save(toArchive);
        if(apiHooks != null) apiHooks.postArchive((T) input, result, dataManager);
        logInfo("archive: Archived {} with id: {}", dataManager.getClazzSimpleName(), id);
        return result;
    }

    public <A extends Archivable> T deArchive(A input) {
        val id = getId(input, reflectionCache);
        T toArchive = getById(id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preDeArchive((T) input, toArchive, dataManager);
        input.setIsArchived(false);
        val result = dataManager.save(toArchive);
        if(apiHooks != null) apiHooks.postDeArchive((T) input, result, dataManager);
        logInfo("deArchive: De-Archived {} with id: {}", dataManager.getClazzSimpleName(), id);
        return result;
    }

    public <A extends Archivable> List<T> batchArchive(List<A> input) {
        val ids = getIdList(input, reflectionCache);
        val entitiesToArchive = (List<A>) getBatchByIds(ids);
        if(apiHooks != null) apiHooks.preBatchArchive((List<T>) input, (List<T>) entitiesToArchive, dataManager);
        entitiesToArchive.forEach(entity -> entity.setIsArchived(true));
        List<T> result = dataManager.saveAll((List<T>)entitiesToArchive);
        if(apiHooks != null) apiHooks.postBatchArchive((List<T>)input, result, dataManager);
        logInfo("batchArchive: Batch archived {} with ids: [{}]",
                toPlural(dataManager.getClazzSimpleName()),
                ids.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public <A extends Archivable> List<T> batchDeArchive(List<A> input) {
        val ids = getIdList(input, reflectionCache);
        val entitiesToDeArchive = (List<A>) getBatchByIds(ids);
        if(apiHooks != null) apiHooks.preBatchDeArchive((List<T>)input, (List<T>) entitiesToDeArchive, dataManager);
        entitiesToDeArchive.forEach(entity -> entity.setIsArchived(false));
        List<T> result = dataManager.saveAll((List<T>)entitiesToDeArchive);
        if(apiHooks != null) apiHooks.postBatchDeArchive((List<T>)input, result, dataManager);
        logInfo("batchDeArchive: Batch de-archived {} with ids [{}]",
                toPlural(dataManager.getClazzSimpleName()),
                ids.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public List<T> getBatchByIds(List<?> ids) {
        return dataManager.findAllById(ids);
    }

    public List<T> batchCreate(List<T> input) {
        if(apiHooks != null) apiHooks.preBatchCreate(input, dataManager);
        val result = dataManager.saveAll(input);
        if(apiHooks != null) apiHooks.postBatchCreate(input, result, dataManager);
        logInfo("batchCreate: created {} new {} with ids [{}]",
                result.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(result, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public List<T> batchUpdate(List<T> input) {
        List<T> toUpdate = getBatchByIds(getIdList(input, reflectionCache));
        if(apiHooks != null) apiHooks.preBatchUpdate(input, toUpdate, dataManager);
        List<T> result = dataManager.cascadeUpdateCollection(toUpdate, input);
        if(apiHooks != null) apiHooks.postBatchUpdate(input, result, dataManager);
        logInfo("batchUpdate: Updated {} {} with ids [{}]",
                result.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(result, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public List<T> batchDelete(List<T> input) {
        List<T> toDelete = getBatchByIds(getIdList(input, reflectionCache));
        if(apiHooks != null) apiHooks.preDeleteEntities(input, toDelete, dataManager);
        dataManager.deleteInBatch(input);
        if(apiHooks != null) apiHooks.postDeleteEntities(input, toDelete, dataManager);
        logInfo("batchDelete: Deleted {} {} with ids [{}]",
                toDelete.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(toDelete, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return toDelete;
    }

    public <TCollection, E extends EntityCollectionApiHooks<TCollection, T>> List<List<TCollection>>
    getEntityCollection(List<T> input, String collectionFieldName, E collectionApiHooks, DataManager<TCollection> collectionDataManager) {
        if(collectionApiHooks != null)
            input.forEach(owner -> collectionApiHooks.preFetch(owner, collectionFieldName, dataManager, collectionDataManager));
        val rawQueryResults = (List)dataManager.entityManager().createQuery(
                        String.format(
                        "SELECT owner, entityCollection " +
                            "FROM %s owner " +
                            "JOIN owner.%s entityCollection " +
                            "WHERE owner.%s IN :ownerIds",
                        dataManager.getClazzSimpleName(),
                        collectionFieldName,
                        reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).getIdField().getName())
                ).setParameter("ownerIds", getIdList(input, reflectionCache))
                .getResultList();
        val owners2EntityCollectionsMap = new HashMap<T, List<TCollection>>();
        rawQueryResults.forEach(item -> {
            val ownerAndCollectionItem = (Object[])item;
            val owner = (T)ownerAndCollectionItem[0];
            val entityCollectionItem = (TCollection)ownerAndCollectionItem[1];
            if(!owners2EntityCollectionsMap.containsKey(owner))
                owners2EntityCollectionsMap.put(owner, new ArrayList<>(Collections.singletonList(entityCollectionItem)));
            else
                owners2EntityCollectionsMap.get(owner).add(entityCollectionItem);
        });
        if(collectionApiHooks != null)
            owners2EntityCollectionsMap.keySet().forEach(
                    owner -> collectionApiHooks
                            .postFetch(owners2EntityCollectionsMap.get(owner),
                                       owner,
                                       collectionDataManager,
                                       dataManager)
            );
        return input.stream().map(key -> {
            final List<TCollection> collection = owners2EntityCollectionsMap.get(key);
            return collection != null ? collection : new ArrayList<TCollection>();
        }).collect(Collectors.toList());
    }

    public  <TCollection> List<TCollection> getEmbedded(
            List<T> input,
            String fieldName,
            DataManager<TCollection> collectionDataManager) {
        val queryString = String.format(
                "SELECT new dev.sanda.apifi.dto.KeyAndValue(owner, owner.%s) FROM %s owner WHERE owner.%s IN :ids " +
                "ORDER BY owner.%s",
                fieldName, entityName, idFieldName, idFieldName
        );
        final Map<T, TCollection> resultMap =
                (Map<T, TCollection>)
                dataManager
                .entityManager()
                .createQuery(queryString)
                .setParameter("ids", getIdList(input, reflectionCache))
                .getResultStream()
                .collect(Collectors.toMap(
                        KeyAndValue::getKey,
                        KeyAndValue::getValue,
                        (first, second) -> first
                ));
        return input.stream().map(resultMap::get).collect(Collectors.toList());
    }

    @NonNull
    private Field getField(String fieldName, T t) {
        return reflectionCache
                .getEntitiesCache()
                .get(t.getClass().getSimpleName())
                .getFields()
                .get(fieldName)
                .getField();
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    List<TCollection> updateEntityCollection(
            T owner,
            DataManager<TCollection> collectionDataManager,
            Collection<TCollection> toUpdate,
            E entityCollectionApiHooks) {
        var temp = dataManager
                .findById(getId(owner, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(owner, reflectionCache);
        owner = temp;
        if(entityCollectionApiHooks != null)
            entityCollectionApiHooks.preUpdate(toUpdate, owner, collectionDataManager, dataManager);
        List<TCollection> entitiesToUpdate = collectionDataManager.findAllById(getIdList(toUpdate, reflectionCache));
        var result = collectionDataManager.cascadeUpdateCollection(entitiesToUpdate, toUpdate);
        if(entityCollectionApiHooks != null)
            entityCollectionApiHooks.postUpdate(toUpdate, result, owner, collectionDataManager, dataManager);
        return result;
    }

    @SneakyThrows
    public  <TCollection, E extends ElementCollectionApiHooks<TCollection, T>> List<TCollection>
    addToElementCollection(T input, String fieldName, List<TCollection> toAdd, E elementCollectionApiHooks){
        var temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.preAdd(input, fieldName, toAdd, dataManager);

        val cachedElementCollectionField = reflectionCache
                .getEntitiesCache()
                .get(dataManager.getClazzSimpleName())
                .getElementCollections()
                .get(fieldName);

        if(cachedElementCollectionField == null)
            throw new RuntimeException(String.format("No element collection \"%s\" found in type \"%s\"",
                    fieldName, dataManager.getClazzSimpleName()));
        final Field collectionField = cachedElementCollectionField.getField();
        if(collectionField.get(input) == null) {
            /*collectionField.setAccessible(true);
            collectionField.set(input, initElementCollection(fieldName));*/
            throw new RuntimeException(
                    "Illegal attempt to add to " + fieldName +
                    " without having initialized the collection");
        }
        cachedElementCollectionField.addAll(input, toAdd);

        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.postAdd(input, fieldName, toAdd, dataManager);
        return toAdd;
    }

    public  <TCollection, E extends ElementCollectionApiHooks<TCollection, T>> List<TCollection>
    removeFromElementCollection(T input, String fieldName, List<TCollection> toRemove, E elementCollectionApiHooks){
        var temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.preRemove(toRemove, input, dataManager);

        val cachedElementCollectionField = reflectionCache
                .getEntitiesCache()
                .get(dataManager.getClazzSimpleName())
                .getElementCollections()
                .get(fieldName);

        if(cachedElementCollectionField == null)
            throw new RuntimeException(String.format("No element collection \"%s\" found in type \"%s\"",
                    fieldName, dataManager.getClazzSimpleName()));

        cachedElementCollectionField.removeAll(input, toRemove);

        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.postRemove(toRemove, input, dataManager);
        return toRemove;
    }

    public  <TCollection, E extends ElementCollectionApiHooks<TCollection, T>>
    Page<TCollection> getPaginatedBatchInElementCollection(
            T owner,
            dev.sanda.datafi.dto.PageRequest input,
            String fieldName,
            E elementCollectionApiHooks) {
        var temp = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        owner = temp;
        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.preGetPaginatedBatch(owner, input, fieldName, dataManager);
        Page<TCollection> returnValue = new Page<>();
        val contentQueryString = String.format(
                "SELECT collection FROM %s owner " +
                        "JOIN owner.%s collection " +
                        "WHERE owner.%s = :ownerId",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName);
        val countQueryString = String.format(
                "SELECT COUNT(collection) FROM %s owner " +
                        "JOIN owner.%s collection " +
                        "WHERE owner.%s = :ownerId",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName);
        Object ownerId = getId(temp, reflectionCache);
        val content = dataManager
                .entityManager()
                .createQuery(contentQueryString)
                .setParameter("ownerId", ownerId)
                .setFirstResult(input.getPageNumber() * input.getPageSize())
                .setMaxResults(input.getPageSize())
                .getResultList();
        val totalRecords = (long)dataManager.entityManager().createQuery(countQueryString)
                .setParameter("ownerId", ownerId)
                .getSingleResult();
        val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
        returnValue.setContent(content);
        returnValue.setTotalPagesCount((long) totalPages);
        returnValue.setTotalItemsCount(totalRecords);
        returnValue.setPageNumber(input.getPageNumber());
        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.postGetPaginatedBatch(returnValue, fieldName, owner, input, dataManager);
        return returnValue;
    }

    public  <TCollection, E extends ElementCollectionApiHooks<TCollection, T>>
    Page<TCollection> getFreeTextSearchPaginatedBatchInElementCollection(
            T owner,
            dev.sanda.datafi.dto.FreeTextSearchPageRequest input,
            String fieldName,
            E elementCollectionApiHooks) {
        owner = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
        if (owner == null) throw_entityNotFound(input, reflectionCache);

        Page<TCollection> returnValue;

        if(elementCollectionApiHooks != null && (returnValue = elementCollectionApiHooks.executeCustomFreeTextSearch(input, owner, dataManager)) != null)
            return returnValue;

        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.preFreeTextSearch(owner, input, fieldName, dataManager);

        returnValue = new Page<>();
        val contentQueryString = String.format(
                "SELECT collection FROM %s owner " +
                        "JOIN owner.%s collection " +
                        "WHERE owner.%s = :ownerId AND " +
                        "LOWER(collection) LIKE LOWER(CONCAT('%%', :searchTerm, '%%')) " +
                        "ORDER BY collection %s",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName,
                input.getSortDirection());
        val countQueryString = String.format(
                "SELECT COUNT(collection) FROM %s owner " +
                        "JOIN owner.%s collection " +
                        "WHERE owner.%s = :ownerId AND " +
                        "LOWER(collection) LIKE LOWER(CONCAT('%%', :searchTerm, '%%'))",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName);
        Object ownerId = getId(owner, reflectionCache);
        val content = dataManager
                .entityManager()
                .createQuery(contentQueryString)
                .setParameter("ownerId", ownerId)
                .setParameter("searchTerm", input.getSearchTerm())
                .setFirstResult(input.getPageNumber() * input.getPageSize())
                .setMaxResults(input.getPageSize())
                .getResultList();
        val totalRecords = (long)dataManager.entityManager().createQuery(countQueryString)
                .setParameter("ownerId", ownerId)
                .setParameter("searchTerm", input.getSearchTerm())
                .getSingleResult();
        val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
        returnValue.setContent(content);
        returnValue.setTotalPagesCount((long) totalPages);
        returnValue.setTotalItemsCount(totalRecords);
        returnValue.setPageNumber(input.getPageNumber());
        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.postFreeTextSearch(input, returnValue, owner, fieldName, dataManager);
        return returnValue;
    }

    public  <TMapKey, TMapValue, E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>>
    Map<TMapKey, TMapValue>
    addToMapElementCollection(T input, String fieldName, Map<TMapKey, TMapValue> toPut, E apiHooks){
        var temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        if(apiHooks != null)
            apiHooks.prePut(toPut, fieldName, input, dataManager);

        val cachedElementCollectionField = reflectionCache
                .getEntitiesCache()
                .get(dataManager.getClazzSimpleName())
                .getMapElementCollections()
                .get(fieldName);

        if(cachedElementCollectionField == null)
            throw new RuntimeException(String.format("No map element collection \"%s\" found in type \"%s\"",
                    fieldName, dataManager.getClazzSimpleName()));

        cachedElementCollectionField.putAll(input, toPut);

        if(apiHooks != null)
            apiHooks.postPut(toPut, fieldName, input, dataManager);
        dataManager.save(input);
        return toPut;
    }

    public  <TMapKey, TMapValue, E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>>
    Map<TMapKey, TMapValue>
    removeFromMapElementCollection(T input, String fieldName, List<TMapKey> toRemove, E elementCollectionApiHooks){
        var temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.preRemove(toRemove, fieldName, input, dataManager);

        val cachedElementCollectionField = reflectionCache
                .getEntitiesCache()
                .get(dataManager.getClazzSimpleName())
                .getMapElementCollections()
                .get(fieldName);

        if(cachedElementCollectionField == null)
            throw new RuntimeException(String.format("No map element collection \"%s\" found in type \"%s\"",
                    fieldName, dataManager.getClazzSimpleName()));
        Map<TMapKey, TMapValue> removed = cachedElementCollectionField.getAllByKey(input, toRemove);
        cachedElementCollectionField.removeAll(input, toRemove);
        if(elementCollectionApiHooks != null)
            elementCollectionApiHooks.postRemove(removed, fieldName, input, dataManager);
        dataManager.save(input);
        return removed;
    }

    public  <TMapKey, TMapValue, E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>>
    Page<Map.Entry<TMapKey, TMapValue>> getPaginatedBatchInMapElementCollection(
            T owner,
            dev.sanda.datafi.dto.PageRequest input,
            String fieldName,
            E apiHooks) {
        var temp = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        owner = temp;
        if(apiHooks != null)
            apiHooks.preGetPaginatedBatch(owner, input, dataManager);
        Page<Map.Entry<TMapKey, TMapValue>> returnValue = new Page<>();
        val contentQueryString = String.format(
                "SELECT new dev.sanda.apifi.dto.KeyAndValue(KEY(map), VALUE(map)) " +
                    "FROM %s owner " +
                    "JOIN owner.%s map " +
                    "WHERE owner.%s = :ownerId " +
                    "ORDER BY KEY(map) %s",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName,
                input.getSortDirection());
        val countQueryString = String.format(
                "SELECT COUNT(map) FROM %s owner " +
                    "JOIN owner.%s map " +
                    "WHERE owner.%s = :ownerId",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName);
        Object ownerId = getId(temp, reflectionCache);

        List<Map.Entry<TMapKey, TMapValue>> content =
                (List<Map.Entry<TMapKey, TMapValue>>)
                 dataManager
                .entityManager()
                .createQuery(contentQueryString)
                .setParameter("ownerId", ownerId)
                .setFirstResult(input.getPageNumber() * input.getPageSize())
                .setMaxResults(input.getPageSize())
                .getResultStream()
                .map(entry -> ((KeyAndValue)entry).toEntry())
                .collect(Collectors.toList());
        val totalRecords = (long)dataManager.entityManager().createQuery(countQueryString)
                .setParameter("ownerId", ownerId)
                .getSingleResult();
        val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
        returnValue.setContent(content);
        returnValue.setTotalPagesCount((long) totalPages);
        returnValue.setTotalItemsCount(totalRecords);
        returnValue.setPageNumber(input.getPageNumber());
        if(apiHooks != null)
            apiHooks.postGetPaginatedBatch(returnValue, input, owner, dataManager);
        return returnValue;
    }

    public  <TMapKey, TMapValue, E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>>
    Page<Map.Entry<TMapKey, TMapValue>> getFreeTextSearchPaginatedBatchInMapElementCollection(
            T owner,
            dev.sanda.datafi.dto.FreeTextSearchPageRequest input,
            String fieldName,
            E apiHooks) {
        owner = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
        if (owner == null) throw_entityNotFound(input, reflectionCache);

        Page<Map.Entry<TMapKey, TMapValue>> returnValue;

        if(apiHooks != null && (returnValue = apiHooks.executeCustomFreeTextSearch(input, owner, dataManager)) != null)
            return returnValue;

        if(apiHooks != null)
            apiHooks.preFreeTextSearch(owner, input, dataManager);
        returnValue = new Page<>();
        val contentQueryString = String.format(
                "SELECT new dev.sanda.apifi.dto.KeyAndValue(KEY(map), VALUE(map)) " +
                        "FROM %s owner " +
                        "JOIN owner.%s map " +
                        "WHERE owner.%s = :ownerId AND " +
                        "LOWER(KEY(map)) LIKE LOWER(CONCAT('%%', :searchTerm, '%%')) " +
                        "ORDER BY KEY(map) %s",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName,
                input.getSortDirection());
        val countQueryString = String.format(
                "SELECT COUNT(map) FROM %s owner " +
                        "JOIN owner.%s map " +
                        "WHERE owner.%s = :ownerId AND " +
                        "LOWER(KEY(map)) LIKE LOWER(CONCAT('%%', :searchTerm, '%%'))",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName);
        Object ownerId = getId(owner, reflectionCache);
        val content =
                (List<java.util.Map.Entry<TMapKey,TMapValue>>)
                 dataManager
                .entityManager()
                .createQuery(contentQueryString)
                .setParameter("ownerId", ownerId)
                .setParameter("searchTerm", input.getSearchTerm())
                .setFirstResult(input.getPageNumber() * input.getPageSize())
                .setMaxResults(input.getPageSize())
                .getResultStream()
                .map(entry -> ((KeyAndValue)entry).toEntry())
                .collect(Collectors.toList());
        val totalRecords = (long)dataManager.entityManager().createQuery(countQueryString)
                .setParameter("ownerId", ownerId)
                .setParameter("searchTerm", input.getSearchTerm())
                .getSingleResult();
        val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
        returnValue.setContent(content);
        returnValue.setTotalPagesCount((long) totalPages);
        returnValue.setTotalItemsCount(totalRecords);
        returnValue.setPageNumber(input.getPageNumber());
        if(apiHooks != null)
            apiHooks.postFreeTextSearch(returnValue, input, owner, dataManager);
        return returnValue;
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    List<TCollection> associateWithEntityCollection(
            T input,
            String fieldName,
            List<TCollection> toAssociate,
            DataManager<TCollection> collectionDataManager,
            E entityCollectionApiHooks) {

        //get collection owner
        var temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        if(entityCollectionApiHooks != null) entityCollectionApiHooks.preAssociate(toAssociate, input, fieldName, collectionDataManager, dataManager);
        Collection<TCollection> existingCollection = getEntityCollectionFrom(input, fieldName);
        if(existingCollection == null)
            existingCollection = initEntityCollection(fieldName, collectionDataManager);
        existingCollection.addAll(toAssociate);

        //save owner
        try {
            reflectionCache
                    .getEntitiesCache()
                    .get(input.getClass().getSimpleName())
                    .getFields()
                    .get(fieldName)
                    .getField()
                    .set(input, existingCollection);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        final T t = input;

        Collection<TCollection> added =
                getEntityCollectionFrom(
                        t,
                        fieldName);
        val newlyAssociated = collectionDataManager.saveAll(toAssociate);
        var result = collectionDataManager.saveAll(extractFromCollection(added, newlyAssociated));
        dataManager.save(t);
        if(entityCollectionApiHooks != null)
            entityCollectionApiHooks.postAssociate(toAssociate, result, input, fieldName, collectionDataManager, dataManager);
        return result;
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    Page<TCollection> paginatedFreeTextSearchInEntityCollection(
            T owner,
            dev.sanda.datafi.dto.FreeTextSearchPageRequest input,
            String fieldName,
            DataManager<TCollection> collectionDataManager,
            E entityCollectionApiHooks) {
        //get collection owner
        owner = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
        if (owner == null) throw_entityNotFound(input, reflectionCache);

        Page<TCollection> returnValue;

        if(entityCollectionApiHooks != null && (returnValue = entityCollectionApiHooks.executeCustomFreeTextSearch(input, owner, dataManager, collectionDataManager)) != null)
            return returnValue;

        if(entityCollectionApiHooks != null)
            entityCollectionApiHooks.preFreeTextSearch(owner, input.getSearchTerm(), dataManager, collectionDataManager);


        returnValue = new Page<>();
        validateSortByIfNonNull(collectionDataManager.getClazz(), input.getSortBy(), reflectionCache);

        String clazzSimpleNamePlural = toPlural(collectionDataManager.getClazzSimpleName());
        val searchTerm = input.getSearchTerm();
        if(searchTerm == null || searchTerm.equals(""))
            throw new IllegalArgumentException("Illegal attempt to search for " + clazzSimpleNamePlural + " with null or blank string");

        boolean isArchivable = isClazzArchivable(collectionDataManager.getClazz(), reflectionCache);
        val isNonArchivedClause = isArchivable ? "AND embedded.isArchived = false " : "";
        val searchTermClause = buildSearchTermQuery(fieldName);
        val contentQueryString = String.format(
                "SELECT DISTINCT(embedded) FROM %s owner JOIN owner.%s embedded " +
                    "WHERE owner.%s = :ownerId " +
                    "%s" +
                    "%s " +
                    "ORDER BY embedded.%s",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName,
                isNonArchivedClause,
                searchTermClause,
                input.getSortBy()
        );
        val countQueryString = String.format(
                "SELECT COUNT(DISTINCT embedded) FROM %s owner JOIN owner.%s embedded " +
                        "WHERE owner.%s = :ownerId " +
                        "%s" +
                        "%s ",
                dataManager.getClazzSimpleName(),
                fieldName,
                idFieldName,
                isNonArchivedClause,
                searchTermClause
        );
        val ownerId = getId(owner, reflectionCache);
        val content = dataManager
                .entityManager()
                .createQuery(contentQueryString)
                .setParameter("searchTerm", input.getSearchTerm())
                .setParameter("ownerId", ownerId)
                .setFirstResult(input.getPageNumber() * input.getPageSize())
                .setMaxResults(input.getPageSize())
                .getResultList();
        val totalItems =
                (long)dataManager
                .entityManager()
                .createQuery(countQueryString)
                .setParameter("searchTerm", input.getSearchTerm())
                .setParameter("ownerId", ownerId)
                .getSingleResult();
        val totalPages = Math.ceil((double) totalItems / input.getPageSize());
        returnValue.setContent(content);
        returnValue.setTotalPagesCount((long) totalPages);
        returnValue.setTotalItemsCount(totalItems);
        returnValue.setPageNumber(input.getPageNumber());
        if(entityCollectionApiHooks != null)
            entityCollectionApiHooks.postFreeTextSearch(returnValue, input,owner, searchTerm, collectionDataManager, dataManager);
        return returnValue;
    }
    private final Map<String, String> searchTermQueryCache = new HashMap<>();
    private String buildSearchTermQuery(String collectionFieldName) {
        val key = collectionFieldName + "Of" + dataManager.getClazzSimpleName() + "FreeTextSearch";
        if(searchTermQueryCache.containsKey(key)) return searchTermQueryCache.get(key);
        val searchFieldNames =
                Arrays.asList(
                 reflectionCache
                .getEntitiesCache()
                .get(dataManager.getClazzSimpleName())
                .getFields()
                .get(collectionFieldName)
                .getField()
                .getAnnotation(EntityCollectionApi.class)
                .freeTextSearchFields());
        if(searchFieldNames.isEmpty()) return "";
        val result = new StringBuilder();
        for (int i = 0; i < searchFieldNames.size(); i++) {
            val fieldName = searchFieldNames.get(i);
            val prefix = i == 0 ? "AND " : "OR ";
            val condition = "lower(embedded." + fieldName + ") LIKE lower(concat('%', :searchTerm, '%')) ";
            result.append(prefix);
            result.append(condition);
        }
        val resultString = result.toString();
        searchTermQueryCache.put(key, resultString);
        return resultString;
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    Page<TCollection> getPaginatedBatchInEntityCollection(
            T owner,
            dev.sanda.datafi.dto.PageRequest input,
            String fieldName,
            DataManager<TCollection> collectionDataManager,
            E entityCollectionApiHooks) {
        //get collection owner
        var temp = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        owner = temp;
        if(entityCollectionApiHooks != null)
            entityCollectionApiHooks.preGetPaginatedBatch(owner, input, dataManager);
        Page<TCollection> returnValue = new Page<>();
        validateSortByIfNonNull(collectionDataManager.getClazz(), input.getSortBy(), reflectionCache);
        val isNonArchivedClause = isClazzArchivable(collectionDataManager.getClazz(), reflectionCache) ?
                "AND embedded.isArchived = false " : " ";
        final String ownerEntityName = dataManager.getClazzSimpleName();
        val contentQueryString = String.format(
                "SELECT embedded FROM %s owner " +
                        "JOIN owner.%s embedded " +
                        "WHERE owner.%s = :ownerId %s" +
                        "ORDER BY owner.%s",
                ownerEntityName,
                fieldName,
                idFieldName,
                isNonArchivedClause,
                input.getSortBy());
        val countQueryString = String.format(
                "SELECT COUNT(embedded) FROM %s owner " +
                        "JOIN owner.%s embedded " +
                        "WHERE owner.%s = :ownerId %s",
                ownerEntityName,
                fieldName,
                idFieldName,
                isNonArchivedClause);
        Object ownerId = getId(temp, reflectionCache);
        val content = dataManager
                .entityManager()
                .createQuery(contentQueryString)
                .setParameter("ownerId", ownerId)
                .setFirstResult(input.getPageNumber() * input.getPageSize())
                .setMaxResults(input.getPageSize())
                .getResultList();
        val totalRecords = (long)dataManager.entityManager().createQuery(countQueryString)
                .setParameter("ownerId", ownerId)
                .getSingleResult();
        val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
        returnValue.setContent(content);
        returnValue.setTotalPagesCount((long) totalPages);
        returnValue.setTotalItemsCount(totalRecords);
        returnValue.setPageNumber(input.getPageNumber());
        if(entityCollectionApiHooks != null)
            entityCollectionApiHooks.postGetPaginatedBatch(returnValue, owner, input, collectionDataManager, dataManager);
        return returnValue;
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    List<TCollection>
    associatePreExistingWithEntityCollection(
            T input,
            String embeddedFieldName,
            List<TCollection> toAssociate,
            DataManager<TCollection> collectionDataManager,
            E entityCollectionApiHooks) {
        //get collection owner
        var temp  = dataManager.findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        if(entityCollectionApiHooks != null) entityCollectionApiHooks.preAssociate(toAssociate, input, embeddedFieldName, collectionDataManager, dataManager);
        //validate all candidates are pre-existing
        List<TCollection> toAssociateReloaded = collectionDataManager.findAllById(getIdList(toAssociate, reflectionCache));
        if (toAssociateReloaded.isEmpty() || toAssociateReloaded.size() != toAssociate.size())
            throw new IllegalArgumentException("illegal attempt made to indirectly add new strong entities");
        Collection<TCollection> existingCollection = getEntityCollectionFrom(input, toCamelCase(embeddedFieldName));
        if(existingCollection == null)
            existingCollection = initEntityCollection(embeddedFieldName, collectionDataManager);
        existingCollection.addAll(toAssociate);
        //update & save owner
        try {
            reflectionCache
                    .getEntitiesCache()
                    .get(input.getClass().getSimpleName())
                    .getFields()
                    .get(embeddedFieldName)
                    .getField()
                    .set(input, existingCollection);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        final T owner = input;
        T updatedInputEntity = dataManager.saveAndFlush(owner);
        Collection<TCollection> newlyAssociated = getEntityCollectionFrom(
        updatedInputEntity,
        toCamelCase(embeddedFieldName));
        var result = extractFromCollection(newlyAssociated, toAssociate);
        if(entityCollectionApiHooks != null) 
            entityCollectionApiHooks.postAssociate(toAssociate, result, owner, embeddedFieldName, collectionDataManager, dataManager);
        return result;
    }

    public  <TCollection>
    List<TCollection>
    removeFromEntityCollection(
            T owner,
            String toRemoveFieldName,
            List<TCollection> toRemove,
            DataManager<TCollection> collectionDataManager,
            EntityCollectionApiHooks<TCollection, T> entityCollectionApiHooks) {
        //get collection owner
        val temp = dataManager
                .findById(getId(owner, reflectionCache)).orElse(null);
        if(temp == null) throw_entityNotFound(owner, reflectionCache);
        owner = temp;
        assert owner != null;
        Collection<TCollection> currentCollection = getEntityCollectionFrom(owner, toRemoveFieldName);
        if(currentCollection == null)
            throw new RuntimeException("Illegal attempt to remove object from null collection");
        if(entityCollectionApiHooks != null) entityCollectionApiHooks.preRemove(toRemove, owner, collectionDataManager, dataManager);
        Set<Object> toRemoveIds = new HashSet<>(getIdList(toRemove, reflectionCache));
        currentCollection.removeIf(item -> toRemoveIds.contains(getId(item, reflectionCache)));
        dataManager.save(owner);
        if(entityCollectionApiHooks != null)
            entityCollectionApiHooks.postRemove(toRemove, owner, collectionDataManager, dataManager);
        return toRemove;
    }

    public <TCollection> List<TCollection> extractFromCollection(Collection<TCollection> toExtractFrom, Collection<TCollection> toExtract) {
        List<TCollection> result = new ArrayList<>();
        toExtract.forEach(item -> result.add(extract(toExtractFrom, item, reflectionCache)));
        return result;
    }

    public <TCollection> TCollection extract(Collection<TCollection> collection, TCollection toCheck, ReflectionCache reflectionCache) {
        Object toCheckId = getId(toCheck, reflectionCache);
        for (TCollection item : collection) {
            if (getId(item, reflectionCache).equals(toCheckId))
                return item;
        }
        throw_entityNotFound(toCheck, reflectionCache);
        return null;
    }

     void throw_entityNotFound(Object input, ReflectionCache reflectionCache) {
         final RuntimeException exception = new RuntimeException(
                 "Cannot find Entity " + input.getClass().getSimpleName() + " with id " + getId(input, reflectionCache));
         logError(exception.toString());
         throw exception;
    }

    public <TCollection> Collection<TCollection> getEntityCollectionFrom(T input, String fieldName) {
        try {
            return (Collection<TCollection>)
                    reflectionCache
                            .getEntitiesCache()
                            .get(input.getClass().getSimpleName())
                            .getFields()
                            .get(fieldName)
                            .getField()
                            .get(input);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    private final static Logger log = LoggerFactory.getLogger(ApiLogic.class);
    private static final Executor loggerThread = Executors.newSingleThreadExecutor();
    private static synchronized void log(String msg, boolean isError, Object... args){
        loggerThread.execute(() -> {
            if (isError) log.error(msg, args);
            else log.info(msg, args);
        });
    }
    private void logInfo(String msg, Object... args){
        log(msg, false, args);
    }

    private <TCollection> Collection<TCollection> initEntityCollection(
            String fieldName, DataManager<TCollection> collectionDataManager) {
        val collectionType = reflectionCache
                .getEntitiesCache()
                .get(dataManager.getClazzSimpleName())
                .getFields()
                .get(fieldName)
                .getField()
                .getType();
        val collectibleType = collectionDataManager.getClazz();
        return collectionInstantiator.instantiateCollection(collectionType, collectibleType);
    }

    private <TCollection> Collection<TCollection> initElementCollection(String fieldName){
        val collectionType = reflectionCache
                .getEntitiesCache()
                .get(entityName)
                .getFields()
                .get(fieldName)
                .getField()
                .getType();
        Class collectibleType = collectionsTypeResolver.resolveFor(entityName + "." + fieldName);
        return collectionInstantiator.instantiateCollection(collectionType, collectibleType);
    }

    private <TKey, TValue> Map<TKey, TValue> initMapElementCollection(String fieldName){
        val mapType = reflectionCache
                .getEntitiesCache()
                .get(entityName)
                .getFields()
                .get(fieldName)
                .getField()
                .getType();
        if(mapType.equals(Map.class)) return new HashMap<>();
        return (Map<TKey, TValue>) genDefaultInstance(mapType);
    }

    private void logError(String msg, Object... args) {
        log(msg, true, args);
    }
}

