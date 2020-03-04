package dev.sanda.testifi;


import dev.sanda.datafi.annotations.attributes.NonApiUpdatable;
import dev.sanda.datafi.annotations.attributes.NonApiUpdatables;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import javax.persistence.Column;
import java.lang.reflect.Field;
import java.util.*;

import static dev.sanda.datafi.reflection.ReflectionCache.getClassFields;


@SuppressWarnings("unchecked")
@RequiredArgsConstructor
public class EquivalencyMatcher extends TypeSafeMatcher<Object> {

    @NonNull
    private Object actual;

    @Override
    protected boolean matchesSafely(Object expected) {
        try {
            if(expected == null && actual == null)
                return true;
            if(expected == null ^ actual == null)
                return false;
            if(Iterable.class.isAssignableFrom(expected.getClass())){
                List<Object> expectedAsList = toSortedList(expected);
                List<Object> actualAsList = toSortedList(actual);
                if(expectedAsList.size() != actualAsList.size())
                    return false;
                for (int i = 0; i < expectedAsList.size(); i++) {
                    boolean embeddedCollectionsAreEquivalent =
                            new EquivalencyMatcher(actualAsList.get(i)).matchesSafely(expectedAsList.get(i));
                    if (!embeddedCollectionsAreEquivalent)
                        return false;
                }
            }else if(expected.getClass().isPrimitive()){
                if(!expected.equals(actual))
                    return false;
            } else{
                for (Field field : getClassFields(expected.getClass())) {
                    field.setAccessible(true);
                    if(isUpdatableField(field)){
                        if(field.get(expected) == null && field.get(actual) == null) return true;
                        if(field.get(expected) == null ^ field.get(actual) == null) return false;
                        if(!field.get(expected).equals(field.get(actual)))
                            return false;
                    }
                }
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        return true;
    }

    /*private String objectKey(Object o){
        return objectKey(o, new StringBuilder());
    }
    private String objectKey(Object o, StringBuilder key){
        if(Iterable.class.isAssignableFrom(o.getClass())){
            for(Object item : (Iterable)o) key.append(objectKey(item, key));
            return key.toString();
        }else return o.toString();
    }*/

    private List<Object> toSortedList(Object collection) {
        List asList = new ArrayList((Collection) collection);
        asList.sort(Comparator.comparing(Object::toString));
        return asList;
    }

    /*private Map<Integer, Object> toMap(Object collection){
        return (Map<Integer, Object>)
               ((Collection)collection)
               .stream()
               .collect(Collectors.toMap(Object::hashCode, obj -> obj));
    }*/

    private boolean isUpdatableField(Field field) {
        Object parent = field.getDeclaringClass();
        return !(field.isAnnotationPresent(Column.class) && !field.getAnnotation(Column.class).updatable()) &&
                !field.isAnnotationPresent(NonApiUpdatable.class) &&
                !(
                        parent.getClass().isAnnotationPresent(NonApiUpdatables.class) &&
                                Arrays.asList(parent.getClass().getAnnotation(NonApiUpdatables.class).value())
                                        .contains(field.getName())
                );
    }

    @Override
    public void describeTo(Description description) {}
    public static Matcher<Object> isEqualTo(Object actual) {return new EquivalencyMatcher(actual);}
}
