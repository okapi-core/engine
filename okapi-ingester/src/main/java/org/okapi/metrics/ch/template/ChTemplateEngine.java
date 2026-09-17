/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.metrics.ch.template;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateException;
import gg.jte.TemplateOutput;
import gg.jte.output.StringOutput;

public class ChTemplateEngine {
  private final TemplateEngine engine;

  public ChTemplateEngine() {
    engine = TemplateEngine.createPrecompiled(ContentType.Plain);
  }

  public void render(String name, Object param, TemplateOutput output) throws TemplateException {
    engine.render(name, param, output);
  }

  public String render(String name, Object data) {
    var output = new StringOutput();
    render(name, data, output);
    return output.toString();
  }
}
