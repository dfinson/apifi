package dev.sanda.apifi.service.graphql_subcriptions.pubsub;

import lombok.Data;
import reactor.core.publisher.FluxSink;

@Data
public class PubSubTopicHandler {
    private final FluxSink downStreamSubscriber;

    public void handleData(Object data){
        downStreamSubscriber.next(data);
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
