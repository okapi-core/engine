/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.oscar.inference;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.okapi.oscar.spring.ConfigKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OscarInferenceJobPool {

  private ExecutorService pool;

  private Map<String, InferenceJob> jobs;

  public OscarInferenceJobPool(@Value(ConfigKeys.INFERENCE_N_THREADS) int nth) {
    this.pool = Executors.newFixedThreadPool(nth);
    jobs = new ConcurrentHashMap<>();
  }

  public void submit(InferenceJob inferenceJob) {
    inferenceJob.startWith(pool);
    jobs.put(inferenceJob.getId(), inferenceJob);
  }

  public void cancel(String id) {
    var job = jobs.get(id);
    if (job == null) return;
    job.cancel();
    jobs.remove(id);
  }

  public int nPending() {
    return jobs.size();
  }

  @PreDestroy
  public void preDestroy() {
    this.pool.shutdownNow();
  }
}
