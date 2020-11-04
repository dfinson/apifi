package dev.sanda.apifi.service;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.dto.PageRequest;
import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public interface ElementCollectionApiHooks<TCollection, T> {
    default void preAdd(T ownerInstance, String fieldName, List<TCollection> toAddInput, DataManager<T> ownerDataManager){}
    default void postAdd(T ownerInstance, String fieldName, List<TCollection> added, DataManager<T> ownerDataManager){}
    default void preRemove(List<TCollection> toRemove, T input, DataManager<T> dataManager){}
    default void postRemove(List<TCollection> toRemove, T input, DataManager<T> dataManager){}
    default void preGetPaginatedBatch(T owner, PageRequest input, String fieldName, DataManager<T> dataManager){}
    default void postGetPaginatedBatch(Page<TCollection> returnPage, String fieldName, T ownerInstance, PageRequest request, DataManager<T> ownerDataManager){}
    default void preFreeTextSearch(T owner, FreeTextSearchPageRequest request, String fieldName, DataManager<T> ownerDataManager){}
    default void postFreeTextSearch(FreeTextSearchPageRequest request, Page<TCollection> returnPage, T owner, String fieldName, DataManager<T> ownerDataManager){}
    default Page<TCollection> executeCustomFreeTextSearch(FreeTextSearchPageRequest input, T owner, DataManager<T> dataManager){
        return null;
    }
}
