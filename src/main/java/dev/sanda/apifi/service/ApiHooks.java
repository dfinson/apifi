package dev.sanda.apifi.service;

import dev.sanda.datafi.service.DataManager;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public interface ApiHooks<T> {
    //queries
    default void preGetById(Object id, DataManager<T> dataManager){}
    default void preApiFindByUnique(Object argument, DataManager<T> dataManager){}
    default void postApiFindByUnique(T result, DataManager<T> dataManager) {}
    default void preApiFindBy(Object argument, DataManager<T> dataManager){}
    default void postApiFindBy(List<T> result, DataManager<T> dataManager) {}
    default void preApiFindAllBy(String fieldName, List<?> arguments, DataManager<T> dataManager){}
    default void postApiFindAllBy(String fieldName, List<T> result, DataManager<T> dataManager) {}
    default void preGetPaginatedBatch(DataManager<T> dataManager) {}
    default void postGetPaginatedBatch(List<T> result, DataManager<T> dataManager){}
    default void preFreeTextSearch(String searchTerm, DataManager<T> dataManager){}
    default void postFreeTextSearch(String searchTerm, List<T> fetched, DataManager<T> dataManager){}
    default void postGetById(T fetched, DataManager<T> dataManager){}
    default void preGetArchivedPaginatedBatch(DataManager<T> dataManager){}
    default void postGetArchivedPaginatedBatch(List<T> result, DataManager<T> dataManager) {}

    //mutations
    default void preCreate(T toAdd, DataManager<T> dataManager){}
    default void postCreate(T added, DataManager<T> dataManager){}
    default void preUpdate(T toUpdate, DataManager<T> dataManager){}
    default void postUpdate(T toUpdate, DataManager<T> dataManager){}
    default void preDelete(T toDelete, DataManager<T> dataManager){}
    default void postDelete(T deleted, DataManager<T> dataManager){}
    default void preArchive(T toArchive, DataManager<T> dataManager){}
    default void postArchive(T toArchive, DataManager<T> dataManager){}
    default void preDeArchive(T toDeArchive, DataManager<T> dataManager){}
    default void postDeArchive(T toDeArchive, DataManager<T> dataManager){}

    default void preBatchCreate(Collection<T> toAdd, DataManager<T> dataManager){}
    default void postBatchCreate(Collection<T> added, DataManager<T> dataManager){}
    default void preBatchUpdate(Collection<T> toUpdate, DataManager<T> dataManager){}
    default void postBatchUpdate(Collection<T> toUpdate, DataManager<T> dataManager){}
    default void preDeleteEntities(Collection<T> toDelete, DataManager<T> dataManager){}
    default void postDeleteEntities(Collection<T> deleted, DataManager<T> dataManager){}
    default void preBatchArchive(Collection<T> toArchive, DataManager<T> dataManager){}
    default void postBatchArchive(Collection<T> toArchive, DataManager<T> dataManager){}
    default void preBatchDeArchive(List<T> toDeArchive, DataManager<T> dataManager){}
    default void postBatchDeArchive(List<T> toDeArchive, DataManager<T> dataManager){}
}