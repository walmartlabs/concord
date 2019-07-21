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

import { push as pushHistory } from 'connected-react-router';
import * as React from 'react';
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { Button, Divider, Header, Icon } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import {
    canBeCancelled,
    hasState,
    isFinal,
    ProcessEntry,
    ProcessStatus
} from '../../../api/process';
import { FormListEntry } from '../../../api/process/form';
import { actions } from '../../../state/data/processes/poll';
import { State } from '../../../state/data/processes/poll/types';
import { ProcessActionList, ProcessLastErrorModal, ProcessStatusTable } from '../../molecules';
import { AnsibleStatsActivity, CancelProcessPopup, DisableProcessPopup } from '../../organisms';
import ProcessCheckpoint from '../CheckpointView/ProcessCheckpoint';

import './styles.css';

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

    static disableIcon(disable: boolean) {
        return <Icon name="power" color={disable ? 'green' : 'grey'} />;
    }

    createAdditionalAction() {
        const { process, refresh, startPolling } = this.props;

        if (!process) {
            return;
        }

        return (
            <>
                {canBeCancelled(process.status) && (
                    <CancelProcessPopup
                        instanceId={process.instanceId}
                        refresh={refresh}
                        trigger={(onClick: any) => (
                            <Button
                                attached={false}
                                negative={true}
                                icon="delete"
                                content="Cancel"
                                onClick={onClick}
                            />
                        )}
                    />
                )}
                {isFinal(process.status) && (
                    <DisableProcessPopup
                        instanceId={process.instanceId}
                        disabled={!process.disabled}
                        refresh={startPolling}
                        trigger={(onClick: any) => (
                            <Button
                                attached={false}
                                icon={ProcessStatusActivity.disableIcon(process.disabled)}
                                content={process.disabled ? 'Enable' : 'Disable'}
                                onClick={onClick}
                            />
                        )}
                    />
                )}
            </>
        );
    }

    render() {
        const { loading, refresh, startWizard, instanceId, process, forms } = this.props;

        // TODO replace the loading icon with something more visually pleasing
        return (
            <>
                <Header as="h3">
                    <div>
                        <Icon
                            disabled={loading}
                            name="refresh"
                            loading={loading}
                            onClick={() => refresh()}
                        />
                        {process && process.status}
                        {process && process.status === ProcessStatus.FAILED && (
                            <ProcessLastErrorModal process={process} />
                        )}
                    </div>
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
                    <>
                        <Divider content="Checkpoints" horizontal={true} />

                        <ProcessCheckpoint process={process} />
                    </>
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
    dispatch: Dispatch<AnyAction>,
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
