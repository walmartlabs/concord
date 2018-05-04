import { connect, Dispatch } from 'react-redux';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { actions } from '../../../state/data/processes/logs';
import { State } from '../../../state/data/processes/logs/types';
import { ProcessLogViewer } from '../../molecules';

import './styles.css';

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
    startPolling: () => void;
    stopPolling: () => void;
    loadWholeLog: () => void;
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
    startPolling: () => dispatch(actions.startProcessLogPolling(instanceId)),
    stopPolling: () => dispatch(actions.stopProcessLogPolling()),
    loadWholeLog: () => dispatch(actions.loadWholeLog(instanceId)),
    refresh: () => dispatch(actions.forceRefresh())
});

export default connect(mapStateToProps, mapDispatchToProps)(ProcessLogViewer);
