/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import * as gitUrlParse from 'git-url-parse';
import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { Link } from 'react-router-dom';
import { Icon, Loader, Table } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import { ProjectEntry } from '../../../api/org/project';
import { RepositoryEntry } from '../../../api/org/project/repository';
import { actions, selectors, State } from '../../../state/data/projects';
import { comparators } from '../../../utils';
import { RequestErrorMessage, RepositoryActionDropdown } from '../../molecules';

interface StateProps {
    repositories: RepositoryEntry[];
    loading: boolean;
    error: RequestError;
}

interface ExternalProps {
    orgName: string;
    projectName: string;
}

interface DispatchProps {
    load: () => void;
}

type Props = StateProps & DispatchProps & ExternalProps;

const getSource = (r: RepositoryEntry) => {
    if (r.commitId) {
        return 'Commit ID';
    }
    return 'Branch/tag';
};

const renderTableRow = (orgName: ConcordKey, projectName: ConcordKey, row: RepositoryEntry) => {
    return (
        <Table.Row key={row.id}>
            <Table.Cell>
                <Link to={`/org/${orgName}/project/${projectName}/repository/${row.name}`}>
                    {row.name}
                </Link>
            </Table.Cell>
            <Table.Cell>
                <a href={gitUrlParse(row.url).toString('https')} target="_blank">
                    {row.url} <Icon name="external" />
                </a>
            </Table.Cell>
            <Table.Cell>{getSource(row)}</Table.Cell>
            <Table.Cell>{row.path}</Table.Cell>
            <Table.Cell>{row.secretName}</Table.Cell>
            <Table.Cell>
                <RepositoryActionDropdown
                    orgName={orgName}
                    projectName={projectName}
                    repoName={row.name}
                />
            </Table.Cell>
        </Table.Row>
    );
};

class RepositoryList extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.load();
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName, projectName: newProjectName } = this.props;
        const { orgName: oldOrgName, projectName: oldProjectName } = prevProps;

        if (oldOrgName !== newOrgName || oldProjectName !== newProjectName) {
            this.props.load();
        }
    }

    render() {
        const { error, loading, repositories, orgName, projectName } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading) {
            return <Loader active={true} />;
        }

        if (repositories.length === 0) {
            return <h3>No repositories found.</h3>;
        }

        return (
            <Table>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell collapsing={true}>Name</Table.HeaderCell>
                        <Table.HeaderCell>Repository URL</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Source</Table.HeaderCell>
                        <Table.HeaderCell singleLine={true}>Path</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Secret</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true} />
                    </Table.Row>
                </Table.Header>
                <Table.Body>
                    {repositories.map((r) => renderTableRow(orgName, projectName, r))}
                </Table.Body>
            </Table>
        );
    }
}

const toRepositoryList = (p?: ProjectEntry) => {
    if (!p || !p.repositories) {
        return [];
    }

    return Object.keys(p.repositories)
        .map((k) => p.repositories![k])
        .sort(comparators.byName);
};

const mapStateToProps = (
    { projects }: { projects: State },
    { orgName, projectName }: ExternalProps
): StateProps => ({
    repositories: toRepositoryList(selectors.projectByName(projects, orgName, projectName)),
    loading: projects.loading,
    error: projects.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { orgName, projectName }: ExternalProps
): DispatchProps => ({
    load: () => dispatch(actions.getProject(orgName, projectName))
});

export default connect(mapStateToProps, mapDispatchToProps)(RepositoryList);
