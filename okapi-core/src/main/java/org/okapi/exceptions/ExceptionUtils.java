/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.exceptions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

public class ExceptionUtils {

  public static String debugFriendlyMsg(Exception e) {
    Writer buffer = new StringWriter();
    PrintWriter pw = new PrintWriter(buffer);
    e.printStackTrace(pw);
    return buffer.toString();
  }
}
