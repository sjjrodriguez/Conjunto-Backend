package com.residencial.conjunto;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.Map;

/**
 * P7: Hibernate toma el conjunto de ConjuntoContexto, los repositorios revisan el conjunto al cargar
 * por id y los avisos en segundo plano (@Async) heredan el conjunto de quien los creó.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.residencial.repository", repositoryBaseClass = RepositorioPorConjunto.class)
public class ConjuntoConfig implements CurrentTenantIdentifierResolver<Long>, HibernatePropertiesCustomizer {

    @Override
    public Long resolveCurrentTenantIdentifier() {
        return ConjuntoContexto.actual();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(Long id) {
        return id != null && id == ConjuntoContexto.RAIZ;
    }

    @Override
    public void customize(Map<String, Object> propiedades) {
        propiedades.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
    }

    /** Spring Boot lo aplica al ejecutor de @Async: el envío de un aviso sigue en el conjunto que lo creó. */
    @Bean
    TaskDecorator conjuntoEnSegundoPlano() {
        return tarea -> {
            Long id = ConjuntoContexto.fijado();
            return () -> {
                Long antes = ConjuntoContexto.fijado();
                ConjuntoContexto.fijar(id);
                try {
                    tarea.run();
                } finally {
                    ConjuntoContexto.fijar(antes);
                }
            };
        };
    }
}
