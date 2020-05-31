package dev.sanda.apifi.service;

import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public interface MapElementCollectionApiHooks<TMapKey, TMapValue, T> {
    default void prePut(Map<TMapKey, TMapValue> toPut, T input, DataManager<T> dataManager){}
    default void postAdd(Map<TMapKey, TMapValue> toPut, T input, DataManager<T> dataManager){}
    default void preRemove(Collection<TMapKey> toRemove, T input, DataManager<T> dataManager){}
    default void postRemove(Map<TMapKey, TMapValue> removed, T input, DataManager<T> dataManager){}
    default void preGetPaginatedBatch(T owner, DataManager<T> dataManager){}
    default void postGetPaginatedBatch(Page<Map.Entry<TMapKey, TMapValue>> returnValue, T owner, DataManager<T> dataManager){}
    default void preFreeTextSearch(T owner, DataManager<T> dataManager){}
    default void postFreeTextSearch(Page<Map.Entry<TMapKey, TMapValue>> returnValue, T owner, DataManager<T> dataManager){}
}
