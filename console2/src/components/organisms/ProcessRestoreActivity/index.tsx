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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/processes';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';
import { isFinal, ProcessStatus } from '../../../api/process';

interface ExternalProps {
    instanceId: ConcordKey;
    processStatus: ProcessStatus;
    checkpointId: ConcordKey;
    checkpoint: string;
    renderOverride?: React.ReactNode;
}

interface StateProps {
    restoring: boolean;
    error: RequestError;
}

interface DispatchProps {
    restoreProcess: (instanceId: ConcordKey, checkpointId: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

export const isFinalStatus = (s: ProcessStatus): boolean =>
    isFinal(s) || s === ProcessStatus.SUSPENDED;

class ProcessRestoreActivity extends React.PureComponent<Props> {
    render() {
        const {
            error,
            restoreProcess,
            restoring,
            instanceId,
            checkpointId,
            checkpoint,
            processStatus,
            renderOverride
        } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}

                <ButtonWithConfirmation
                    renderOverride={renderOverride}
                    size={'mini'}
                    floated={'right'}
                    disabled={!isFinalStatus(processStatus)}
                    content="restore"
                    loading={restoring}
                    confirmationHeader="Restore the process?"
                    confirmationContent={`Are you sure you want to restore process at ${checkpoint} checkpoint?`}
                    onConfirm={() => restoreProcess(instanceId, checkpointId)}
                />
            </>
        );
    }
}

const mapStateToProps = ({ processes }: { processes: State }): StateProps => ({
    restoring: processes.restoreProcess.running,
    error: processes.restoreProcess.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    restoreProcess: (instanceId, checkpointId) =>
        dispatch(actions.restoreProcess(instanceId, checkpointId))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessRestoreActivity);
