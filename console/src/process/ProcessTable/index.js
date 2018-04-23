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
import * as React from 'react';
import { Table } from 'semantic-ui-react';
import { Link } from 'react-router';
import { ProcessStatusIcon } from '../ProcessStatusIcon';
import { formatTimestamp } from '../util';

export class ProcessTable extends React.Component {
    render() {
        const {
            processes,
            celled,
            listProject,
            renderActions
            // renderTopMenuItems
        } = this.props;

        return (
            <div>
                {/* {renderTopMenuItems && (
                    // TODO: Pretty This up
                    <Menu attached="top" borderless={true}>
                        {renderTopMenuItems()}
                    </Menu>
                )} */}
                <Table celled={celled} attached="bottom" selectable={true}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell>Status</Table.HeaderCell>
                            <Table.HeaderCell>Instance ID</Table.HeaderCell>
                            {listProject && <Table.HeaderCell>Project</Table.HeaderCell>}
                            <Table.HeaderCell>Initiator</Table.HeaderCell>
                            <Table.HeaderCell>Created</Table.HeaderCell>
                            <Table.HeaderCell>Last Update</Table.HeaderCell>
                            {renderActions && <Table.HeaderCell />}
                        </Table.Row>
                    </Table.Header>
                    <Table.Body>
                        {processes.map((process, index) => {
                            return (
                                <Table.Row key={index}>
                                    <Table.Cell collapsing textAlign="center">
                                        <ProcessStatusIcon status={process.status} />
                                    </Table.Cell>
                                    <Table.Cell singleLine>
                                        <Link to={`/process/${process.instanceId}`}>
                                            {process.instanceId}
                                        </Link>
                                    </Table.Cell>
                                    {listProject && (
                                        <Table.Cell collapsing>
                                            {process.projectName !== 'concordTriggers' ? (
                                                <Link to={`/project/${process.projectName}`}>
                                                    {process.projectName}
                                                </Link>
                                            ) : (
                                                <div>{process.projectName}</div>
                                            )}
                                        </Table.Cell>
                                    )}
                                    <Table.Cell collapsing>{process.initiator}</Table.Cell>
                                    <Table.Cell>{formatTimestamp(process.createdAt)}</Table.Cell>
                                    <Table.Cell>
                                        {formatTimestamp(process.lastUpdatedAt)}
                                    </Table.Cell>
                                    {renderActions && (
                                        <Table.Cell textAlign="right">
                                            {renderActions(process.status, process.instanceId)}
                                        </Table.Cell>
                                    )}
                                </Table.Row>
                            );
                        })}
                    </Table.Body>
                    {/* TODO: Add support for pagination
                    <Table.Footer>
                        <Table.Row>
                            <Table.HeaderCell colSpan="5">
                                <Menu floated="right">
                                    <Pagination
                                        defaultActivePage={1}
                                        firstItem={null}
                                        lastItem={null}
                                        ellipsisItem={null}
                                        prevItem={{
                                            content: <Icon name="left chevron" />,
                                            icon: true
                                        }}
                                        nextItem={{
                                            content: <Icon name="right chevron" />,
                                            icon: true
                                        }}
                                        totalPages={0}
                                    />
                                </Menu>
                            </Table.HeaderCell>
                        </Table.Row>
                    </Table.Footer> */}
                </Table>
            </div>
        );
    }
}

export default ProcessTable;
