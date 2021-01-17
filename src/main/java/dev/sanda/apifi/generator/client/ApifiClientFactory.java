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
        builder.append("let bearerToken = undefined;\n\n");
        if(isTypescriptMode)
            builder.append(TypescriptModelFactory.objectModel(entities, enums, processingEnv));
        builder.append("\n\n// project specific client side API calls\n");
        builder.append("\nexport default{\n");
        builder.append("\n\tsetBearerToken(token){\n\t\tbearerToken = token;\n\t},\n");
        builder.append("\n\tsetApiUrl(url){\n\t\tapiUrl = url;\n\t},\n");
        queries.forEach(query -> builder.append(generateQueryFetcher(query)));
        builder.append("\n}");
        val finalContent = builder.toString();
        try {
            Path path = Paths.get(clientName());
            if(Files.exists(path)) Files.delete(path);
            Files.createFile(path);
            Files.write(path, finalContent.getBytes());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private String clientName(){
        return isTypescriptMode ? "typedApifiClient.ts" : "apifiClient.js";
    }

    private String generateQueryFetcher(GraphQLQueryBuilder query) {

        return "\n\tasync " + query.getQueryName() + "(" + query.args() + "customHeaders)" +
                "{\n" +
                "\t\t\tlet requestHeaders = { \"Content-Type\": \"application/json\" }\n" +
                "\t\t\tif(customHeaders !== undefined) requestHeaders = Object.assign({}, requestHeaders, customHeaders);\n" +
                "\t\t\tif(bearerToken !== undefined) requestHeaders[\"Authorization\"] = bearerToken;\n" +
                "\t\t\tconst opts = {\n" +
                "\t\t\t\tmethod: \"POST\",\n" +
                "\t\t\t\tcredentials: \"include\",\n" +
                "\t\t\t\theaders: requestHeaders,\n" +
                "\t\t\t\tbody: JSON.stringify({" + query.buildQueryString() + "\t})" +
                "\n\t\t\t};\n" +
                "\t\t\treturn await (await fetch(apiUrl, opts)).json();" +
                "\n\t},\n";
    }
}
