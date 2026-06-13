/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class PostgresDataStore {
  private static final String DEFAULT_URL =
      "jdbc:postgresql://127.0.0.1:5432/okapi_oscar?currentSchema=okapi_web";
  private static final String DEFAULT_USER = "okapi_web_user";
  private static final String DEFAULT_PASSWORD = "okapi_web_password";

  private final String url;
  private final String user;
  private final String password;
  private final Gson gson;

  public static PostgresDataStore fromEnvironment() {
    return new PostgresDataStore(
        System.getenv().getOrDefault("OKAPI_WEB_DB_URL", DEFAULT_URL),
        System.getenv().getOrDefault("OKAPI_WEB_DB_USER", DEFAULT_USER),
        System.getenv().getOrDefault("OKAPI_WEB_DB_PASSWORD", DEFAULT_PASSWORD));
  }

  public PostgresDataStore(String url, String user, String password) {
    this.url = url;
    this.user = user;
    this.password = password;
    this.gson =
        new GsonBuilder()
            .registerTypeAdapter(
                Instant.class,
                new TypeAdapter<Instant>() {
                  @Override
                  public void write(JsonWriter out, Instant value) throws IOException {
                    if (value == null) out.nullValue();
                    else out.value(value.toString());
                  }

                  @Override
                  public Instant read(JsonReader in) throws IOException {
                    if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                      in.nextNull();
                      return null;
                    }
                    return Instant.parse(in.nextString());
                  }
                })
            .create();
    initialize();
  }

  Connection connection() throws SQLException {
    return DriverManager.getConnection(url, user, password);
  }

  Gson gson() {
    return gson;
  }

  void initialize() {
    execute(
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
    execute(
        "create index if not exists data_records_scope_idx "
            + "on data_records(record_type, scope, scope2)");
    execute(
        "create index if not exists data_records_status_idx "
            + "on data_records(record_type, scope, status, source)");
    execute(
        "create unique index if not exists data_records_user_email_idx "
            + "on data_records(lower(scope)) where record_type = 'user'");
  }

  public void clearAll() {
    execute("truncate table data_records");
  }

  void put(
      String type,
      String key,
      String scope,
      String scope2,
      String status,
      String source,
      Object value) {
    var sql =
        """
        insert into data_records(record_type, record_key, scope, scope2, status, source, payload)
        values (?, ?, ?, ?, ?, ?, ?::jsonb)
        on conflict (record_type, record_key) do update set
          scope = excluded.scope,
          scope2 = excluded.scope2,
          status = excluded.status,
          source = excluded.source,
          payload = excluded.payload
        """;
    try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
      statement.setString(1, type);
      statement.setString(2, key);
      statement.setString(3, scope);
      statement.setString(4, scope2);
      statement.setString(5, status);
      statement.setString(6, source);
      statement.setString(7, gson.toJson(value));
      statement.executeUpdate();
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  <T> Optional<T> get(String type, String key, Class<T> clazz) {
    return queryOne(
        "select payload::text from data_records where record_type = ? and record_key = ?",
        clazz,
        type,
        key);
  }

  <T> Optional<T> findByScope(String type, String scope, Class<T> clazz) {
    return queryOne(
        "select payload::text from data_records "
            + "where record_type = ? and lower(scope) = lower(?) limit 1",
        clazz,
        type,
        scope);
  }

  <T> List<T> list(String type, String scope, String scope2, Class<T> clazz) {
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

  <T> List<T> listByStatus(
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

  void delete(String type, String key) {
    update("delete from data_records where record_type = ? and record_key = ?", type, key);
  }

  int deleteByScope(String type, String scope) {
    return update("delete from data_records where record_type = ? and scope = ?", type, scope);
  }

  int deleteBySource(String type, String source) {
    return update("delete from data_records where record_type = ? and source = ?", type, source);
  }

  private <T> Optional<T> queryOne(String sql, Class<T> clazz, Object... args) {
    var values = query(sql, clazz, args);
    return values.stream().findFirst();
  }

  private <T> List<T> query(String sql, Class<T> clazz, Object... args) {
    try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
      bind(statement, args);
      try (var result = statement.executeQuery()) {
        var values = new ArrayList<T>();
        while (result.next()) values.add(gson.fromJson(result.getString(1), clazz));
        return values;
      }
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  private int update(String sql, Object... args) {
    try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
      bind(statement, args);
      return statement.executeUpdate();
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  private void execute(String sql) {
    try (var connection = connection(); var statement = connection.createStatement()) {
      statement.execute(sql);
    } catch (SQLException e) {
      throw failure(e);
    }
  }

  private void bind(java.sql.PreparedStatement statement, Object... args) throws SQLException {
    for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
  }

  private IllegalStateException failure(SQLException e) {
    return new IllegalStateException("PostgreSQL data operation failed", e);
  }
}
