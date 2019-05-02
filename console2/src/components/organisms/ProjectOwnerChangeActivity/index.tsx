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

import { ConcordId, ConcordKey, EntityOwner, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/projects';
import { EntityOwnerChangeForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectId: ConcordId;
    projectName: ConcordKey;
    owner: EntityOwner;
}

interface StateProps {
    changing: boolean;
    error: RequestError;
}

interface DispatchProps {
    change: (
        orgName: ConcordKey,
        projectId: ConcordId,
        projectName: ConcordKey,
        owner: string
    ) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectOwnerChangeActivity extends React.PureComponent<Props> {
    constructor(props: Props) {
        super(props);

        this.state = { dirty: false, showConfirm: false, value: props.owner };
    }

    render() {
        const { error, changing, change, orgName, projectId, projectName, owner } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <EntityOwnerChangeForm
                    originalOwner={owner || { username: '' }}
                    confirmationHeader="Change project owner?"
                    confirmationContent="Are you sure you want to change the project's owner?"
                    onSubmit={(value) => change(orgName, projectId, projectName, value.username)}
                    submitting={changing}
                />
            </>
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    changing: projects.changeOwner.running,
    error: projects.changeOwner.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    change: (orgName, projectId, projectName, owner) =>
        dispatch(actions.changeProjectOwner(orgName, projectId, projectName, owner))
});
export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProjectOwnerChangeActivity);
