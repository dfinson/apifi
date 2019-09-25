package org.sindaryn.apifi.generator;


import lombok.Data;
import lombok.Getter;
import lombok.NonNull;
import org.sindaryn.apifi.annotations.NonDirectlyExposable;


import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    public boolean exposeDirectly(VariableElement embeddedCollection){
        String typeNameString = getCollectionType(embeddedCollection);
        TypeElement typeElement = typeElementMap.get(typeNameString);
        return typeElement.getAnnotation(NonDirectlyExposable.class) == null;
    }
    public String getCollectionType(VariableElement element){
        return element
                .asType()
                .toString()
                .replaceAll("^.+<", "")
                .replaceAll(">", "");
    }
}
