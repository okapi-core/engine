/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.query;

import java.util.Map;
import org.okapi.exceptions.MalformedQueryException;

public class VarsPreprocessor {

  public static String substituteVars(String query, Map<String, String> vars)
      throws MalformedQueryException {
    var resub = new StringBuilder();
    var varRegister = new StringBuilder();
    var charArr = query.toCharArray();
    for (int i = 0; i < charArr.length; i++) {
      if (charArr[i] == '$') {
        int j = i;
        while (j < charArr.length && charArr[j] != '}') j++;
        if (j == charArr.length) {
          throw new MalformedQueryException(
              "The variable starting at position: "
                  + i
                  + " in query: "
                  + query
                  + " is not terminated");
        }
        for (int k = i + 4; k < j; k++) {
          varRegister.append(charArr[k]);
        }
        var var = varRegister.toString();
        varRegister.setLength(0);
        var varValue = vars.getOrDefault(var, "");
        resub.append(varValue);
        i = j;
      } else {
        resub.append(charArr[i]);
      }
    }
    return resub.toString();
  }
}
