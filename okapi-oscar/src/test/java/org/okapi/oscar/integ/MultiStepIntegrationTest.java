package org.okapi.oscar.integ;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junitpioneer.jupiter.RetryingTest;
import org.okapi.oscar.integ.corpus.HighLatencyCpuSpikeCorpus;
import org.okapi.oscar.integ.judge.Judgment;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class MultiStepIntegrationTest extends OscarIntegTestBase {

  @BeforeAll
  void seedData() {
    new HighLatencyCpuSpikeCorpus(ingesterClient).seed();
  }

  @RetryingTest(2)
  void debugHighLatencyDueToCpuSpike() {
    String question =
        "The "
            + HighLatencyCpuSpikeCorpus.CHECKOUT_SERVICE
            + " service is experiencing high latency (>= 500ms). Debug the root cause. "
            + "The postgres database runs on host "
            + HighLatencyCpuSpikeCorpus.POSTGRES_HOST
            + ". Check recent slow traces and correlate with host metrics.";
    String session = session(question);
    var contents = pollUntilFinAndReturnResponse(session);
    assertThat(
            judgeAgent.judge(
                question,
                "Mentions high CPU usage on " + HighLatencyCpuSpikeCorpus.POSTGRES_HOST,
                contents))
        .isNotEqualTo(Judgment.WRONG);
  }
}
