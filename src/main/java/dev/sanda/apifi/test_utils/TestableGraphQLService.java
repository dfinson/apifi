package dev.sanda.apifi.test_utils;

import lombok.SneakyThrows;
import lombok.val;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

public interface TestableGraphQLService<TEntity> {

    Map<String, Method> getMethodsMap();

    @SneakyThrows
    default <TReturn> TReturn invokeEndpoint(String methodName, Object... args){
        try {
            val methodToInvoke = getMethodsMap().get(methodName);
            if(methodToInvoke == null) throw new NoSuchMethodException();
            return (TReturn) methodToInvoke.invoke(this, args);
        }
        catch (InvocationTargetException e){
            throw e.getTargetException();
        }
        catch (IllegalAccessException | NoSuchMethodException e){
            throw new RuntimeException(e);
        }
    }
}
