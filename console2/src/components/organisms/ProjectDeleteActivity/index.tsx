import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/projects';
import { ProjectDeleteButton, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

interface StateProps {
    deleting: boolean;
    error: RequestError;
}

interface DispatchProps {
    deleteProject: (orgName: ConcordKey, projectName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectRenameActivity extends React.PureComponent<Props> {
    render() {
        const { error, deleting, orgName, projectName, deleteProject } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <ProjectDeleteButton
                    submitting={deleting}
                    onConfirm={() => deleteProject(orgName, projectName)}
                />
            </>
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    deleting: projects.deleteProject.running,
    error: projects.deleteProject.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    deleteProject: (orgName, projectName) => dispatch(actions.deleteProject(orgName, projectName))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectRenameActivity);
