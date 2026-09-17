/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.promql;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.okapi.promql.query.UrlUnEscaper;

public class UrlUnEscaperTests {

  @Test
  void sanity() {
    var unescaper = new UrlUnEscaper();
    var escaped = unescaper.unescape("U__service_2e_instance_2e_id");
    Assertions.assertEquals("service.instance.id", escaped);
  }
}
