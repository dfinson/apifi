package dev.sanda.apifi.test_utils;

import dev.sanda.apifi.annotations.WithCRUDEndpoints;
import dev.sanda.apifi.generator.entity.CRUDEndpoints;
import dev.sanda.apifi.service.ApiHooks;
import dev.sanda.apifi.service.ApiLogic;
import dev.sanda.datafi.service.DataManager;
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
