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
import {Link} from 'react-router-dom';
import {Grid, Label, Popup, Table} from 'semantic-ui-react';

import {getStatusSemanticColor, ProcessEntry, ProcessKind, ProcessStatus} from '../../../api/process';
import {formatDuration} from '../../../utils';
import {GitHubLink, LocalTimestamp, ProcessLastErrorModal} from '../../molecules';
import {TriggeredByPopup} from '../../organisms';

interface Props {
    process?: ProcessEntry;
}

const kindToDescription = (k: ProcessKind): string => {
    switch (k) {
        case ProcessKind.DEFAULT:
            return 'Default';
        case ProcessKind.FAILURE_HANDLER:
            return 'onFailure handler';
        case ProcessKind.CANCEL_HANDLER:
            return 'onCancel handler';
        case ProcessKind.TIMEOUT_HANDLER:
            return 'onTimeout handler';
        default:
            return 'Unknown';
    }
};

class ProcessStatusTable extends React.PureComponent<Props> {
    static renderCommitId(process?: ProcessEntry) {
        if (!process || !process.commitId || !process.repoUrl) {
            return ' - ';
        }

        return (
            <GitHubLink
                url={process.repoUrl}
                commitId={process.commitId}
                text={process.commitId}
            />
        );
    }

    static renderProcessKind(process?: ProcessEntry) {
        if (!process) {
            return '-';
        }

        return (
            <>
                {kindToDescription(process.kind)}
                {process.kind === ProcessKind.FAILURE_HANDLER &&
                    process.status !== ProcessStatus.FAILED && (
                        <ProcessLastErrorModal
                            processMeta={process.meta}
                            title="Parent process' error"
                        />
                    )}
            </>
        );
    }

    static renderTags(process?: ProcessEntry) {
        if (!process) {
            return '-';
        }

        const tags = process.tags;
        if (!tags || tags.length === 0) {
            return ' - ';
        }

        const items = tags.map((t) => <Link to={`/process?tags=${t}`}>{t}</Link>);

        const result = [];
        for (let i = 0; i < items.length; i++) {
            result.push(items[i]);
            if (i + 1 !== items.length) {
                result.push(', ');
            }
        }

        return result;
    }

    static renderTriggeredBy(process?: ProcessEntry) {
        if (!process) {
            return ' - ';
        }

        return <TriggeredByPopup entry={process} />;
    }

    static renderParentInstanceId(process?: ProcessEntry) {
        if (!process || !process.parentInstanceId) {
            return '-';
        }

        const parentId = process.parentInstanceId;
        return <Link to={`/process/${parentId}`}>{parentId}</Link>;
    }

    static renderInitiator(process?: ProcessEntry) {
        if (!process) {
            return '-';
        }

        return process.initiator;
    }

    static renderCreatedAt(process?: ProcessEntry) {
        if (!process) {
            return '-';
        }

        return <LocalTimestamp value={process.createdAt} />;
    }

    static renderStartAt(process?: ProcessEntry) {
        if (!process || !process.startAt) {
            return '-';
        }

        return <LocalTimestamp value={process.startAt} />;
    }

    static renderLastUpdatedAt(process?: ProcessEntry) {
        if (!process) {
            return '-';
        }

        return <LocalTimestamp value={process.lastUpdatedAt} />;
    }

    static renderTimeout(process?: ProcessEntry) {
        if (!process || (!process.timeout && !process.suspendTimeout)) {
            return '-';
        }

        return (
            <>
                {process.timeout && (
                    <Label key={'running'}>
                        <Popup
                            trigger={<span>running: {formatDuration(process.timeout * 1000)}</span>}
                            content={`${process.timeout}s`}
                        />
                    </Label>
                )}
                {process.suspendTimeout && (
                    <Label key={'suspended'}>
                        <Popup
                            trigger={
                                <span>
                                    suspended: {formatDuration(process.suspendTimeout * 1000)}
                                </span>
                            }
                            content={`${process.suspendTimeout}s`}
                        />
                    </Label>
                )}
            </>
        );
    }

    static renderProject(process?: ProcessEntry) {
        if (!process || !process.projectName) {
            return '-';
        }

        return (
            <Link to={`/org/${process.orgName}/project/${process.projectName}`}>
                {process.projectName}
            </Link>
        );
    }

    static renderRepo(process?: ProcessEntry) {
        if (!process || !process.repoName) {
            return '-';
        }

        return (
            <Link
                to={`/org/${process.orgName}/project/${process.projectName}/repository/${process.repoName}`}>
                {process.repoName}
            </Link>
        );
    }

    static renderRepoUrl(process?: ProcessEntry) {
        if (!process || !process.repoUrl) {
            return '-';
        }

        return <GitHubLink url={process.repoUrl} text={process.repoUrl} />;
    }

    static renderRepoPath(process?: ProcessEntry) {
        if (!process || !process.commitId || !process.repoUrl) {
            return '-';
        }

        return (
            <GitHubLink
                url={process.repoUrl!}
                commitId={process.commitId}
                path={process.repoPath || '/'}
                text={process.repoPath || '/'}
            />
        );
    }

    render() {
        const { process } = this.props;

        return (
            <Grid columns={2} className={process ? '' : 'loading'}>
                <Grid.Column>
                    <Table
                        definition={true}
                        color={process ? getStatusSemanticColor(process.status) : 'grey'}
                        style={{ height: '100%' }}>
                        <Table.Body>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Parent ID
                                </Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderParentInstanceId(process)}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Initiator
                                </Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderInitiator(process)}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Type</Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderProcessKind(process)}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Created At
                                </Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderCreatedAt(process)}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Start At
                                </Table.Cell>
                                <Table.Cell>{ProcessStatusTable.renderStartAt(process)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Last Update
                                </Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderLastUpdatedAt(process)}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row style={{ height: '100%' }}>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Timeout
                                </Table.Cell>
                                <Table.Cell>{ProcessStatusTable.renderTimeout(process)}</Table.Cell>
                            </Table.Row>
                        </Table.Body>
                    </Table>
                </Grid.Column>
                <Grid.Column>
                    <Table
                        definition={true}
                        color={process ? getStatusSemanticColor(process.status) : 'grey'}
                        style={{ height: '100%' }}>
                        <Table.Body style={{ wordBreak: 'break-all' }}>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Project
                                </Table.Cell>
                                <Table.Cell>{ProcessStatusTable.renderProject(process)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Concord Repository
                                </Table.Cell>
                                <Table.Cell>{ProcessStatusTable.renderRepo(process)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Repository URL
                                </Table.Cell>
                                <Table.Cell>{ProcessStatusTable.renderRepoUrl(process)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Repository Path
                                </Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderRepoPath(process)}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Commit ID
                                </Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderCommitId(process)}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Process Tags
                                </Table.Cell>
                                <Table.Cell>{ProcessStatusTable.renderTags(process)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Triggered By
                                </Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderTriggeredBy(process)}
                                </Table.Cell>
                            </Table.Row>
                        </Table.Body>
                    </Table>
                </Grid.Column>
            </Grid>
        );
    }
}

export default ProcessStatusTable;
