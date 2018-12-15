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
import { Header, Icon, Table, Loader } from 'semantic-ui-react';
import { ConcordId } from '../../../api/common';
import { ProcessHistoryEntry } from '../../../api/process/history';
import { actions, selectors } from '../../../state/data/processes/history';
import { State } from '../../../state/data/processes/history/types';
import { LocalTimestamp } from '../../molecules';
import { formatDuration } from '../../../utils';

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    loading: boolean;
    data: ProcessHistoryEntry[];
}

interface DispatchProps {
    load: (instanceId: ConcordId) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProcessHistoryActivity extends React.Component<Props> {
    componentDidMount() {
        this.init();
    }
    init() {
        const { instanceId, load } = this.props;
        load(instanceId);
    }

    renderTableHeader = () => {
        return (
            <Table.Row>
                <Table.HeaderCell collapsing={true}>Status</Table.HeaderCell>
                <Table.HeaderCell collapsing={true}>Change Time </Table.HeaderCell>
                <Table.HeaderCell collapsing={true}>Elapsed Time </Table.HeaderCell>
            </Table.Row>
        );
    };

    renderTableRow = (row: ProcessHistoryEntry, idx: number) => {
        const { data } = this.props;
        let elapsedTime: string | undefined;

        if (idx > 0) {
            const endTime: Date = new Date(row.changeDate);
            const startTime: Date = new Date(data[idx - 1].changeDate);
            const duration = startTime.getTime() - endTime.getTime();
            elapsedTime = formatDuration(duration);
        }

        return (
            <Table.Row>
                <Table.Cell>{row.status}</Table.Cell>
                <Table.Cell>
                    <LocalTimestamp value={row.changeDate} />
                </Table.Cell>
                <Table.Cell>{elapsedTime}</Table.Cell>
            </Table.Row>
        );
    };

    render() {
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
                    <Table.Header>{this.renderTableHeader()}</Table.Header>
                    <Table.Body>{data.map((p, idx) => this.renderTableRow(p, idx))}</Table.Body>
                </Table>
            </>
        );
    }
}

interface StateType {
    processes: {
        history: State;
    };
}

const makeProcessHistoryList = (data: ProcessHistoryEntry[]): ProcessHistoryEntry[] => {
    if (data === undefined) {
        return [];
    }

    return Object.keys(data)
        .map((k) => data[k])
        .sort((a, b) => (a.changeDate < b.changeDate ? 1 : a.changeDate > b.changeDate ? -1 : 0));
};

export const mapStateToProps = ({ processes: { history } }: StateType): StateProps => ({
    loading: history.getHistory.running,
    data: makeProcessHistoryList(selectors.processHistory(history))
});

export const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (instanceId: ConcordId) => {
        dispatch(actions.getProcessHistory(instanceId));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessHistoryActivity);
