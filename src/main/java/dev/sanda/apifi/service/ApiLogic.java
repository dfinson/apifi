package dev.sanda.apifi.service;


import dev.sanda.datafi.persistence.Archivable;
import dev.sanda.datafi.reflection.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.val;
import lombok.var;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import static dev.sanda.apifi.ApifiStaticUtils.isClazzArchivable;
import static dev.sanda.datafi.DatafiStaticUtils.*;


@Service
@SuppressWarnings("unchecked")
public final class ApiLogic<T> {

    @Autowired(required = false)
    private ApiHooksAndCustomResolvers<T> apiHooks;
    @Autowired
    private ReflectionCache reflectionCache;
    @Autowired
    private DataManager<T> dataManager;

    public List<T> getPaginatedBatch(int offset, int limit, String sortBy, Sort.Direction sortDirection) {
        validateSortByIfNonNull(dataManager.getClazz(), sortBy, reflectionCache);
        if(apiHooks != null) apiHooks.preGetPaginatedBatch();
        List<T> result;
        final PageRequest pageRequest = generatePageRequest(offset, limit, sortBy, sortDirection);
        if(isClazzArchivable(dataManager.getClazz(), reflectionCache))
            result = dataManager
                        .findAll((Specification<T>) (root, query, cb) -> cb.isFalse(root.get("isArchived")), pageRequest)
                        .getContent();
        else result = dataManager.findAll(pageRequest).getContent();
        if(apiHooks != null) apiHooks.postGetPaginatedBatch(result);
        return result;
    }

    public List<T> getArchivedPaginatedBatch(int offset, int limit, String sortBy, Sort.Direction sortDirection) {
        validateSortByIfNonNull(dataManager.getClazz(), sortBy, reflectionCache);
        if(apiHooks != null) apiHooks.preGetArchivedPaginatedBatch();
        List<T> result;
        final PageRequest pageRequest = generatePageRequest(offset, limit, sortBy, sortDirection);
        if(isClazzArchivable(dataManager.getClazz(), reflectionCache))
            result = dataManager
                    .findAll((Specification<T>) (root, query, cb) -> cb.isTrue(root.get("isArchived")), pageRequest)
                    .getContent();
        else result = dataManager.findAll(pageRequest).getContent();
        if(apiHooks != null) apiHooks.postGetArchivedPaginatedBatch(result);
        return result;
    }

    public  List<T> freeTextSearch(int offset, int limit, String searchTerm, String sortBy, Sort.Direction sortDirection) {
        if(apiHooks != null) apiHooks.preFetchEntitiesInFreeTextSearch(searchTerm);
        List<T> result = dataManager
                .freeTextSearchBy(searchTerm, offset, limit, sortBy, sortDirection);
        if(apiHooks != null) apiHooks.postFetchEntitiesInFuzzySearch(searchTerm, result);
        return result;
    }

    public T getById(Object id) {
        if(apiHooks != null) apiHooks.preGetById(id);
        var result = dataManager.findById(id).orElse(null);
        if(result == null) throwEntityNotFoundException(dataManager.getClazzSimpleName(), id);
        if(apiHooks != null) apiHooks.postGetById(result);
        return result;
    }

    public T apiFindByUnique(String fieldName, Object fieldValue) {
        if(apiHooks != null) apiHooks.preApiFindByUnique(fieldValue);
        T result = dataManager.findByUnique(fieldName, fieldValue).orElse(null);
        if(result == null) throwEntityNotFoundException(dataManager.getClazzSimpleName(), fieldValue);
        if(apiHooks != null) apiHooks.postApiFindByUnique(result);
        return result;
    }

    public List<T> apiFindBy(String fieldName, Object argument) {
        if(apiHooks != null) apiHooks.preApiFindBy(argument);
        List<T> result = dataManager.findBy(fieldName, argument);
        if(apiHooks != null) apiHooks.postApiFindBy(result);
        return result;
    }

    public List<T> apiFindAllBy(String fieldName, List<?> arguments) {
        if(apiHooks != null) apiHooks.preApiFindAllBy(fieldName, arguments);
        List<T> result = dataManager.findAllBy(fieldName, arguments.toArray());
        if(apiHooks != null) apiHooks.postApiFindAllBy(fieldName, result);
        return result;
    }

    public T create(T input) {
        if(apiHooks != null) apiHooks.preCreate(input);
        val result = dataManager.save(input);
        if(apiHooks != null) apiHooks.postCreate(result);
        return result;
    }

    public T update(T input) {
        final Object id = getId(input, reflectionCache);
        T toUpdate = getById(id);
        if (toUpdate == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preUpdate(toUpdate);
        dataManager.cascadeUpdate(toUpdate, input);
        val result = dataManager.save(toUpdate);
        if(apiHooks != null) apiHooks.postUpdate(result);
        return result;
    }

    public T delete(T input) {
        final Object id = getId(input, reflectionCache);
        T toDelete = getById(id);
        if(apiHooks != null) apiHooks.preDelete(toDelete);
        dataManager.deleteById(id);
        if(apiHooks != null) apiHooks.postDelete(toDelete);
        return toDelete;
    }

    public <A extends Archivable> T archive(A input) {
        final Object id = getId(input, reflectionCache);
        T toArchive = getById(id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preArchive(toArchive);
        input.setIsArchived(true);
        assert toArchive != null;
        val result = dataManager.save(toArchive);
        if(apiHooks != null) apiHooks.postArchive(result);
        return result;
    }

    public <A extends Archivable> T deArchive(A input) {
        final Object id = getId(input, reflectionCache);
        T toArchive = getById(id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        if(apiHooks != null) apiHooks.preDeArchive(toArchive);
        input.setIsArchived(false);
        assert toArchive != null;
        val result = dataManager.save(toArchive);
        if(apiHooks != null) apiHooks.postDeArchive(result);
        return result;
    }

    public <A extends Archivable> List<T> batchArchive(List<A> input) {
        List<A> entitiesToArchive = (List<A>) getBatchByIds(dataManager.idList((Iterable<T>) input));
        if(apiHooks != null) apiHooks.preBatchArchive((Collection<T>) entitiesToArchive);
        entitiesToArchive.forEach(entity -> entity.setIsArchived(true));
        List<T> result = dataManager.saveAll((List<T>)entitiesToArchive);
        if(apiHooks != null) apiHooks.postBatchArchive(result);
        return result;
    }

    public <A extends Archivable> List<T> batchDeArchive(List<A> input) {
        List<A> entitiesToDeArchive = (List<A>) getBatchByIds(dataManager.idList((Iterable<T>) input));
        if(apiHooks != null) apiHooks.preBatchDeArchive((List<T>) entitiesToDeArchive);
        entitiesToDeArchive.forEach(entity -> entity.setIsArchived(false));
        List<T> result = dataManager.saveAll((List<T>)entitiesToDeArchive);
        if(apiHooks != null) apiHooks.postBatchDeArchive(result);
        return result;
    }

    public List<T> getBatchByIds(List<?> ids) {
        return dataManager.findAllById(ids);
    }

    public List<T> batchCreate(List<T> input) {
        if(apiHooks != null) apiHooks.preBatchCreate(input);
        val result = dataManager.saveAll(input);
        if(apiHooks != null) apiHooks.postBatchCreate(result);
        return result;
    }

    public List<T> batchUpdate(List<T> input) {
        List<T> toUpdate = getBatchByIds(dataManager.idList(input));
        if(apiHooks != null) apiHooks.preBatchUpdate(toUpdate);
        List<T> result = dataManager.cascadeUpdateCollection(toUpdate, input);
        if(apiHooks != null) apiHooks.postBatchUpdate(result);
        return result;
    }

    public List<T> batchDelete(List<T> input) {
        List<T> toDelete = getBatchByIds(dataManager.idList(input));
        if(apiHooks != null) apiHooks.preDeleteEntities(toDelete);
        dataManager.deleteInBatch(input);
        if(apiHooks != null) apiHooks.postDeleteEntities(toDelete);
        return toDelete;
    }

    public <TEmbedded, E extends EmbeddedCollectionApiHooks<TEmbedded, T>> List<List<TEmbedded>>
    getEmbeddedCollection(
            List<T> input,
            String embeddedFieldName,
            E embeddedCollectionApiHooks,
            DataManager<TEmbedded> tEmbeddedDataManager) {
        List<List<TEmbedded>> lists = new ArrayList<>();
        input.forEach(t -> {
            if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preFetch(t);
            final List<TEmbedded> embeddedCollection =
                    tEmbeddedDataManager.findAllById(tEmbeddedDataManager
                            .idList((Iterable<TEmbedded>) getEmbeddedCollectionFrom(t, embeddedFieldName)));
            if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postFetch(embeddedCollection, t);
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
            T input,
            DataManager<TEmbedded> tEmbeddedDataManager,
            Iterable<TEmbedded> toUpdate,
            E embeddedCollectionApiHooks) {
        var temp = dataManager
                .findById(getId(input, reflectionCache)).orElse(null);
        if (temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        return updateCollectionAsEmbedded(input, toUpdate, embeddedCollectionApiHooks, tEmbeddedDataManager);
    }

    public  <TEmbedded, E extends EmbeddedCollectionApiHooks<TEmbedded, T>>
    List<TEmbedded> updateCollectionAsEmbedded(
            T input,
            Iterable<TEmbedded> toUpdate,
            E embeddedCollectionApiHooks,
            DataManager<TEmbedded> tEmbeddedDataManager) {
        List<TEmbedded> entitiesToUpdate = (List<TEmbedded>) getBatchByIds(tEmbeddedDataManager.idList(toUpdate));
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preUpdate(entitiesToUpdate, input);
        var result = tEmbeddedDataManager.cascadeUpdateCollection(entitiesToUpdate, toUpdate);
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postUpdate(result, input);
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
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preAssociate(toAssociate, input);
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
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postAssociate(result, input);
        return result;
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
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preAssociate(toAssociate, input);
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
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postAssociate(result, input);
        return result;
    }

    public  <TEmbedded>
    List<TEmbedded>
    removeFromEmbeddedCollection(
            T input,
            String toRemoveFieldName,
            List<TEmbedded> toRemove,
            EmbeddedCollectionApiHooks<TEmbedded, T> embeddedCollectionApiHooks) {
        //get collection owner
        val temp = dataManager
                .findById(getId(input, reflectionCache)).orElse(null);
        if(temp == null) throw_entityNotFound(input, reflectionCache);
        input = temp;
        Collection<TEmbedded> currentEmbeddedCollection = getEmbeddedCollectionFrom(input, toRemoveFieldName);
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.preRemove(toRemove, input);
        currentEmbeddedCollection.removeIf(toRemove::contains);
        dataManager.save(input);
        if(embeddedCollectionApiHooks != null) embeddedCollectionApiHooks.postRemove(toRemove, input);
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
        throw new RuntimeException(
                "Cannot find Entity " + input.getClass().getSimpleName() + " with id " + getId(input, reflectionCache));
    }

    public <TEmbedded> Collection<TEmbedded> getEmbeddedCollectionFrom(T input, String fieldName) {
        return (Collection<TEmbedded>)
                reflectionCache
                        .getEntitiesCache()
                        .get(input.getClass()
                                .getSimpleName())
                        .invokeGetter(input, fieldName);
    }

}

