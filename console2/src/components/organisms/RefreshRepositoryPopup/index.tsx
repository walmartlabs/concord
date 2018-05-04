import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/projects';
import { SingleOperationPopup } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: () => void;
}

interface StateProps {
    refreshing: boolean;
    success: boolean;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

class RefreshRepositoryPopup extends React.Component<Props> {
    render() {
        const { trigger, refreshing, success, error, reset, onConfirm } = this.props;

        return (
            <SingleOperationPopup
                trigger={trigger}
                title="Refresh repository?"
                introMsg={
                    <p>
                        Refreshing the repository will update the Concord's cache and reload the
                        project's trigger definitions.
                    </p>
                }
                running={refreshing}
                success={success}
                successMsg={<p>The repository will be refreshed momentarily.</p>}
                error={error}
                reset={reset}
                onConfirm={onConfirm}
            />
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    refreshing: projects.refreshRepository.running,
    success: !!projects.refreshRepository.response && projects.refreshRepository.response.ok,
    error: projects.refreshRepository.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { orgName, projectName, repoName }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.resetRepository()),
    onConfirm: () => dispatch(actions.refreshRepository(orgName, projectName, repoName))
});

export default connect(mapStateToProps, mapDispatchToProps)(RefreshRepositoryPopup);
