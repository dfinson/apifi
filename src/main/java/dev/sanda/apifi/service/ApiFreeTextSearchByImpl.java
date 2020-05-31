package dev.sanda.apifi.service;

import dev.sanda.apifi.annotations.WithApiFreeTextSearchByFields;
import dev.sanda.datafi.code_generator.FreeTextSearchMethodsFactory;
import dev.sanda.datafi.dto.FreeTextSearchPageRequest;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.reflection.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.val;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.sanda.apifi.utils.ApifiStaticUtils.isClazzArchivable;


public class ApiFreeTextSearchByImpl {
    
    private static final Map<Class<?>, String> contentQueryCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> countQueryCache = new ConcurrentHashMap<>();

    private static <T> String contentQueryOf(Class<T> clazz, String clazzSimpleName, boolean isArchivable, String sortBy){
        if(contentQueryCache.containsKey(clazz))
            return contentQueryCache.get(clazz);
        val searchFieldNames = Arrays.asList(clazz.getAnnotation(WithApiFreeTextSearchByFields.class).value());
        val query = FreeTextSearchMethodsFactory
                .freeTextSearchQuery(clazzSimpleName, searchFieldNames, isArchivable, false) +
                " ORDER BY " + sortBy;
        contentQueryCache.putIfAbsent(clazz, query);
        return query;
    }

    private static <T> String countQueryOf(Class<T> clazz, String clazzSimpleName, boolean isArchivable) {
        if(countQueryCache.containsKey(clazz))
            return countQueryCache.get(clazz);
        val searchFieldNames = Arrays.asList(clazz.getAnnotation(WithApiFreeTextSearchByFields.class).value());
        val query = FreeTextSearchMethodsFactory
                .freeTextSearchQuery(clazzSimpleName, searchFieldNames, isArchivable, true);
        countQueryCache.putIfAbsent(clazz, query);
        return query;
    }

    public static <T> Page<T> freeTextSearch(
            DataManager<T> dataManager,
            FreeTextSearchPageRequest request,
            ApiHooks<T> apiHooks,
            ReflectionCache reflectionCache) {

        if(apiHooks != null)
            apiHooks.preFreeTextSearch(request.getSearchTerm(), dataManager);

        val isClazzArchivable = isClazzArchivable(dataManager.getClazz(), reflectionCache);
        val contentQueryString =
                contentQueryOf(dataManager.getClazz(), dataManager.getClazzSimpleName(), isClazzArchivable, request.getSortBy());
        val content = dataManager
                .entityManager()
                .createQuery(contentQueryString)
                .setParameter("searchTerm", request.getSearchTerm())
                .setFirstResult(request.getPageNumber() * request.getPageSize())
                .setMaxResults(request.getPageSize())
                .getResultList();

        val countQueryString = countQueryOf(dataManager.getClazz(), dataManager.getClazzSimpleName(), isClazzArchivable);
        val totalRecords = (long)dataManager.entityManager()
                .createQuery(countQueryString)
                .setParameter("searchTerm", request.getSearchTerm())
                .getSingleResult();
        val totalPages = Math.ceil((double) totalRecords / request.getPageSize());
        val returnValue = new dev.sanda.datafi.dto.Page<T>();

        returnValue.setContent(content);
        returnValue.setTotalPagesCount((long) totalPages);
        returnValue.setTotalItemsCount(totalRecords);

        if(apiHooks != null)
            apiHooks.postFreeTextSearch(request.getSearchTerm(), returnValue.getContent(), dataManager);

        return returnValue;
    }
}
