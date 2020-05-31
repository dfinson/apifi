package dev.sanda.apifi.service;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.service.DataManager;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public interface ElementCollectionApiHooks<TCollection, T> {
    default void preAdd(List<TCollection> toAdd, T input, DataManager<T> dataManager){}
    default void postAdd(List<TCollection> toAdd, T input, DataManager<T> dataManager){}
    default void preRemove(List<TCollection> toRemove, T input, DataManager<T> dataManager){}
    default void postRemove(List<TCollection> toRemove, T input, DataManager<T> dataManager){}
    default void preGetPaginatedBatch(T owner, DataManager<T> dataManager){}
    default void postGetPaginatedBatch(Page<TCollection> returnValue, T owner, DataManager<T> dataManager){}
    default void preFreeTextSearch(T owner, DataManager<T> dataManager){}
    default void postFreeTextSearch(Page<TCollection> returnValue, T owner, DataManager<T> dataManager){}
}
