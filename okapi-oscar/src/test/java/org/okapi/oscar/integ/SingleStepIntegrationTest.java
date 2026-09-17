/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.integ;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.okapi.oscar.integ.corpus.SingleStepFlowCorpus;
import org.okapi.oscar.integ.judge.Judgment;

@Disabled
class SingleStepIntegrationTest extends OscarIntegTestBase {

  @BeforeAll
  void seedData() {
    new SingleStepFlowCorpus(ingesterClient).seed();
  }

  @Test
  void getMetricsForCheckoutService() {
    String question =
        "Get metrics for the checkout service over the last hour. I need values for: "
            + String.join(", ", SingleStepFlowCorpus.METRIC_PATHS);
    String sessionId = session(question);
    var response = pollUntilFinAndReturnResponse(sessionId);
    assertThat(
            judgeAgent.judge(
                question,
                "Metric values found for checkout.http.requests, checkout.jvm.heap, and checkout.cpu.usage for the checkout service",
                response))
        .isNotEqualTo(Judgment.WRONG);
  }

  @Test
  void getErrorTracesForPaymentService() {
    String question =
        "Find error traces for the " + SingleStepFlowCorpus.PAYMENT_SERVICE + " from the last hour";
    String sessionId = session(question);
    var response = pollUntilFinAndReturnResponse(sessionId);
    assertThat(judgeAgent.judge(question, "Found error traces for payment-service.", response))
        .isNotEqualTo(Judgment.WRONG);
  }

  @Test
  void getTracesByDatabaseSystem() {
    String question =
        "Find trace IDs for spans that used postgresql as their database system in the last hour";
    String sessionId = session(question);
    var response = pollUntilFinAndReturnResponse(sessionId);
    assertThat(
            judgeAgent.judge(
                question, "Found trace IDs for spans with db.system=postgresql", response))
        .isNotEqualTo(Judgment.WRONG);
  }

  @Test
  void getRedMetricsForGatewayService() {
    String question =
        "Get RED metrics for the "
            + SingleStepFlowCorpus.GATEWAY_SERVICE
            + " service for the last hour";
    String sessionId = session(question);
    var response = pollUntilFinAndReturnResponse(sessionId);
    assertThat(
            judgeAgent.judge(
                question,
                "RED metrics for api-gateway show request rate, error rate, and duration percentiles",
                response))
        .isNotEqualTo(Judgment.WRONG);
  }

  @Test
  void getRedMetricsForGatewayOperation() {
    String question =
        "Get RED metrics for the "
            + SingleStepFlowCorpus.GATEWAY_OPERATION
            + " operation on the "
            + SingleStepFlowCorpus.GATEWAY_SERVICE
            + " service for the last hour";
    String sessionId = session(question);
    var response = pollUntilFinAndReturnResponse(sessionId);
    assertThat(
            judgeAgent.judge(
                question,
                "RED metrics for POST /checkout on api-gateway are shown with some summary of counts, errors and durations.",
                response))
        .isNotEqualTo(Judgment.WRONG);
  }
}
