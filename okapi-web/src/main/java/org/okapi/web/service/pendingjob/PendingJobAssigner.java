/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.service.pendingjob;

import java.util.List;
import org.okapi.data.model.PendingJob;

public interface PendingJobAssigner {
  List<PendingJob> getPendingJobs(String orgId, List<String> sources, int maxJobs);
}
