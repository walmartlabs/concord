import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ConcordId, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/processes';
import { SingleOperationPopup } from '../../molecules';

interface ExternalProps {
    instanceId: ConcordId;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: () => void;
}

interface StateProps {
    cancelling: boolean;
    success: boolean;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

class CancelProcessPopup extends React.Component<Props> {
    render() {
        const { trigger, cancelling, success, error, reset, onConfirm } = this.props;

        return (
            <SingleOperationPopup
                trigger={trigger}
                title="Cancel the process?"
                introMsg={<p>Are you sure you want to cancel the selected process?</p>}
                running={cancelling}
                runningMsg={<p>Cancelling...</p>}
                success={success}
                successMsg={<p>The cancel command was sent successfully.</p>}
                error={error}
                reset={reset}
                onConfirm={onConfirm}
            />
        );
    }
}

const mapStateToProps = ({ processes }: { processes: State }): StateProps => ({
    cancelling: processes.cancelProcess.running,
    success: !!processes.cancelProcess.response,
    error: processes.cancelProcess.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { instanceId }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    onConfirm: () => dispatch(actions.cancel(instanceId))
});

export default connect(mapStateToProps, mapDispatchToProps)(CancelProcessPopup);
