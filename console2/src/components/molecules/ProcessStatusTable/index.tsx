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
import ReactJson from 'react-json-view';
import { Link } from 'react-router-dom';
import { Button, Grid, Header, Modal, Popup, Table } from 'semantic-ui-react';

import {
    getStatusSemanticColor,
    ProcessEntry,
    ProcessKind,
    ProcessStatus
} from '../../../api/process';
import { formatDuration } from '../../../utils';
import { GitHubLink, LocalTimestamp, ProcessLastErrorModal } from '../../molecules';

interface Props {
    data: ProcessEntry;
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
    static renderCommitId(data: ProcessEntry) {
        if (!data.commitId) {
            return ' - ';
        }

        const link = (
            <GitHubLink
                url={data.repoUrl!}
                path={data.repoPath}
                commitId={data.commitId}
                text={data.commitId}
            />
        );
        if (!data.commitMsg) {
            return link;
        }

        return <Popup trigger={<span>{link}</span>} content={data.commitMsg} />;
    }

    static renderProcessKind(data: ProcessEntry) {
        return (
            <>
                {kindToDescription(data.kind)}
                {data.kind === ProcessKind.FAILURE_HANDLER &&
                    data.status !== ProcessStatus.FAILED && (
                        <ProcessLastErrorModal
                            processMeta={data.meta}
                            title="Parent process' error"
                        />
                    )}
            </>
        );
    }

    static renderTags({ tags }: ProcessEntry) {
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

    static renderTriggeredBy({ triggeredBy }: ProcessEntry) {
        if (!triggeredBy || !triggeredBy.trigger) {
            return ' - ';
        }

        const type = triggeredBy.trigger.eventSource;
        let icon;
        switch (type) {
            case 'github':
                icon = 'github';
                break;
            case 'cron':
                icon = 'clock outline';
                break;
            default:
                icon = 'question';
        }
        const title = type === 'github' ? 'GitHub' : type;

        return (
            <Modal dimmer="inverted" trigger={<Button icon={icon} basic={true} content={title} />}>
                <Header icon={icon} content={`${title} Trigger`} />
                <Modal.Content>
                    <ReactJson
                        src={triggeredBy}
                        collapsed={false}
                        name={null}
                        enableClipboard={false}
                        displayDataTypes={false}
                    />
                </Modal.Content>
            </Modal>
        );
    }

    render() {
        const { data } = this.props;

        return (
            <Grid columns={2}>
                <Grid.Column>
                    <Table
                        definition={true}
                        color={getStatusSemanticColor(data.status)}
                        style={{ height: '100%' }}>
                        <Table.Body>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Parent ID
                                </Table.Cell>
                                <Table.Cell>
                                    {data.parentInstanceId ? (
                                        <Link to={`/process/${data.parentInstanceId}`}>
                                            {data.parentInstanceId}
                                        </Link>
                                    ) : (
                                        ' - '
                                    )}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Initiator
                                </Table.Cell>
                                <Table.Cell>{data.initiator}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell>Type</Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderProcessKind(data)}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Created At
                                </Table.Cell>
                                <Table.Cell>
                                    <LocalTimestamp value={data.createdAt} />
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Start At
                                </Table.Cell>
                                <Table.Cell>
                                    {data.startAt ? <LocalTimestamp value={data.startAt} /> : ' - '}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Last Update
                                </Table.Cell>
                                <Table.Cell>
                                    <LocalTimestamp value={data.lastUpdatedAt} />
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row style={{ height: '100%' }}>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Timeout
                                </Table.Cell>
                                <Table.Cell>
                                    {data.timeout ? (
                                        <Popup
                                            trigger={
                                                <span>{formatDuration(data.timeout * 1000)}</span>
                                            }
                                            content={`${data.timeout}s`}
                                        />
                                    ) : (
                                        ' - '
                                    )}
                                </Table.Cell>
                            </Table.Row>
                        </Table.Body>
                    </Table>
                </Grid.Column>
                <Grid.Column>
                    <Table definition={true} color={getStatusSemanticColor(data.status)}>
                        <Table.Body style={{ wordBreak: 'break-all' }}>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Project
                                </Table.Cell>
                                <Table.Cell>
                                    {data.projectName ? (
                                        <Link
                                            to={`/org/${data.orgName}/project/${data.projectName}`}>
                                            {data.projectName}
                                        </Link>
                                    ) : (
                                        ' - '
                                    )}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Concord Repository
                                </Table.Cell>
                                <Table.Cell>
                                    {data.repoName ? (
                                        <Link
                                            to={`/org/${data.orgName}/project/${data.projectName}/repository/${data.repoName}`}>
                                            {data.repoName}
                                        </Link>
                                    ) : (
                                        ' - '
                                    )}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Repository URL
                                </Table.Cell>
                                <Table.Cell>
                                    {data.repoUrl ? (
                                        <GitHubLink url={data.repoUrl} text={data.repoUrl} />
                                    ) : (
                                        ' - '
                                    )}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Repository Path
                                </Table.Cell>
                                <Table.Cell>
                                    {data.repoPath ? (
                                        <GitHubLink
                                            url={data.repoUrl!}
                                            path={data.repoPath}
                                            text={data.repoPath}
                                        />
                                    ) : (
                                        ' - '
                                    )}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Commit ID
                                </Table.Cell>
                                <Table.Cell>{ProcessStatusTable.renderCommitId(data)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Process Tags
                                </Table.Cell>
                                <Table.Cell>{ProcessStatusTable.renderTags(data)}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell collapsing={true} singleLine={true}>
                                    Triggered By
                                </Table.Cell>
                                <Table.Cell>
                                    {ProcessStatusTable.renderTriggeredBy(data)}
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
