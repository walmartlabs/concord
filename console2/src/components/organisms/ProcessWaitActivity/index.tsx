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
import { connect, Dispatch } from 'react-redux';
import { Link } from 'react-router-dom';
import { Header, Icon, Loader, Table } from 'semantic-ui-react';
import { ConcordId } from '../../../api/common';
import { ProcessWaitHistoryEntry, ProcessWaitPayload, WaitType } from '../../../api/process';
import { actions, selectors } from '../../../state/data/processes/waits';
import { State } from '../../../state/data/processes/waits/types';
import { LocalTimestamp } from '../../molecules';

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    loading: boolean;
    data: ProcessWaitHistoryEntry[];
}

interface DispatchProps {
    load: (instanceId: ConcordId) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProcessWaitActivity extends React.Component<Props> {
    componentDidMount() {
        this.init();
    }

    init() {
        const { instanceId, load } = this.props;
        load(instanceId);
    }

    static renderTableHeader() {
        return (
            <Table.Row>
                <Table.HeaderCell collapsing={true}>Date</Table.HeaderCell>
                <Table.HeaderCell collapsing={true}>Condition</Table.HeaderCell>
                <Table.HeaderCell>Dependencies</Table.HeaderCell>
            </Table.Row>
        );
    }

    static renderProcessLink = (id: ConcordId) => {
        return (
            <p>
                <Link to={`/process/${id}`}>{id}</Link>
            </p>
        );
    };

    static renderCondition({ type, reason, payload }: ProcessWaitHistoryEntry) {
        switch (type) {
            case WaitType.NONE: {
                return (
                    <>
                        <Icon name="check" /> No wait conditions
                    </>
                );
            }
            case WaitType.PROCESS_COMPLETION: {
                return (
                    <>
                        <Icon name="hourglass half" />
                        Waiting for the process to complete
                        {reason && ` (${reason})`}
                    </>
                );
            }
            default:
                return type;
        }
    }

    renderTableRow = (row: ProcessWaitHistoryEntry) => {
        return (
            <Table.Row key={row.id} verticalAlign="top">
                <Table.Cell singleLine={true}>
                    <LocalTimestamp value={row.eventDate} />
                </Table.Cell>
                <Table.Cell collapsing={true}>
                    {ProcessWaitActivity.renderCondition(row)}
                </Table.Cell>
                <Table.Cell>
                    {row.payload.processes && row.payload.processes.map((p) => ProcessWaitActivity.renderProcessLink(p))}
                </Table.Cell>
            </Table.Row>
        );
    };

    render() {
        // TODO error handling

        const { instanceId, loading, load, data } = this.props;

        if (loading) {
            return <Loader active={true} />;
        }

        return (
            <>
                <Header as="h3">
                    <Icon
                        disabled={loading}
                        name="refresh"
                        loading={loading}
                        onClick={() => load(instanceId)}
                    />
                </Header>

                <Table celled={true} attached="bottom">
                    <Table.Header>{ProcessWaitActivity.renderTableHeader()}</Table.Header>
                    <Table.Body>
                        {data && data.length > 0 && data.map((p) => this.renderTableRow(p))}
                        {(!data || (data && data.length === 0)) && (
                            <tr style={{ fontWeight: 'bold' }}>
                                <Table.Cell colSpan={3}>No data available</Table.Cell>
                            </tr>
                        )}
                    </Table.Body>
                </Table>
            </>
        );
    }
}

interface StateType {
    processes: {
        waits: State;
    };
}

const makeProcessWaitList = (data: ProcessWaitHistoryEntry[]): ProcessWaitHistoryEntry[] => {
    if (data === undefined) {
        return [];
    }

    return Object.keys(data)
        .map((k) => data[k])
        .sort((a, b) => (a.waitStart < b.waitStart ? 1 : a.waitStart > b.waitStart ? -1 : 0));
};

export const mapStateToProps = ({ processes: { waits } }: StateType): StateProps => ({
    loading: waits.getWait.running,
    data: makeProcessWaitList(selectors.processWait(waits))
});

export const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (instanceId: ConcordId) => {
        dispatch(actions.getProcessWait(instanceId));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessWaitActivity);
