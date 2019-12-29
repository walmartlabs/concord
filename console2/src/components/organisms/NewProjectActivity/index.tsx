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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';

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
                        visibility: ProjectVisibility.PRIVATE,
                        description: ''
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

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { orgName }: ExternalProps
): DispatchProps => ({
    submit: (values: NewProjectFormValues) => dispatch(actions.createProject(orgName, values))
});

export default connect(mapStateToProps, mapDispatchToProps)(NewProjectActivity);
