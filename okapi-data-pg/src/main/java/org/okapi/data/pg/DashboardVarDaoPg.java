/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.data.pg;

import java.util.List;
import java.util.Optional;
import org.okapi.data.dao.DashboardVarDao;
import org.okapi.data.model.DashboardVariable;
import org.okapi.data.pg.entity.DashboardVariableEntity;
import org.okapi.data.pg.repository.DashboardVariableRepository;

public final class DashboardVarDaoPg implements DashboardVarDao {
  private final DashboardVariableRepository repository;

  public DashboardVarDaoPg(DashboardVariableRepository repository) {
    this.repository = repository;
  }

  public Optional<DashboardVariable> get(
      String org, String dashboard, String version, String name) {
    return repository.findById(id(org, dashboard, version, name)).map(this::toDto);
  }

  public DashboardVariable save(
      String org, String dashboard, String version, DashboardVariable variable) {
    if (variable == null) throw new NullPointerException("variable");
    repository.saveAndFlush(
        new DashboardVariableEntity(
            id(org, dashboard, version, variable.getVarName()),
            variable.getTag(),
            variable.getVarType()));
    return variable;
  }

  public void delete(String org, String dashboard, String version, String name) {
    repository.deleteById(id(org, dashboard, version, name));
  }

  public List<DashboardVariable> list(String org, String dashboard, String version) {
    return repository
        .findAllByIdOrgIdAndIdDashboardIdAndIdVersionIdOrderByIdVarName(org, dashboard, version)
        .stream()
        .map(this::toDto)
        .toList();
  }

  private DashboardVariableEntity.Id id(String org, String dashboard, String version, String name) {
    return new DashboardVariableEntity.Id(org, dashboard, version, name);
  }

  private DashboardVariable toDto(DashboardVariableEntity entity) {
    return DashboardVariable.builder()
        .varName(entity.getId().getVarName())
        .tag(entity.getTag())
        .varType(entity.getVarType())
        .build();
  }
}
