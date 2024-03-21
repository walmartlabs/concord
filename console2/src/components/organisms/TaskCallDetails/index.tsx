/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import { isObject } from 'formik';
import * as React from 'react';
import { useEffect, useState } from 'react';
import ReactJson from 'react-json-view';
import { Grid, Loader, Table } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import {
    listEvents as apiListEvents,
    ProcessElementEvent,
    ProcessEventEntry,
    VariableMapping
} from '../../../api/process/event';
import { comparators } from '../../../utils';
import { RequestErrorMessage } from '../../molecules';
import { ScrollableX } from '../../atoms/Scrollable';

export interface Props {
    instanceId: ConcordId;
    correlationId: ConcordId;
}

const renderSource = (v: VariableMapping) => {
    if (v.source) {
        return `Flow variable: ${v.source}`;
    }

    if (v.sourceValue) {
        return 'Literal value';
    }

    if (v.sourceExpression) {
        return <pre>{v.sourceExpression}</pre>;
    }
};

const renderTarget = (v: VariableMapping) => {
    return <pre>{v.target}</pre>;
};

const renderValue = (v: VariableMapping) => {
    const data = v.resolved || v.sourceValue;

    if (isObject(data)) {
        return <ReactJson src={data} name={null} enableClipboard={false} collapsed={true} />;
    }

    return <pre>{JSON.stringify(data)}</pre>;
};

const renderInputVariable = (v: VariableMapping, idx: number) => (
    <Table.Row key={idx}>
        <Table.Cell collapsing={true}>{renderTarget(v)}</Table.Cell>
        <Table.Cell collapsing={true}>{renderSource(v)}</Table.Cell>
        <Table.Cell>{renderValue(v)}</Table.Cell>
    </Table.Row>
);

const renderInput = (data: VariableMapping[]) => (
    <>
        <h4>Input</h4>
        <div style={{ overflowX: 'auto' }}>
            <Table celled={true} compact={true}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell collapsing={true}>Parameter</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Source</Table.HeaderCell>
                        <Table.HeaderCell>Value</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>
                <Table.Body>
                    {data.sort(comparators.byProperty((i) => i.target)).map(renderInputVariable)}
                </Table.Body>
            </Table>
        </div>
    </>
);

const renderValueV2 = (v: any) => {
    if (isObject(v)) {
        return <ReactJson src={v} name={null} enableClipboard={false} collapsed={true} />;
    }

    return <pre>{JSON.stringify(v)}</pre>;
};

const renderVariablesV2 = (key: string, value: any, index: number) => {
    return (
        <Table.Row key={index}>
            <Table.Cell collapsing={true}>
                <pre>{key}</pre>
            </Table.Cell>
            <Table.Cell>{renderValueV2(value)}</Table.Cell>
        </Table.Row>
    );
};

const renderDetailsV2 = (label: string, data: {}) => (
    <>
        <h4>{label}</h4>
        <div style={{ overflowX: 'auto' }}>
            <Table celled={true} compact={true}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell collapsing={true}>Parameter</Table.HeaderCell>
                        <Table.HeaderCell>Value</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>
                <Table.Body>
                    {Object.keys(data).map((key, index) =>
                        renderVariablesV2(key, data[key], index)
                    )}
                </Table.Body>
            </Table>
        </div>
    </>
);

/**
 * Return a POST event if it's present in the array or PRE if it's not.
 */
const pickEvent = (
    data?: ProcessEventEntry<ProcessElementEvent>[]
): ProcessEventEntry<ProcessElementEvent> | undefined => {
    if (!data) {
        return;
    }

    if (data.length === 1) {
        return data[0];
    }

    if (data.length !== 2) {
        return;
    }

    if (data[0].data.phase === 'pre') {
        // runtime-v1 events have 'in' data only in the 'pre' phase
        // Copy the 'post' event to a new object and merge 'pre' phase 'in' data
        // if it exists
        const entry: ProcessEventEntry<ProcessElementEvent> = {
            ...data[1]
        };

        if (!entry.data.in && data[0].data.in) {
            entry.data.in = data[0].data.in;
        }

        return entry;
    } else {
        return data[0];
    }
};

const TaskCallDetails = (props: Props) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [data, setData] = useState<ProcessEventEntry<ProcessElementEvent>[]>();

    useEffect(() => {
        const fetchData = async () => {
            setLoading(true);
            setError(undefined);

            try {
                const result = await apiListEvents({
                    instanceId: props.instanceId,
                    type: 'ELEMENT',
                    eventCorrelationId: props.correlationId,
                    includeAll: true,
                    limit: 2
                });

                setData(result as ProcessEventEntry<ProcessElementEvent>[]);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [props.instanceId, props.correlationId]);

    if (loading) {
        return <Loader active={loading} />;
    }

    if (error) {
        return <RequestErrorMessage error={error} />;
    }

    const details = pickEvent(data)?.data;

    if (!details) {
        return <p>No data found.</p>;
    }

    return (
        <>
            <Grid columns={2}>
                <Grid.Row>
                    <Grid.Column>
                        <ScrollableX style={{ height: '100%' }}>
                            <Table definition={true} style={{ height: '100%' }}>
                                <Table.Body>
                                    <Table.Row>
                                        <Table.Cell collapsing={true}>Location</Table.Cell>
                                        <Table.Cell>
                                            {details.fileName ? details.fileName + ' @ ' : ''} line:{' '}
                                            {details.line}, column: {details.column}
                                        </Table.Cell>
                                    </Table.Row>
                                </Table.Body>
                            </Table>
                        </ScrollableX>
                    </Grid.Column>
                    <Grid.Column>
                        <ScrollableX style={{ height: '100%' }}>
                            <Table definition={true} style={{ height: '100%' }}>
                                <Table.Body>
                                    <Table.Row>
                                        <Table.Cell collapsing={true}>Flow</Table.Cell>
                                        <Table.Cell>{details.processDefinitionId}@{details.threadId || 0}</Table.Cell>
                                    </Table.Row>
                                </Table.Body>
                            </Table>
                        </ScrollableX>
                    </Grid.Column>
                </Grid.Row>
            </Grid>

            {details.in &&
                details.in instanceof Array &&
                details.in.length > 0 &&
                renderInput(details.in)}

            {details.in &&
                !(details.in instanceof Array) &&
                isObject(details.in) &&
                renderDetailsV2('Input', details.in)}

            {details.out &&
                !(details.out instanceof Array) &&
                isObject(details.out) &&
                renderDetailsV2('Output', details.out)}
        </>
    );
};

export default TaskCallDetails;
