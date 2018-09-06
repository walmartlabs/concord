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
import { ColumnDefinition } from '../../../api/org';

interface StateProps {
    processes: ProcessEntry[];
    reload?: () => void;
}

export const INSTANCE_ID_COLUMN: ColumnDefinition = {
    caption: 'Instance ID',
    source: 'instanceId',
    render: 'process-link'
};

export const PROJECT_COLUMN: ColumnDefinition = {
    caption: 'Project',
    source: 'projectName',
    render: 'project-link'
};

export const INITIATOR_COLUMN: ColumnDefinition = {
    caption: 'Initiator',
    source: 'initiator'
};

export const CREATED_AT_COLUMN: ColumnDefinition = {
    caption: 'Created',
    source: 'createdAt',
    render: 'timestamp'
};

export const UPDATED_AT_COLUMN: ColumnDefinition = {
    caption: 'Updated',
    source: 'lastUpdatedAt',
    render: 'timestamp'
};

interface ExternalProps {
    orgName?: string;
    columns: ColumnDefinition[];
}

const getValue = (source: string, e: ProcessEntry) => {
    if (e === undefined) {
        return {};
    }

    const result = {};
    Object.keys(e).forEach((k) => {
        const v = e[k];
        if (v !== undefined && v !== null) {
            result[k] = v;
        }
    });

    if (e.meta !== undefined) {
        const meta = e.meta;
        Object.keys(meta).forEach((k) => {
            const v = meta[k];
            if (v !== undefined && v !== null) {
                result[k] = v;
            }
        });
    }

    return result[source];
};

const renderColumnContent = (e: ProcessEntry, c: ColumnDefinition) => {
    const v = getValue(c.source, e);

    if (c.render === 'process-link') {
        const caption = v || e.instanceId;
        return <Link to={`/process/${e.instanceId}`}>{caption}</Link>;
    } else if (c.render === 'timestamp') {
        return <LocalTimestamp value={v} />;
    } else if (c.render === 'project-link') {
        return <Link to={`/org/${e.orgName}/project/${e.projectName}`}>{v}</Link>;
    }

    return v;
};

const renderColumn = (e: ProcessEntry, c: ColumnDefinition) => {
    return <Table.Cell key={e.instanceId}>{renderColumnContent(e, c)}</Table.Cell>;
};

const renderColumnCaption = (c: ColumnDefinition) => {
    return <Table.HeaderCell key={c.caption}>{c.caption}</Table.HeaderCell>;
};

const renderTableRow = (row: ProcessEntry, columns: ColumnDefinition[]) => {
    return (
        <Table.Row key={row.instanceId}>
            <Table.Cell textAlign="center">
                <ProcessStatusIcon status={row.status} />
            </Table.Cell>
            {columns.map((c) => renderColumn(row, c))}
            <Table.Cell>
                <ProcessActionDropdown instanceId={row.instanceId} status={row.status} />
            </Table.Cell>
        </Table.Row>
    );
};

class ProcessList extends React.PureComponent<StateProps & ExternalProps> {
    render() {
        const { columns, processes } = this.props;

        if (!processes) {
            return <p>No processes found.</p>;
        }

        return (
            <Table celled={true} attached="bottom">
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell collapsing={true}>Status</Table.HeaderCell>
                        {columns.map((c) => renderColumnCaption(c))}
                        <Table.HeaderCell collapsing={true} />
                    </Table.Row>
                </Table.Header>

                <Table.Body>{processes.map((p) => renderTableRow(p, columns))}</Table.Body>
            </Table>
        );
    }
}

export default ProcessList;
