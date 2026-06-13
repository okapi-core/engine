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
import org.okapi.data.dao.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

@AutoConfiguration
@EnableConfigurationProperties(PostgresCfg.class)
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
  UsersDao usersDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new UsersDaoPg(jdbc);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  OrgDao orgDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new OrgDaoPg(jdbc);
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
  DashboardDao dashboardDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new DashboardDaoPg(jdbc, gson());
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  FederatedSourceRepo federatedSourceRepo(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new FederatedSourceRepoPg(jdbc);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardRowDao dashboardRowDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new DashboardRowDaoPg(jdbc, gson());
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardPanelDao dashboardPanelDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new DashboardPanelDaoPg(jdbc, gson());
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  UserEntityRelationsDao userEntityRelationsDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new UserEntityRelationsDaoPg(jdbc);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnBean(ResultUploader.class)
  @ConditionalOnMissingBean
  PendingJobsDao pendingJobsDao(
      @Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc, ResultUploader uploader) {
    return new PendingJobsDaoPg(jdbc, uploader, gson());
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  TokenMetaDao tokenMetaDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new TokenMetaDaoPg(jdbc);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardVarDao dashboardVarDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new DashboardVarDaoPg(jdbc);
  }

  @Bean
  @DependsOn("okapiFlyway")
  @ConditionalOnMissingBean
  DashboardVersionDao dashboardVersionDao(@Qualifier("okapiJdbcTemplate") JdbcTemplate jdbc) {
    return new DashboardVersionDaoPg(jdbc);
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
