/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { isProjectExists } from '../../../api/service/console';
import { actions, State } from '../../../state/data/projects';
import { projectAlreadyExistsError } from '../../../validation';
import { EntityRenameForm, RequestErrorMessage } from '../../molecules';

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
                <EntityRenameForm
                    originalName={projectName}
                    submitting={renaming}
                    onSubmit={(values) => rename(orgName, projectId, values.name)}
                    inputPlaceholder="Project name"
                    confirmationHeader="Rename the project?"
                    confirmationContent="Are you sure you want to rename the project?"
                    isExists={(name) => isProjectExists(orgName, name)}
                    alreadyExistsTemplate={projectAlreadyExistsError}
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

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProjectRenameActivity);
