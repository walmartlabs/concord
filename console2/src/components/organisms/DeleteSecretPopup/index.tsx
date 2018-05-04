import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';

import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/secrets';
import { SingleOperationPopup } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: () => void;
    onDone: () => void;
}

interface StateProps {
    deleting: boolean;
    success: boolean;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

class DeleteSecretPopup extends React.Component<Props> {
    render() {
        const { trigger, deleting, success, error, reset, onConfirm, onDone } = this.props;

        return (
            <SingleOperationPopup
                trigger={trigger}
                title="Delete secret?"
                introMsg={
                    <p>
                        Are you sure you want to delete the secret? Any process or repository that
                        uses this secret may stop working correctly.
                    </p>
                }
                running={deleting}
                runningMsg={<p>Removing the secret...</p>}
                success={success}
                successMsg={<p>The secret was removed successfully.</p>}
                error={error}
                reset={reset}
                onConfirm={onConfirm}
                onDone={onDone}
            />
        );
    }
}

const mapStateToProps = ({ secrets }: { secrets: State }): StateProps => ({
    deleting: secrets.deleteSecret.running,
    success: !!secrets.deleteSecret.response && secrets.deleteSecret.response.ok,
    error: secrets.deleteSecret.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { orgName, secretName }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    onConfirm: () => dispatch(actions.deleteSecret(orgName, secretName)),
    onDone: () => dispatch(pushHistory(`/org/${orgName}/secret`))
});

export default connect(mapStateToProps, mapDispatchToProps)(DeleteSecretPopup);
