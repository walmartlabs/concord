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
import { EditProjectForm, RequestErrorMessage } from '../../molecules';
import { UpdateProjectEntry } from '../../../api/org/project';

interface ExternalProps {
    orgName: ConcordKey;
    projectEntry: UpdateProjectEntry;
}

interface StateProps {
    submitting: boolean;
    error: RequestError;
}

interface DispatchProps {
    update: (orgName: ConcordKey, projectEntry: UpdateProjectEntry) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class EditProjectActivity extends React.PureComponent<Props> {
    render() {
        const { error, submitting, update, orgName, projectEntry } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}

                <EditProjectForm
                    submitting={submitting}
                    data={projectEntry}
                    onSubmit={(values) => update(orgName, values.data)}
                />
            </>
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    submitting: projects.updateProject.running,
    error: projects.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    update: (orgName, projectEntity) => dispatch(actions.updateProject(orgName, projectEntity))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(EditProjectActivity);
