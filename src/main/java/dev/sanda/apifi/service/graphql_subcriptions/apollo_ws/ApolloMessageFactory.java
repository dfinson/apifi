package dev.sanda.apifi.service.graphql_subcriptions.apollo_ws;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.messages.ApolloMessage;
import dev.sanda.apifi.service.graphql_subcriptions.apollo_ws.messages.ApolloPayloadMessage;
import graphql.ErrorType;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import lombok.SneakyThrows;
import lombok.val;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ApolloMessageFactory {

    //Client messages
    public static final String GRAPHQL_CONNECTION_INIT = "connection_init";
    public static final String GRAPHQL_CONNECTION_TERMINATE = "connection_terminate";
    public static final String GRAPHQL_START = "start";
    public static final String GRAPHQL_STOP = "stop";

    //Server messages
    public static final String GRAPHQL_CONNECTION_ACK = "connection_ack";
    public static final String GRAPHQL_CONNECTION_ERROR = "connection_error";
    public static final String GRAPHQL_CONNECTION_KEEP_ALIVE = "ka";
    public static final String GRAPHQL_DATA = "data";
    public static final String GRAPHQL_ERROR = "error";
    public static final String GRAPHQL_COMPLETE = "complete";

    public static final ApolloMessage CONNECTION_ACK = new ApolloMessage(GRAPHQL_CONNECTION_ACK);
    public static final ApolloMessage KEEP_ALIVE = new ApolloMessage(GRAPHQL_CONNECTION_ACK);

    private static final Class<ApolloPayloadMessage> apolloPayloadMessageClass = ApolloPayloadMessage.class;
    private static final Class<ApolloMessage> apolloMessageClass = ApolloMessage.class;


    private static ObjectMapper mapper;

    @PostConstruct
    private void initMapper(){
        mapper = new ObjectMapper();
        val module = new SimpleModule();
        module.addDeserializer(apolloMessageClass, new JsonDeserializer<ApolloMessage>() {
            @Override @SneakyThrows
            public ApolloMessage deserialize(JsonParser jsonParser, DeserializationContext deserializationContext){
                val node = (JsonNode) jsonParser.getCodec().readTree(jsonParser);
                val apolloMessage = new ApolloMessage();
                apolloMessage.setId(node.has("id") ? node.get("id").asText() : null);
                apolloMessage.setType(node.has("type") ? node.get("type").asText() : null);
                return apolloMessage;
            }
        }).addSerializer(apolloMessageClass, new JsonSerializer<ApolloMessage>() {
            @Override @SneakyThrows
            public void serialize(ApolloMessage apolloMessage, JsonGenerator gen, SerializerProvider serializerProvider) {
                gen.writeStartObject();
                gen.writeFieldName("id");
                gen.writeString(apolloMessage.getId());
                gen.writeFieldName("type");
                gen.writeString(apolloMessage.getType());
                gen.writeEndObject();
            }
        }).addDeserializer(apolloPayloadMessageClass, new JsonDeserializer<ApolloPayloadMessage>() {
            @Override @SneakyThrows
            public ApolloPayloadMessage deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {
                val node = (JsonNode) jsonParser.getCodec().readTree(jsonParser);
                val apolloPayloadMessage = new ApolloPayloadMessage<>((Object) node.get("payload"));
                apolloPayloadMessage.setId(node.has("id") ? node.get("id").asText() : null);
                apolloPayloadMessage.setType(node.has("type") ? node.get("type").asText() : null);
                return apolloPayloadMessage;
            }
        }).addSerializer(apolloPayloadMessageClass, new JsonSerializer<ApolloPayloadMessage>() {
            @Override @SneakyThrows
            public void serialize(ApolloPayloadMessage apolloPayloadMessage, JsonGenerator gen, SerializerProvider serializerProvider) {
                gen.writeStartObject();
                gen.writeFieldName("id");
                gen.writeString(apolloPayloadMessage.getId());
                gen.writeFieldName("type");
                gen.writeString(apolloPayloadMessage.getType());
                gen.writeObjectField("payload", apolloPayloadMessage.getPayload());
                gen.writeEndObject();
            }
        });
        mapper.registerModule(module);
    }

    public static ApolloMessage from(TextMessage message) throws IOException {
        return  message.getPayload().contains("\"payload\"")
                ? mapper.readValue(message.getPayload(), apolloPayloadMessageClass)
                : mapper.readValue(message.getPayload(), apolloMessageClass);
    }

    public static TextMessage connectionAck() throws JsonProcessingException {
        return jsonMessage(CONNECTION_ACK);
    }

    public static TextMessage keepAlive() throws JsonProcessingException {
        return jsonMessage(KEEP_ALIVE);
    }

    public static TextMessage connectionError(final String message) throws JsonProcessingException {
        val errors = Collections.singletonMap("message", message);
        return jsonMessage(new ApolloPayloadMessage<>(GRAPHQL_CONNECTION_ERROR, errors));
    }

    public static TextMessage connectionError() throws JsonProcessingException {
        return connectionError("Invalid message");
    }

    public static TextMessage data(String id, ExecutionResult result) throws JsonProcessingException {
        return jsonMessage(new ApolloPayloadMessage<>(id, GRAPHQL_DATA, result.toSpecification()));
    }

    public static TextMessage complete(String id) throws JsonProcessingException {
        return jsonMessage(new ApolloMessage(id, GRAPHQL_COMPLETE));
    }

    public static TextMessage error(String id, List<GraphQLError> errors) throws JsonProcessingException {
        val errorMap =
                 errors
                .stream()
                .filter(error -> !error.getErrorType().equals(ErrorType.DataFetchingException))
                .map(GraphQLError::toSpecification)
                .collect(Collectors.toList());
        return jsonMessage(new ApolloPayloadMessage<>(id, GRAPHQL_ERROR, errorMap));
    }

    public static TextMessage error(String id, Throwable exception) throws JsonProcessingException {
        return error(id, exception.getMessage());
    }

    public static TextMessage error(String id, String message) throws JsonProcessingException {
        val errorMap = Collections.singletonList(Collections.singletonMap("message", message));
        return jsonMessage(new ApolloPayloadMessage<>(id, GRAPHQL_ERROR, errorMap));
    }

    private static TextMessage jsonMessage(ApolloMessage message) throws JsonProcessingException {
        return new TextMessage(mapper.writeValueAsString(message));
    }

}
