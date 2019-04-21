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

import { connect, Dispatch } from 'react-redux';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { actions } from '../../../state/data/processes/logs';
import { State } from '../../../state/data/processes/logs/types';
import { ProcessLogViewer } from '../../molecules';

import './styles.css';
import * as React from 'react';

interface ExternalProps {
    instanceId: ConcordId;
}

interface StateProps {
    status: ProcessStatus | null;
    loading: boolean;
    data: string[];
    error: RequestError;
    completed: boolean;
}

interface DispatchProps {
    startPolling: (useLocalTime: boolean, showDate: boolean) => void;
    stopPolling: () => void;
    loadWholeLog: (useLocalTime: boolean, showDate: boolean) => void;
    refresh: () => void;
}

interface StateType {
    processes: {
        log: State;
    };
}

const mapStateToProps = ({ processes: { log } }: StateType): StateProps => ({
    status: log.status,
    loading: log.getLog.running,
    error: log.getLog.error,
    data: log.data,
    completed: log.completed
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { instanceId }: ExternalProps
): DispatchProps => ({
    startPolling: (useLocalTime: boolean, showDate: boolean) =>
        dispatch(actions.startProcessLogPolling(instanceId, useLocalTime, showDate)),
    stopPolling: () => dispatch(actions.stopProcessLogPolling()),
    loadWholeLog: (useLocalTime: boolean, showDate: boolean) =>
        dispatch(actions.loadWholeLog(instanceId, useLocalTime, showDate)),
    refresh: () => dispatch(actions.forceRefresh())
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProcessLogViewer);
