package dev.sanda.apifi.generator.client;

import lombok.Data;
import lombok.val;

import javax.annotation.processing.ProcessingEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static dev.sanda.apifi.utils.ApifiStaticUtils.inQuotes;

@Data
public class ApifiClientFactory {

    private List<GraphQLQueryBuilder> queries = new ArrayList<>();
    private String fileName = "apifiClient";

    public void addQuery(GraphQLQueryBuilder graphQLQueryBuilder){
        if(queries == null) queries = new ArrayList<>();
        queries.add(graphQLQueryBuilder);
    }

    public void generate(){//TODO custom headers
        StringBuilder builder = new StringBuilder();
        builder.append("let apiUrl = ").append(apiUrl()).append(";\n");
        builder.append("let bearerToken = undefined;\n\n");
        builder.append("export default{\n");
        builder.append("\n\tsetBearerToken(token){\n\t\tbearerToken = token;\n\t},\n");
        builder.append("\n\tsetApiUrl(url){\n\t\tapiUrl = url;\n\t},\n");
        queries.forEach(query -> builder.append(generateQueryFetcher(query)));
        builder.append("\n}");
        val finalContent = builder.toString();
        try {
            Path path = Paths.get(fileName + ".js");
            if(Files.exists(path)) Files.delete(path);
            Files.createFile(path);
            Files.write(path, finalContent.getBytes());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private String generateQueryFetcher(GraphQLQueryBuilder query) {

        return "\n\tasync " + query.getQueryName() + "(" + query.args() + ")" +
                "{\n" +
                "\t\t\tlet requestHeaders = { \"Content-Type\": \"application/json\" }\n" +
                "\t\t\tif(bearerToken !== undefined) requestHeaders[\"Authorization\"] = bearerToken;\n" +
                "\t\t\tlet opts = {\n" +
                "\t\t\t\tmethod: \"POST\",\n" +
                "\t\t\t\tcredentials: \"include\",\n" +
                "\t\t\t\theaders: requestHeaders,\n" +
                "\t\t\t\tbody: JSON.stringify({" + query.buildQueryString() + "\t})" +
                "\n\t\t\t};\n" +
                "\t\t\treturn await (await fetch(apiUrl, opts)).json();" +
                "\n\t},\n";
    }

    private String apiUrl(){
        val url = System.getenv("API_URL");
        return url != null ? inQuotes(url) : "undefined";
    }
}
