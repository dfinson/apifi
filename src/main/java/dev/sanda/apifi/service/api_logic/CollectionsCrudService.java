package dev.sanda.apifi.service.api_logic;

import dev.sanda.apifi.annotations.EntityCollectionApi;
import dev.sanda.apifi.dto.KeyAndValue;
import dev.sanda.apifi.service.api_hooks.ElementCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.EntityCollectionApiHooks;
import dev.sanda.apifi.service.api_hooks.MapElementCollectionApiHooks;
import dev.sanda.apifi.service.graphql_config.GraphQLSubscriptionSupport;
import dev.sanda.apifi.service.graphql_subcriptions.pubsub.AsyncExecutorService;
import dev.sanda.datafi.dto.Page;
import dev.sanda.datafi.reflection.runtime_services.CollectionInstantiator;
import dev.sanda.datafi.reflection.runtime_services.CollectionsTypeResolver;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.val;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static dev.sanda.apifi.utils.ApifiStaticUtils.isClazzArchivable;
import static dev.sanda.datafi.DatafiStaticUtils.*;

@Component
@Scope("prototype")
public class CollectionsCrudService<T> extends BaseCrudService<T> {

  private final CollectionInstantiator collectionInstantiator;
  private final CollectionsTypeResolver collectionsTypeResolver;

  @Autowired
  public CollectionsCrudService(
    ReflectionCache reflectionCache,
    AsyncExecutorService asyncExecutorService,
    GraphQLSubscriptionSupport graphQLSubscriptionSupport,
    CollectionInstantiator collectionInstantiator,
    CollectionsTypeResolver collectionsTypeResolver
  ) {
    super(reflectionCache, asyncExecutorService, graphQLSubscriptionSupport);
    this.collectionInstantiator = collectionInstantiator;
    this.collectionsTypeResolver = collectionsTypeResolver;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public <TCollection, E extends EntityCollectionApiHooks<TCollection, T>> List<List<TCollection>> getEntityCollectionImpl(
          List<T> input,
          String collectionFieldName,
          E collectionApiHooks,
          DataManager<TCollection> collectionDataManager
  ) {
    if (collectionApiHooks != null) input.forEach(
            owner ->
                    collectionApiHooks.preFetch(
                            owner,
                            collectionFieldName,
                            dataManager,
                            collectionDataManager
                    )
    );
    val rawQueryResults = (List) dataManager
            .entityManager()
            .createQuery(
                    String.format(
                            "SELECT owner, entityCollection " +
                                    "FROM %s owner " +
                                    "JOIN owner.%s entityCollection " +
                                    "WHERE owner.%s IN :ownerIds",
                            dataManager.getClazzSimpleName(),
                            collectionFieldName,
                            reflectionCache
                                    .getEntitiesCache()
                                    .get(dataManager.getClazzSimpleName())
                                    .getIdField()
                                    .getName()
                    )
            )
            .setParameter("ownerIds", getIdList(input, reflectionCache))
            .getResultList();
    val owners2EntityCollectionsMap = new HashMap<T, List<TCollection>>();
    rawQueryResults.forEach(
            item -> {
              val ownerAndCollectionItem = (Object[]) item;
              val owner = (T) ownerAndCollectionItem[0];
              val entityCollectionItem = (TCollection) ownerAndCollectionItem[1];
              if (
                      !owners2EntityCollectionsMap.containsKey(owner)
              ) owners2EntityCollectionsMap.put(
                      owner,
                      new ArrayList<>(Collections.singletonList(entityCollectionItem))
              ); else owners2EntityCollectionsMap
                      .get(owner)
                      .add(entityCollectionItem);
            }
    );
    if (collectionApiHooks != null) owners2EntityCollectionsMap
            .keySet()
            .forEach(
                    owner ->
                            collectionApiHooks.postFetch(
                                    owners2EntityCollectionsMap.get(owner),
                                    owner,
                                    collectionDataManager,
                                    dataManager
                            )
            );
    return input
            .stream()
            .map(
                    key -> {
                      final List<TCollection> collection = owners2EntityCollectionsMap.get(
                              key
                      );
                      return collection != null ? collection : new ArrayList<TCollection>();
                    }
            )
            .collect(Collectors.toList());
  }


  public <TCollection> List<TCollection> getForeignKeyEntityImpl(
          List<T> input,
          String fieldName,
          DataManager<TCollection> collectionDataManager
  ) {
    val queryString = String.format(
            "SELECT new dev.sanda.apifi.dto.KeyAndValue(owner, owner.%s) FROM %s owner WHERE owner.%s IN :ids " +
                    "ORDER BY owner.%s",
            fieldName,
            entityName,
            idFieldName,
            idFieldName
    );
    @SuppressWarnings("unchecked")
    final Map<T, TCollection> resultMap = (Map<T, TCollection>) dataManager
            .entityManager()
            .createQuery(queryString)
            .setParameter("ids", getIdList(input, reflectionCache))
            .getResultStream()
            .collect(
                    Collectors.toMap(
                            KeyAndValue::getKey,
                            KeyAndValue::getValue,
                            (first, second) -> first
                    )
            );
    return input.stream().map(resultMap::get).collect(Collectors.toList());
  }


  public <
    TCollection, E extends ElementCollectionApiHooks<TCollection, T>
  > List<TCollection> addToElementCollectionImpl(
    T input,
    String fieldName,
    List<TCollection> toAdd,
    E elementCollectionApiHooks
  ) {
    T ownerLoaded = dataManager
      .findById(getId(input, reflectionCache))
      .orElse(null);
    if (ownerLoaded == null) throw_entityNotFound(input, reflectionCache);
    input = ownerLoaded;
    if (elementCollectionApiHooks != null) elementCollectionApiHooks.preAdd(
      input,
      fieldName,
      toAdd,
      dataManager
    );

    val cachedElementCollectionField = reflectionCache
      .getEntitiesCache()
      .get(dataManager.getClazzSimpleName())
      .getElementCollections()
      .get(fieldName);

    if (cachedElementCollectionField == null) throw new RuntimeException(
      String.format(
        "No element collection \"%s\" found in type \"%s\"",
        fieldName,
        dataManager.getClazzSimpleName()
      )
    );
    final Field collectionField = cachedElementCollectionField.getField();
    try {
      if (collectionField.get(input) == null) {
        collectionField.setAccessible(true);
        collectionField.set(input, initCollection(fieldName));
      }
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
    cachedElementCollectionField.addAll(input, toAdd);

    if (elementCollectionApiHooks != null) elementCollectionApiHooks.postAdd(
      input,
      fieldName,
      toAdd,
      dataManager
    );
    return toAdd;
  }

  public <
    TCollection, E extends ElementCollectionApiHooks<TCollection, T>
  > List<TCollection> removeFromElementCollectionImpl(
    T input,
    String fieldName,
    List<TCollection> toRemove,
    E elementCollectionApiHooks
  ) {
    T temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
    if (temp == null) throw_entityNotFound(input, reflectionCache);
    input = temp;
    if (elementCollectionApiHooks != null) elementCollectionApiHooks.preRemove(
      toRemove,
      input,
      dataManager
    );

    val cachedElementCollectionField = reflectionCache
      .getEntitiesCache()
      .get(dataManager.getClazzSimpleName())
      .getElementCollections()
      .get(fieldName);

    if (cachedElementCollectionField == null) throw new RuntimeException(
      String.format(
        "No element collection \"%s\" found in type \"%s\"",
        fieldName,
        dataManager.getClazzSimpleName()
      )
    );

    cachedElementCollectionField.removeAll(input, toRemove);

    if (elementCollectionApiHooks != null) elementCollectionApiHooks.postRemove(
      toRemove,
      input,
      dataManager
    );
    return toRemove;
  }

  public <
    TCollection, E extends ElementCollectionApiHooks<TCollection, T>
  > Page<TCollection> getPaginatedBatchInElementCollectionImpl(
    T owner,
    dev.sanda.datafi.dto.PageRequest input,
    String fieldName,
    E elementCollectionApiHooks
  ) {
    T temp = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
    if (temp == null) throw_entityNotFound(input, reflectionCache);
    owner = temp;
    if (
      elementCollectionApiHooks != null
    ) elementCollectionApiHooks.preGetPaginatedBatch(
      owner,
      input,
      fieldName,
      dataManager
    );
    Page<TCollection> returnValue = new Page<>();
    val contentQueryString = String.format(
      "SELECT collection FROM %s owner " +
      "JOIN owner.%s collection " +
      "WHERE owner.%s = :ownerId",
      dataManager.getClazzSimpleName(),
      fieldName,
      idFieldName
    );
    val countQueryString = String.format(
      "SELECT COUNT(collection) FROM %s owner " +
      "JOIN owner.%s collection " +
      "WHERE owner.%s = :ownerId",
      dataManager.getClazzSimpleName(),
      fieldName,
      idFieldName
    );
    Object ownerId = getId(temp, reflectionCache);
    val content = dataManager
      .entityManager()
      .createQuery(contentQueryString)
      .setParameter("ownerId", ownerId)
      .setFirstResult(input.getPageNumber() * input.getPageSize())
      .setMaxResults(input.getPageSize())
      .getResultList();
    val totalRecords = (long) dataManager
      .entityManager()
      .createQuery(countQueryString)
      .setParameter("ownerId", ownerId)
      .getSingleResult();
    val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
    returnValue.setContent(content);
    returnValue.setTotalPagesCount((long) totalPages);
    returnValue.setTotalItemsCount(totalRecords);
    returnValue.setPageNumber(input.getPageNumber());
    if (
      elementCollectionApiHooks != null
    ) elementCollectionApiHooks.postGetPaginatedBatch(
      returnValue,
      fieldName,
      owner,
      input,
      dataManager
    );
    return returnValue;
  }

  public <
    TCollection, E extends ElementCollectionApiHooks<TCollection, T>
  > Page<TCollection> getFreeTextSearchPaginatedBatchInElementCollectionImpl(
    T owner,
    dev.sanda.datafi.dto.FreeTextSearchPageRequest input,
    String fieldName,
    E elementCollectionApiHooks
  ) {
    owner = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
    if (owner == null) throw_entityNotFound(input, reflectionCache);

    Page<TCollection> returnValue;
    if (input.getFetchAll()) input.setPageNumber(0);
    if (
      elementCollectionApiHooks != null &&
      (
        returnValue =
          elementCollectionApiHooks.executeCustomFreeTextSearch(
            input,
            owner,
            dataManager
          )
      ) !=
      null
    ) return returnValue;

    if (
      elementCollectionApiHooks != null
    ) elementCollectionApiHooks.preFreeTextSearch(
      owner,
      input,
      fieldName,
      dataManager
    );

    returnValue = new Page<>();
    val contentQueryString = String.format(
      "SELECT collection FROM %s owner " +
      "JOIN owner.%s collection " +
      "WHERE owner.%s = :ownerId AND " +
      "LOWER(collection) LIKE LOWER(CONCAT('%%', :searchTerm, '%%')) " +
      "ORDER BY collection %s",
      dataManager.getClazzSimpleName(),
      fieldName,
      idFieldName,
      input.getSortDirection()
    );
    val countQueryString = String.format(
      "SELECT COUNT(collection) FROM %s owner " +
      "JOIN owner.%s collection " +
      "WHERE owner.%s = :ownerId AND " +
      "LOWER(collection) LIKE LOWER(CONCAT('%%', :searchTerm, '%%'))",
      dataManager.getClazzSimpleName(),
      fieldName,
      idFieldName
    );
    Object ownerId = getId(owner, reflectionCache);
    val content = dataManager
      .entityManager()
      .createQuery(contentQueryString)
      .setParameter("ownerId", ownerId)
      .setParameter("searchTerm", input.getSearchTerm())
      .setFirstResult(input.getPageNumber() * input.getPageSize())
      .setMaxResults(input.getPageSize())
      .getResultList();
    val totalRecords = (long) dataManager
      .entityManager()
      .createQuery(countQueryString)
      .setParameter("ownerId", ownerId)
      .setParameter("searchTerm", input.getSearchTerm())
      .getSingleResult();
    val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
    returnValue.setContent(content);
    returnValue.setTotalPagesCount((long) totalPages);
    returnValue.setTotalItemsCount(totalRecords);
    returnValue.setPageNumber(input.getPageNumber());
    if (
      elementCollectionApiHooks != null
    ) elementCollectionApiHooks.postFreeTextSearch(
      input,
      returnValue,
      owner,
      fieldName,
      dataManager
    );
    return returnValue;
  }

  public <
    TMapKey,
    TMapValue,
    E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>
  > Map<TMapKey, TMapValue> addToMapElementCollectionImpl(
    T input,
    String fieldName,
    Map<TMapKey, TMapValue> toPut,
    E apiHooks
  ) {
    T temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
    if (temp == null) throw_entityNotFound(input, reflectionCache);
    input = temp;
    if (apiHooks != null) apiHooks.prePut(toPut, fieldName, input, dataManager);

    val cachedElementCollectionField = reflectionCache
      .getEntitiesCache()
      .get(dataManager.getClazzSimpleName())
      .getMapElementCollections()
      .get(fieldName);

    if (cachedElementCollectionField == null) throw new RuntimeException(
      String.format(
        "No map element collection \"%s\" found in type \"%s\"",
        fieldName,
        dataManager.getClazzSimpleName()
      )
    );

    cachedElementCollectionField.putAll(input, toPut);

    if (apiHooks != null) apiHooks.postPut(
      toPut,
      fieldName,
      input,
      dataManager
    );
    dataManager.save(input);
    return toPut;
  }

  public <
    TMapKey,
    TMapValue,
    E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>
  > Map<TMapKey, TMapValue> removeFromMapElementCollectionImpl(
    T input,
    String fieldName,
    List<TMapKey> toRemove,
    E elementCollectionApiHooks
  ) {
    T temp = dataManager.findById(getId(input, reflectionCache)).orElse(null);
    if (temp == null) throw_entityNotFound(input, reflectionCache);
    input = temp;
    if (elementCollectionApiHooks != null) elementCollectionApiHooks.preRemove(
      toRemove,
      fieldName,
      input,
      dataManager
    );

    val cachedElementCollectionField = reflectionCache
      .getEntitiesCache()
      .get(dataManager.getClazzSimpleName())
      .getMapElementCollections()
      .get(fieldName);

    if (cachedElementCollectionField == null) throw new RuntimeException(
      String.format(
        "No map element collection \"%s\" found in type \"%s\"",
        fieldName,
        dataManager.getClazzSimpleName()
      )
    );
    Map<TMapKey, TMapValue> removed = cachedElementCollectionField.getAllByKey(
      input,
      toRemove
    );
    cachedElementCollectionField.removeAll(input, toRemove);
    if (elementCollectionApiHooks != null) elementCollectionApiHooks.postRemove(
      removed,
      fieldName,
      input,
      dataManager
    );
    dataManager.save(input);
    return removed;
  }

  public <
    TMapKey,
    TMapValue,
    E extends MapElementCollectionApiHooks<TMapKey, TMapValue, T>
  > Page<Map.Entry<TMapKey, TMapValue>> getPaginatedBatchInMapElementCollectionImpl(
    T owner,
    dev.sanda.datafi.dto.PageRequest input,
    String fieldName,
    E apiHooks
  ) {
    T temp = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
    if (temp == null) throw_entityNotFound(input, reflectionCache);
    owner = temp;
    if (apiHooks != null) apiHooks.preGetPaginatedBatch(
      owner,
      input,
      dataManager
    );
    Page<Map.Entry<TMapKey, TMapValue>> returnValue = new Page<>();
    val contentQueryString = String.format(
      "SELECT new dev.sanda.apifi.dto.KeyAndValue(KEY(map), VALUE(map)) " +
      "FROM %s owner " +
      "JOIN owner.%s map " +
      "WHERE owner.%s = :ownerId " +
      "ORDER BY KEY(map) %s",
      dataManager.getClazzSimpleName(),
      fieldName,
      idFieldName,
      input.getSortDirection()
    );
    val countQueryString = String.format(
      "SELECT COUNT(map) FROM %s owner " +
      "JOIN owner.%s map " +
      "WHERE owner.%s = :ownerId",
      dataManager.getClazzSimpleName(),
      fieldName,
      idFieldName
    );
    Object ownerId = getId(temp, reflectionCache);

    List<Map.Entry<TMapKey, TMapValue>> content =
      (List<Map.Entry<TMapKey, TMapValue>>) dataManager
        .entityManager()
        .createQuery(contentQueryString)
        .setParameter("ownerId", ownerId)
        .setFirstResult(input.getPageNumber() * input.getPageSize())
        .setMaxResults(input.getPageSize())
        .getResultStream()
        .map(entry -> ((KeyAndValue) entry).toEntry())
        .collect(Collectors.toList());
    val totalRecords = (long) dataManager
      .entityManager()
      .createQuery(countQueryString)
      .setParameter("ownerId", ownerId)
      .getSingleResult();
    val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
    returnValue.setContent(content);
    returnValue.setTotalPagesCount((long) totalPages);
    returnValue.setTotalItemsCount(totalRecords);
    returnValue.setPageNumber(input.getPageNumber());
    if (apiHooks != null) apiHooks.postGetPaginatedBatch(
      returnValue,
      input,
      owner,
      dataManager
    );
    return returnValue;
  }

  public <
    TCollection, E extends EntityCollectionApiHooks<TCollection, T>
  > List<TCollection> associateWithEntityCollectionImpl(
    T input,
    String fieldName,
    List<TCollection> toAssociate,
    DataManager<TCollection> collectionDataManager,
    E entityCollectionApiHooks,
    SubscriptionsLogicService<TCollection> collectionSubscriptionsLogicService
  ) {
    //get collection owner
    T ownerLoaded = dataManager
      .findById(getId(input, reflectionCache))
      .orElse(null);
    if (ownerLoaded == null) throw_entityNotFound(input, reflectionCache);
    input = ownerLoaded;
    if (entityCollectionApiHooks != null) entityCollectionApiHooks.preAssociate(
      toAssociate,
      input,
      fieldName,
      collectionDataManager,
      dataManager
    );
    assert input != null;
    Collection<TCollection> existingCollection = getEntityCollectionFrom(
      input,
      fieldName
    );
    if (existingCollection == null) existingCollection =
      initEntityCollection(fieldName, collectionDataManager);
    Set<Object> preExistingInstancesIds = toAssociate
      .stream()
      .filter(obj -> getId(obj, reflectionCache) != null)
      .map(obj -> getId(obj, reflectionCache))
      .collect(Collectors.toSet());
    if (!preExistingInstancesIds.isEmpty()) {
      val existingInstancesById = collectionDataManager
        .findAllById(preExistingInstancesIds)
        .stream()
        .collect(
          Collectors.toMap(
            item -> getId(item, reflectionCache),
            Function.identity()
          )
        );
      if (existingInstancesById.size() != preExistingInstancesIds.size()) {
        throw new IllegalArgumentException(
          "At least one inputted element contains a non-existent id"
        );
      }
      toAssociate =
        toAssociate
          .stream()
          .map(item -> {
            val id = getId(item, reflectionCache);
            val preExisting = id != null ? existingInstancesById.get(id) : null;
            if (preExisting != null) return preExisting; else return item;
          })
          .collect(Collectors.toList());
    }
    existingCollection.addAll(toAssociate);
    //save owner
    try {
      reflectionCache
        .getEntitiesCache()
        .get(input.getClass().getSimpleName())
        .getFields()
        .get(fieldName)
        .getField()
        .set(input, existingCollection);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    final T t = input;

    Collection<TCollection> added = getEntityCollectionFrom(t, fieldName);
    val newlyAssociated = collectionDataManager.saveAll(toAssociate);
    List<TCollection> result = collectionDataManager.saveAll(
      extractFromCollection(added, newlyAssociated)
    );
    setBackpointersIfRelationshipIsBiDirectional(
      ownerLoaded,
      result,
      fieldName
    );
    dataManager.save(t);
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.postAssociate(
      toAssociate,
      result,
      input,
      fieldName,
      collectionDataManager,
      dataManager
    );
    if (
      preExistingInstancesIds.size() < result.size()
    ) fireSubscriptionEvent(() ->
      collectionSubscriptionsLogicService.onCreateEvent(
        result
          .stream()
          .filter(obj ->
            !preExistingInstancesIds.contains(getId(obj, reflectionCache))
          )
          .collect(Collectors.toList())
      )
    );
    fireSubscriptionEvent(() ->
      subscriptionsLogicService.onAssociateWithEvent(
        ownerLoaded,
        fieldName,
        result,
        collectionDataManager,
        entityCollectionApiHooks
      )
    );
    return result;
  }

  public <
    TCollection, E extends EntityCollectionApiHooks<TCollection, T>
  > Page<TCollection> paginatedFreeTextSearchInEntityCollectionImpl(
    T owner,
    dev.sanda.datafi.dto.FreeTextSearchPageRequest input,
    String fieldName,
    DataManager<TCollection> collectionDataManager,
    E entityCollectionApiHooks
  ) {
    //get collection owner
    owner = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
    if (owner == null) throw_entityNotFound(input, reflectionCache);
    if (input.getFetchAll()) input.setPageNumber(0);
    Page<TCollection> returnValue;

    if (
      entityCollectionApiHooks != null &&
      (
        returnValue =
          entityCollectionApiHooks.executeCustomFreeTextSearch(
            input,
            owner,
            dataManager,
            collectionDataManager
          )
      ) !=
      null
    ) return returnValue;

    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.preFreeTextSearch(
      owner,
      input.getSearchTerm(),
      dataManager,
      collectionDataManager
    );

    returnValue = new Page<>();
    validateSortByIfNonNull(
      collectionDataManager.getClazz(),
      input.getSortBy(),
      reflectionCache
    );

    String clazzSimpleNamePlural = toPlural(
      collectionDataManager.getClazzSimpleName()
    );
    val searchTerm = input.getSearchTerm();
    if (
      searchTerm == null || searchTerm.equals("")
    ) throw new IllegalArgumentException(
      "Illegal attempt to search for " +
      clazzSimpleNamePlural +
      " with null or blank string"
    );

    boolean isArchivable = isClazzArchivable(
      collectionDataManager.getClazz(),
      reflectionCache
    );
    val isNonArchivedClause = isArchivable
      ? "AND embedded.isArchived = false "
      : "";
    val searchTermClause = buildSearchTermQuery(fieldName);
    val contentQueryString = String.format(
      "SELECT DISTINCT(embedded) FROM %s owner JOIN owner.%s embedded " +
      "WHERE owner.%s = :ownerId " +
      "%s" +
      "%s " +
      "ORDER BY embedded.%s",
      dataManager.getClazzSimpleName(),
      fieldName,
      idFieldName,
      isNonArchivedClause,
      searchTermClause,
      input.getSortBy()
    );
    val countQueryString = String.format(
      "SELECT COUNT(DISTINCT embedded) FROM %s owner JOIN owner.%s embedded " +
      "WHERE owner.%s = :ownerId " +
      "%s" +
      "%s ",
      dataManager.getClazzSimpleName(),
      fieldName,
      idFieldName,
      isNonArchivedClause,
      searchTermClause
    );
    val ownerId = getId(owner, reflectionCache);
    val content = dataManager
      .entityManager()
      .createQuery(contentQueryString)
      .setParameter("searchTerm", input.getSearchTerm())
      .setParameter("ownerId", ownerId)
      .setFirstResult(input.getPageNumber() * input.getPageSize())
      .setMaxResults(input.getPageSize())
      .getResultList();
    val totalItems = (long) dataManager
      .entityManager()
      .createQuery(countQueryString)
      .setParameter("searchTerm", input.getSearchTerm())
      .setParameter("ownerId", ownerId)
      .getSingleResult();
    val totalPages = Math.ceil((double) totalItems / input.getPageSize());
    returnValue.setContent(content);
    returnValue.setTotalPagesCount((long) totalPages);
    returnValue.setTotalItemsCount(totalItems);
    returnValue.setPageNumber(input.getPageNumber());
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.postFreeTextSearch(
      returnValue,
      input,
      owner,
      searchTerm,
      collectionDataManager,
      dataManager
    );
    return returnValue;
  }

  private final Map<String, String> searchTermQueryCache = new HashMap<>();

  private String buildSearchTermQuery(String collectionFieldName) {
    val key =
      collectionFieldName +
      "Of" +
      dataManager.getClazzSimpleName() +
      "FreeTextSearch";
    if (searchTermQueryCache.containsKey(key)) return searchTermQueryCache.get(
      key
    );
    val searchFieldNames = Arrays.asList(
      reflectionCache
        .getEntitiesCache()
        .get(dataManager.getClazzSimpleName())
        .getFields()
        .get(collectionFieldName)
        .getField()
        .getAnnotation(EntityCollectionApi.class)
        .freeTextSearchFields()
    );
    if (searchFieldNames.isEmpty()) return "";
    val result = new StringBuilder();
    for (int i = 0; i < searchFieldNames.size(); i++) {
      val fieldName = searchFieldNames.get(i);
      val prefix = i == 0 ? "AND " : "OR ";
      val condition =
        "lower(embedded." +
        fieldName +
        ") LIKE lower(concat('%', :searchTerm, '%')) ";
      result.append(prefix);
      result.append(condition);
    }
    val resultString = result.toString();
    searchTermQueryCache.put(key, resultString);
    return resultString;
  }

  public <
    TCollection, E extends EntityCollectionApiHooks<TCollection, T>
  > Page<TCollection> getPaginatedBatchInEntityCollectionImpl(
    T owner,
    dev.sanda.datafi.dto.PageRequest input,
    String fieldName,
    DataManager<TCollection> collectionDataManager,
    E entityCollectionApiHooks
  ) {
    //get collection owner
    T temp = dataManager.findById(getId(owner, reflectionCache)).orElse(null);
    if (temp == null) throw_entityNotFound(input, reflectionCache);
    owner = temp;
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.preGetPaginatedBatch(owner, input, dataManager);
    Page<TCollection> returnValue = new Page<>();
    validateSortByIfNonNull(
      collectionDataManager.getClazz(),
      input.getSortBy(),
      reflectionCache
    );
    val isNonArchivedClause = isClazzArchivable(
        collectionDataManager.getClazz(),
        reflectionCache
      )
      ? "AND embedded.isArchived = false "
      : " ";
    final String ownerEntityName = dataManager.getClazzSimpleName();
    val contentQueryString = String.format(
      "SELECT embedded FROM %s owner " +
      "JOIN owner.%s embedded " +
      "WHERE owner.%s = :ownerId %s" +
      "ORDER BY owner.%s",
      ownerEntityName,
      fieldName,
      idFieldName,
      isNonArchivedClause,
      input.getSortBy()
    );
    val countQueryString = String.format(
      "SELECT COUNT(embedded) FROM %s owner " +
      "JOIN owner.%s embedded " +
      "WHERE owner.%s = :ownerId %s",
      ownerEntityName,
      fieldName,
      idFieldName,
      isNonArchivedClause
    );
    Object ownerId = getId(temp, reflectionCache);
    val content = dataManager
      .entityManager()
      .createQuery(contentQueryString)
      .setParameter("ownerId", ownerId)
      .setFirstResult(input.getPageNumber() * input.getPageSize())
      .setMaxResults(input.getPageSize())
      .getResultList();
    val totalRecords = (long) dataManager
      .entityManager()
      .createQuery(countQueryString)
      .setParameter("ownerId", ownerId)
      .getSingleResult();
    val totalPages = Math.ceil((double) totalRecords / input.getPageSize());
    returnValue.setContent(content);
    returnValue.setTotalPagesCount((long) totalPages);
    returnValue.setTotalItemsCount(totalRecords);
    returnValue.setPageNumber(input.getPageNumber());
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.postGetPaginatedBatch(
      returnValue,
      owner,
      input,
      collectionDataManager,
      dataManager
    );
    return returnValue;
  }

  public <
    TCollection, E extends EntityCollectionApiHooks<TCollection, T>
  > List<TCollection> associatePreExistingWithEntityCollectionImpl(
    T input,
    String fieldName,
    List<TCollection> toAssociate,
    DataManager<TCollection> collectionDataManager,
    E entityCollectionApiHooks,
    SubscriptionsLogicService<TCollection> collectionSubscriptionsLogicService
  ) {
    //get collection owner
    T ownerLoaded = dataManager
      .findById(getId(input, reflectionCache))
      .orElse(null);
    if (ownerLoaded == null) throw_entityNotFound(input, reflectionCache);
    input = ownerLoaded;
    if (entityCollectionApiHooks != null) entityCollectionApiHooks.preAssociate(
      toAssociate,
      input,
      fieldName,
      collectionDataManager,
      dataManager
    );
    //validate all candidates are pre-existing
    List<TCollection> toAssociateReloaded = collectionDataManager.findAllById(
      getIdList(toAssociate, reflectionCache)
    );
    if (
      toAssociateReloaded.isEmpty() ||
      toAssociateReloaded.size() != toAssociate.size()
    ) throw new IllegalArgumentException(
      "illegal attempt made to indirectly add new strong entities"
    );
    Collection<TCollection> existingCollection = getEntityCollectionFrom(
      input,
      toCamelCase(fieldName)
    );
    if (existingCollection == null) existingCollection =
      initEntityCollection(fieldName, collectionDataManager);
    existingCollection.addAll(toAssociate);
    //update & save owner
    try {
      reflectionCache
        .getEntitiesCache()
        .get(input.getClass().getSimpleName())
        .getFields()
        .get(fieldName)
        .getField()
        .set(input, existingCollection);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }

    final T owner = input;
    T updatedInputEntity = dataManager.saveAndFlush(owner);
    Collection<TCollection> newlyAssociated = getEntityCollectionFrom(
      updatedInputEntity,
      toCamelCase(fieldName)
    );
    List<TCollection> result = extractFromCollection(
      newlyAssociated,
      toAssociate
    );
    if (
      entityCollectionApiHooks != null
    ) entityCollectionApiHooks.postAssociate(
      toAssociate,
      result,
      owner,
      fieldName,
      collectionDataManager,
      dataManager
    );
    fireSubscriptionEvent(() ->
      subscriptionsLogicService.onAssociateWithEvent(
        ownerLoaded,
        fieldName,
        result,
        collectionDataManager,
        entityCollectionApiHooks
      )
    );
    return result;
  }

  public <TCollection> List<TCollection> removeFromEntityCollectionImpl(
    T owner,
    String toRemoveFieldName,
    List<TCollection> toRemove,
    DataManager<TCollection> collectionDataManager,
    EntityCollectionApiHooks<TCollection, T> entityCollectionApiHooks
  ) {
    //get collection owner
    val ownerLoaded = dataManager
      .findById(getId(owner, reflectionCache))
      .orElse(null);
    if (ownerLoaded == null) throw_entityNotFound(owner, reflectionCache);
    owner = ownerLoaded;
    assert owner != null;
    Collection<TCollection> currentCollection = getEntityCollectionFrom(
      owner,
      toRemoveFieldName
    );
    if (currentCollection == null) throw new RuntimeException(
      "Illegal attempt to remove object from null collection"
    );
    if (entityCollectionApiHooks != null) entityCollectionApiHooks.preRemove(
      toRemove,
      owner,
      collectionDataManager,
      dataManager
    );
    Set<Object> toRemoveIds = new HashSet<>(
      getIdList(toRemove, reflectionCache)
    );
    currentCollection.removeIf(item ->
      toRemoveIds.contains(getId(item, reflectionCache))
    );
    dataManager.save(owner);
    if (entityCollectionApiHooks != null) entityCollectionApiHooks.postRemove(
      toRemove,
      owner,
      collectionDataManager,
      dataManager
    );
    fireSubscriptionEvent(() ->
      subscriptionsLogicService.onRemoveFromEvent(
        ownerLoaded,
        toRemoveFieldName,
        toRemove,
        collectionDataManager,
        entityCollectionApiHooks
      )
    );
    return toRemove;
  }

  private <TCollection> Collection<TCollection> initCollection(
    String fieldName
  ) {
    val collectionType = reflectionCache
      .getEntitiesCache()
      .get(entityName)
      .getFields()
      .get(fieldName)
      .getField()
      .getType();
    return collectionInstantiator.instantiateCollection(collectionType);
  }

  public <TCollection> Collection<TCollection> getEntityCollectionFrom(
    T input,
    String fieldName
  ) {
    try {
      return (Collection<TCollection>) reflectionCache
        .getEntitiesCache()
        .get(input.getClass().getSimpleName())
        .getFields()
        .get(fieldName)
        .getField()
        .get(input);
    } catch (IllegalAccessException e) {
      e.printStackTrace();
      return null;
    }
  }

  private <TCollection> Collection<TCollection> initEntityCollection(
    String fieldName,
    DataManager<TCollection> collectionDataManager
  ) {
    val collectionType = reflectionCache
      .getEntitiesCache()
      .get(dataManager.getClazzSimpleName())
      .getFields()
      .get(fieldName)
      .getField()
      .getType();
    return collectionInstantiator.instantiateCollection(collectionType);
  }

  public <TCollection> List<TCollection> extractFromCollection(
    Collection<TCollection> toExtractFrom,
    Collection<TCollection> toExtract
  ) {
    List<TCollection> result = new ArrayList<>();
    toExtract.forEach(item ->
      result.add(extract(toExtractFrom, item, reflectionCache))
    );
    return result;
  }

  private <TCollection> void setBackpointersIfRelationshipIsBiDirectional(
    T owner,
    List<TCollection> associated,
    String fieldName
  ) {
    if (associated.isEmpty()) return;
    val sourceField = reflectionCache
      .getEntitiesCache()
      .get(entityName)
      .getFields()
      .get(fieldName)
      .getField();
    val fieldTypeSimpleName = associated.get(0).getClass().getSimpleName();
    for (val instance : associated) {
      reflectionCache
        .getEntitiesCache()
        .get(fieldTypeSimpleName)
        .getRelationshipSyncronizer()
        .trySetBackpointer(sourceField, instance, owner);
    }
  }

  public <TCollection> TCollection extract(
    Collection<TCollection> collection,
    TCollection toCheck,
    ReflectionCache reflectionCache
  ) {
    Object toCheckId = getId(toCheck, reflectionCache);
    for (TCollection item : collection) {
      if (getId(item, reflectionCache).equals(toCheckId)) return item;
    }
    throw_entityNotFound(toCheck, reflectionCache);
    return null;
  }
}
