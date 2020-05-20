package dev.sanda.apifi.service;

import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
public interface EmbeddedCollectionApiHooks<TEmbedded, T>{
    default void preFetch(T t, DataManager<T> dataManager) {}
    default void postFetch(Collection<TEmbedded> tEmbeddeds, T t, DataManager<TEmbedded> tEmbeddedDataManager, DataManager<T> dataManager ){}
    default void preGetPaginatedBatch(T t, DataManager<T> dataManager) {}
    default void postGetPaginatedBatch(Page<TEmbedded> batch, T t, DataManager<TEmbedded> tEmbeddedDataManager, DataManager<T> dataManager ){}
    default void preFreeTextSearch(T t, String searchTerm, DataManager<T> dataManager, DataManager<TEmbedded> tEmbeddedDataManager) {}
    default void postFreeTextSearch(Page<TEmbedded> batch, T t, String searchTerm, DataManager<TEmbedded> tEmbeddedDataManager, DataManager<T> dataManager){}
    default void preRemove(Collection<TEmbedded> tEmbeddeds, T t, DataManager<T> dataManager){}
    default void postRemove(Collection<TEmbedded> tEmbeddeds, T t, DataManager<T> dataManager){}
    default void preAssociate(Collection<TEmbedded> tEmbeddeds, T t, DataManager<TEmbedded> tEmbeddedDataManager, DataManager<T> dataManager ){}
    default void postAssociate(Collection<TEmbedded> tEmbeddeds, T t, DataManager<TEmbedded> tEmbeddedDataManager, DataManager<T> dataManager ){}
    default void preUpdate(Collection<TEmbedded> tEmbeddeds, T t, DataManager<TEmbedded> tEmbeddedDataManager, DataManager<T> dataManager ){}
    default void postUpdate(Collection<TEmbedded> result, Collection<TEmbedded> originalInput, T t, DataManager<TEmbedded> tEmbeddedDataManager, DataManager<T> dataManager ){}
}
