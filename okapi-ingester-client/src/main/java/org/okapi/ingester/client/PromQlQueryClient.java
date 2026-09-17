/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.ingester.client;

import java.io.IOException;
import java.util.List;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class PromQlQueryClient {

  private final String endpoint;
  private final OkHttpClient client;
  private final ProxyResponseTranslator translator;

  public PromQlQueryClient(
      String endpoint, OkHttpClient client, ProxyResponseTranslator translator) {
    this.endpoint = endpoint;
    this.client = client;
    this.translator = translator;
  }

  public String queryInstant(String query, String time, String timeout) {
    var url = urlBuilder("/api/v1/query").addQueryParameter("query", query);
    addOptionalQueryParameter(url, "time", time);
    addOptionalQueryParameter(url, "timeout", timeout);
    return getRequest(url.build());
  }

  public String queryInstantPost(String query, String time, String timeout) {
    var form = new FormBody.Builder().add("query", query);
    addOptionalFormField(form, "time", time);
    addOptionalFormField(form, "timeout", timeout);
    return postFormRequest("/api/v1/query", form);
  }

  public String queryRange(String query, String start, String end, String step, String timeout) {
    var url =
        urlBuilder("/api/v1/query_range")
            .addQueryParameter("query", query)
            .addQueryParameter("start", start)
            .addQueryParameter("end", end)
            .addQueryParameter("step", step);
    addOptionalQueryParameter(url, "timeout", timeout);
    return getRequest(url.build());
  }

  public String queryRangePost(
      String query, String start, String end, String step, String timeout) {
    var form =
        new FormBody.Builder()
            .add("query", query)
            .add("start", start)
            .add("end", end)
            .add("step", step);
    addOptionalFormField(form, "timeout", timeout);
    return postFormRequest("/api/v1/query_range", form);
  }

  public String listLabels(String start, String end, List<String> matchers) {
    var url = urlBuilder("/api/v1/labels");
    addOptionalQueryParameter(url, "start", start);
    addOptionalQueryParameter(url, "end", end);
    addMatchers(url, matchers);
    return getRequest(url.build());
  }

  public String listLabelValues(String label, String start, String end, List<String> matchers) {
    var url = urlBuilder("/api/v1/label").addPathSegment(label).addPathSegment("values");
    addOptionalQueryParameter(url, "start", start);
    addOptionalQueryParameter(url, "end", end);
    addMatchers(url, matchers);
    return getRequest(url.build());
  }

  public String metadata(String metric, Integer limit) {
    var url = urlBuilder("/api/v1/metadata");
    addOptionalQueryParameter(url, "metric", metric);
    if (limit != null) {
      url.addQueryParameter("limit", Integer.toString(limit));
    }
    return getRequest(url.build());
  }

  private String getRequest(HttpUrl url) {
    var request = new Request.Builder().url(url).header("Accept", "application/json").get().build();
    try (var response = client.newCall(request).execute()) {
      return translator.translateRawResponse(response);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String postFormRequest(String path, FormBody.Builder form) {
    var request =
        new Request.Builder()
            .url(ClientUrls.concat(endpoint, path))
            .header("Accept", "application/json")
            .post(form.build())
            .build();
    try (var response = client.newCall(request).execute()) {
      return translator.translateRawResponse(response);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private HttpUrl.Builder urlBuilder(String path) {
    var url = HttpUrl.parse(ClientUrls.concat(endpoint, path));
    if (url == null) {
      throw new IllegalArgumentException("Invalid ingester endpoint: " + endpoint);
    }
    return url.newBuilder();
  }

  private static void addOptionalQueryParameter(HttpUrl.Builder url, String name, String value) {
    if (value != null) {
      url.addQueryParameter(name, value);
    }
  }

  private static void addOptionalFormField(FormBody.Builder form, String name, String value) {
    if (value != null) {
      form.add(name, value);
    }
  }

  private static void addMatchers(HttpUrl.Builder url, List<String> matchers) {
    if (matchers == null) {
      return;
    }
    for (var matcher : matchers) {
      url.addQueryParameter("match[]", matcher);
    }
  }
}
