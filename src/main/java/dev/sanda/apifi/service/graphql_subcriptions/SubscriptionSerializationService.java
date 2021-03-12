package dev.sanda.apifi.service.graphql_subcriptions;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.SneakyThrows;
import lombok.val;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.lang.reflect.Field;

@Component
public class SubscriptionSerializationService {

    private static final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    private void initMapper(){
        val module = new SimpleModule();
        module.addSerializer(Publisher.class, new JsonSerializer<Publisher>() {
            @SneakyThrows
            @Override
            public void serialize(// {"source":{},"backpressure":"BUFFER","createMode":"PUSH_PULL"}
                    Publisher publisher,
                    JsonGenerator gen,
                    SerializerProvider serializerProvider) throws IOException {
                gen.writeStartObject();
                for (Field field : publisher.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    if(field.getType().isPrimitive() || field.getType().equals(String.class) || field.getType().isEnum())
                        gen.writeObjectField(field.getName(), field.get(publisher));
                    else {
                        val value = field.get(publisher);
                        for (Field nestedField : field.getType().getDeclaredFields()) {
                            nestedField.setAccessible(true);
                            gen.writeObjectField(field.getName(), field.get(value));
                        }
                    }
                }
                gen.writeEndObject();
            }
        });
        mapper.registerModule(module);
    }

    public String serializePublisher(Publisher publisher){
        try {
            return mapper.writeValueAsString(publisher);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
