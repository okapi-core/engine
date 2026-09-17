/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.okapi.exceptions.MalformedQueryException;

public class VarsPreprocessorTests {

  @Test
  void singleVar() throws MalformedQueryException {
    var template = "hello $__{value}";
    var resub = VarsPreprocessor.substituteVars(template, Map.of("value", "world"));
    Assertions.assertEquals("hello world", resub);
  }

  @Test
  void missingValueVar() throws MalformedQueryException {
    var template = "hello $__{value}";
    var resub = VarsPreprocessor.substituteVars(template, Map.of("value1", ""));
    Assertions.assertEquals("hello ", resub);
  }

  @Test
  void malformedVar() {
    var template = "hello $__{value";
    Assertions.assertThrows(
        MalformedQueryException.class,
        () -> VarsPreprocessor.substituteVars(template, Map.of("value", "world")));
  }

  @Test
  void twoVars() throws MalformedQueryException {
    var template = "hello $__{value1} $__{value2}";
    var resub =
        VarsPreprocessor.substituteVars(template, Map.of("value1", "world", "value2", "again"));
    Assertions.assertEquals("hello world again", resub);
  }

  @Test
  void sameVarRepeated() {}
}
