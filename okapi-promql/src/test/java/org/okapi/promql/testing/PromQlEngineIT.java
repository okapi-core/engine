/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql.testing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PromQlEngineIT {

  @Test
  void testRunAllScripts() throws Exception {
    List<Path> scripts = discoverTestScripts();
    assertTrue(!scripts.isEmpty(), "no promql test scripts discovered");
    for (Path script : scripts) {
      String content = Files.readString(script, StandardCharsets.UTF_8);
      System.out.println("Testing: " + script);
      testScript(content);
    }
  }

  private void testScript(String script) {
    PromQlTestPipeline pipeline = new PromQlTestPipeline();
    List<TestExpectationDifference> diffs = pipeline.run(script);
    for (var diff : diffs) {
      System.out.println("Test failure with: " + diff);
    }
    assertTrue(diffs.isEmpty(), "there are differences in eval, total failures: " + diffs.size());
  }

  private List<Path> discoverTestScripts() throws IOException, URISyntaxException {
    URL url = PromQlEngineIT.class.getClassLoader().getResource("promqltest/testdata");
    if (url == null) {
      return List.of();
    }
    URI uri = url.toURI();
    if ("jar".equals(uri.getScheme())) {
      FileSystem fs = FileSystems.newFileSystem(uri, java.util.Map.of());
      return walkTestFiles(fs.getPath("/promqltest/testdata"));
    }
    return walkTestFiles(Paths.get(uri));
  }

  private List<Path> walkTestFiles(Path root) throws IOException {
    List<Path> files = new ArrayList<>();
    try (var stream = Files.walk(root)) {
      stream
          .filter(p -> p.toString().endsWith(".test"))
              // limit related features are currently experimental so we exclude them from our testing.
          .filter(p -> !p.getFileName().toString().equals("limit.test"))
          .forEach(files::add);
    }
    return files;
  }
}
