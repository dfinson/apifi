package dev.sanda.apifi.test_utils;

import lombok.val;

import java.util.Arrays;

public interface TestableGraphQLService<TEntity> {
    default <TReturn> TReturn invokeEndpoint(String methodName, Object... args){
        try {
            val params = Arrays.stream(args).map(Object::getClass).toArray(Class[]::new);
            val methodToInvoke = this.getClass().getMethod(methodName, params);
            return (TReturn) methodToInvoke.invoke(this, args);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
