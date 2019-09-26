package org.sindaryn.apifi.service;


import lombok.val;
import lombok.var;
import org.sindaryn.datafi.persistence.Archivable;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.datafi.service.ArchivableDataManager;
import org.sindaryn.datafi.service.BaseDataManager;
import org.sindaryn.datafi.service.DataManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.sindaryn.datafi.StaticUtils.*;

@SuppressWarnings("unchecked")
public interface ApiLogic {

    static <T, E extends ApiMetaOperations<T>> 
    List<T> getAll(
            Class<?> clazz, BaseDataManager<T> dataManager, ReflectionCache reflectionCache,
            E metaOps, int offset, int limit, String sortBy, Sort.Direction sortDirection) {
        validateSortByIfNonNull(clazz, sortBy, reflectionCache);
        metaOps.preFetchEntitiesInGetAll((Class<T>) clazz);
        var result = 
                dataManager.findAll(generatePageRequest(offset, limit, sortBy, sortDirection))
                .getContent();
        metaOps.postFetchEntities(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>>
    List<T> fuzzySearch(
            Class<?> clazz, BaseDataManager<T> dataManager,
            E metaOps, int offset, int limit, String searchTerm,
            String sortBy, Sort.Direction sortDirection) {
        metaOps.preFetchEntitiesInFuzzySearch((Class<T>) clazz, searchTerm);
        List<T> result = dataManager
                .fuzzySearchBy(searchTerm, offset, limit, sortBy, sortDirection);
        metaOps.postFetchEntitiesInFuzzySearch((Class<T>)clazz, searchTerm, result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> T 
    getById(Class<?> clazz, BaseDataManager<T> dataManager, E metaOps, Object id) {
        metaOps.preFetchEntityInGetById(id);
        var result = dataManager.findById(id).orElse(null);
        if(result == null) throwEntityNotFoundException(clazz.getSimpleName(), id);
        metaOps.postFetchEntity(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> T
    getByUnique(Class<?> clazz, BaseDataManager<T> dataManager, E metaOps, String resolverName, Object argument) {
        metaOps.preFetchEntityInGetByUnique(argument);
        T result = dataManager.getByUnique(resolverName, argument).orElse(null);
        if(result == null) throwEntityNotFoundException(clazz.getSimpleName(), argument);
        metaOps.postFetchEntity(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> List<T>//TODO
    getBy(BaseDataManager<T> dataManager, E metaOps, String fieldName, Object argument) {
        metaOps.preFetchEntityInGetBy(argument);
        List<T> result = dataManager.getBy(fieldName, argument);
        metaOps.postFetchEntities(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> List<T>//TODO
    getAllBy(BaseDataManager<T> dataManager, E metaOps, String resolverName, List<?> arguments) {
        metaOps.preFetchEntityInGetAllBy(arguments);
        List<T> result = dataManager.getAllBy(resolverName, arguments.toArray());
        metaOps.postFetchEntities(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> List<T>//TODO
    selectBy(BaseDataManager<T> dataManager, E metaOps, String resolverName, List<?> arguments) {
        metaOps.preFetchEntityInCustomResolver(arguments);
        List<T> result = dataManager.selectByResolver(resolverName, arguments.toArray());
        metaOps.postFetchEntities(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> T
    add(BaseDataManager<T> dataManager, T input, E metaOps) {
        metaOps.preAddEntity(input);
        val result = dataManager.save(input);
        metaOps.postAddEntity(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> T
    update(BaseDataManager<T> dataManager, T input, ReflectionCache reflectionCache, E metaOps) {
        final Object id = getId(input, reflectionCache);
        T toUpdate = getById(input.getClass(), dataManager, metaOps, id);
        if (toUpdate == null) throw_entityNotFound(input, reflectionCache);
        metaOps.preUpdateEntity(toUpdate);
        dataManager.cascadedUpdate(toUpdate, input);
        val result = dataManager.save(toUpdate);
        metaOps.postUpdateEntity(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> T
    delete(BaseDataManager<T> dataManager, ReflectionCache reflectionCache, T input, E metaOps) {
        final Object id = getId(input, reflectionCache);
        T toDelete = getById(input.getClass(), dataManager, metaOps, id);
        metaOps.preDeleteEntity(toDelete);
        dataManager.deleteById(id);
        metaOps.postDeleteEntity(toDelete);
        return toDelete;
    }

    static <T extends Archivable, E extends ApiMetaOperations<T>> T
    archive(ArchivableDataManager<T> dataManager, T input, ReflectionCache reflectionCache, E metaOps) {
        final Object id = getId(input, reflectionCache);
        T toArchive = getById(input.getClass(), dataManager, metaOps, id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        metaOps.preArchiveEntity(toArchive);
        input.setIsArchived(true);
        val result = dataManager.save(toArchive);
        metaOps.postArchiveEntity(result);
        return result;
    }

    static <T extends Archivable, E extends ApiMetaOperations<T>> T
    deArchive(ArchivableDataManager<T> dataManager, T input, ReflectionCache reflectionCache, E metaOps) {
        final Object id = getId(input, reflectionCache);
        T toArchive = getById(input.getClass(), dataManager, metaOps, id);
        if (toArchive == null) throw_entityNotFound(input, reflectionCache);
        metaOps.preDeArchiveEntity(toArchive);
        input.setIsArchived(false);
        val result = dataManager.save(toArchive);
        metaOps.postDeArchiveEntity(result);
        return result;
    }

    static <T extends Archivable, E extends ApiMetaOperations<T>> List<T>
    archiveCollection(ArchivableDataManager<T> dataManager, List<T> input, E metaOps) {
        List<T> entitiesToArchive = getCollectionById(dataManager, dataManager.idList(input));
        metaOps.preArchiveEntities(entitiesToArchive);
        entitiesToArchive.forEach(entity -> entity.setIsArchived(true));
        List<T> result = dataManager.saveAll(entitiesToArchive);
        metaOps.postArchiveEntities(result);
        return result;
    }

    static <T extends Archivable, E extends ApiMetaOperations<T>> List<T>
    deArchiveCollection(ArchivableDataManager<T> dataManager, List<T> input, E metaOps) {
        List<T> entitiesToArchive = getCollectionById(dataManager, dataManager.idList(input));
        metaOps.preDeArchiveEntities(entitiesToArchive);
        entitiesToArchive.forEach(entity -> entity.setIsArchived(false));
        List<T> result = dataManager.saveAll(entitiesToArchive);
        metaOps.postDeArchiveEntities(result);
        return result;
    }
    
    static <T> List<T>//TODO
    getCollectionById(BaseDataManager<T> dataManager, List<?> ids) {
        return dataManager.findAllById(ids);
    }

    static <T, E extends ApiMetaOperations<T>> List<T>
    addCollection(BaseDataManager<T> dataManager, List<T> input, E metaOps) {
        metaOps.preAddEntities(input);
        val result = dataManager.saveAll(input);
        metaOps.postAddEntities(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>> List<T>
    updateCollection(BaseDataManager<T> dataManager, List<T> input, E metaOps) {
        List<T> entitiesToUpdate = getCollectionById(dataManager, dataManager.idList(input));
        metaOps.preUpdateEntities(entitiesToUpdate);
        List<T> result = dataManager.cascadeUpdateCollection(entitiesToUpdate, input);
        metaOps.postUpdateEntities(result);
        return result;
    }

    static <T, E extends ApiMetaOperations<T>>
    List<T> deleteCollection(BaseDataManager<T> dataManager, List<T> input, E metaOps) {
        List<T> toDeletes = getCollectionById(dataManager, dataManager.idList(input));
        metaOps.preDeleteEntities(toDeletes);
        dataManager.deleteInBatch(input);
        metaOps.postDeleteEntities(toDeletes);
        return toDeletes;
    }

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>> List<List<T>>
    getAsEmbeddedCollection(
            BaseDataManager<T> dataManager,
            List<HasTs> input,
            String embeddedFieldName,
            E metaOps,
            ReflectionCache reflectioncache) {
        List<List<T>> lists = new ArrayList<>();
        input.forEach(hasTs -> {
            final List<T> embeddedCollection =
                    dataManager.findAllById(dataManager
                            .idList(getEmbeddedCollectionFrom(hasTs, embeddedFieldName, reflectioncache)));
            metaOps.postFetch(embeddedCollection, hasTs);
            lists.add(embeddedCollection);
        });
        return lists;
    }

    static <T, HasT> List<T> getAsEmbeddedEntity(
            BaseDataManager<T> dataManager,
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

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>>
    List<T> updateEmbeddedCollection(
            BaseDataManager<HasTs> collectionOwnerDataManager,
            BaseDataManager<T> toUpdateDataManager,
            HasTs collectionOwner,
            Iterable<T> toUpdate,
            E metaOps,
            ReflectionCache reflectioncache) {
        var temp = collectionOwnerDataManager
                .findById(getId(collectionOwner, reflectioncache)).orElse(null);
        if (temp == null) throw_entityNotFound(collectionOwner, reflectioncache);
        collectionOwner = temp;
        return updateCollectionAsEmbedded(toUpdateDataManager, toUpdate, collectionOwner, metaOps);
    }

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>>
    List<T> updateCollectionAsEmbedded(
            BaseDataManager<T> toUpdateDataManager,
            Iterable<T> toUpdate,
            HasTs collectionOwner,
            E metaOps) {
        List<T> entitiesToUpdate = getCollectionById(toUpdateDataManager, toUpdateDataManager.idList(toUpdate));
        metaOps.preUpdate(entitiesToUpdate, collectionOwner);
        val result = toUpdateDataManager.cascadeUpdateCollection(entitiesToUpdate, toUpdate);
        metaOps.postUpdate(result, collectionOwner);
        return result;
    }


    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>>
    List<T> addNewToEmbeddedCollection(
            BaseDataManager<HasTs> toAddToDataManager,
            BaseDataManager<T> toAddDataManager,
            HasTs toAddTo,
            String fieldName,
            List<T> toAdd,
            E metaOps,
            ReflectionCache reflectioncache) {

        //get collection owner
        var temp = toAddToDataManager.findById(getId(toAddTo, reflectioncache)).orElse(null);
        if (temp == null) throw_entityNotFound(toAddTo, reflectioncache);
        toAddTo = temp;
        metaOps.preAttachOrAdd(toAdd, toAddTo);
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
        metaOps.postAttachOrAdd(result, toAddTo);
        return result;
    }

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>>
    List<T>
    attachExistingToEmbeddedCollection(
            BaseDataManager<HasTs> toAttachToDataManager,
            BaseDataManager<T> toAttachDataManager,
            HasTs toAttachTo,
            String embeddedFieldName,
            List<T> toAttach,
            E metaOps,
            ReflectionCache reflectioncache) {
        //get collection owner
        var temp  = toAttachToDataManager.findById(getId(toAttachTo, reflectioncache)).orElse(null);
        if (temp == null) throw_entityNotFound(toAttachTo, reflectioncache);
        toAttachTo = temp;
        metaOps.preAttachOrAdd(toAttach, toAttachTo);
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
        metaOps.postAttachOrAdd(result, toAttachTo);
        return result;
    }

    static <T, HasTs, E extends EmbeddedCollectionMetaOperations<T, HasTs>> List<T>
    removeFromEmbeddedCollection(
            BaseDataManager<HasTs> toRemoveFromDataManager,
            BaseDataManager<T> toRemoveDataManager,
            HasTs toRemoveFrom,
            String toRemoveFieldName,
            List<T> toRemove,
            E metaOps,
            ReflectionCache reflectioncache) {
        //get collection owner
        val temp = toRemoveFromDataManager
                .findById(getId(toRemoveFrom, reflectioncache)).orElse(null);
        if(temp == null) throw_entityNotFound(toRemoveFrom, reflectioncache);
        toRemoveFrom = temp;
        //get Set<T> toDelete
        Collection<T> currentEmbeddedCollection = (Collection<T>)
                getEmbeddedCollectionFrom(toRemoveFrom, toRemoveFieldName, reflectioncache);
        metaOps.preRemove(toRemove, toRemoveFrom);
        currentEmbeddedCollection.removeIf(toRemove::contains);
        HasTs updatedToRemoveFrom = toRemoveFromDataManager.save(toRemoveFrom);
        Collection<T> attached =
                getEmbeddedCollectionFrom(
                        updatedToRemoveFrom,
                        toRemoveFieldName,
                        reflectioncache);
        metaOps.postRemove(toRemove, toRemoveFrom);
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

