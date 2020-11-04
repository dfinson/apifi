package dev.sanda.apifi.service;

import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.dto.PageRequest;
import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public interface MapElementCollectionApiHooks<TMapKey, TMapValue, T> {
    default void prePut(Map<TMapKey, TMapValue> inputToPut, String fieldName, T ownerInstance, DataManager<T> ownerDataManager){}
    default void postPut(Map<TMapKey, TMapValue> inputToPut, String fieldName, T ownerInstance, DataManager<T> ownerDataManager){}
    default void preRemove(Collection<TMapKey> toRemove, String fieldName, T ownerInstance, DataManager<T> ownerDataManager){}
    default void postRemove(Map<TMapKey, TMapValue> removed, String fieldName, T ownerInstance, DataManager<T> ownerDataManager){}
    default void preGetPaginatedBatch(T ownerInstance, PageRequest request, DataManager<T> ownerDataManager){}
    default void postGetPaginatedBatch(Page<Map.Entry<TMapKey, TMapValue>> returnValue, PageRequest request, T ownerInstance, DataManager<T> ownerDataManager){}
    default void preFreeTextSearch(T ownerInstance, FreeTextSearchPageRequest request, DataManager<T> ownerDataManager){}
    default void postFreeTextSearch(Page<Map.Entry<TMapKey, TMapValue>> returnPage, FreeTextSearchPageRequest request, T ownerInstance, DataManager<T> ownerDataManager){}
    default Page<Map.Entry<TMapKey,TMapValue>> executeCustomFreeTextSearch(FreeTextSearchPageRequest input, T owner, DataManager<T> dataManager){
        return null;
    }
}
