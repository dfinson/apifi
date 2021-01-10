package dev.sanda.apifi.service.api_logic;

import dev.sanda.apifi.service.ApiFreeTextSearchByImpl;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.dto.PageRequest;
import dev.sanda.datafi.persistence.Archivable;
import lombok.val;
import org.springframework.context.annotation.Scope;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.isClazzArchivable;
import static dev.sanda.datafi.DatafiStaticUtils.*;

@Service
@Scope("prototype")
public class BatchedCrudService<T> extends BaseCrudService<T> {

    public Long getTotalNonArchivedCountImpl(){
        if (!reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).isArchivable())
            return dataManager.count();
        else return dataManager.count(((Specification<T>) (root, query, cb) -> cb.isFalse(root.get("isArchived"))));
    }
    public Long getTotalArchivedCountImpl(){
        if(reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).isArchivable())
            return dataManager.count(((Specification<T>) (root, query, cb) -> cb.isTrue(root.get("isArchived"))));
        else
            throw new RuntimeException("Entity " + dataManager.getClazzSimpleName() + " does not implement Archivable");
    }
    public Page<T> getPaginatedBatchImpl(PageRequest request) {
        validateSortByIfNonNull(dataManager.getClazz(), request.getSortBy(), reflectionCache);
        if(apiHooks != null) apiHooks.preGetPaginatedBatch(request, dataManager);
        org.springframework.data.domain.Page result;
        if(request.getFetchAll()) request.setPageNumber(0);
        val pageRequest = generatePageRequest(request, getTotalNonArchivedCountImpl());
        if(isClazzArchivable(dataManager.getClazz(), reflectionCache))
            result = dataManager.findAll((Specification<T>)
                    (root, query, cb) -> cb.isFalse(root.get("isArchived")), pageRequest);
        else result = dataManager.findAll(pageRequest);
        val pageResult = new Page<>(result);
        if(apiHooks != null) apiHooks.postGetPaginatedBatch(request, pageResult, dataManager);
        logInfo("getPaginatedBatch: Got {} {}", result.getContent().size(), toPlural(dataManager.getClazzSimpleName()));
        return pageResult;
    }
    public Page<T> getArchivedPaginatedBatchImpl(PageRequest request) {
        validateSortByIfNonNull(dataManager.getClazz(), request.getSortBy(), reflectionCache);
        if(request.getFetchAll()) request.setPageNumber(0);
        if(apiHooks != null)
            apiHooks.preGetArchivedPaginatedBatch(request, dataManager);
        org.springframework.data.domain.Page<T> result;
        val pageRequest = generatePageRequest(request, getTotalArchivedCountImpl());
        if(isClazzArchivable(dataManager.getClazz(), reflectionCache))
            result = dataManager.findAll((Specification<T>)
                    (root, query, cb) -> cb.isTrue(root.get("isArchived")), pageRequest);
        else result = dataManager.findAll(pageRequest);
        val pageResult = new Page<>(result);
        if(apiHooks != null)
            apiHooks.postGetArchivedPaginatedBatch(request, pageResult, dataManager);
        logInfo("getArchivedPaginatedBatch: Got {} {}", result.getContent().size(), toPlural(dataManager.getClazzSimpleName()));
        return pageResult;
    }

    public Page<T> freeTextSearchImpl(FreeTextSearchPageRequest request) {
        try{

            String clazzSimpleNamePlural = toPlural(dataManager.getClazzSimpleName());

            if(request.getSearchTerm() == null || request.getSearchTerm().equals(""))
                throw new IllegalArgumentException("Illegal attempt to search for " + clazzSimpleNamePlural + " with null or blank string");
            validateSortByIfNonNull(dataManager.getClazz(), request.getSortBy(), reflectionCache);

            if(request.getFetchAll())
                request.setPageNumber(0);
            Page<T> result;

            if(apiHooks != null && (result = apiHooks.executeCustomFreeTextSearch(request, dataManager)) != null)
                return result;

            result = ApiFreeTextSearchByImpl.freeTextSearch(dataManager, request, apiHooks, reflectionCache);
            logInfo("freeTextSearchBy(String searchTerm)", "found {} {} by searchTerm '{}'",
                    result.getTotalItemsCount(), toPlural(dataManager.getClazzSimpleName()), request.getSearchTerm());
            return result;
        }catch (Exception e){
            logError("freeTextSearchBy(String searchTerm, int offset, int limit, String sortBy, Sort.Direction sortDirection)", e.toString());
            throw new RuntimeException(e);
        }
    }

    public List<T> apiFindByImpl(String fieldName, Object argument) {
        if(apiHooks != null) apiHooks.preApiFindBy(fieldName, argument, dataManager);
        List<T> result = dataManager.findBy(fieldName, argument);
        if(apiHooks != null) apiHooks.postApiFindBy(fieldName, argument, result, dataManager);
        logInfo("apiFindBy: found {} {} by {} == {}",
                result.size(),
                dataManager.getClazzSimpleName(),
                fieldName,
                argument);
        return result;
    }

    public List<T> apiFindAllByImpl(String fieldName, List<?> arguments) {
        if(apiHooks != null) apiHooks.preApiFindAllBy(fieldName, arguments, dataManager);
        List<T> result = dataManager.findAllBy(fieldName, arguments.toArray());
        if(apiHooks != null) apiHooks.postApiFindAllBy(fieldName, arguments, result, dataManager);
        logInfo("apiFindAllBy: found {} {} by [{}]",
                result.size(),
                dataManager.getClazzSimpleName(),
                arguments.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public <A extends Archivable> List<T> batchArchiveImpl(List<A> input) {
        val ids = getIdList(input, reflectionCache);
        val entitiesToArchive = (List<A>) getBatchByIdsImpl(ids);
        if(apiHooks != null) apiHooks.preBatchArchive((List<T>) input, (List<T>) entitiesToArchive, dataManager);
        entitiesToArchive.forEach(entity -> entity.setIsArchived(true));
        List<T> result = dataManager.saveAll((List<T>)entitiesToArchive);
        if(apiHooks != null) apiHooks.postBatchArchive((List<T>)input, result, dataManager);
        logInfo("batchArchive: Batch archived {} with ids: [{}]",
                toPlural(dataManager.getClazzSimpleName()),
                ids.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public <A extends Archivable> List<T> batchDeArchiveImpl(List<A> input) {
        val ids = getIdList(input, reflectionCache);
        val entitiesToDeArchive = (List<A>) getBatchByIdsImpl(ids);
        if(apiHooks != null) apiHooks.preBatchDeArchive((List<T>)input, (List<T>) entitiesToDeArchive, dataManager);
        entitiesToDeArchive.forEach(entity -> entity.setIsArchived(false));
        List<T> result = dataManager.saveAll((List<T>)entitiesToDeArchive);
        if(apiHooks != null) apiHooks.postBatchDeArchive((List<T>)input, result, dataManager);
        logInfo("batchDeArchive: Batch de-archived {} with ids [{}]",
                toPlural(dataManager.getClazzSimpleName()),
                ids.stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public List<T> getBatchByIdsImpl(List<?> ids) {
        if(apiHooks != null)
            apiHooks.preGetBatchByIds(ids, dataManager);
        List<T> result = dataManager.findAllById(ids);
        if(result.size() != ids.size())
            throw new IllegalArgumentException(
                    "Could not find " + ids.size() + " " +
                            toPlural(entityName) + " by ids: [" +
                            ids.stream().map(Object::toString).collect(Collectors.joining(", ")) +
                            "]");
        if(apiHooks != null)
            apiHooks.postGetBatchByIds(result, dataManager);
        return result;
    }

    public List<T> batchCreateImpl(List<T> input) {
        if(apiHooks != null) apiHooks.preBatchCreate(input, dataManager);
        val result = dataManager.saveAll(input);
        if(apiHooks != null) apiHooks.postBatchCreate(input, result, dataManager);
        logInfo("batchCreate: created {} new {} with ids [{}]",
                result.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(result, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public List<T> batchUpdateImpl(List<T> input) {
        List<T> toUpdate = getBatchByIdsImpl(getIdList(input, reflectionCache));
        if(apiHooks != null) apiHooks.preBatchUpdate(input, toUpdate, dataManager);
        List<T> result = dataManager.cascadeUpdateCollection(toUpdate, input);
        if(apiHooks != null) apiHooks.postBatchUpdate(input, result, dataManager);
        logInfo("batchUpdate: Updated {} {} with ids [{}]",
                result.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(result, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return result;
    }

    public List<T> batchDeleteImpl(List<T> input) {
        List<T> toDelete = getBatchByIdsImpl(getIdList(input, reflectionCache));
        if(apiHooks != null) apiHooks.preDeleteEntities(input, toDelete, dataManager);
        dataManager.deleteInBatch(input);
        if(apiHooks != null) apiHooks.postDeleteEntities(input, toDelete, dataManager);
        logInfo("batchDelete: Deleted {} {} with ids [{}]",
                toDelete.size(),
                toPlural(dataManager.getClazzSimpleName()),
                getIdList(toDelete, reflectionCache).stream().map(Object::toString).collect(Collectors.joining(", ")));
        return toDelete;
    }
}
