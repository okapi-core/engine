/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.cfg.SchemaToolingSettings;
import org.okapi.data.dao.*;
import org.okapi.data.pg.entity.DashboardVariableEntity;
import org.okapi.data.pg.repository.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(PostgresCfg.class)
@EntityScan(basePackageClasses = DashboardVariableEntity.class)
@EnableJpaRepositories(basePackageClasses = DashboardVariableRepository.class)
public class PostgresDataAutoConfiguration {
  @Bean
  @ConditionalOnMissingBean(name = "okapiDataSource")
  DataSource okapiDataSource(PostgresCfg cfg) {
    var dataSource = new PGSimpleDataSource();
    dataSource.setUrl(cfg.getUrl());
    dataSource.setUser(cfg.getUsername());
    dataSource.setPassword(cfg.getPassword());
    return dataSource;
  }

  @Bean
  @ConditionalOnMissingBean(name = "okapiJdbcTemplate")
  JdbcTemplate okapiJdbcTemplate(@Qualifier("okapiDataSource") DataSource okapiDataSource) {
    return new JdbcTemplate(okapiDataSource);
  }

  @Bean
  HibernatePropertiesCustomizer disableHibernateSchemaManagement() {
    return properties -> properties.put(SchemaToolingSettings.HBM2DDL_AUTO, "none");
  }

  @Bean
  @ConditionalOnMissingBean(name = "okapiFlyway")
  Flyway okapiFlyway(@Qualifier("okapiDataSource") DataSource okapiDataSource) {
    var flyway =
        Flyway.configure()
            .dataSource(okapiDataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load();
    flyway.migrate();
    return flyway;
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  UsersDao usersDao(UserRepository repository) {
    return new UsersDaoPg(repository);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  OrgDao orgDao(OrganizationRepository repository) {
    return new OrgDaoPg(repository);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  RelationGraphDao relationGraphDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new RelationGraphDaoPg(jdbc);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardDao dashboardDao(DashboardRepository repository) {
    return new DashboardDaoPg(repository, gson());
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  FederatedSourceRepo federatedSourceRepo(FederatedSourceRepository repository) {
    return new FederatedSourceRepoPg(repository);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardRowDao dashboardRowDao(DashboardRowRepository repository) {
    return new DashboardRowDaoPg(repository, gson());
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardPanelDao dashboardPanelDao(DashboardPanelRepository repository) {
    return new DashboardPanelDaoPg(repository, gson());
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  UserEntityRelationsDao userEntityRelationsDao(UserEntityRelationRepository repository) {
    return new UserEntityRelationsDaoPg(repository);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnBean(ResultUploader.class)
  @ConditionalOnMissingBean
  PendingJobsDao pendingJobsDao(PendingJobRepository repository, ResultUploader uploader) {
    return new PendingJobsDaoPg(repository, uploader, gson());
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  TokenMetaDao tokenMetaDao(TokenMetadataRepository repository) {
    return new TokenMetaDaoPg(repository);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardVarDao dashboardVarDao(DashboardVariableRepository repository) {
    return new DashboardVarDaoPg(repository);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardVersionDao dashboardVersionDao(DashboardVersionRepository repository) {
    return new DashboardVersionDaoPg(repository);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  InfraEntityNodeDao infraEntityNodeDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new InfraEntityNodeDaoPg(jdbc, gson());
  }

  private static Gson gson() {
    return new GsonBuilder().registerTypeAdapter(Instant.class, new InstantAdapter()).create();
  }

  private static final class InstantAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
      if (value == null) out.nullValue();
      else out.value(value.toString());
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      return Instant.parse(in.nextString());
    }
  }
}
