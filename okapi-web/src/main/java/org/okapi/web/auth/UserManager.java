/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.web.auth;

import lombok.AllArgsConstructor;
import org.okapi.data.bcrypt.BCrypt;
import org.okapi.data.dao.OrgDao;
import org.okapi.data.dao.RelationGraphDao;
import org.okapi.data.dao.UsersDao;
import org.okapi.data.model.Organization;
import org.okapi.data.model.User;
import org.okapi.data.exceptions.UserAlreadyExistsException;
import org.okapi.exceptions.BadRequestException;
import org.okapi.exceptions.UnAuthorizedException;
import org.okapi.metrics.IdCreationFailedException;
import org.okapi.usermessages.UserFacingMessages;
import org.okapi.web.auth.tx.AddMemberToOrgTx;
import org.okapi.web.auth.tx.MakeUserOrgAdmin;
import org.okapi.web.dtos.auth.*;
import org.okapi.web.dtos.dashboards.PersonalName;
import org.okapi.web.service.Mappers;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class UserManager {
  private final UsersDao usersDao;
  private final OrgDao orgDao;
  private final TokenManager tokenManager;
  private final RelationGraphDao relationGraphDao;
  private final OrgIdAssigner orgIdAssigner;

  public String signupWithEmailPassword(CreateUserRequest request)
      throws BadRequestException, IdCreationFailedException {
    if (request.getPassword() == null) {
      throw new BadRequestException(UserFacingMessages.NO_PASSWORD);
    }
    try {
      var user =
          usersDao.createIfNotExists(
              request.getFirstName(),
              request.getLastName(),
              request.getEmail(),
              request.getPassword());

      // create a unique org for this user (user can be part of multiple orgs later)
      var org = createUniqueOrgForUser(user.getUserId());
      var makeUserAdminTx = new MakeUserOrgAdmin(user.getUserId(), org.getOrgId());
      makeUserAdminTx.doTx(relationGraphDao);

      var createMemberTx = new AddMemberToOrgTx(user.getUserId(), org.getOrgId());
      createMemberTx.doTx(relationGraphDao);
      // create a temporary token for this user
      var tempToken = tokenManager.issueLoginToken(user.getUserId());
      return tempToken;
    } catch (UserAlreadyExistsException e) {
      throw new BadRequestException(UserFacingMessages.USER_ALREADY_EXISTS);
    }
  }

  public String signInWithEmailPassword(SignInRequest request) throws UnAuthorizedException {
    var user = usersDao.getWithEmail(request.getEmail());
    if (user.isEmpty()) {
      throw new UnAuthorizedException(UserFacingMessages.WRONG_CREDS);
    }

    var passwordMatch = BCrypt.checkpw(request.getPassword(), user.get().getHashedPassword());
    if (!passwordMatch) {
      throw new UnAuthorizedException(UserFacingMessages.WRONG_CREDS);
    }

    var userIsActive = user.get().getStatus() == User.Status.ACTIVE;
    if (!userIsActive) {
      throw new UnAuthorizedException(UserFacingMessages.WRONG_CREDS);
    }
    return tokenManager.issueLoginToken(user.get().getUserId());
  }

  private Organization createUniqueOrgForUser(String userId) throws IdCreationFailedException {
    var user = usersDao.get(userId).get();
    var orgName = "Default Org";

    if (user.getFirstName() != null) {
      orgName = user.getFirstName() + "'s Org";
    }

    var orgId = orgIdAssigner.getOrgId();
    var existing = orgDao.findById(orgId);
    if (existing.isPresent()) {
      return existing.get();
    }
    var orgDto = Organization.builder().orgId(orgId).orgCreator(userId).orgName(orgName).build();
    orgDao.save(orgDto);
    return orgDto;
  }

  public TokenResponse getSessionToken(String loginToken, String orgId)
      throws UnAuthorizedException {
    var token = tokenManager.issueTemporaryToken(loginToken, orgId);
    return new TokenResponse(token);
  }

  public GetUserProfileResponse updateProfile(
      String loginToken, UpdateUserRequest updateUserRequest) throws UnAuthorizedException {
    var userId = tokenManager.getUserId(loginToken);
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
    return Mappers.mapUserProfileDtoToResponse(userDto);
  }

  public GetUserProfileResponse getUserProfileRes(String login) throws UnAuthorizedException {
    var userId = tokenManager.getUserId(login);
    var userDto = usersDao.get(userId).orElseThrow(UnAuthorizedException::new);
    return Mappers.mapUserProfileDtoToResponse(userDto);
  }

  public PersonalName getPersonalName(String userId) {
    var userDto = usersDao.get(userId);
    if (userDto.isEmpty()) {
      return PersonalName.builder().build();
    }
    var user = userDto.get();
    return PersonalName.builder()
        .firstName(user.getFirstName())
        .lastName(user.getLastName())
        .build();
  }
}
