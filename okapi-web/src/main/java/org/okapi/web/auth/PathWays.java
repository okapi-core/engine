/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import static org.okapi.data.dao.RelationGraphDao.makeRelation;
import static org.okapi.data.model.EntityType.*;
import static org.okapi.data.model.RelationType.*;

import java.util.Arrays;
import java.util.List;
import org.okapi.data.model.EdgeSequence;

public class PathWays {

  public static final List<EdgeSequence> DASH_EDIT_PATH_WAY =
      List.of(
          new EdgeSequence(
              Arrays.asList(
                  makeRelation(ORG, ORG_MEMBER), makeRelation(DASHBOARD, DASHBOARD_EDIT))));

  public static final List<EdgeSequence> DASH_READ_PATH_WAY =
      List.of(
          new EdgeSequence(
              Arrays.asList(
                  makeRelation(ORG, ORG_MEMBER), makeRelation(DASHBOARD, DASHBOARD_READ))));
}
