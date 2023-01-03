/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { ConcordId } from '../../../api/common';
import { Table } from 'semantic-ui-react';

interface ExternalProps {
    instanceId: ConcordId;
    data?: string[];
}

const ProcessAttachmentsList = ({ instanceId, data }: ExternalProps) => {
    return (
        <Table celled={true} className={data ? '' : 'loading'}>
            <Table.Header>{renderTableHeader()}</Table.Header>
            <Table.Body>{renderElements(instanceId, data)}</Table.Body>
        </Table>
    );
};

const renderTableHeader = () => {
    return (
        <Table.Row>
            <Table.HeaderCell collapsing={true}>Attached File(s)</Table.HeaderCell>
        </Table.Row>
    );
};

const renderTableRow = (instanceId: ConcordId, attachment: string) => {
    const attachmentName = attachment.substring(attachment.lastIndexOf('/'));
    return (
        <Table.Row key={attachment}>
            <Table.Cell>
                <a
                    href={'/api/v1/process/' + instanceId + '/attachment/' + attachment}
                    download={attachmentName}>
                    {attachment}
                </a>
            </Table.Cell>
        </Table.Row>
    );
};

const renderElements = (instanceId: ConcordId, data?: string[]) => {
    if (!data) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={3}>-</Table.Cell>
            </tr>
        );
    }

    if (data.length === 0) {
        return (
            <tr style={{ fontWeight: 'bold' }}>
                <Table.Cell colSpan={3}>No data available</Table.Cell>
            </tr>
        );
    }

    return data.map((p) => renderTableRow(instanceId, p));
};

export default ProcessAttachmentsList;
