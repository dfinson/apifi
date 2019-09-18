package org.sindaryn.apifi.generator;


import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import org.sindaryn.apifi.annotations.GraphQLApiEntity;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.sindaryn.datafi.StaticUtils.logCompilationError;

@Getter
@Data
public class EntitiesInfoCache {
    @NonNull
    private ProcessingEnvironment processingEnvironment;
    private Map<String, TypeElement> typeElementMap;
    public void setTypeElementMap(Set<? extends TypeElement> typeElements){
        typeElementMap = new HashMap<>();
        for(TypeElement typeElement : typeElements){
            typeElementMap.put(typeElement.getQualifiedName().toString(), typeElement);
        }
    }
    public boolean isStrongEntity(VariableElement embeddedCollection){
        String typeNameString = getCollectionType(embeddedCollection);
        TypeElement typeElement = typeElementMap.get(typeNameString);
        /*if(typeElement == null){
            processingEnvironment
                    .getMessager()
                    .printMessage(Diagnostic.Kind.ERROR,
                            "Entity type " + typeNameString +
                                    " is referenced by another entity" +
                                    " but is not annotated with @GraphQLApi. " +
                                    "Either remove @GraphQLApi annotation from the " +
                                    "referencing entity, or annotate " + typeNameString +
                                    " as well.",
                            typeElement);
        }*/
        GraphQLApiEntity graphQLApiEntity = typeElement.getAnnotation(GraphQLApiEntity.class);
        return graphQLApiEntity.exposeDirectly();
    }
    public String getCollectionType(VariableElement element){
        return element
                .asType()
                .toString()
                .replaceAll("^.+<", "")
                .replaceAll(">", "");
    }
}
