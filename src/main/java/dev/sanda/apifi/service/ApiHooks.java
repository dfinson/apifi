package dev.sanda.apifi.service;

import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.dto.PageRequest;
import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public interface ApiHooks<T> {
    //queries
    default void preGetById(Object id, DataManager<T> dataManager){}
    default void preApiFindByUnique(String fieldName, Object argument, DataManager<T> dataManager){}
    default void postApiFindByUnique(String fieldName, Object fieldValue, T result, DataManager<T> dataManager) {}
    default void preApiFindBy(String fieldName, Object argument, DataManager<T> dataManager){}
    default void postApiFindBy(String fieldName, Object argument, List<T> result, DataManager<T> dataManager) {}
    default void preApiFindAllBy(String fieldName, List<?> arguments, DataManager<T> dataManager){}
    default void postApiFindAllBy(String fieldName, List<?> arguments, List<T> result, DataManager<T> dataManager) {}
    default void preGetPaginatedBatch(PageRequest request, DataManager<T> dataManager) {}
    default void postGetPaginatedBatch(PageRequest request, Page<T> result, DataManager<T> dataManager){}
    default void preFreeTextSearch(String searchTerm, DataManager<T> dataManager){}
    default void postFreeTextSearch(String searchTerm, List<T> result, DataManager<T> dataManager){}
    default void postGetById(T result, DataManager<T> dataManager){}
    default void preGetArchivedPaginatedBatch(PageRequest request, DataManager<T> dataManager){}
    default void postGetArchivedPaginatedBatch(PageRequest request, Page<T> result, DataManager<T> dataManager) {}

    //mutations
    default void preCreate(T originalInput, DataManager<T> dataManager){}
    default void postCreate(T originalInput, T created, DataManager<T> dataManager){}
    default void preUpdate(T originalInput, T toUpdate, DataManager<T> dataManager){}
    default void postUpdate(T originalInput, T toUpdate, T updated, DataManager<T> dataManager){}
    default void preDelete(T originalInput, T toDelete, DataManager<T> dataManager){}
    default void postDelete(T originalInput, T deleted, DataManager<T> dataManager){}
    default void preArchive(T originalInput, T toArchive, DataManager<T> dataManager){}
    default void postArchive(T originalInput, T archived, DataManager<T> dataManager){}
    default void preDeArchive(T originalInput, T toDeArchive, DataManager<T> dataManager){}
    default void postDeArchive(T originalInput, T deArchived, DataManager<T> dataManager){}

    default void preBatchCreate(List<T> input, DataManager<T> dataManager){}
    default void postBatchCreate(List<T> originalInput, List<T> created, DataManager<T> dataManager){}
    default void preBatchUpdate(List<T> originalInput, List<T> toUpdate, DataManager<T> dataManager){}
    default void postBatchUpdate(List<T> originalInput, List<T> updated, DataManager<T> dataManager){}
    default void preDeleteEntities(List<T> originalInput, List<T> toDelete, DataManager<T> dataManager){}
    default void postDeleteEntities(List<T> originalInput, List<T> deleted, DataManager<T> dataManager){}
    default void preBatchArchive(List<T> originalInput, List<T> toArchive, DataManager<T> dataManager){}
    default void postBatchArchive(List<T> originalInput, List<T> archived, DataManager<T> dataManager){}
    default void preBatchDeArchive(List<T> originalInput, List<T> toDeArchive, DataManager<T> dataManager){}
    default void postBatchDeArchive(List<T> originalInput, List<T> deArchived, DataManager<T> dataManager){}
}