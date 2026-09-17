/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.tools.results;

import java.util.ArrayList;
import java.util.List;
import org.okapi.rest.logs.ChLogsQueryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ObjectStoreSearchLogsResultDao implements SearchLogsResultDao {

  private final LogsResultSplitter splitter;
  private final LogsResultSerializer serializer;
  private final ObjectStore objectStore;
  private final ResultKeyMaker resultKeyMaker;
  private final int maxSizePerPage;

  public ObjectStoreSearchLogsResultDao(
      LogsResultSplitter splitter,
      LogsResultSerializer serializer,
      ObjectStore objectStore,
      ResultKeyMaker resultKeyMaker,
      @Value("${okapi.oscar.logs-search.max-size-per-page:8192}") int maxSizePerPage) {
    this.splitter = splitter;
    this.serializer = serializer;
    this.objectStore = objectStore;
    this.resultKeyMaker = resultKeyMaker;
    this.maxSizePerPage = maxSizePerPage;
  }

  @Override
  public PagedResultWrite persist(String toolCallId, ChLogsQueryResponse response) {
    var pages = splitter.split(toolCallId, response, maxSizePerPage);
    List<String> pageKeys = new ArrayList<>(pages.size());
    for (var page : pages) {
      String key = resultKeyMaker.createKeyForPage(toolCallId, page.pageNumber());
      objectStore.upload(key, serializer.serialize(page));
      pageKeys.add(key);
    }
    int totalItems =
        response == null || response.getItems() == null ? 0 : response.getItems().size();
    return new PagedResultWrite(toolCallId, pages.size(), totalItems, List.copyOf(pageKeys));
  }

  @Override
  public SearchLogsResultDetail getPage(String toolCallId, int pageNumber) {
    String key = resultKeyMaker.createKeyForPage(toolCallId, pageNumber);
    return serializer.deserialize(objectStore.download(key));
  }
}
