package dev.sanda.apifi.service.api_logic;

import dev.sanda.datafi.reflection.ReflectionCache;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.collect.Maps.immutableEntry;
import static dev.sanda.datafi.reflection.CachedEntityTypeInfo.genDefaultInstance;

@Component
public class CollectionInstantiator {
    private Reflections javaUtils = new Reflections("java.util");
    private Map<Class<? extends Collection>, List<Class<? extends Collection>>> collectionTypes = new HashMap<>();
    private Map<Map.Entry<Class<?>, Class<?>>, Class<?>> cache = new HashMap<>();

    @PostConstruct
    private void init(){
        Collection<Class<? extends Collection>> allCollectionTypes = javaUtils.getSubTypesOf(Collection.class);
        Collection<Class<? extends Collection>> collectionInterfaces =
                allCollectionTypes.stream().filter(Class::isInterface).collect(Collectors.toList());
        Collection<Class<? extends Collection>> collectionImplementations =
                allCollectionTypes.stream().filter(type -> !type.isInterface()).collect(Collectors.toList());
        for(Class<? extends Collection> collectionInterface : collectionInterfaces){
            collectionTypes.put(collectionInterface, new ArrayList<>());
            for(Class<? extends Collection> collectionImplementation : collectionImplementations)
                if (collectionInterface.isAssignableFrom(collectionImplementation))
                    collectionTypes.get(collectionInterface).add(collectionImplementation);
        }
    }

    public Collection instantiateCollection(Class<?> collectionType, Class<?> collectableEntityType){
        final Map.Entry<Class<?>, Class<?>> key = immutableEntry(collectionType, collectableEntityType);
        if(cache.get(key) != null){
            return (Collection) genDefaultInstance(cache.get(key));
        }
        if(collectableEntityType.equals(Collection.class)) return new ArrayList();

        if(Modifier.isInterface(collectionType.getModifiers())){
            if(collectionType.equals(Collection.class)) return new HashSet();
            if(collectionType.equals(Set.class)) return new HashSet();
            if(collectionType.equals(List.class)) return new ArrayList();
            if(collectionType.equals(Queue.class)) return new LinkedList();
            if(collectionType.equals(Deque.class)) return new ArrayDeque();
        }

        Collection result = (Collection)genDefaultInstance(collectionType);

        /*for(val entry : collectionTypes.entrySet()){
            if(entry.getKey().equals(collectionType))
                result = assignDefaultType(entry, collectableEntityType);
            else if(entry.getKey().isAssignableFrom(collectionType)){
                result = (Collection)genDefaultInstance(collectionType);
            }
        }*/
        if(result == null)
            throw new IllegalArgumentException("unrecognized collection type: " + collectionType.getSimpleName());
        cache.put(key, result.getClass());
        return result;
    }
}
