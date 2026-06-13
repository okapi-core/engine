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
import org.okapi.data.dao.*;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
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
  @ConditionalOnMissingBean
  JdbcRecordStore jdbcRecordStore(
      @Qualifier("okapiJdbcTemplate") JdbcTemplate okapiJdbcTemplate) {
    return new JdbcRecordStore(
        okapiJdbcTemplate,
        new GsonBuilder().registerTypeAdapter(Instant.class, new InstantAdapter()).create());
  }

  @Bean
  @ConditionalOnMissingBean
  UsersDao usersDao(JdbcRecordStore store) {
    return new UsersDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  OrgDao orgDao(JdbcRecordStore store) {
    return new OrgDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  RelationGraphDao relationGraphDao(JdbcRecordStore store) {
    return new RelationGraphDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  DashboardDao dashboardDao(JdbcRecordStore store) {
    return new DashboardDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  FederatedSourceRepo federatedSourceRepo(JdbcRecordStore store) {
    return new FederatedSourceRepoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  DashboardRowDao dashboardRowDao(JdbcRecordStore store) {
    return new DashboardRowDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  DashboardPanelDao dashboardPanelDao(JdbcRecordStore store) {
    return new DashboardPanelDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  UserEntityRelationsDao userEntityRelationsDao(JdbcRecordStore store) {
    return new UserEntityRelationsDaoPg(store);
  }

  @Bean
  @ConditionalOnBean(ResultUploader.class)
  @ConditionalOnMissingBean
  PendingJobsDao pendingJobsDao(JdbcRecordStore store, ResultUploader uploader) {
    return new PendingJobsDaoPg(store, uploader);
  }

  @Bean
  @ConditionalOnMissingBean
  TokenMetaDao tokenMetaDao(JdbcRecordStore store) {
    return new TokenMetaDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  DashboardVarDao dashboardVarDao(JdbcRecordStore store) {
    return new DashboardVarDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  DashboardVersionDao dashboardVersionDao(JdbcRecordStore store) {
    return new DashboardVersionDaoPg(store);
  }

  @Bean
  @ConditionalOnMissingBean
  InfraEntityNodeDao infraEntityNodeDao(JdbcRecordStore store) {
    return new InfraEntityNodeDaoPg(store);
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
