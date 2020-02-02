package dev.sanda.apifi.service;


import dev.sanda.datafi.persistence.Archivable;
import dev.sanda.datafi.reflection.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.val;
import lombok.var;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static dev.sanda.apifi.ApifiStaticUtils.getId;
import static dev.sanda.apifi.ApifiStaticUtils.isClazzArchivable;
import static dev.sanda.datafi.DatafiStaticUtils.*;


@SuppressWarnings("unchecked")
public interface ApiLogic {

    static <T, E extends ApiHooksAndCustomResolvers<T>>
    List<T> getPaginatedBatch(
            Class<?> clazz, DataManager<T> dataManager, ReflectionCache reflectionCache,
            E apiHooks, int offset, int limit, String sortBy, Sort.Direction sortDirection) {
        validateSortByIfNonNull(clazz, sortBy, reflectionCache);
        apiHooks.preGetPaginatedBatch((Class<T>) clazz);
        List<T> result;
        final PageRequest pageRequest = generatePageRequest(offset, limit, sortBy, sortDirection);
        if(isClazzArchivable(clazz, reflectionCache))
            result = dataManager
                        .findAll((Specification<T>) (root, query, cb) -> cb.isFalse(root.get("isArchived")), pageRequest)
                        .getContent();
        else result = dataManager.findAll(pageRequest).getContent();
        apiHooks.postFetchEntities(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>>
    List<T> freeTextSearch(
            Class<?> clazz, DataManager<T> dataManager,
            E apiHooks, int offset, int limit, String searchTerm,
            String sortBy, Sort.Direction sortDirection) {
        apiHooks.preFetchEntitiesInFuzzySearch((Class<T>) clazz, searchTerm);
        List<T> result = dataManager
                .freeTextSearchBy(searchTerm, offset, limit, sortBy, sortDirection);
        apiHooks.postFetchEntitiesInFuzzySearch((Class<T>)clazz, searchTerm, result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> T
    getById(Class<?> clazz, DataManager<T> dataManager, E apiHooks, Object id) {
        apiHooks.preFetchEntityInGetById(id);
        var result = dataManager.findById(id).orElse(null);
        if(result == null) throwEntityNotFoundException(clazz.getSimpleName(), id);
        apiHooks.postFetchEntity(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> T
    getByUnique(Class<?> clazz, DataManager<T> dataManager, E apiHooks, String resolverName, Object argument) {
        apiHooks.preFetchEntityInGetByUnique(argument);
        T result = dataManager.findByUnique(resolverName, argument).orElse(null);
        if(result == null) throwEntityNotFoundException(clazz.getSimpleName(), argument);
        apiHooks.postFetchEntity(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> List<T>//TODO
    getBy(DataManager<T> dataManager, E apiHooks, String fieldName, Object argument) {
        apiHooks.preFetchEntityInGetBy(argument);
        List<T> result = dataManager.findBy(fieldName, argument);
        apiHooks.postFetchEntities(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> List<T>//TODO
    getAllBy(DataManager<T> dataManager, E apiHooks, String resolverName, List<?> arguments) {
        apiHooks.preFetchEntityInGetAllBy(arguments);
        List<T> result = dataManager.findAllBy(resolverName, arguments.toArray());
        apiHooks.postFetchEntities(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> List<T>//TODO
    selectBy(DataManager<T> dataManager, E apiHooks, String resolverName, List<?> arguments) {
        apiHooks.preFetchEntityInCustomResolver(arguments);
        List<T> result = dataManager.callQuery(resolverName, arguments.toArray());
        apiHooks.postFetchEntities(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> T
    create(DataManager<T> dataManager, T input, E apiHooks) {
        apiHooks.preAddEntity(input);
        val result = dataManager.save(input);
        apiHooks.postAddEntity(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> T
    update(DataManager<T> dataManager, T input, ReflectionCache reflectionCache, E apiHooks) {
        final Object id = getId(input, reflectionCache);
        T toUpdate = getById(input.getClass(), dataManager, apiHooks, id);
        if (toUpdate == null) throw_entityNotFound(input, reflectionCache);
        apiHooks.preUpdateEntity(toUpdate);
        dataManager.cascadeUpdate(toUpdate, input);
        val result = dataManager.save(toUpdate);
        apiHooks.postUpdateEntity(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> T
    delete(DataManager<T> dataManager, ReflectionCache reflectionCache, T input, E apiHooks) {
        final Object id = getId(input, reflectionCache);
        T toDelete = getById(input.getClass(), dataManager, apiHooks, id);
        apiHooks.preDeleteEntity(toDelete);
        dataManager.deleteById(id);
        apiHooks.postDeleteEntity(toDelete);
        return toDelete;
    }

    static <T extends Archivable, E extends ApiHooksAndCustomResolvers<T>> T
    archive(DataManager<T> dataManager, T input, ReflectionCache reflectionCache, E apiHooks) {
        final Object id = getId(input, reflectionCache);
        T toArchive = getById(input.getClass(), dataManager, apiHooks, id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        apiHooks.preArchiveEntity(toArchive);
        input.setIsArchived(true);
        val result = dataManager.save(toArchive);
        apiHooks.postArchiveEntity(result);
        return result;
    }

    static <T extends Archivable, E extends ApiHooksAndCustomResolvers<T>> T
    deArchive(DataManager<T> dataManager, T input, ReflectionCache reflectionCache, E apiHooks) {
        final Object id = getId(input, reflectionCache);
        T toArchive = getById(input.getClass(), dataManager, apiHooks, id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        apiHooks.preDeArchiveEntity(toArchive);
        input.setIsArchived(false);
        val result = dataManager.save(toArchive);
        apiHooks.postDeArchiveEntity(result);
        return result;
    }

    static <T extends Archivable, E extends ApiHooksAndCustomResolvers<T>> List<T>
    archiveCollection(DataManager<T> dataManager, List<T> input, E apiHooks) {
        List<T> entitiesToArchive = getCollectionById(dataManager, dataManager.idList(input));
        apiHooks.preArchiveEntities(entitiesToArchive);
        entitiesToArchive.forEach(entity -> entity.setIsArchived(true));
        List<T> result = dataManager.saveAll(entitiesToArchive);
        apiHooks.postArchiveEntities(result);
        return result;
    }

    static <T extends Archivable, E extends ApiHooksAndCustomResolvers<T>> List<T>
    deArchiveCollection(DataManager<T> dataManager, List<T> input, E apiHooks) {
        List<T> entitiesToArchive = getCollectionById(dataManager, dataManager.idList(input));
        apiHooks.preDeArchiveEntities(entitiesToArchive);
        entitiesToArchive.forEach(entity -> entity.setIsArchived(false));
        List<T> result = dataManager.saveAll(entitiesToArchive);
        apiHooks.postDeArchiveEntities(result);
        return result;
    }
    
    static <T> List<T>//TODO
    getCollectionById(DataManager<T> dataManager, List<?> ids) {
        return dataManager.findAllById(ids);
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> List<T>
    addCollection(DataManager<T> dataManager, List<T> input, E apiHooks) {
        apiHooks.preAddEntities(input);
        val result = dataManager.saveAll(input);
        apiHooks.postAddEntities(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>> List<T>
    updateCollection(DataManager<T> dataManager, List<T> input, E apiHooks) {
        List<T> entitiesToUpdate = getCollectionById(dataManager, dataManager.idList(input));
        apiHooks.preUpdateEntities(entitiesToUpdate);
        List<T> result = dataManager.cascadeUpdateCollection(entitiesToUpdate, input);
        apiHooks.postUpdateEntities(result);
        return result;
    }

    static <T, E extends ApiHooksAndCustomResolvers<T>>
    List<T> deleteCollection(DataManager<T> dataManager, List<T> input, E apiHooks) {
        List<T> toDeletes = getCollectionById(dataManager, dataManager.idList(input));
        apiHooks.preDeleteEntities(toDeletes);
        dataManager.deleteInBatch(input);
        apiHooks.postDeleteEntities(toDeletes);
        return toDeletes;
    }

    static <T, HasTs, E extends EmbeddedCollectionApiHooks<T, HasTs>> List<List<T>>
    getAsEmbeddedCollection(
            DataManager<T> dataManager,
            List<HasTs> input,
            String embeddedFieldName,
            E apiHooks,
            ReflectionCache reflectioncache) {
        List<List<T>> lists = new ArrayList<>();
        input.forEach(hasTs -> {
            final List<T> embeddedCollection =
                    dataManager.findAllById(dataManager
                            .idList(getEmbeddedCollectionFrom(hasTs, embeddedFieldName, reflectioncache)));
            apiHooks.postFetch(embeddedCollection, hasTs);
            lists.add(embeddedCollection);
        });
        return lists;
    }

    static <T, HasT> List<T> getAsEmbeddedEntity(
            DataManager<T> dataManager,
            List<HasT> input,
            String fieldName,
            ReflectionCache reflectioncache) {
        List<Object> ids = new ArrayList<>();
        input.forEach(hasT -> {
            final T embeddedReference = (T) reflectioncache
                    .getEntitiesCache().get(hasT.getClass().getSimpleName())
                    .invokeGetter(hasT, fieldName);
            ids.add(getId(embeddedReference, reflectioncache));
        });
        return dataManager.findAllById(ids);
    }

    static <T, HasTs, E extends EmbeddedCollectionApiHooks<T, HasTs>>
    List<T> updateEmbeddedCollection(
            DataManager<HasTs> collectionOwnerDataManager,
            DataManager<T> toUpdateDataManager,
            HasTs collectionOwner,
            Iterable<T> toUpdate,
            E apiHooks,
            ReflectionCache reflectioncache) {
        var temp = collectionOwnerDataManager
                .findById(getId(collectionOwner, reflectioncache)).orElse(null);
        if (temp == null) throw_entityNotFound(collectionOwner, reflectioncache);
        collectionOwner = temp;
        return updateCollectionAsEmbedded(toUpdateDataManager, toUpdate, collectionOwner, apiHooks);
    }

    static <T, HasTs, E extends EmbeddedCollectionApiHooks<T, HasTs>>
    List<T> updateCollectionAsEmbedded(
            DataManager<T> toUpdateDataManager,
            Iterable<T> toUpdate,
            HasTs collectionOwner,
            E apiHooks) {
        List<T> entitiesToUpdate = getCollectionById(toUpdateDataManager, toUpdateDataManager.idList(toUpdate));
        apiHooks.preUpdate(entitiesToUpdate, collectionOwner);
        val result = toUpdateDataManager.cascadeUpdateCollection(entitiesToUpdate, toUpdate);
        apiHooks.postUpdate(result, collectionOwner);
        return result;
    }


    static <T, HasTs, E extends EmbeddedCollectionApiHooks<T, HasTs>>
    List<T> addNewToEmbeddedCollection(
            DataManager<HasTs> toAddToDataManager,
            DataManager<T> toAddDataManager,
            HasTs toAddTo,
            String fieldName,
            List<T> toAdd,
            E apiHooks,
            ReflectionCache reflectioncache) {

        //get collection owner
        var temp = toAddToDataManager.findById(getId(toAddTo, reflectioncache)).orElse(null);
        if (temp == null) throw_entityNotFound(toAddTo, reflectioncache);
        toAddTo = temp;
        apiHooks.preAttachOrAdd(toAdd, toAddTo);
        Collection<T> existingCollection = getEmbeddedCollectionFrom(toAddTo, fieldName, reflectioncache);
        existingCollection.addAll(toAdd);

        //save owner
        reflectioncache.getEntitiesCache().get(toAddTo.getClass().getSimpleName())
                .invokeSetter(toAddTo, fieldName, existingCollection);
        final HasTs hasTs = toAddTo;

        Collection<T> added =
                getEmbeddedCollectionFrom(
                        hasTs,
                        fieldName,
                        reflectioncache);
        val result = toAddDataManager.saveAll(extractFromCollection(added, toAdd, reflectioncache));
        toAddToDataManager.save(hasTs);
        apiHooks.postAttachOrAdd(result, toAddTo);
        return result;
    }

    static <T, HasTs, E extends EmbeddedCollectionApiHooks<T, HasTs>>
    List<T>
    attachExistingToEmbeddedCollection(
            DataManager<HasTs> toAttachToDataManager,
            DataManager<T> toAttachDataManager,
            HasTs toAttachTo,
            String embeddedFieldName,
            List<T> toAttach,
            E apiHooks,
            ReflectionCache reflectioncache) {
        //get collection owner
        var temp  = toAttachToDataManager.findById(getId(toAttachTo, reflectioncache)).orElse(null);
        if (temp == null) throw_entityNotFound(toAttachTo, reflectioncache);
        toAttachTo = temp;
        apiHooks.preAttachOrAdd(toAttach, toAttachTo);
        //validate all candidates are pre-existing
        List<T> toAttachLoaded = toAttachDataManager.findAllById(toAttachDataManager.idList(toAttach));
        if (toAttachLoaded.isEmpty() || toAttachLoaded.size() != toAttach.size())
            throw new IllegalArgumentException("illegal attempt made to indirectly add new strong entities");
        Collection<T> existingCollection = getEmbeddedCollectionFrom(toAttachTo, toCamelCase(embeddedFieldName), reflectioncache);
        existingCollection.addAll(toAttach);
        //update save owner
        reflectioncache.getEntitiesCache().get(toAttachTo.getClass().getSimpleName())
                .invokeSetter(toAttachTo, toCamelCase(embeddedFieldName), existingCollection);
        final HasTs hasTs = toAttachTo;
        HasTs updatedToAttachTo = toAttachToDataManager.saveAndFlush(hasTs);
        Collection<T> attached = getEmbeddedCollectionFrom(
        updatedToAttachTo,
        toCamelCase(embeddedFieldName),
        reflectioncache);
        val result = extractFromCollection(attached, toAttach, reflectioncache);
        apiHooks.postAttachOrAdd(result, toAttachTo);
        return result;
    }

    static <T, HasTs, E extends EmbeddedCollectionApiHooks<T, HasTs>> List<T>
    removeFromEmbeddedCollection(
            DataManager<HasTs> toRemoveFromDataManager,
            DataManager<T> toRemoveDataManager,
            HasTs toRemoveFrom,
            String toRemoveFieldName,
            List<T> toRemove,
            E apiHooks,
            ReflectionCache reflectioncache) {
        //get collection owner
        val temp = toRemoveFromDataManager
                .findById(getId(toRemoveFrom, reflectioncache)).orElse(null);
        if(temp == null) throw_entityNotFound(toRemoveFrom, reflectioncache);
        toRemoveFrom = temp;
        //get Set<T> toDelete
        Collection<T> currentEmbeddedCollection = (Collection<T>)
                getEmbeddedCollectionFrom(toRemoveFrom, toRemoveFieldName, reflectioncache);
        apiHooks.preRemove(toRemove, toRemoveFrom);
        currentEmbeddedCollection.removeIf(toRemove::contains);
        HasTs updatedToRemoveFrom = toRemoveFromDataManager.save(toRemoveFrom);
        Collection<T> attached =
                getEmbeddedCollectionFrom(
                        updatedToRemoveFrom,
                        toRemoveFieldName,
                        reflectioncache);
        apiHooks.postRemove(toRemove, toRemoveFrom);
        return toRemove;
    }

    static <T> List<T> extractFromCollection(Collection<T> toExtractFrom, Collection<T> toExtract, ReflectionCache reflectionCache) {
        List<T> result = new ArrayList<>();
        toExtract.forEach(item -> result.add(extract(toExtractFrom, item, reflectionCache)));
        return result;
    }

    static <T> T extract(Collection<T> collection, T toCheck, ReflectionCache reflectionCache) {
        Object toCheckId = getId(toCheck, reflectionCache);

        for (T item : collection) {
            if (getId(item, reflectionCache).equals(toCheckId))
                return item;
        }
        throw_entityNotFound(toCheck, reflectionCache);
        return null;
    }

    static void throw_entityNotFound(Object input, ReflectionCache reflectionCache) {
        throw new RuntimeException(
                "Cannot find Entity " + input.getClass().getSimpleName() + " with id " + getId(input, reflectionCache));
    }

    static <T, HasTs> Collection<T> getEmbeddedCollectionFrom(HasTs iHasTs, String fieldName, ReflectionCache reflectioncache) {
        return (Collection<T>) reflectioncache.getEntitiesCache().get(iHasTs.getClass().getSimpleName()).invokeGetter(iHasTs, fieldName);
    }

}

