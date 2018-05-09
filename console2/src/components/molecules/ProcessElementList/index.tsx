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
import { Icon, Table } from 'semantic-ui-react';
import {
    ProcessElementEvent,
    ProcessEventEntry,
    ProcessEventType
} from '../../../api/process/event';
import { formatTimestamp } from '../../../utils';

interface Props {
    events: Array<ProcessEventEntry<{}>>;
}

const phaseToStr = (v?: string) => {
    switch (v) {
        case 'pre':
            return '(start)';
        case 'post':
            return '(end)';
        default:
            return '';
    }
};

const renderDefinitionId = (
    { data: { processDefinitionId } }: ProcessEventEntry<ProcessElementEvent>,
    idx: number,
    arr: Array<ProcessEventEntry<ProcessElementEvent>>
) => {
    if (idx !== 0 && arr[idx - 1].data.processDefinitionId === processDefinitionId) {
        return;
    }

    return processDefinitionId;
};

const renderTimestamp = (
    { eventDate }: ProcessEventEntry<{}>,
    idx: number,
    arr: Array<ProcessEventEntry<{}>>
) => {
    const s = formatTimestamp(eventDate);

    if (idx !== 0 && formatTimestamp(arr[idx - 1].eventDate) === s) {
        return '(same)';
    }

    return s;
};

const renderElementRow = (
    ev: ProcessEventEntry<ProcessElementEvent>,
    idx: number,
    arr: Array<ProcessEventEntry<ProcessElementEvent>>
) => {
    return (
        <Table.Row key={idx}>
            <Table.Cell textAlign="right">{renderDefinitionId(ev, idx, arr)}</Table.Cell>
            <Table.Cell>
                {ev.data.description} {phaseToStr(ev.data.phase)}
            </Table.Cell>
            <Table.Cell singleLine={true}>{renderTimestamp(ev, idx, arr)}</Table.Cell>
            <Table.Cell>{ev.data.line}</Table.Cell>
            <Table.Cell>{ev.data.column}</Table.Cell>
        </Table.Row>
    );
};

class ProcessElementList extends React.PureComponent<Props> {
    render() {
        const { events } = this.props;
        return (
            <Table celled={true} definition={true}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell width={1} />
                        <Table.HeaderCell>Step</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true} singleLine={true}>
                            <Icon name="time" />Timestamp
                        </Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Line</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Col</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>

                <Table.Body>
                    {events
                        .filter((e) => e.eventType === ProcessEventType.ELEMENT)
                        .map((e) => e as ProcessEventEntry<ProcessElementEvent>)
                        .map((e, idx, arr) => renderElementRow(e, idx, arr))}
                </Table.Body>
            </Table>
        );
    }
}

export default ProcessElementList;
