package dev.sanda.apifi.service.api_hooks;

import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.dto.PageRequest;
import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public interface EntityCollectionApiHooks<TCollection, T>{
    default void preFetch(T ownerInstance, String collectionFieldName, DataManager<T> ownerDataManager, DataManager<TCollection> collectionDataManager){}
    default void postFetch(Collection<TCollection> fetchedItems, T ownerInstance, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
    default void preGetPaginatedBatch(T ownerInstance, PageRequest requestInput, DataManager<T> dataManager) {}
    default void postGetPaginatedBatch(Page<TCollection> resultPage, T ownerInstance, PageRequest inputRequest, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
    default Page<TCollection> executeCustomFreeTextSearch(FreeTextSearchPageRequest input, T owner, DataManager<T> dataManager, DataManager<TCollection> collectionDataManager){
        return null;
    }
    default void preFreeTextSearch(T ownerInstance, String searchTerm, DataManager<T> ownerDataManager, DataManager<TCollection> collectionDataManager) {}
    default void postFreeTextSearch(Page<TCollection> resultPage, FreeTextSearchPageRequest inputRequest, T ownerInstance, String searchTerm, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
    default void preRemove(Collection<TCollection> toRemoveInput, T ownerInstance, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
    default void postRemove(Collection<TCollection> toRemoveInput, T ownerInstance, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
    default void preAssociate(Collection<TCollection> toAssociateInput, T ownerInstance, String fieldName, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
    default void postAssociate(Collection<TCollection> toAssociateInput, Collection<TCollection> newlyAssociated,T ownerInstance, String fieldName, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
    default void preUpdate(Collection<TCollection> toUpdateInput, T ownerInstance, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
    default void postUpdate(Collection<TCollection> toUpdateInput, Collection<TCollection> updated, T ownerInstance, DataManager<TCollection> collectionDataManager, DataManager<T> ownerDataManager){}
}
