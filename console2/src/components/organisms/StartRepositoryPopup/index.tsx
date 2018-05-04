import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';
import { Button } from 'semantic-ui-react';

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { StartProcessResponse } from '../../../api/org/process';
import { actions, State } from '../../../state/data/processes';
import { SingleOperationPopup } from '../../molecules';

import './styles.css';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: () => void;
    openProcessPage: (instanceId: ConcordId) => void;
}

interface StateProps {
    starting: boolean;
    success: boolean;
    response: StartProcessResponse | null;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

class StartRepositoryPopup extends React.Component<Props> {
    render() {
        const {
            repoName,
            trigger,
            starting,
            success,
            response,
            error,
            reset,
            onConfirm,
            openProcessPage
        } = this.props;

        const successMsg = response ? (
            <div className="resultBox">
                {JSON.stringify(
                    {
                        ok: response.ok,
                        instanceId: response.instanceId
                    },
                    null,
                    2
                )}
            </div>
        ) : null;

        const instanceId = response ? response.instanceId : undefined;

        return (
            <SingleOperationPopup
                trigger={trigger}
                title="Start a new process?"
                introMsg={
                    <p>
                        Are you sure you want to start a new process using <b>{repoName}</b>{' '}
                        repository?
                    </p>
                }
                running={starting}
                runningMsg={<p>Starting the process...</p>}
                success={success}
                successMsg={successMsg}
                error={error}
                reset={reset}
                onConfirm={onConfirm}
                onDoneElements={() => (
                    <Button
                        basic={true}
                        content="Open the process page"
                        onClick={() => instanceId && openProcessPage(instanceId)}
                    />
                )}
            />
        );
    }
}

const mapStateToProps = ({ processes }: { processes: State }): StateProps => ({
    starting: processes.startProcess.running,
    success: !!processes.startProcess.response && processes.startProcess.response.ok,
    response: processes.startProcess.response,
    error: processes.startProcess.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { orgName, projectName, repoName }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    onConfirm: () => dispatch(actions.startProcess(orgName, projectName, repoName)),
    openProcessPage: (instanceId: ConcordId) => dispatch(pushHistory(`/process/${instanceId}`))
});

export default connect(mapStateToProps, mapDispatchToProps)(StartRepositoryPopup);
