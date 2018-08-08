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
import { Table } from 'semantic-ui-react';
import { ProcessEntry } from '../../../api/process';
import { LocalTimestamp, ProcessActionDropdown, ProcessStatusIcon } from '../../molecules';

interface StateProps {
    processes: ProcessEntry[];
    reload?: () => void;
}

export enum Column {
    STATUS,
    INSTANCE_ID,
    PROJECT,
    INITIATOR,
    CREATED_AT,
    UPDATED_AT,
    ACTIONS
}

interface ExternalProps {
    orgName?: string;
    hideColumns?: Column[];
}

const renderProcessLink = (row: ProcessEntry) => {
    const { instanceId } = row;
    return <Link to={`/process/${instanceId}`}>{instanceId}</Link>;
};

const showColumn = (column: Column, hideColumns?: Column[]): boolean => {
    return !hideColumns || hideColumns.indexOf(column) < 0;
};

const renderTableRow = (row: ProcessEntry, hideColumns?: Column[]) => {
    return (
        <Table.Row key={row.instanceId}>
            {showColumn(Column.STATUS, hideColumns) && (
                <Table.Cell textAlign="center">
                    <ProcessStatusIcon status={row.status} />
                </Table.Cell>
            )}
            {showColumn(Column.INSTANCE_ID, hideColumns) && (
                <Table.Cell>{renderProcessLink(row)}</Table.Cell>
            )}
            {showColumn(Column.PROJECT, hideColumns) && (
                <Table.Cell>
                    <Link to={`/org/${row.orgName}/project/${row.projectName}`}>
                        {row.projectName}
                    </Link>
                </Table.Cell>
            )}
            {showColumn(Column.INITIATOR, hideColumns) && (
                <Table.Cell singleLine={true}>{row.initiator}</Table.Cell>
            )}
            {showColumn(Column.CREATED_AT, hideColumns) && (
                <Table.Cell>
                    <LocalTimestamp value={row.createdAt} />
                </Table.Cell>
            )}
            {showColumn(Column.UPDATED_AT, hideColumns) && (
                <Table.Cell>
                    <LocalTimestamp value={row.lastUpdatedAt} />
                </Table.Cell>
            )}
            {showColumn(Column.ACTIONS, hideColumns) && (
                <Table.Cell>
                    <ProcessActionDropdown instanceId={row.instanceId} status={row.status} />
                </Table.Cell>
            )}
        </Table.Row>
    );
};

class ProcessList extends React.PureComponent<StateProps & ExternalProps> {
    render() {
        const { hideColumns, processes } = this.props;

        if (!processes) {
            return <p>No processes found.</p>;
        }

        return (
            <Table celled={true} attached="bottom">
                <Table.Header>
                    <Table.Row>
                        {showColumn(Column.STATUS, hideColumns) && (
                            <Table.HeaderCell collapsing={true}>Status</Table.HeaderCell>
                        )}
                        {showColumn(Column.INSTANCE_ID, hideColumns) && (
                            <Table.HeaderCell>Instance ID</Table.HeaderCell>
                        )}
                        {showColumn(Column.PROJECT, hideColumns) && (
                            <Table.HeaderCell>Project</Table.HeaderCell>
                        )}
                        {showColumn(Column.INITIATOR, hideColumns) && (
                            <Table.HeaderCell collapsing={true} singleLine={true}>
                                Initiator
                            </Table.HeaderCell>
                        )}
                        {showColumn(Column.CREATED_AT, hideColumns) && (
                            <Table.HeaderCell>Created</Table.HeaderCell>
                        )}
                        {showColumn(Column.UPDATED_AT, hideColumns) && (
                            <Table.HeaderCell>Updated</Table.HeaderCell>
                        )}
                        {showColumn(Column.ACTIONS, hideColumns) && (
                            <Table.HeaderCell collapsing={true} />
                        )}
                    </Table.Row>
                </Table.Header>

                <Table.Body>{processes.map((p) => renderTableRow(p, hideColumns))}</Table.Body>
            </Table>
        );
    }
}

export default ProcessList;
