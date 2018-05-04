import * as React from 'react';
import { connect, Dispatch } from 'react-redux';

import { ConcordKey, RequestError } from '../../../api/common';
import { ProjectVisibility } from '../../../api/org/project';
import { actions, State } from '../../../state/data/projects';
import { NewProjectForm, NewProjectFormValues, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
}

interface StateProps {
    submitting: boolean;
    error: RequestError;
}

interface DispatchProps {
    submit: (values: NewProjectFormValues) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class NewProjectActivity extends React.PureComponent<Props> {
    render() {
        const { error, submitting, submit, orgName } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <NewProjectForm
                    orgName={orgName}
                    submitting={submitting}
                    onSubmit={submit}
                    initial={{
                        name: '',
                        visibility: ProjectVisibility.PUBLIC,
                        description: '',
                        acceptsRawPayload: false
                    }}
                />
            </>
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    submitting: projects.loading,
    error: projects.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>, { orgName }: ExternalProps): DispatchProps => ({
    submit: (values: NewProjectFormValues) => dispatch(actions.createProject(orgName, values))
});

export default connect(mapStateToProps, mapDispatchToProps)(NewProjectActivity);
