package com.company.expense.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class EmbeddedPostgresConfiguration {

    @Bean(destroyMethod = "close")
    EmbeddedPostgres embeddedPostgres() throws IOException {
        return EmbeddedPostgres.start();
    }

    @Bean
    DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
        return DataSourceBuilder.create()
                .url(embeddedPostgres.getJdbcUrl("postgres", "postgres"))
                .build();
    }
}
