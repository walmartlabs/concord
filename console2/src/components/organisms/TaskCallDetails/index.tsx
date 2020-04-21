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
    </>
);

const renderValueV2 = (v: any) => {
    if (isObject(v)) {
        return <ReactJson src={v} name={null} enableClipboard={false} collapsed={true} />;
    }

    return <pre>{JSON.stringify(v)}</pre>;
};

const renderInputVariablesV2 = (key: string, value: any, index: number) => {
    return (
        <Table.Row key={index}>
            <Table.Cell collapsing={true}>
                <pre>{key}</pre>
            </Table.Cell>
            <Table.Cell>{renderValueV2(value)}</Table.Cell>
        </Table.Row>
    );
};

const renderInputV2 = (data: {}) => (
    <>
        <h4>Input</h4>
        <Table celled={true} compact={true}>
            <Table.Header>
                <Table.Row>
                    <Table.HeaderCell collapsing={true}>Parameter</Table.HeaderCell>
                    <Table.HeaderCell>Value</Table.HeaderCell>
                </Table.Row>
            </Table.Header>
            <Table.Body>
                {Object.keys(data).map((key, index) =>
                    renderInputVariablesV2(key, data[key], index)
                )}
            </Table.Body>
        </Table>
    </>
);

const TaskCallDetails = (props: Props) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError>();
    const [data, setData] = useState<ProcessEventEntry<{}>[]>();

    useEffect(() => {
        const fetchData = async () => {
            setLoading(true);
            setError(undefined);

            try {
                const result = await apiListEvents({
                    instanceId: props.instanceId,
                    type: 'ELEMENT',
                    eventCorrelationId: props.correlationId,
                    eventPhase: 'PRE',
                    includeAll: true,
                    limit: 1
                });

                setData(result);
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

    if (!data || data.length < 1) {
        return <p>No data found.</p>;
    }

    const details = data[0].data as ProcessElementEvent;

    return (
        <>
            <Grid columns={2}>
                <Grid.Row>
                    <Grid.Column>
                        <Table definition={true}>
                            <Table.Body>
                                <Table.Row>
                                    <Table.Cell collapsing={true}>Location</Table.Cell>
                                    <Table.Cell>
                                        line: {details.line}, column: {details.column}
                                    </Table.Cell>
                                </Table.Row>
                            </Table.Body>
                        </Table>
                    </Grid.Column>
                    <Grid.Column>
                        <Table definition={true}>
                            <Table.Body>
                                <Table.Row>
                                    <Table.Cell collapsing={true}>Flow</Table.Cell>
                                    <Table.Cell>{details.processDefinitionId}</Table.Cell>
                                </Table.Row>
                            </Table.Body>
                        </Table>
                    </Grid.Column>
                </Grid.Row>
            </Grid>

            {details.in &&
                details.in instanceof Array &&
                details.in.length > 0 &&
                renderInput(details.in)}
            {details.in && isObject(details.in) && renderInputV2(details.in)}
        </>
    );
};

export default TaskCallDetails;
