package dev.sanda.apifi.utils.spqr_fixes;

import io.leangen.graphql.ExtensionProvider;
import io.leangen.graphql.GeneratorConfiguration;
import io.leangen.graphql.generator.mapping.TypeMapper;
import io.leangen.graphql.generator.mapping.common.IdAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphQLConfig {
    @Bean
    public ExtensionProvider<GeneratorConfiguration, TypeMapper> jodaMapper() {
        return (conf, current) -> current.insertAfter(IdAdapter.class, new JodaScalarMapper());
    }
}
