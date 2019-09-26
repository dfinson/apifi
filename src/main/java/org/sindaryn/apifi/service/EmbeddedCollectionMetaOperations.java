package org.sindaryn.apifi.service;

import lombok.Getter;
import org.sindaryn.datafi.persistence.GenericDao;
import org.sindaryn.datafi.reflection.ReflectionCache;
import org.sindaryn.datafi.service.DataManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Collection;

@Component
public class EmbeddedCollectionMetaOperations<T, HasTs>{

    @Autowired @Getter
    private ReflectionCache reflectionCache;
    @Autowired @Getter
    private DataManager<T> tDataManager;
    @Autowired @Getter
    private DataManager<HasTs> hasTsDataManager;

    public void postFetch(Collection<T> Ts, HasTs hasTs){}
    public void preRemove(Collection<T> Ts, HasTs hasTs){}
    public void postRemove(Collection<T> Ts, HasTs hasTs){}
    public void preAttachOrAdd(Collection<T> Ts, HasTs hasTs){}
    public void postAttachOrAdd(Collection<T> Ts, HasTs hasTs){}
    public void preUpdate(Collection<T> Ts, HasTs hasTs){}
    public void postUpdate(Collection<T> Ts, HasTs hasTs){}
}
