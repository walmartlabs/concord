import * as React from 'react';
import { Button, Header, Icon } from 'semantic-ui-react';

import { ConcordId, RequestError } from '../../../api/common';
import { ProcessStatus } from '../../../api/process';
import { RequestErrorMessage } from '../../molecules';

import './styles.css';

interface Props {
    instanceId: ConcordId;

    loading: boolean;
    status: ProcessStatus | null;
    data: string[];
    error: RequestError;
    length: number;
    completed: boolean;

    startPolling: () => void;
    stopPolling: () => void;
    loadWholeLog: () => void;
    refresh: () => void;
}

class ProcessLogViewer extends React.PureComponent<Props> {
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

    render() {
        const {
            error,
            loading,
            data,
            length,
            completed,
            loadWholeLog,
            refresh,
            status
        } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        // TODO sticky refresh button?
        return (
            <>
                <Header as="h3">
                    <Icon
                        disabled={loading}
                        name="refresh"
                        loading={loading}
                        onClick={() => refresh()}
                    />
                    {status}
                </Header>

                {status &&
                    !completed && (
                        <Button basic={true} disabled={loading} onClick={() => loadWholeLog()}>
                            Show the whole log, {length} byte(s)
                        </Button>
                    )}

                {data.map((value, idx) => (
                    <pre key={idx} className="logEntry">
                        {value}
                    </pre>
                ))}
            </>
        );
    }
}

export default ProcessLogViewer;
