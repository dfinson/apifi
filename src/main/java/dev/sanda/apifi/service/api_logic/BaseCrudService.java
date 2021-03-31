package dev.sanda.apifi.service.api_logic;

import dev.sanda.apifi.service.api_hooks.ApiHooks;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static dev.sanda.datafi.DatafiStaticUtils.getId;

@Slf4j
@Component
@DependsOn("ApiLogic")
public abstract class BaseCrudService<T> {

    @Autowired
    protected ReflectionCache reflectionCache;

    protected DataManager<T> dataManager;
    protected ApiHooks<T> apiHooks;
    protected String entityName;
    protected String idFieldName;

    public void init(DataManager<T> dataManager, ApiHooks<T> apiHooks, boolean datafiLoggingEnabled){
        this.dataManager = dataManager;
        this.dataManager.setLoggingEnabled(datafiLoggingEnabled);
        this.apiHooks = apiHooks;
        this.entityName = dataManager.getClazzSimpleName();
        this.idFieldName = reflectionCache.getEntitiesCache().get(dataManager.getClazzSimpleName()).getIdField().getName();
    }

    protected void throw_entityNotFound(Object input, ReflectionCache reflectionCache) {
        final RuntimeException exception = new RuntimeException(
                "Cannot find Entity " + input.getClass().getSimpleName() + " with id " + getId(input, reflectionCache));
        logError(exception.toString());
        throw exception;
    }

    private static final Executor loggerThread = Executors.newSingleThreadExecutor();
    private static synchronized void log(String msg, boolean isError, Object... args){
        loggerThread.execute(() -> {
            if (isError) log.error(msg, args);
            else log.info(msg, args);
        });
    }
    protected void logInfo(String msg, Object... args){
        log(msg, false, args);
    }
    protected void logError(String msg, Object... args) {
        log(msg, true, args);
    }

}
