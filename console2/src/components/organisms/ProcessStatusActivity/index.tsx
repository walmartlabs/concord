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
import { push as pushHistory } from 'react-router-redux';
import { Button, Divider, Header, Icon } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { canBeCancelled, hasState, ProcessEntry } from '../../../api/process';
import { FormListEntry } from '../../../api/process/form';
import { actions } from '../../../state/data/processes/poll';
import { State } from '../../../state/data/processes/poll/types';
import { ProcessActionList, ProcessStatusTable } from '../../molecules';
import { CancelProcessPopup, AnsibleStatsActivity } from '../../organisms';
import ProcessCheckpoint from '../CheckpointView/ProcessCheckpoint';
import LogDrawer from '../CheckpointView/LogDrawer';
import { Provider as ConstateProvider } from 'constate';

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    loading: boolean;
    process?: ProcessEntry;
    forms: FormListEntry[];
}

interface DispatchProps {
    startPolling: (forceLoadAll?: boolean) => void;
    stopPolling: () => void;
    refresh: () => void;
    startWizard: () => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

// TODO extract the AnsibleEventBrowser component
class ProcessStatusActivity extends React.Component<Props> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    componentDidMount() {
        this.props.startPolling();
    }

    componentWillUnmount() {
        this.props.stopPolling();
    }

    componentDidUpdate(prevProps: Props) {
        const { instanceId, startPolling, stopPolling } = this.props;
        if (instanceId !== prevProps.instanceId) {
            stopPolling();
            startPolling();
        }
    }

    createAdditionalAction() {
        const { process, refresh } = this.props;

        if (!process) {
            return;
        }

        if (!canBeCancelled(process.status)) {
            return;
        }

        return (
            <CancelProcessPopup
                instanceId={process.instanceId}
                refresh={refresh}
                trigger={(onClick) => (
                    <Button negative={true} icon="delete" content="Cancel" onClick={onClick} />
                )}
            />
        );
    }

    handleForceLoadAll(ev: React.SyntheticEvent) {
        ev.preventDefault();
        this.props.startPolling(true);
    }

    render() {
        const { loading, refresh, startWizard, instanceId, process, forms } = this.props;

        // TODO replace the loading icon with something more visually pleasing
        return (
            <>
                <Header as="h3">
                    <Icon
                        disabled={loading}
                        name="refresh"
                        loading={loading}
                        onClick={() => refresh()}
                    />
                    {process && process.status}
                </Header>

                {process && (
                    <>
                        <Divider content="Process Details" horizontal={true} />
                        <ProcessStatusTable
                            data={process}
                            onOpenWizard={forms.length > 0 ? startWizard : undefined}
                            showStateDownload={hasState(process.status)}
                            additionalActions={this.createAdditionalAction()}
                        />
                    </>
                )}

                {forms.length > 0 && (
                    <>
                        <Divider content="Required Actions" horizontal={true} />
                        <ProcessActionList instanceId={instanceId} forms={forms} />
                    </>
                )}

                {process && (
                    <ConstateProvider>
                        <LogDrawer />

                        <Divider content="Checkpoints" horizontal={true} />

                        <ProcessCheckpoint process={process} />
                    </ConstateProvider>
                )}

                <AnsibleStatsActivity instanceId={instanceId} />
            </>
        );
    }
}

interface StateType {
    processes: {
        poll: State;
    };
}

export const mapStateToProps = ({ processes: { poll } }: StateType): StateProps => ({
    loading: poll.currentRequest.running,
    process: poll.currentRequest.response ? poll.currentRequest.response.process : undefined,
    forms: poll.forms
});

export const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { instanceId }: ExternalProps
): DispatchProps => ({
    startPolling: (forceLoadAll?: boolean) =>
        dispatch(actions.startProcessPolling(instanceId, forceLoadAll)),
    stopPolling: () => dispatch(actions.stopProcessPolling()),
    refresh: () => dispatch(actions.forcePoll()),
    startWizard: () => dispatch(pushHistory(`/process/${instanceId}/wizard?fullScreen=true`))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessStatusActivity);
