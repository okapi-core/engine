/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.dtos.users;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@AllArgsConstructor
@Getter
@Builder
public class GetUserMetadataResponse {
  String firstName;
  String lastName;
  List<GetOrgUserView> orgs;
  String email;
}
