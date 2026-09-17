/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.traces.client;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.okapi.rest.traces.SpanDto;
import org.okapi.rest.traces.SpanQueryRequest;
import org.okapi.rest.traces.SpanQueryResponse;

@RequiredArgsConstructor
public class TracesQueryClient {
  private final OkHttpClient client;
  private final Gson gson;

  public List<SpanDto> query(
      String tenant, String application, String baseUrl, SpanQueryRequest request)
      throws IOException {
    String next = null;
    var out = new ArrayList<SpanDto>();
    SpanQueryRequest req =
        SpanQueryRequest.builder()
            .start(request.getStart())
            .end(request.getEnd())
            .limit(request.getLimit() > 0 ? request.getLimit() : 1000)
            .pageToken(next)
            .filter(request.getFilter())
            .build();
    byte[] body = gson.toJson(req).getBytes();
    Request httpReq =
        new Request.Builder()
            .url(baseUrl)
            .post(RequestBody.create(MediaType.parse("application/json"), body))
            .addHeader("X-Okapi-Tenant-Id", tenant)
            .addHeader("X-Okapi-App", application)
            .addHeader("X-Okapi-Fan-Out", "true")
            .build();
    try (Response resp = client.newCall(httpReq).execute()) {
      if (!resp.isSuccessful() || resp.body() == null)
        throw new IOException("Remote query failed: " + resp.code());
      SpanQueryResponse qresp = gson.fromJson(resp.body().string(), SpanQueryResponse.class);
      if (qresp.items() != null) out.addAll(qresp.items());
    }
    return out;
  }
}
