package dev.sanda.apifi.generator.client;

import lombok.Data;
import lombok.val;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
public class ApifiClientFactory {

    private List<GraphQLQueryBuilder> queries = new ArrayList<>();
    private boolean isTypescriptMode;
    private ProcessingEnvironment processingEnv;
    private Set<TypeElement> entities;
    private Set<TypeElement> enums;

    public void addQuery(GraphQLQueryBuilder graphQLQueryBuilder){
        if(queries == null) queries = new ArrayList<>();
        queries.add(graphQLQueryBuilder);
    }

    public void generate(){
        StringBuilder builder = new StringBuilder();
        builder.append("let apiUrl = location.origin + '/graphql';\n");
        builder.append(String.format("let bearerToken%s;\n\n", isTypescriptMode ? ": string" : ""));
        if(isTypescriptMode)
            builder.append(TypescriptModelFactory.objectModel(entities, enums, processingEnv));
        builder.append("\n\n// project specific client side API calls\n");
        builder.append("\nexport default{\n");
        builder.append("\n\tsetBearerToken(token")
                .append(isTypescriptMode ? ": string" : "")
                .append("){\n\t\tbearerToken = token;\n\t},\n");
        builder.append("\n\tsetApiUrl(url")
                .append(isTypescriptMode ? ": string" : "")
                .append("){\n\t\tapiUrl = url;\n\t},\n");
        queries.forEach(query -> builder.append(generateQueryFetcher(query)));
        builder.append("\n}");
        val finalContent = builder.toString();
        try {
            Path dirPath = Paths.get("Apifi clients");
            if(!Files.exists(dirPath))
                Files.createDirectory(dirPath);
            Path path = Paths.get(dirPath.toString(), clientName());
            Files.deleteIfExists(path);
            Files.createFile(path);
            Files.write(path, finalContent.getBytes());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private String clientName(){
        return isTypescriptMode ? "apifiClientTS.ts" : "apifiClientJS.js";
    }

    private String generateQueryFetcher(GraphQLQueryBuilder query) {
        query.setTypescriptMode(isTypescriptMode);
        return "\n\tasync " +
                query.getQueryName() +
                "(" +
                    query.args() +
                    "customHeaders" +
                    customHeadersType() +
                ")" +
                queryFetcherReturnType(query) +
                "{\n" +
                "\t\t\tlet requestHeaders = { \"Content-Type\": \"application/json\" }\n" +
                "\t\t\tif(customHeaders) requestHeaders = Object.assign({}, requestHeaders, customHeaders);\n" +
                "\t\t\tif(bearerToken) requestHeaders[\"Authorization\"] = bearerToken;\n" +
                "\t\t\tconst requestInit" + (isTypescriptMode ? ": RequestInit" : "") + " = {\n" +
                "\t\t\t\tmethod: \"POST\",\n" +
                "\t\t\t\tcredentials: 'include',\n" +
                "\t\t\t\theaders: requestHeaders,\n" +
                "\t\t\t\tbody: JSON.stringify({" + query.buildQueryString() + "\t})" +
                "\n\t\t\t};\n" +
                "\t\t\treturn await (await fetch(apiUrl, requestInit)).json();" +
                "\n\t},\n";
    }

    private String queryFetcherReturnType(GraphQLQueryBuilder query) {
        return isTypescriptMode ? ": Promise<GraphQLResult<" + resolveQueryPromiseType(query) + ">>" : "";
    }

    private String resolveQueryPromiseType(GraphQLQueryBuilder query) {
        val returnType = query.getEntityReturnType();
        switch (query.getClientSideReturnType()){
            case PAGE: return String.format("Page<%s>", returnType);
            case NUMBER: return "number";
            case INSTANCE: return returnType;
            case ARRAY: return String.format("Array<%s>", returnType);
            case SET: return String.format("Set<%s>", returnType);
            case MAP: return String.format("Map<%s>", returnType);
            default: return "any";
        }
    }

    private String customHeadersType(){
        return isTypescriptMode ? "?: Dictionary<string>" : "";
    }
}
