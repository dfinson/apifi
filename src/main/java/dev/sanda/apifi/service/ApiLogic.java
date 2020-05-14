package dev.sanda.apifi.service;

import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.persistence.Archivable;
import dev.sanda.datafi.reflection.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.Setter;
import lombok.val;
import lombok.var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.isClazzArchivable;
import static dev.sanda.datafi.DatafiStaticUtils.*;


@Service
@Scope("prototype")
public final class ApiLogic<T> {

    @Setter
    private ApiHooks<T> apiHooks;
    @Autowired
    private ReflectionCache reflectionCache;
    @Setter
    private DataManager<T> dataManager;

    public Page<T> getPaginatedBatch(dev.sanda.datafi.dto.PageRequest request) {
        validateSortByIfNonNull(dataManager.getClazz(), request.getSortBy(), reflectionCache);
        if(apiHooks != null) apiHooks.preGetPaginatedBatch(dataManager);
        org.springframework.data.domain.Page result;
        final PageRequest pageRequest = generatePageRequest(request, getTotalNonArchivedCount());
        if(isClazzArchivable(dataManager.getClazz(), reflectionCache))
            result = dataManager
                        .findAll((Specification<T>)
                                (root, query, cb) ->
                                        cb.isFalse(root.get("isArchived")), pageRequest
                        );
        else {
            result = dataManager.findAll(pageRequest);
        }
        if(apiHooks != null) apiHooks.postGetPaginatedBatch(result.getContent(), dataManager);
        logInfo("getPaginatedBatch: Got {} {}", result.getContent().size(), toPlural(dataManager.getClazzSimpleName()));
        return new Page<>(result);
    }


    public Long getTotalNonArchivedCount(){
        if(reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).isArchivable()){
            return dataManager.count(((Specification<T>) (root, query, cb) -> cb.isFalse(root.get("isArchived"))));
        }else {
            return dataManager.count();
        }
    }

    public Long getTotalArchivedCount(){
        if(reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).isArchivable()){
            return dataManager.count(((Specification<T>) (root, query, cb) -> cb.isTrue(root.get("isArchived"))));
        }else {
            throw new RuntimeException("Entity " + dataManager.getClazzSimpleName() + " does not implement Archivable");
        }
    }

    public Page<T> getArchivedPaginatedBatch(dev.sanda.datafi.dto.PageRequest request) {
        validateSortByIfNonNull(dataManager.getClazz(), request.getSortBy(), reflectionCache);
        if(apiHooks != null) apiHooks.preGetArchivedPaginatedBatch(dataManager);
        org.springframework.data.domain.Page<T> result;
        final PageRequest pageRequest = generatePageRequest(request, getTotalArchivedCount());
        if(isClazzArchivable(dataManager.getClazz(), reflectionCache))
            result = dataManager
                    .findAll((Specification<T>)
                            (root, query, cb) ->
                                    cb.isTrue(root.get("isArchived")), pageRequest
                    );
        else result = dataManager.findAll(pageRequest);
        if(apiHooks != null) apiHooks.postGetArchivedPaginatedBatch(result.getContent(), dataManager);
        logInfo("getArchivedPaginatedBatch: Got {} {}", result.getContent().size(), toPlural(dataManager.getClazzSimpleName()));
        return new Page<>(result);
    }

    public Page<T> freeTextSearch(FreeTextSearchPageRequest request) {
        if(apiHooks != null) apiHooks.preFreeTextSearch(request.getSearchTerm(), dataManager);
        val result = dataManager.freeTextSearchBy(request, getTotalNonArchivedCount());
        if(apiHooks != null)
            apiHooks.postFreeTextSearch(request.getSearchTerm(), result.getContent(), dataManager);
        logInfo("freeTextSearch: Got {} {} by free text search term \"{}\"",
                result.getTotalRecordsCount(),
                toPlural(dataManager.getClazzSimpleName()), request.getSearchTerm());
        return result;
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
        if(apiHooks != null) apiHooks.preApiFindByUnique(fieldValue, dataManager);
        T result = dataManager.findByUnique(fieldName, fieldValue).orElse(null);
        if(result == null) throwEntityNotFoundException(dataManager.getClazzSimpleName(), fieldValue);
        if(apiHooks != null) apiHooks.postApiFindByUnique(result, dataManager);
        logInfo("apiFindByUnique: Found {} with id {} by {} == {}",
                dataManager.getClazzSimpleName(),
                getId(result, reflectionCache),
                fieldName,
                fieldValue);
        return result;
    }

    public List<T> apiFindBy(String fieldName, Object argument) {
        if(apiHooks != null) apiHooks.preApiFindBy(argument, dataManager);
        List<T> result = dataManager.findBy(fieldName, argument);
        if(apiHooks != null) apiHooks.postApiFindBy(result, dataManager);
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
        if(apiHooks != null) apiHooks.postApiFindAllBy(fieldName, result, dataManager);
        logInfo("apiFindAllBy: found {} {} by [{}]",
                result.size(),
                dataManager.getClazzSimpleName(),
                arguments.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public T create(T input) {
        if(apiHooks != null) apiHooks.preCreate(input, dataManager);
        val result = dataManager.save(input);
        if(apiHooks != null) apiHooks.postCreate(result, dataManager);
        logInfo("create: Created {} with id #{}",
                dataManager.getClazzSimpleName(),
                getId(result, reflectionCache));
        return result;
    }

    public T update(T input) {
        final Object id = getId(input, reflectionCache);
        T toUpdate = getById(id);
        if (toUpdate == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preUpdate(input, dataManager);
        dataManager.cascadeUpdate(toUpdate, input);
        val result = dataManager.save(toUpdate);
        if(apiHooks != null) apiHooks.postUpdate(result, dataManager);
        logInfo("update: Updated {} with id #{}", dataManager.getClazzSimpleName(), getId(result, reflectionCache));
        return result;
    }

    public T delete(T input) {
        final Object id = getId(input, reflectionCache);
        T toDelete = getById(id);
        if(apiHooks != null) apiHooks.preDelete(input, dataManager);
        dataManager.deleteById(id);
        if(apiHooks != null) apiHooks.postDelete(toDelete, dataManager);
        logInfo("delete: deleted {} with id #{}", dataManager.getClazzSimpleName(), id);
        return toDelete;
    }

    public <A extends Archivable> T archive(A input) {
        final Object id = getId(input, reflectionCache);
        T toArchive = getById(id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preArchive((T) input, dataManager);
        input.setIsArchived(true);
        val result = dataManager.save(toArchive);
        if(apiHooks != null) apiHooks.postArchive(result, dataManager);
        logInfo("archive: Archived {} with id #{}", dataManager.getClazzSimpleName(), id);
        return result;
    }

    public <A extends Archivable> T deArchive(A input) {
        final Object id = getId(input, reflectionCache);
        T toArchive = getById(id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preDeArchive(toArchive, dataManager);
        input.setIsArchived(false);
        val result = dataManager.save(toArchive);
        if(apiHooks != null) apiHooks.postDeArchive(result, dataManager);
        logInfo("deArchive: De-Archived {} with id #{}", dataManager.getClazzSimpleName(), id);
        return result;
    }

    public <A extends Archivable> List<T> batchArchive(List<A> input) {
        final List<Object> ids = dataManager.idList((Iterable<T>) input);
        List<A> entitiesToArchive = (List<A>) getBatchByIds(ids);
        if(apiHooks != null) apiHooks.preBatchArchive((Collection<T>) input, dataManager);
        entitiesToArchive.forEach(entity -> entity.setIsArchived(true));
        List<T> result = dataManager.saveAll((List<T>)entitiesToArchive);
        if(apiHooks != null) apiHooks.postBatchArchive(result, dataManager);
        logInfo("batchArchive: Batch archived {} with ids [{}]",
                toPlural(dataManager.getClazzSimpleName()),
                ids.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public <A extends Archivable> List<T> batchDeArchive(List<A> input) {
        final List<Object> ids = dataManager.idList((Iterable<T>) input);
        List<A> entitiesToDeArchive = (List<A>) getBatchByIds(ids);
        if(apiHooks != null) apiHooks.preBatchDeArchive((List<T>) input, dataManager);
        entitiesToDeArchive.forEach(entity -> entity.setIsArchived(false));
        List<T> result = dataManager.saveAll((List<T>)entitiesToDeArchive);
        if(apiHooks != null) apiHooks.postBatchDeArchive(result, dataManager);
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
        if(apiHooks != null) apiHooks.postBatchCreate(result, dataManager);
        logInfo("batchCreate: created {} new {} with ids [{}]",
                result.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(result, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public List<T> batchUpdate(List<T> input) {
        List<T> toUpdate = getBatchByIds(dataManager.idList(input));
        if(apiHooks != null) apiHooks.preBatchUpdate(input, dataManager);
        List<T> result = dataManager.cascadeUpdateCollection(toUpdate, input);
        if(apiHooks != null) apiHooks.postBatchUpdate(result, dataManager);
        logInfo("batchUpdate: Updated {} {} with ids [{}]",
                result.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(result, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public List<T> batchDelete(List<T> input) {
        List<T> toDelete = getBatchByIds(dataManager.idList(input));
        if(apiHooks != null) apiHooks.preDeleteEntities(input, dataManager);
        dataManager.deleteInBatch(input);
        if(apiHooks != null) apiHooks.postDeleteEntities(toDelete, dataManager);
        logInfo("batchDelete: Deleted {} {} with ids [{}]",
                toDelete.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(toDelete, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return toDelete;
    }

    public <TEmbedded, E extends EmbeddedCollectionApiHooks<TEmbedded, T>> List<List<TEmbedded>>
    getEmbeddedCollection(//TODO - optimize
            List<T> input,
            String embeddedFieldName,
            E embeddedCollectionApiHooks,
            DataManager<TEmbedded> tEmbeddedDataManager) {
        List<List<TEmbedded>> lists = new ArrayList<>();
        input.forEach(t -> {
            if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preFetch(t, dataManager);
            final List<TEmbedded> embeddedCollection =
                    tEmbeddedDataManager.findAllById(tEmbeddedDataManager
                            .idList((Iterable<TEmbedded>) getEmbeddedCollectionFrom(t, embeddedFieldName)));
            if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postFetch(embeddedCollection, t, tEmbeddedDataManager, dataManager);
            lists.add(embeddedCollection);
        });
        return lists;
    }

    public  <TEmbedded> List<TEmbedded> getEmbedded(
            List<T> input,
            String fieldName,
            DataManager<TEmbedded> tEmbeddedDataManager) {
        List<Object> ids = new ArrayList<>();
        input.forEach(t -> {
            final TEmbedded embeddedReference = (TEmbedded) reflectionCache
                    .getEntitiesCache().get(t.getClass().getSimpleName())
                    .invokeGetter(t, fieldName);
            ids.add(getId(embeddedReference, reflectionCache));
        });
        return tEmbeddedDataManager.findAllById(ids);
    }

    public  <TEmbedded, E extends EmbeddedCollectionApiHooks<TEmbedded, T>>
    List<TEmbedded> updateEmbeddedCollection(
            T owner,
            DataManager<TEmbedded> tEmbeddedDataManager,
            Iterable<TEmbedded> toUpdate,
            E embeddedCollectionApiHooks) {
        var temp = dataManager
                .findById(getId(owner, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(owner, reflectionCache);
        owner = temp;
        return updateCollectionAsEmbedded(owner, toUpdate, embeddedCollectionApiHooks, tEmbeddedDataManager);
    }

    public  <TEmbedded, E extends EmbeddedCollectionApiHooks<TEmbedded, T>>
    List<TEmbedded> updateCollectionAsEmbedded(
            T input,
            Iterable<TEmbedded> toUpdate,
            E embeddedCollectionApiHooks,
            DataManager<TEmbedded> tEmbeddedDataManager) {
        List<TEmbedded> entitiesToUpdate = (List<TEmbedded>) getBatchByIds(tEmbeddedDataManager.idList(toUpdate));
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preUpdate(entitiesToUpdate, input, tEmbeddedDataManager, dataManager);
        var result = tEmbeddedDataManager.cascadeUpdateCollection(entitiesToUpdate, toUpdate);
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postUpdate(result, input, tEmbeddedDataManager, dataManager);
        return result;
    }


    public  <TEmbedded, E extends EmbeddedCollectionApiHooks<TEmbedded, T>>
    List<TEmbedded> associateWithEmbeddedCollection(
            T input,
            String fieldName,
            List<TEmbedded> toAssociate,
            DataManager<TEmbedded> tEmbeddedDataManager,
            E embeddedCollectionApiHooks) {

        //get collection owner
        var temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preAssociate(toAssociate, input, tEmbeddedDataManager, dataManager);
        Collection<TEmbedded> existingCollection = getEmbeddedCollectionFrom(input, fieldName);
        existingCollection.addAll(toAssociate);

        //save owner
        reflectionCache.getEntitiesCache().get(input.getClass().getSimpleName())
                .invokeSetter(input, fieldName, existingCollection);
        final T t = input;

        Collection<TEmbedded> added =
                getEmbeddedCollectionFrom(
                        t,
                        fieldName);
        var result = tEmbeddedDataManager.saveAll(extractFromCollection(added, toAssociate));
        dataManager.save(t);
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postAssociate(result, input, tEmbeddedDataManager, dataManager);
        return result;
    }

    public  <TEmbedded, E extends EmbeddedCollectionApiHooks<TEmbedded, T>>
    Page<TEmbedded> getPaginatedBatchInEmbeddedCollection(
            T owner,
            dev.sanda.datafi.dto.PageRequest input,
            String fieldName,
            DataManager<TEmbedded> tEmbeddedDataManager,
            E embeddedCollectionApiHooks) {
        //get collection owner
        var temp = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        owner = temp;
        if(embeddedCollectionApiHooks != null)
            embeddedCollectionApiHooks.preGetPaginatedBatch(owner, dataManager);
        Page<TEmbedded> returnValue = new Page<>();
        validateSortByIfNonNull(tEmbeddedDataManager.getClazz(), input.getSortBy(), reflectionCache);
        val isNonArchivedClause = isClazzArchivable(tEmbeddedDataManager.getClazz(), reflectionCache) ?
                "WHERE embedded.isArchived = false" : "";
        val contentQueryString = String.format("SELECT embedded FROM %s owner JOIN owner.%s embedded %s ORDER BY %s",
                dataManager.getClazzSimpleName(),
                fieldName,
                isNonArchivedClause,
                input.getSortBy());
        val content = dataManager
                .entityManager()
                .createQuery(contentQueryString)
                .setFirstResult(input.getPageNumber() * input.getPageSize())
                .setMaxResults(input.getPageSize())
                .getResultList();
        val countQueryString = String.format("SELECT COUNT(embedded) FROM %s owner JOIN owner.%s embedded %s",
                dataManager.getClazzSimpleName(),
                fieldName,
                isNonArchivedClause);
        val totalRecords = (long)dataManager.entityManager().createQuery(countQueryString).getSingleResult();
        val totalPages = totalRecords / input.getPageSize();
        returnValue.setContent(content);
        returnValue.setTotalPagesCount(totalPages);
        returnValue.setTotalRecordsCount(totalRecords);
        if(embeddedCollectionApiHooks != null)
            embeddedCollectionApiHooks.postGetPagintedBatch(returnValue, owner, tEmbeddedDataManager, dataManager);
        return returnValue;
    }

    public  <TEmbedded, E extends EmbeddedCollectionApiHooks<TEmbedded, T>>
    List<TEmbedded>
    associatePreExistingWithEmbeddedCollection(
            T input,
            String embeddedFieldName,
            List<TEmbedded> toAssociate,
            DataManager<TEmbedded> tEmbeddedDataManager,
            E embeddedCollectionApiHooks) {
        //get collection owner
        var temp  = dataManager.findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preAssociate(toAssociate, input, tEmbeddedDataManager, dataManager);
        //validate all candidates are pre-existing
        List<TEmbedded> toAssociateReloaded = tEmbeddedDataManager.findAllById(tEmbeddedDataManager.idList(toAssociate));
        if (toAssociateReloaded.isEmpty() || toAssociateReloaded.size() != toAssociate.size())
            throw new IllegalArgumentException("illegal attempt made to indirectly add new strong entities");
        Collection<TEmbedded> existingCollection = getEmbeddedCollectionFrom(input, toCamelCase(embeddedFieldName));
        existingCollection.addAll(toAssociate);
        //update & save owner
        reflectionCache.getEntitiesCache().get(input.getClass().getSimpleName())
                .invokeSetter(input, toCamelCase(embeddedFieldName), existingCollection);
        final T hasTs = input;
        T updatedInputEntity = dataManager.saveAndFlush(hasTs);
        Collection<TEmbedded> newlyAssociated = getEmbeddedCollectionFrom(
        updatedInputEntity,
        toCamelCase(embeddedFieldName));
        var result = extractFromCollection(newlyAssociated, toAssociate);
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postAssociate(result, input, tEmbeddedDataManager, dataManager);
        return result;
    }

    public  <TEmbedded>
    List<TEmbedded>
    removeFromEmbeddedCollection(
            T owner,
            String toRemoveFieldName,
            List<TEmbedded> toRemove,
            EmbeddedCollectionApiHooks<TEmbedded, T> embeddedCollectionApiHooks) {
        //get collection owner
        val temp = dataManager
                .findById(getId(owner, reflectionCache)).orElse(null);
        if(temp == null) throw_entityNotFound(owner, reflectionCache);
        owner = temp;
        Collection<TEmbedded> currentEmbeddedCollection = getEmbeddedCollectionFrom(owner, toRemoveFieldName);
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preRemove(toRemove, owner, dataManager);
        currentEmbeddedCollection.removeIf(toRemove::contains);
        dataManager.save(owner);
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postRemove(toRemove, owner, dataManager);
        return toRemove;
    }

    public <TEmbedded> List<TEmbedded> extractFromCollection(Collection<TEmbedded> toExtractFrom, Collection<TEmbedded> toExtract) {
        List<TEmbedded> result = new ArrayList<>();
        toExtract.forEach(item -> result.add(extract(toExtractFrom, item, reflectionCache)));
        return result;
    }

    public <TEmbedded> TEmbedded extract(Collection<TEmbedded> collection, TEmbedded toCheck, ReflectionCache reflectionCache) {
        Object toCheckId = getId(toCheck, reflectionCache);
        for (TEmbedded item : collection) {
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

    public <TEmbedded> Collection<TEmbedded> getEmbeddedCollectionFrom(T input, String fieldName) {
        return (Collection<TEmbedded>)
                reflectionCache
                        .getEntitiesCache()
                        .get(input.getClass()
                                .getSimpleName())
                        .invokeGetter(input, fieldName);
    }

    private final static Logger log = LoggerFactory.getLogger(ApiLogic.class);
    private static Executor loggerThread = Executors.newSingleThreadExecutor();
    private static synchronized void log(String msg, boolean isError, Object... args){
        loggerThread.execute(() -> {
            if (isError) log.error(msg, args);
            else log.info(msg, args);
        });
    }
    private void logInfo(String msg, Object... args){
        log(msg, false, args);
    }

    private void logError(String msg, Object... args) {
        log(msg, true, args);
    }
}

