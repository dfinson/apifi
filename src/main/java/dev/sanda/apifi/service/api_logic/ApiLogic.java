package dev.sanda.apifi.service.api_logic;

import dev.sanda.apifi.service.api_hooks.ApiHooks;
import dev.sanda.apifi.service.api_hooks.ElementCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.EntityCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.MapElementCollectionApiHooks;
import dev.sanda.apifi.utils.ConfigValues;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.persistence.Archivable;
import dev.sanda.datafi.service.DataManager;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Collection;
import java.util.List;
import java.util.Map;


@Service
@Scope("prototype")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public final class ApiLogic<T> {

    @NonNull
    private final ConfigValues configValues;
    @NonNull
    private final CrudService<T> crudService;
    @NonNull
    private final BatchedCrudService<T> batchedCrudService;
    @NonNull
    private final CollectionsCrudService<T> collectionsCrudService;
    @NonNull
    private final SubscriptionsLogicService<T> subscriptionsLogicService;

    public void init(DataManager<T> dataManager, ApiHooks<T> apiHooks){
        val datafiLoggingEnabled = configValues.getDatafiLoggingEnabled();
        this.subscriptionsLogicService.init(dataManager, apiHooks);
        this.crudService.init(dataManager, apiHooks, datafiLoggingEnabled, subscriptionsLogicService);
        this.batchedCrudService.init(dataManager, apiHooks, datafiLoggingEnabled, subscriptionsLogicService);
        this.collectionsCrudService.init(dataManager, apiHooks, datafiLoggingEnabled, subscriptionsLogicService);
    }

    public Page<T> getPaginatedBatch(dev.sanda.datafi.dto.PageRequest request) {
        return batchedCrudService.getPaginatedBatchImpl(request);
    }

    public Long getTotalNonArchivedCount(){
        return batchedCrudService.getTotalNonArchivedCountImpl();
    }

    public Long getTotalArchivedCount(){
        return batchedCrudService.getTotalArchivedCountImpl();
    }

    public Page<T> getArchivedPaginatedBatch(dev.sanda.datafi.dto.PageRequest request) {
        return batchedCrudService.getArchivedPaginatedBatchImpl(request);
    }

    public Page<T> freeTextSearch(FreeTextSearchPageRequest request){
        return batchedCrudService.freeTextSearchImpl(request);
    }

    public T getById(Object id) {
        return crudService.getByIdImpl(id);
    }

    public T apiFindByUnique(String fieldName, Object fieldValue) {
        return crudService.apiFindByUniqueImpl(fieldName, fieldValue);
    }

    public List<T> apiFindBy(String fieldName, Object argument) {
        return batchedCrudService.apiFindByImpl(fieldName, argument);
    }

    public List<T> apiFindAllBy(String fieldName, List<?> arguments) {
        return batchedCrudService.apiFindAllByImpl(fieldName, arguments);
    }

    public T create(T input) {
        return crudService.createImpl(input);
    }

    public T update(T input) {
        return crudService.updateImpl(input);
    }

    public T delete(T input) {
        return crudService.deleteImpl(input);
    }

    public <A extends Archivable> T archive(A input) {
        return crudService.archiveImpl(input);
    }

    public <A extends Archivable> T deArchive(A input) {
        return crudService.deArchiveImpl(input);
    }

    public <A extends Archivable> List<T> batchArchive(List<A> input) {
        return batchedCrudService.batchArchiveImpl(input);
    }

    public <A extends Archivable> List<T> batchDeArchive(List<A> input) {
        return batchedCrudService.batchDeArchiveImpl(input);
    }

    public List<T> getBatchByIds(List<?> ids) {
        return batchedCrudService.getBatchByIdsImpl(ids);
    }

    public List<T> batchCreate(List<T> input) {
        return batchedCrudService.batchCreateImpl(input);
    }

    public List<T> batchUpdate(List<T> input) {
        return batchedCrudService.batchUpdateImpl(input);
    }

    public List<T> batchDelete(List<T> input) {
        return batchedCrudService.batchDeleteImpl(input);
    }

    public <TCollection, E extends EntityCollectionApiHooks<TCollection, T>> List<List<TCollection>>
    getEntityCollection(List<T> input, String collectionFieldName, E collectionApiHooks, DataManager<TCollection> collectionDataManager) {
        return collectionsCrudService.getEntityCollectionImpl(input, collectionFieldName, collectionApiHooks, collectionDataManager);
    }

    public <TCollection> List<TCollection> getEmbedded(List<T> input, String fieldName, DataManager<TCollection> collectionDataManager) {
        return collectionsCrudService.getEmbeddedImpl(input, fieldName, collectionDataManager);
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    List<TCollection> updateEntityCollection(
            T owner,
            DataManager<TCollection> collectionDataManager,
            Collection<TCollection> toUpdate,
            E entityCollectionApiHooks,
            String collectionFieldName,
            SubscriptionsLogicService<TCollection> collectionSubscriptionsLogicService) {
        return collectionsCrudService.updateEntityCollectionImpl(
                        owner,
                        collectionDataManager,
                        toUpdate,
                        entityCollectionApiHooks,
                        collectionFieldName,
                        collectionSubscriptionsLogicService
                );
    }


    public  <TCollection, E extends ElementCollectionApiHooks<TCollection, T>> List<TCollection>
    addToElementCollection(T input, String fieldName, List<TCollection> toAdd, E elementCollectionApiHooks){
        return collectionsCrudService.addToElementCollectionImpl(input, fieldName, toAdd, elementCollectionApiHooks);
    }

    public  <TCollection, E extends ElementCollectionApiHooks<TCollection, T>> List<TCollection>
    removeFromElementCollection(T input, String fieldName, List<TCollection> toRemove, E elementCollectionApiHooks){
        return collectionsCrudService.removeFromElementCollectionImpl(input, fieldName, toRemove, elementCollectionApiHooks);
    }

    public  <TCollection, E extends ElementCollectionApiHooks<TCollection, T>>
    Page<TCollection> getPaginatedBatchInElementCollection(
            T owner,
            dev.sanda.datafi.dto.PageRequest input,
            String fieldName,
            E elementCollectionApiHooks) {
        return collectionsCrudService.getPaginatedBatchInElementCollectionImpl(owner, input, fieldName, elementCollectionApiHooks);
    }

    public  <TCollection, E extends ElementCollectionApiHooks<TCollection, T>>
    Page<TCollection> getFreeTextSearchPaginatedBatchInElementCollection(
            T owner,
            dev.sanda.datafi.dto.FreeTextSearchPageRequest input,
            String fieldName,
            E elementCollectionApiHooks) {
        return collectionsCrudService.getFreeTextSearchPaginatedBatchInElementCollectionImpl(owner, input, fieldName, elementCollectionApiHooks);
    }

    public  <TMapKey, TMapValue, E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>>
    Map<TMapKey, TMapValue>
    addToMapElementCollection(T input, String fieldName, Map<TMapKey, TMapValue> toPut, E apiHooks){
        return collectionsCrudService.addToMapElementCollectionImpl(input, fieldName, toPut, apiHooks);
    }

    public  <TMapKey, TMapValue, E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>>
    Map<TMapKey, TMapValue>
    removeFromMapElementCollection(T input, String fieldName, List<TMapKey> toRemove, E elementCollectionApiHooks){
        return collectionsCrudService.removeFromMapElementCollectionImpl(input, fieldName, toRemove, elementCollectionApiHooks);
    }

    public  <TMapKey, TMapValue, E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>>
    Page<Map.Entry<TMapKey, TMapValue>> getPaginatedBatchInMapElementCollection(
            T owner,
            dev.sanda.datafi.dto.PageRequest input,
            String fieldName,
            E apiHooks) {
        return collectionsCrudService.getPaginatedBatchInMapElementCollectionImpl(owner, input, fieldName, apiHooks);
    }

    public  <TMapKey, TMapValue, E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>>
    Page<Map.Entry<TMapKey, TMapValue>> getFreeTextSearchPaginatedBatchInMapElementCollection(
            T owner,
            dev.sanda.datafi.dto.FreeTextSearchPageRequest input,
            String fieldName,
            E apiHooks) {
        return collectionsCrudService.getFreeTextSearchPaginatedBatchInMapElementCollectionImpl(owner, input, fieldName, apiHooks);
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    List<TCollection> associateWithEntityCollection(
            T input,
            String fieldName,
            List<TCollection> toAssociate,
            DataManager<TCollection> collectionDataManager,
            E entityCollectionApiHooks,
            SubscriptionsLogicService<TCollection> collectionSubscriptionsLogicService) {
        return collectionsCrudService.associateWithEntityCollectionImpl(
                input,
                fieldName,
                toAssociate,
                collectionDataManager,
                entityCollectionApiHooks,
                collectionSubscriptionsLogicService
        );
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    Page<TCollection> paginatedFreeTextSearchInEntityCollection(
            T owner,
            dev.sanda.datafi.dto.FreeTextSearchPageRequest input,
            String fieldName,
            DataManager<TCollection> collectionDataManager,
            E entityCollectionApiHooks) {
        return collectionsCrudService
                .paginatedFreeTextSearchInEntityCollectionImpl(owner, input, fieldName, collectionDataManager, entityCollectionApiHooks);
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    Page<TCollection> getPaginatedBatchInEntityCollection(
            T owner,
            dev.sanda.datafi.dto.PageRequest input,
            String fieldName,
            DataManager<TCollection> collectionDataManager,
            E entityCollectionApiHooks) {
        return collectionsCrudService
                .getPaginatedBatchInEntityCollectionImpl(owner, input, fieldName, collectionDataManager, entityCollectionApiHooks);
    }

    public  <TCollection, E extends EntityCollectionApiHooks<TCollection, T>>
    List<TCollection>
    associatePreExistingWithEntityCollection(
            T input,
            String embeddedFieldName,
            List<TCollection> toAssociate,
            DataManager<TCollection> collectionDataManager,
            E entityCollectionApiHooks,
            SubscriptionsLogicService<TCollection> collectionSubscriptionsLogicService) {
        return collectionsCrudService
                .associatePreExistingWithEntityCollectionImpl(
                        input,
                        embeddedFieldName,
                        toAssociate,
                        collectionDataManager,
                        entityCollectionApiHooks,
                        collectionSubscriptionsLogicService
                );
    }

    public  <TCollection>
    List<TCollection>
    removeFromEntityCollection(
            T owner,
            String toRemoveFieldName,
            List<TCollection> toRemove,
            DataManager<TCollection> collectionDataManager,
            EntityCollectionApiHooks<TCollection, T> entityCollectionApiHooks) {
        return collectionsCrudService
                .removeFromEntityCollectionImpl(
                        owner,
                        toRemoveFieldName,
                        toRemove,
                        collectionDataManager,
                        entityCollectionApiHooks
                );
    }

    // subscriptions

    public Flux<List<T>> onCreateSubscription(FluxSink.OverflowStrategy backPressureStrategy){
        return subscriptionsLogicService.onCreateSubscription(backPressureStrategy);
    }

    public Flux<T> onUpdateSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressureStrategy){
        return subscriptionsLogicService.onUpdateSubscription(toObserve, backPressureStrategy);
    }

    public Flux<T> onDeleteSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressureStrategy){
        return subscriptionsLogicService.onDeleteSubscription(toObserve, backPressureStrategy);
    }

    public Flux<T> onArchiveSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressureStrategy){
        return subscriptionsLogicService.onArchiveSubscription(toObserve, backPressureStrategy);
    }

    public Flux<T> onDeArchiveSubscription(List<T> toObserve, FluxSink.OverflowStrategy backPressureStrategy){
        return subscriptionsLogicService.onDeArchiveSubscription(toObserve, backPressureStrategy);
    }

    // entity collection API subscriptions

    public <TCollection> Flux<List<TCollection>> onAssociateWithSubscription(T owner, String collectionFieldName, FluxSink.OverflowStrategy backPressureStrategy){
        return subscriptionsLogicService.onAssociateWithSubscription(owner, collectionFieldName, backPressureStrategy);
    }

    public <TCollection> Flux<List<TCollection>> onUpdateInSubscription(T owner, String collectionFieldName, FluxSink.OverflowStrategy backPressureStrategy){
        return subscriptionsLogicService.onUpdateInSubscription(owner, collectionFieldName, backPressureStrategy);
    }

    public <TCollection> Flux<List<TCollection>> onRemoveFromSubscription(T owner, String collectionFieldName, FluxSink.OverflowStrategy backPressureStrategy){
        return subscriptionsLogicService.onRemoveFromSubscription(owner, collectionFieldName, backPressureStrategy);
    }

}

