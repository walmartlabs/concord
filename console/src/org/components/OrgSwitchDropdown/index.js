/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */
import React from 'react';
import { connect } from 'react-redux';
import { Dropdown } from 'semantic-ui-react';
import { selectors, actions } from '../../../session';

const byName = ({ name: a }, { name: b }) => {
  if (a < b) {
    return -1;
  } else if (a > b) {
    return 1;
  }
  return 0;
};

const orgsToOptions = (orgs) =>
  orgs
    .slice()
    .sort(byName)
    .map((t) => ({ text: t.name, value: t.id }));

const OrgSwitchDropdown = ({ currentOrg, orgs, onChangeFn }) => (
  <Dropdown
    item
    text={`Organization: ${currentOrg.name}`}
    value={currentOrg.id}
    options={orgsToOptions(orgs)}
    onChange={(ev, v) => onChangeFn(v.value)}
  />
);

const mapStateToProps = ({ session }) => ({
  currentOrg: selectors.getCurrentOrg(session),
  orgs: selectors.getAvailableOrgs(session)
});

const mapDispatchToProps = (dispatch) => ({
  onChangeFn: (orgId) => dispatch(actions.changeOrg(orgId))
});

export default connect(mapStateToProps, mapDispatchToProps)(OrgSwitchDropdown);
