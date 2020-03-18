package dev.sanda.apifi.service;

import dev.sanda.datafi.service.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public abstract class ApiHooks<T> {

    @Autowired
    private DataManager<T> dataManager;
    protected DataManager<T> dataManager(){return dataManager;}
    @Autowired
    private ServiceContext context;
    protected ServiceContext context(){return context;}

    //queries
    public void preGetById(Object id){}
    public void preApiFindByUnique(Object argument){}
    public void postApiFindByUnique(T result) {}
    public void preApiFindBy(Object argument){}
    public void postApiFindBy(List<T> result) {}
    public void preApiFindAllBy(String fieldName, List<?> arguments){}
    public void postApiFindAllBy(String fieldName, List<T> result) {}
    public void preGetPaginatedBatch() {}
    public void postGetPaginatedBatch(List<T> result){}
    public void preFetchEntitiesInFreeTextSearch(String searchTerm){}
    public void postFetchEntitiesInFuzzySearch(String searchTerm, List<T> fetched){}
    public void postGetById(T fetched){}
    public void preGetArchivedPaginatedBatch(){}
    public void postGetArchivedPaginatedBatch(List<T> result) {}

    //mutations
    public void preCreate(T toAdd){}
    public void postCreate(T added){}
    public void preUpdate(T toUpdate){}
    public void postUpdate(T toUpdate){}
    public void preDelete(T toDelete){}
    public void postDelete(T deleted){}
    public void preArchive(T toArchive){}
    public void postArchive(T toArchive){}
    public void preDeArchive(T toDeArchive){}
    public void postDeArchive(T toDeArchive){}

    public void preBatchCreate(Collection<T> toAdd){toAdd.forEach(this::preCreate);}
    public void postBatchCreate(Collection<T> added){added.forEach(this::postCreate);}
    public void preBatchUpdate(Collection<T> toUpdate){toUpdate.forEach(this::preUpdate);}
    public void postBatchUpdate(Collection<T> toUpdate){toUpdate.forEach(this::postUpdate);}
    public void preDeleteEntities(Collection<T> toDelete){toDelete.forEach(this::preDelete);}
    public void postDeleteEntities(Collection<T> deleted){deleted.forEach(this::postDelete);}
    public void preBatchArchive(Collection<T> toArchive){toArchive.forEach(this::preArchive);}
    public void postBatchArchive(Collection<T> toArchive){toArchive.forEach(this::postArchive);}
    public void preBatchDeArchive(List<T> toDeArchive){toDeArchive.forEach(this::preDeArchive);}
    public void postBatchDeArchive(List<T> toDeArchive){toDeArchive.forEach(this::postDeArchive);}
}