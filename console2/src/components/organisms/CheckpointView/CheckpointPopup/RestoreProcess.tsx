/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

/**
 * This is seemingly a duplicate connection, but it was necessary
 * for the Checkpoint View feature.  I expose the redux values to
 * other components via render props
 */

import * as React from 'react';
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { ConcordKey } from '../../../../api/common';
import { actions } from '../../../../state/data/processes';
import { isFinal, ProcessStatus } from '../../../../api/process';
import { isFunction } from 'util';

interface ExternalProps {
    children?: RenderCallback;
    render?: RenderCallback;
}

interface DispatchProps {
    restoreProcess: (instanceId: ConcordKey, checkpointId: ConcordKey) => void;
}

type Props = ExternalProps & DispatchProps;

type RenderCallback = (args: RestoreProcessExposedProps) => JSX.Element;
interface RestoreProcessExposedProps {
    restoreProcess: (instanceId: string, checkpointId: string) => void;
}

export const isFinalStatus = (s: ProcessStatus): boolean =>
    isFinal(s) || s === ProcessStatus.SUSPENDED;

// Exposes Function as children render prop to dispatch redux action
class RestoreProcess extends React.PureComponent<Props> {
    render() {
        const { children, render, restoreProcess } = this.props;

        const renderProps = {
            restoreProcess
        };

        if (render) {
            return render(renderProps);
        }

        if (children) {
            return isFunction(children) ? children(renderProps) : null;
        }

        return null;
    }
}

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    restoreProcess: (instanceId, checkpointId) =>
        dispatch(actions.restoreProcess(instanceId, checkpointId))
});

export default connect(null, mapDispatchToProps)(RestoreProcess);
