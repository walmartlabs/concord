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

import { ConcordId, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/processes';
import { SingleOperationPopup } from '../../molecules';

interface ExternalProps {
    instanceId: ConcordId;
    disabled: boolean;
    refresh: () => void;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: () => void;
}

interface StateProps {
    disabling: boolean;
    success: boolean;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

class DisableProcessPopup extends React.Component<Props> {
    render() {
        const {
            disabled,
            trigger,
            disabling,
            success,
            error,
            reset,
            refresh,
            onConfirm
        } = this.props;
        const operation = disabled ? 'Disable' : 'Enable';
        return (
            <SingleOperationPopup
                trigger={trigger}
                title={operation + ' the process?'}
                introMsg={
                    <p>Are you sure you want to {operation.toLowerCase()} the selected process?</p>
                }
                running={disabling}
                runningMsg={disabled ? 'Disabling...' : 'Enabling...'}
                success={success}
                error={error}
                reset={reset}
                onDone={refresh}
                onConfirm={onConfirm}
            />
        );
    }
}

const mapStateToProps = ({ processes }: { processes: State }): StateProps => ({
    disabling: processes.disableProcess.running,
    success: !!processes.disableProcess.response,
    error: processes.disableProcess.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { instanceId, disabled }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    onConfirm: () => dispatch(actions.disable(instanceId, disabled))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(DisableProcessPopup);
