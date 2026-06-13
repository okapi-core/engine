/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcRecordStore {
  private final JdbcTemplate jdbc;
  private final Gson gson;

  public JdbcRecordStore(JdbcTemplate jdbc, Gson gson) {
    this.jdbc = jdbc;
    this.gson = gson;
    initialize();
  }

  public Gson gson() {
    return gson;
  }

  public void clearAll() {
    jdbc.execute("truncate table data_records");
  }

  public void put(
      String type,
      String key,
      String scope,
      String scope2,
      String status,
      String source,
      Object value) {
    jdbc.update(
        """
        insert into data_records(record_type, record_key, scope, scope2, status, source, payload)
        values (?, ?, ?, ?, ?, ?, ?::jsonb)
        on conflict (record_type, record_key) do update set
          scope = excluded.scope,
          scope2 = excluded.scope2,
          status = excluded.status,
          source = excluded.source,
          payload = excluded.payload
        """,
        type,
        key,
        scope,
        scope2,
        status,
        source,
        gson.toJson(value));
  }

  public <T> Optional<T> get(String type, String key, Class<T> clazz) {
    return query(
            "select payload::text from data_records where record_type = ? and record_key = ?",
            clazz,
            type,
            key)
        .stream()
        .findFirst();
  }

  public <T> Optional<T> findByScope(String type, String scope, Class<T> clazz) {
    return query(
            "select payload::text from data_records "
                + "where record_type = ? and lower(scope) = lower(?) limit 1",
            clazz,
            type,
            scope)
        .stream()
        .findFirst();
  }

  public <T> List<T> list(String type, String scope, String scope2, Class<T> clazz) {
    if (scope == null) {
      return query(
          "select payload::text from data_records where record_type = ? order by record_key",
          clazz,
          type);
    }
    if (scope2 == null) {
      return query(
          "select payload::text from data_records "
              + "where record_type = ? and scope = ? order by record_key",
          clazz,
          type,
          scope);
    }
    return query(
        "select payload::text from data_records "
            + "where record_type = ? and scope = ? and scope2 = ? order by record_key",
        clazz,
        type,
        scope,
        scope2);
  }

  public <T> List<T> listByStatus(
      String type, String scope, String source, String status, int limit, Class<T> clazz) {
    if (source == null) {
      return query(
          "select payload::text from data_records "
              + "where record_type = ? and scope = ? and status = ? order by record_key limit ?",
          clazz,
          type,
          scope,
          status,
          limit);
    }
    return query(
        "select payload::text from data_records where record_type = ? and scope = ? "
            + "and source = ? and status = ? order by record_key limit ?",
        clazz,
        type,
        scope,
        source,
        status,
        limit);
  }

  public void delete(String type, String key) {
    jdbc.update("delete from data_records where record_type = ? and record_key = ?", type, key);
  }

  public void deleteByScope(String type, String scope) {
    jdbc.update("delete from data_records where record_type = ? and scope = ?", type, scope);
  }

  public void deleteBySource(String type, String source) {
    jdbc.update("delete from data_records where record_type = ? and source = ?", type, source);
  }

  private <T> List<T> query(String sql, Class<T> clazz, Object... args) {
    return jdbc.query(sql, (result, row) -> gson.fromJson(result.getString(1), clazz), args);
  }

  private void initialize() {
    jdbc.execute(
        """
        create table if not exists data_records (
          record_type text not null,
          record_key text not null,
          scope text,
          scope2 text,
          status text,
          source text,
          payload jsonb not null,
          primary key (record_type, record_key)
        )
        """);
    jdbc.execute(
        "create index if not exists data_records_scope_idx "
            + "on data_records(record_type, scope, scope2)");
    jdbc.execute(
        "create index if not exists data_records_status_idx "
            + "on data_records(record_type, scope, status, source)");
    jdbc.execute(
        "create unique index if not exists data_records_user_email_idx "
            + "on data_records(lower(scope)) where record_type = 'user'");
  }
}
