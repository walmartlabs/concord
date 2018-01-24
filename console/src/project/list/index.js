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
import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { connect } from 'react-redux';
import { Link } from 'react-router';
import { Header, Icon } from 'semantic-ui-react';
import RefreshButton from '../../shared/RefreshButton';
import ErrorMessage from '../../shared/ErrorMessage';
import DataTable from '../../shared/DataTable';
import * as api from './api';
import DataItem from '../../shared/DataItem';
import { getCurrentOrg } from '../../session/reducers';

const { actions, reducers, selectors, sagas } = DataItem('project/list', [], api.listProjects);

const columns = [
  { key: 'visibility', label: 'Access', collapsing: true },
  { key: 'name', label: 'Project', collapsing: true },
  { key: 'description', label: 'Description' }
];

const projectNameKey = 'name';
const visibilityKey = 'visibility';

const cellFn = (row, key) => {
  if (key === visibilityKey) {
    const n = row[key];
    return (
      <Icon name={n === 'PUBLIC' ? 'unlock' : 'lock'} color={n === 'PRIVATE' ? 'red' : 'grey'} />
    );
  }

  // link to the project page
  if (key === projectNameKey) {
    const n = row[key];
    return <Link to={`/project/${n}`}>{n}</Link>;
  }

  return row[key];
};

class ProjectListPage extends Component {
  componentDidMount() {
    this.load();
  }

  componentDidUpdate(prevProps) {
    const { org: currentOrg } = this.props;
    const { org: prevOrg } = prevProps;
    if (currentOrg !== prevOrg) {
      this.load();
    }
  }

  load() {
    const { loadData, org } = this.props;
    loadData(org.name);
  }

  render() {
    const { loading, error, data } = this.props;

    if (error) {
      return <ErrorMessage message={error} retryFn={() => this.load()} />;
    }

    return (
      <div>
        <Header as="h3">
          <RefreshButton loading={loading} onClick={() => this.load()} /> Projects
        </Header>
        <DataTable cols={columns} rows={data ? data : []} cellFn={cellFn} />
      </div>
    );
  }
}

ProjectListPage.propTypes = {
  loading: PropTypes.bool,
  error: PropTypes.any,
  data: PropTypes.array
};

const mapStateToProps = ({ projectList, session }) => ({
  org: getCurrentOrg(session),
  loading: selectors.isLoading(projectList),
  error: selectors.getError(projectList),
  data: selectors.getData(projectList)
});

const mapDispatchToProps = (dispatch) => ({
  loadData: (orgName) => dispatch(actions.loadData([orgName]))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectListPage);

export { reducers, sagas };
