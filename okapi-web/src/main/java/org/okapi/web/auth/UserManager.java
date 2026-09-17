/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import static org.okapi.data.model.EntityType.ORG;
import static org.okapi.data.model.EntityType.USER;
import static org.okapi.data.model.RelationType.ORG_MEMBER;

import lombok.AllArgsConstructor;
import org.okapi.data.bcrypt.BCrypt;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.exceptions.UserAlreadyExistsException;
import org.okapi.data.model.EntityId;
import org.okapi.data.model.User;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.usermessages.UserFacingMessages;
import org.okapi.web.dtos.auth.CreateUserRequest;
import org.okapi.web.dtos.auth.GetUserProfileResponse;
import org.okapi.web.dtos.auth.UpdateUserRequest;
import org.okapi.web.service.Mappers;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class UserManager {
  private final UsersDao usersDao;
  private final OrgDao orgDao;
  private final OrgIdSupplier orgIdSupplier;
  private final RelationGraphDao relationGraphDao;

  public void signupWithEmailPassword(CreateUserRequest request) throws BadRequestException {
    if (request.getPassword() == null) {
      throw new BadRequestException(UserFacingMessages.NO_PASSWORD);
    }
    try {
      var orgId = orgIdSupplier.getOrgId();
      var user =
          usersDao.createIfNotExists(
              request.getFirstName(),
              request.getLastName(),
              request.getEmail(),
              request.getPassword(),
              orgId);
      relationGraphDao.addRelationship(
          EntityId.of(USER, user.getUserId()), EntityId.of(ORG, orgId), ORG_MEMBER);
    } catch (UserAlreadyExistsException e) {
      throw new BadRequestException(UserFacingMessages.USER_ALREADY_EXISTS);
    }
  }

  public GetUserProfileResponse updateProfile(String userId, UpdateUserRequest updateUserRequest)
      throws UnAuthorizedException {
    var userDto = usersDao.get(userId).orElseThrow(UnAuthorizedException::new);
    if (updateUserRequest.getPassword() != null) {
      var passwordMatch =
          BCrypt.checkpw(updateUserRequest.getOldPassword(), userDto.getHashedPassword());
      if (!passwordMatch) {
        throw new UnAuthorizedException();
      }
      var newHashedPassword = BCrypt.hashpw(updateUserRequest.getPassword(), BCrypt.gensalt());
      userDto.setHashedPassword(newHashedPassword);
    }
    userDto.setFirstName(updateUserRequest.getFirstName());
    userDto.setLastName(updateUserRequest.getLastName());
    usersDao.update(userDto);
    return mapUserProfileDtoToResponse(userDto);
  }

  public GetUserProfileResponse getUserProfileRes(String userId) throws UnAuthorizedException {
    var userDto = usersDao.get(userId).orElseThrow(UnAuthorizedException::new);
    return mapUserProfileDtoToResponse(userDto);
  }

  private GetUserProfileResponse mapUserProfileDtoToResponse(User user) {
    var orgSummary = orgDao.getSummary(user.getOrgId()).orElse(null);
    return Mappers.mapUserProfileDtoToResponse(user, orgSummary);
  }
}
