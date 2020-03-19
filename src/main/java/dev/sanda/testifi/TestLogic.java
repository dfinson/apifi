package dev.sanda.testifi;

import com.google.common.collect.Lists;
import com.maximeroussy.invitrode.WordGenerator;
import dev.sanda.apifi.service.ApiHooks;
import dev.sanda.apifi.service.ApiLogic;
import dev.sanda.apifi.service.EmbeddedCollectionApiHooks;
import dev.sanda.datafi.persistence.Archivable;
import dev.sanda.datafi.reflection.CachedEntityTypeInfo;
import dev.sanda.datafi.reflection.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import dev.sanda.mockeri.generator.EntityMocker;
import lombok.NonNull;
import lombok.Setter;
import lombok.val;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static dev.sanda.testifi.EquivalencyMatcher.isEqualTo;
import static dev.sanda.testifi.TestifiStaticUtils.*;
import static org.junit.Assert.*;
import static org.junit.Assert.assertThat;
import static dev.sanda.datafi.DatafiStaticUtils.*;
import static dev.sanda.mockeri.generator.TestDataGenerator.randomFrom;

@Component
public final class TestLogic<T> {

    @Setter
    private ApiLogic<T> apiLogic;
    @Setter
    private DataManager<T> dataManager;
    @Autowired
    private ReflectionCache reflectionCache;
    @Setter
    private ApiHooks<T> apiHooks;
    @Autowired
    private EntityMocker entityMocker;
    @Setter
    private Class<T> clazz;
    @Setter
    private String clazzSimpleName;



    //test methods
    public void getPaginatedBatchTest(){
        int offset = 0;
        int limit = totalCount(clazz, dataManager);
        Collection<T> allTs = dataManager.findAll();
        Collection<T> allApiFetchedTs = apiLogic.getPaginatedBatch(offset, limit, null, null);
        assertThat(
            "result of api call to " + pluralCamelCaseName(clazz) +
                   "()' " + " equals original entries in database",
                allTs,
                isEqualTo(allApiFetchedTs)
        );
    }

    public <A extends Archivable> void getArchivedPaginatedBatchTest(){
        int offset = 0;
        int limit = totalCount(clazz, dataManager);
        List<T> allTs = dataManager.findAll();
        allTs.forEach(t -> ((A)t).setIsArchived(true));
        dataManager.saveAll(allTs);
        Collection<A> allApiFetchedTs = (Collection<A>) apiLogic.getArchivedPaginatedBatch(offset, limit, null, null);
        assertThat(
                "result of api call to " + toPlural(clazz.getSimpleName()) +
                        "()' " + " equals original entries in database",
                allTs,
                isEqualTo(allApiFetchedTs)
        );
    }

    public void freeTextSearchTest(){
        int offset = 0;
        int limit = totalCount(clazz, dataManager);
        Collection<T> allTs = dataManager.findAll();
        Field toSearchBy = resolveFieldToFuzzySearchBy(clazz, reflectionCache);
        WordGenerator wordGenerator = new WordGenerator();
        String searchTerm = wordGenerator.newWord(ThreadLocalRandom.current().nextInt(3, 5));
        String prefix = wordGenerator.newWord(ThreadLocalRandom.current().nextInt(3, 5));
        String suffix = wordGenerator.newWord(ThreadLocalRandom.current().nextInt(3, 5));
        String testValue = prefix + searchTerm + suffix;
        allTs.forEach(t -> setField(t, testValue, toSearchBy.getName()));
        dataManager.saveAll(allTs);
        Collection<T> allApiFuzzySearchFetchedTs = apiLogic
                .freeTextSearch(offset, limit, searchTerm, null, null);
        assertThat(
                "result of api call to " + pluralCamelCaseName(clazz) + "FreeTextSearch" +
                        "(...)' " + " equals original entries in database",
                allTs,
                isEqualTo(allApiFuzzySearchFetchedTs));
    }


    public void getByIdTest(){
        T toGetById = randomFrom(dataManager.findAll());
        Object id = getId(toGetById, reflectionCache);
        T fetchedById = apiLogic.getById(id);
        assertThat(clazzSimpleName + " successfully fetched by id",
                toGetById,
                isEqualTo(fetchedById));
    }

    public void apiFindByUniqueTest(String fieldName){
        T toGet = randomFrom(dataManager.findAll());
        clazzSimpleName = clazzSimpleName;
        Object uniqueValue = reflectionCache.getEntitiesCache().get(clazzSimpleName).invokeGetter(toGet, fieldName);
        T fetched = apiLogic.apiFindByUnique(fieldName, uniqueValue);
        assertThat(
                "Successfully fetched a " + toPascalCase(clazzSimpleName) +
                      " by the unique value of " + fieldName + " = " + uniqueValue.toString(),
                toGet, isEqualTo(fetched));
    }

    public void
    apiFindByTest(String fieldName){
        T toGet = randomFrom(dataManager.findAll());
        final CachedEntityTypeInfo entityType = reflectionCache.getEntitiesCache().get(clazzSimpleName);
        Object value = entityType.invokeGetter(toGet, fieldName);
        Collection<T> fetched = apiLogic.apiFindBy(fieldName, value);
        for(T instance : fetched){
            assertThat(
                    "successfully fetched instance of " + clazzSimpleName +
                    " by value of " + fieldName + " = " + value.toString(),
                    entityType.invokeGetter(instance, fieldName),
                    isEqualTo(value)
            );
        }
    }

    public void apiFindAllByTest(String fieldName){
        Map<Object, T> toGet = firstRandomNIdMap(clazz, dataManager, reflectionCache);
        final CachedEntityTypeInfo entityType = reflectionCache.getEntitiesCache().get(clazzSimpleName);
        List<?> valuesList = fieldValues(fieldName, Arrays.asList(toGet.values().toArray()), entityType);
        Collection<T> fetched = apiLogic.apiFindAllBy(fieldName, valuesList);
        assertTrue(fetched.size() >= toGet.size());
        for(T fetchedInstance : fetched){
            T toGetInstance = toGet.get(getId(fetchedInstance, reflectionCache));
            if(toGetInstance != null)
                assertThat(fetchedInstance, isEqualTo(toGetInstance));
        }
    }

    public void
    createTest(){
        T toAdd = entityMocker.instantiateEntity(clazz);
        T added = apiLogic.create(toAdd);
        assertThat(clazzSimpleName + " successfully added", toAdd, isEqualTo(added));
    }

    public void
    updateTest(){
        T original = randomInstance(clazz, dataManager);
        T updated = entityMocker.mockUpdate(original);
        setField(updated, getId(original, reflectionCache), "id");
        T updatedOriginal = apiLogic.update(updated);
        assertThat("successfully updated " + clazzSimpleName,
                   updated,  isEqualTo(updatedOriginal));
    }

    public <A extends Archivable>  void archiveTest(){
        A instance = (A) randomInstance(clazz, dataManager);
        final String simpleName = clazzSimpleName;
        assertFalse("Default state of " + simpleName + " is non archived", instance.getIsArchived());
        A archivedInstance = (A) apiLogic.archive(instance);
        assertTrue("Instance of " + simpleName + " successfully archived", archivedInstance.getIsArchived());
    }

    public <A extends Archivable>  void batchArchiveTest(){
        List<A> instances = (List<A>)firstRandomN(clazz, dataManager);
        int amountToArchive = instances.size();
        final String simpleName = clazzSimpleName;
        boolean defaultStateIsNonArchived = true;
        for (A instance : instances)
            if(instance.getIsArchived()){
                defaultStateIsNonArchived = false;
                break;
            }
        assertTrue("Default state of " + simpleName + " is non archived", defaultStateIsNonArchived);
        List<A> archivedInstances = (List<A>) apiLogic.batchArchive(instances);
        boolean successfullyArchivedAllInstances = true;
        for (A instance : archivedInstances)
            if(!instance.getIsArchived()){
                successfullyArchivedAllInstances = false;
                break;
            }
        assertTrue("Successfully archived " + amountToArchive + " instances of " + simpleName, 
                successfullyArchivedAllInstances);
    }

    public <A extends Archivable>  void deArchiveTest(){
        A instance = (A) randomInstance(clazz, dataManager);
        final String simpleName = clazzSimpleName;
        assertFalse("Default state of " + simpleName + " is non archived", instance.getIsArchived());
        T archivedInstance = (T) dataManager.archive(instance);
        A deArchivedInstance = (A)apiLogic.deArchive((A)archivedInstance);
        assertFalse("Instance of " + simpleName + " successfully de archived", deArchivedInstance.getIsArchived());
    }

    public <A extends Archivable>  void batchDeArchiveTest(){
        List<A> instances = (List<A>) firstRandomN(clazz, dataManager);
        int amountToArchive = instances.size();
        final String simpleName = clazzSimpleName;
        boolean defaultStateIsNonArchived = true;
        for (A instance : instances)
            if(instance.getIsArchived()){
                defaultStateIsNonArchived = false;
                break;
            }
        assertTrue("Default state of " + simpleName + " is non archived", defaultStateIsNonArchived);
        List<A> archivedInstances = dataManager.archiveCollection(instances);
        List<A> deArchivedInstances = (List<A>) apiLogic.batchDeArchive(archivedInstances);
        boolean successfullyDeArchivedAllInstances = true;
        for (A instance : deArchivedInstances)
            if(instance.getIsArchived()){
                successfullyDeArchivedAllInstances = false;
                break;
            }
        assertTrue("Successfully de archived " + amountToArchive + " instances of " + simpleName, 
                successfullyDeArchivedAllInstances);
    }

    public void deleteTest(){
        T toDelete = randomInstance(clazz, dataManager);
        T deleted = apiLogic.delete(toDelete);
        Optional<T> shouldNotBePresent = dataManager.findById(getId(deleted, reflectionCache));
        assertFalse(clazzSimpleName + " successfully deleted", shouldNotBePresent.isPresent());
        entityMocker.instantiateEntity(clazz);
    }

    public void getBatchByIdsTest(){
        Collection<T> present = dataManager.findAll();
        List<?> ids = dataManager.idList(present);
        Collection<T> fetched = apiLogic.getBatchByIds(ids);
        assertThat( "successfully fetched " + present.size() + " " + toPlural(clazzSimpleName) + " by id",
                present, isEqualTo(fetched));
    }

    public void batchCreateTest(){
        int amountToAdd = ThreadLocalRandom.current().nextInt(5, 10);
        List<T> toCreate = new ArrayList<>();
        for (int i = 0; i <amountToAdd; i++)
            toCreate.add(entityMocker.instantiateTransientEntity(clazz));
        Collection<T> created = apiLogic.batchCreate(toCreate);
        assertThat( "successfully created " + amountToAdd + " " + toPlural(clazzSimpleName),
                toCreate,
                isEqualTo(created));
    }

    public void batchUpdateTest(){
        List<T> updated = firstRandomN(clazz, dataManager);
        int amountToUpdate = updated.size();
        updated.forEach(entityMocker::mockUpdate);
        Collection<T> updatedViaApi = apiLogic.batchUpdate(updated);
        assertThat("successfully updated " + amountToUpdate + " " + toPlural(clazzSimpleName),
                updated,  isEqualTo(updatedViaApi));
    }

    public void batchDeleteTest(){
        List<T> toDelete = firstRandomN(clazz, dataManager);
        int amountToDelete = toDelete.size();
        apiLogic.batchDelete(toDelete);
        Collection<?> ids = dataManager.idList(toDelete);
        Collection<T> shouldBeEmpty = dataManager.findAllById(ids);
        assertTrue(amountToDelete + " " + toPlural(clazzSimpleName) + " successfully deleted",
                shouldBeEmpty.isEmpty());
        for (int i = 0; i <amountToDelete; i++) entityMocker.instantiateEntity(clazz);
    }

    public <TEmbedded> void getEmbeddedTest(DataManager<TEmbedded> tEmbeddedDataManager, String fieldName) {
        if(dataManager.count() == 0) populate(clazz, entityMocker, 20);
        List<T> owners = firstRandomN(clazz, dataManager);
        Collection<TEmbedded> embeddedEntities = new ArrayList<>();
        TEmbedded embeddedEntity;
        for (T owner : owners) {
            embeddedEntity = entityMocker.instantiateEntity(tEmbeddedDataManager.getClazz());
            setField(owner, embeddedEntity, fieldName);
            embeddedEntities.add(embeddedEntity);
        }
        owners = dataManager.saveAll(owners);
        Collection<TEmbedded> fetchedAsEmbedded = apiLogic.getEmbedded(owners, fieldName, tEmbeddedDataManager);
        assertThat(
                "successfully fetched " + fetchedAsEmbedded.size() + " " + toPlural(fieldName) +
                        " referenced within " + owners.size() + " " + toPlural(clazzSimpleName),
                embeddedEntities,
                isEqualTo(fetchedAsEmbedded));
    }

    public <TEmbedded> void getEmbeddedCollectionTest(
            DataManager<TEmbedded> tEmbeddedDataManager,
            String fieldName,
            EmbeddedCollectionApiHooks<TEmbedded, T> embeddedCollectionApiHooks) {
        if(dataManager.count() == 0) populate(clazz, entityMocker, 20);
        List<T> owners = firstRandomN(clazz, dataManager);
        Collection<Collection<TEmbedded>> embeddedEntityCollections = new ArrayList<>();
        Collection<TEmbedded> embeddedEntityCollection;
        for (T owner : owners) {
            embeddedEntityCollection = persistCollectionOf(tEmbeddedDataManager.getClazz(), entityMocker);
            setCollectionField(owner, embeddedEntityCollection, fieldName, entityMocker);
            embeddedEntityCollections.add(embeddedEntityCollection);
        }
        owners = dataManager.saveAll(owners);
        List<List<TEmbedded>> fetchedAsEmbedded =
                apiLogic.getEmbeddedCollection(owners, fieldName, embeddedCollectionApiHooks, tEmbeddedDataManager);

        assertThat(
                "Successfully fetched " + embeddedEntityCollections.size() +
                      " collections of " + toPlural(tEmbeddedDataManager.getClazzSimpleName()) + " from " + owners.size() +
                      " " + toPlural(clazzSimpleName),
                embeddedEntityCollections, isEqualTo(fetchedAsEmbedded));
    }

    public <TEmbedded>
    void associateWithEmbeddedCollectionTest(DataManager<TEmbedded> tEmbeddedDataManager,
                                             String fieldName,
                                             EmbeddedCollectionApiHooks<TEmbedded, T> embeddedCollectionApiHooks) {
        T toAddTo = entityMocker.instantiateEntity(clazz);
        @NonNull final Class<TEmbedded> tEmbeddedClass = tEmbeddedDataManager.getClazz();
        List<TEmbedded> toCreate = transientlyInstantiateCollectionOf(tEmbeddedClass, entityMocker);
        List<TEmbedded> created = apiLogic
                .associateWithEmbeddedCollection(toAddTo, fieldName, toCreate, tEmbeddedDataManager, embeddedCollectionApiHooks);
        assertThat("successfully created " + created.size() +
                    " " + toPlural(tEmbeddedClass.getSimpleName()) + " to " +
                    clazzSimpleName,
                    toCreate, isEqualTo(created));
    }

    public <TEmbedded>
    void attachExistingToEmbeddedCollectionTest(DataManager<TEmbedded> tEmbeddedDataManager,
                                                String fieldName,
                                                EmbeddedCollectionApiHooks<TEmbedded, T> embeddedCollectionApiHooks) {
        T toAssociateWith = entityMocker.instantiateEntity(clazz);
        List<TEmbedded> toAssociate = persistCollectionOf(tEmbeddedDataManager.getClazz(), entityMocker);
        List<TEmbedded> associated = apiLogic
                .associatePreExistingWithEmbeddedCollection(
                        toAssociateWith, fieldName, toAssociate, 
                        tEmbeddedDataManager, embeddedCollectionApiHooks);
        assertThat("successfully associated " + associated.size() +
                        " pre-existing " + toPlural(tEmbeddedDataManager.getClazzSimpleName()) + " to " +
                        clazzSimpleName,
                toAssociate, isEqualTo(associated));
    }

    public <TEmbedded>
    void updateEmbeddedCollectionTest(DataManager<TEmbedded> tEmbeddedDataManager,
                                      String fieldName,
                                      EmbeddedCollectionApiHooks<TEmbedded, T> embeddedCollectionApiHooks) {
        T owner = entityMocker.instantiateEntity(clazz);
        val tEmbeddedClass = tEmbeddedDataManager.getClazz();
        List<TEmbedded> originalEmbeddedCollection = persistCollectionOf(tEmbeddedClass, entityMocker);
        setCollectionField(owner, originalEmbeddedCollection, fieldName, entityMocker);
        owner = dataManager.saveAndFlush(owner);

        Iterable<TEmbedded> updatedEmbeddedCollection = Lists.newArrayList(originalEmbeddedCollection);
        updatedEmbeddedCollection.forEach(entityMocker::mockUpdate);

        Collection<TEmbedded> fetchedEmbeddedCollection = apiLogic
                .updateEmbeddedCollection(owner, tEmbeddedDataManager, updatedEmbeddedCollection, embeddedCollectionApiHooks);
        assertThat(
            "successfully updated " + fetchedEmbeddedCollection.size() + " " +
                    fieldName + " in " + clazzSimpleName,
                    updatedEmbeddedCollection, isEqualTo(fetchedEmbeddedCollection));
    }

    public <TEmbedded>
    void removeFromEmbeddedCollectionTest(DataManager<TEmbedded> tEmbeddedDataManager,
                                          String fieldName,
                                          EmbeddedCollectionApiHooks<TEmbedded, T> embeddedCollectionApiHooks) {
        T owner = entityMocker.instantiateEntity(clazz);
        val tEmbeddedClazz = tEmbeddedDataManager.getClazz();
        List<TEmbedded> originalEmbeddedCollection = persistCollectionOf(tEmbeddedClazz, entityMocker);
        setCollectionField(owner, originalEmbeddedCollection, fieldName, entityMocker);
        tEmbeddedDataManager.saveAll(originalEmbeddedCollection);
        owner = dataManager.saveAndFlush(owner);

        List<TEmbedded> toRemoveFromCollection = firstRandomEmbeddedN(owner, fieldName, reflectionCache);

        Collection<TEmbedded> removedFromEmbeddedCollection = apiLogic
                .removeFromEmbeddedCollection(owner, fieldName, toRemoveFromCollection, embeddedCollectionApiHooks);
        int expectedCollectionSize = originalEmbeddedCollection.size() - toRemoveFromCollection.size();
        int actualCollectionSize =
                ((Collection<T>)reflectionCache.getEntitiesCache()
                .get(owner.getClass().getSimpleName())
                .invokeGetter(owner, fieldName)).size();

        assertEquals(
                "successfully removed " + removedFromEmbeddedCollection.size() + " " +
                        fieldName + " from " + clazzSimpleName,
                expectedCollectionSize, actualCollectionSize);
    }
}
