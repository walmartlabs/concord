import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/projects';
import { ProjectRenameForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectId: ConcordId;
    projectName: ConcordKey;
}

interface StateProps {
    renaming: boolean;
    error: RequestError;
}

interface DispatchProps {
    rename: (orgName: ConcordKey, projectId: ConcordId, projectName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectRenameActivity extends React.PureComponent<Props> {
    render() {
        const { error, renaming, orgName, projectId, projectName, rename } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <ProjectRenameForm
                    orgName={orgName}
                    submitting={renaming}
                    initial={{
                        name: projectName
                    }}
                    onSubmit={(values) => rename(orgName, projectId, values.name)}
                />
            </>
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    renaming: projects.rename.running,
    error: projects.rename.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    rename: (orgName, projectId, projectName) =>
        dispatch(actions.renameProject(orgName, projectId, projectName))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectRenameActivity);
