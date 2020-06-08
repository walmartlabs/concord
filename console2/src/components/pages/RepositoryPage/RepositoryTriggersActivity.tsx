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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { Dimmer, Loader, Table } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import { TriggerEntry } from '../../../api/org/project/repository';
import { actions, ListTriggersResponse, State } from '../../../state/data/triggers';
import { comparators } from '../../../utils';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

interface DispatchProps {
    load: (orgName: ConcordKey, projectName: ConcordKey, repoName: ConcordKey) => void;
}

interface StateProps {
    error: RequestError;
    data?: TriggerEntry[];
}

interface OwnState {
    loading: boolean;
    err: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

class RepositoryTriggersActivity extends React.Component<Props, OwnState> {
    constructor(props: Props) {
        super(props);

        this.state = {
            loading: false,
            err: null
        };
    }

    async componentDidMount() {
        const { load, orgName, projectName, repoName } = this.props;

        try {
            this.setState({ loading: true });
            load(orgName, projectName, repoName);
        } catch (e) {
            this.setState({ err: e });
        } finally {
            this.setState({ loading: false });
        }
    }

    render() {
        const { data, error } = this.props;

        if (error || this.state.err) {
            return <RequestErrorMessage error={error} />;
        }

        if (this.state.loading) {
            return (
                <Dimmer active={this.state.loading} inverted={true} page={true}>
                    <Loader active={true} size="large" />
                </Dimmer>
            );
        }

        return (
            <>
                <Table celled={true} striped={true}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell collapsing={true}>Source</Table.HeaderCell>
                            <Table.HeaderCell>Conditions</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>Entry Point</Table.HeaderCell>
                            <Table.HeaderCell>Configuration</Table.HeaderCell>
                            <Table.HeaderCell>Arguments</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {data &&
                            data.map((t, idx) => (
                                <Table.Row key={idx}>
                                    <Table.Cell>{t.eventSource}</Table.Cell>
                                    <Table.Cell>
                                        {t.conditions && (
                                            <ReactJson
                                                src={t.conditions}
                                                collapsed={true}
                                                name={null}
                                                enableClipboard={false}
                                            />
                                        )}
                                    </Table.Cell>
                                    <Table.Cell>{t.cfg.entryPoint}</Table.Cell>
                                    <Table.Cell>
                                        <ReactJson
                                            src={t.cfg}
                                            collapsed={true}
                                            name={null}
                                            enableClipboard={false}
                                        />
                                    </Table.Cell>
                                    <Table.Cell>
                                        {t.arguments && (
                                            <ReactJson
                                                src={t.arguments}
                                                collapsed={true}
                                                name={null}
                                                enableClipboard={false}
                                            />
                                        )}
                                    </Table.Cell>
                                </Table.Row>
                            ))}
                    </Table.Body>
                </Table>
            </>
        );
    }
}

const prepareData = (resp: ListTriggersResponse | null) => {
    if (!resp || !resp.items) {
        return undefined;
    }

    return resp.items
        .sort(comparators.byProperty((i) => i.cfg.entryPoint))
        .sort(comparators.byProperty((i) => i.eventSource));
};

const mapStateToProps = ({ triggers }: { triggers: State }): StateProps => ({
    error: triggers.listTriggers.error,
    data: prepareData(triggers.listTriggers.response)
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    load: (orgName, projectName, repoName) =>
        dispatch(actions.listTriggers(orgName, projectName, repoName))
});

export default connect(mapStateToProps, mapDispatchToProps)(RepositoryTriggersActivity);
