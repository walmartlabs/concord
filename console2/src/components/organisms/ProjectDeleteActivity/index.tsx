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

import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/projects';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';

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

class ProjectDeleteActivity extends React.PureComponent<Props> {
    render() {
        const { error, deleting, orgName, projectName, deleteProject } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <ButtonWithConfirmation
                    primary={true}
                    negative={true}
                    content="Delete"
                    loading={deleting}
                    confirmationHeader="Delete the project?"
                    confirmationContent="Are you sure you want to delete the project?"
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

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProjectDeleteActivity);
