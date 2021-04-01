package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import dev.sanda.datafi.DatafiStaticUtils;
import dev.sanda.datafi.reflection.runtime_services.ReflectionCache;
import dev.sanda.datafi.service.DataManager;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

import java.util.Collection;

import static dev.sanda.datafi.DatafiStaticUtils.getIdList;

@Data
@Slf4j
public class PubSubTopicHandler {
    private final String id;
    private final FluxSink downStreamSubscriber;
    private final DataManager dataManager;
    private final ReflectionCache reflectionCache;

    @SneakyThrows
    public void handleDataInTransaction(Object data){
        log.debug("As its name suggests, this method should be run within a transaction so as to avoid lazy loading exceptions");
        if(isSingleEntity(data))
            data = dataManager.findById(DatafiStaticUtils.getId(data, reflectionCache)).orElseThrow(RuntimeException::new);
        else if(isEntityCollection(data))
            data = dataManager.findAllById(getIdList((Collection)data, reflectionCache));
        downStreamSubscriber.next(data);
    }

    private boolean isEntityCollection(Object data) {
        return data instanceof Collection &&
               !((Collection)data).isEmpty() &&
                isSingleEntity(((Collection)data).iterator().next());
    }

    private boolean isSingleEntity(Object data) {
        return !(data instanceof Collection) && reflectionCache.getEntitiesCache().containsKey(data.getClass().getSimpleName());
    }

    public void complete(){
        if(!downStreamSubscriber.isCancelled())
            downStreamSubscriber.complete();
    }

    public void completeWithError(Throwable error) {
        if(!downStreamSubscriber.isCancelled())
            downStreamSubscriber.error(error);
    }
}
