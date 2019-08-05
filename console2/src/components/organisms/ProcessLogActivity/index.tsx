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

import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessEntry } from '../../../api/process';
import { actions } from '../../../state/data/processes/logs';
import { LogProcessorOptions } from '../../../state/data/processes/logs/processors';
import { LogSegment, State } from '../../../state/data/processes/logs/types';
import { ProcessLogViewer } from '../../molecules';

import './styles.css';

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    process: ProcessEntry | null;
    loading: boolean;
    data: LogSegment[];
    error: RequestError;
    completed: boolean;
}

interface DispatchProps {
    startPolling: (opts: LogProcessorOptions) => void;
    stopPolling: () => void;
    loadWholeLog: (opts: LogProcessorOptions) => void;
    refresh: () => void;
}

interface StateType {
    processes: {
        log: State;
    };
}

const mapStateToProps = ({ processes: { log } }: StateType): StateProps => ({
    process: log.process,
    loading: log.getLog.running,
    error: log.getLog.error,
    data: log.data,
    completed: log.completed
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { instanceId }: ExternalProps
): DispatchProps => ({
    startPolling: (opts: LogProcessorOptions) =>
        dispatch(actions.startProcessLogPolling(instanceId, opts)),
    stopPolling: () => dispatch(actions.stopProcessLogPolling()),
    loadWholeLog: (opts: LogProcessorOptions) => dispatch(actions.loadWholeLog(instanceId, opts)),
    refresh: () => dispatch(actions.forceRefresh())
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessLogViewer);
