package dev.sanda.apifi.service;

import dev.sanda.datafi.reflection.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class ApiHooksAndCustomResolvers<T> {

    @Autowired
    protected ReflectionCache reflectionCache;
    @Autowired
    protected DataManager<T> dataManager;

    //queries
    public void preFetchEntityInGetById(Object id){}
    public void preFetchEntityInGetByUnique(Object argument){}
    public void preFetchEntityInGetBy(Object argument){}
    public void preFetchEntityInGetAllBy(List<?> arguments){}
    public void preFetchEntityInCustomResolver(List<?> arguments){}
    public void preGetPaginatedBatch(Class<T> clazz) {}
    public void preFetchEntitiesInFuzzySearch(Class<T> clazz, String searchTerm){}
    public void postFetchEntitiesInFuzzySearch(Class<T> clazz, String searchTerm, List<T> fetched){}
    public void postFetchEntity(T fetched){}
    public void postFetchEntities(Collection<T> fetched){fetched.forEach(this::postFetchEntity);}

    //mutations
    public void preAddEntity(T toAdd){}
    public void postAddEntity(T added){}
    public void preUpdateEntity(T toUpdate){}
    public void postUpdateEntity(T toUpdate){}
    public void preDeleteEntity(T toDelete){}
    public void postDeleteEntity(T deleted){}
    public void preArchiveEntity(T toArchive){}
    public void postArchiveEntity(T toArchive){}
    public void preDeArchiveEntity(T toDeArchive){}
    public void postDeArchiveEntity(T toDeArchive){}

    public void preAddEntities(Collection<T> toAdd){toAdd.forEach(this::preAddEntity);}
    public void postAddEntities(Collection<T> added){added.forEach(this::postAddEntity);}
    public void preUpdateEntities(Collection<T> toUpdate){toUpdate.forEach(this::preUpdateEntity);}
    public void postUpdateEntities(Collection<T> toUpdate){toUpdate.forEach(this::postUpdateEntity);}
    public void preDeleteEntities(Collection<T> toDelete){toDelete.forEach(this::preDeleteEntity);}
    public void postDeleteEntities(Collection<T> deleted){deleted.forEach(this::postDeleteEntity);}
    public void preArchiveEntities(Collection<T> toArchive){toArchive.forEach(this::preArchiveEntity);}
    public void postArchiveEntities(Collection<T> toArchive){toArchive.forEach(this::postArchiveEntity);}
    public void preDeArchiveEntities(List<T> toDeArchive){toDeArchive.forEach(this::preDeArchiveEntity);}
    public void postDeArchiveEntities(List<T> toDeArchive){toDeArchive.forEach(this::postDeArchiveEntity);}
}