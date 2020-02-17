package dev.sanda.apifi.service;

import dev.sanda.datafi.reflection.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class EmbeddedCollectionApiHooks<TEmbedded, T>{

    @Autowired @Getter
    private ReflectionCache reflectionCache;
    @Autowired @Getter
    private DataManager<TEmbedded> tDataManager;
    @Autowired @Getter
    private DataManager<T> hasTsDataManager;

    public void preFetch(T t) {}
    public void postFetch(Collection<TEmbedded> tEmbeddeds, T t){}
    public void preRemove(Collection<TEmbedded> tEmbeddeds, T t){}
    public void postRemove(Collection<TEmbedded> tEmbeddeds, T t){}
    public void preAssociate(Collection<TEmbedded> tEmbeddeds, T t){}
    public void postAssociate(Collection<TEmbedded> tEmbeddeds, T t){}
    public void preUpdate(Collection<TEmbedded> tEmbeddeds, T t){}
    public void postUpdate(Collection<TEmbedded> tEmbeddeds, T t){}
}
