/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import * as React from 'react';
import { Link } from 'react-router-dom';
import { Icon, Table, Popup } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { RepositoryEntry } from '../../../api/org/project/repository';
import { GitHubLink } from '../../molecules';
import { RepositoryActionDropdown } from '../../organisms';

interface Props {
    orgName: ConcordKey;
    projectName: ConcordKey;
    data: RepositoryEntry[];
}

const getSource = (r: RepositoryEntry) => {
    if (r.commitId) {
        return r.commitId;
    }
    return r.branch || 'master';
};

const renderTableRow = (orgName: ConcordKey, projectName: ConcordKey, row: RepositoryEntry) => {
    return (
        <Table.Row key={row.id}>
            <Table.Cell>
                <Popup
                    trigger={
                        <Icon
                            name={row.disabled ? 'power off' : 'power'}
                            color={row.disabled ? 'grey' : 'green'}
                        />
                    }>
                    {row.disabled ? 'Disabled' : 'Enabled'}
                </Popup>
            </Table.Cell>
            <Table.Cell singleLine={true}>
                <Link to={`/org/${orgName}/project/${projectName}/repository/${row.name}`}>
                    {row.name}
                </Link>
            </Table.Cell>
            <Table.Cell>
                <GitHubLink url={row.url} text={row.url} />
            </Table.Cell>
            <Table.Cell>{getSource(row)}</Table.Cell>
            <Table.Cell>{row.path || '/'}</Table.Cell>
            <Table.Cell>{row.secretName}</Table.Cell>
            <RepositoryActionDropdown orgName={orgName} projectName={projectName} repo={row} />
        </Table.Row>
    );
};

class RepositoryList extends React.PureComponent<Props> {
    render() {
        const { data, orgName, projectName } = this.props;

        if (data.length === 0) {
            return <h3>No repositories found.</h3>;
        }

        return (
            <div style={{ overflowX: 'auto' }}>
                <Table striped>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell collapsing={true} />
                            <Table.HeaderCell collapsing={true}>Name</Table.HeaderCell>
                            <Table.HeaderCell>Repository URL</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>Branch/Commit ID</Table.HeaderCell>
                            <Table.HeaderCell singleLine={true}>Path</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true} style={{ width: '8%' }}>
                                Secret
                            </Table.HeaderCell>
                            <Table.HeaderCell
                                collapsing={true}
                                colSpan={2}
                                style={{ width: '1%', textAlign: 'center' }}>
                                Execute
                            </Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>
                    <Table.Body>
                        {data.map((r) => renderTableRow(orgName, projectName, r))}
                    </Table.Body>
                </Table>
            </div>
        );
    }
}

export default RepositoryList;
