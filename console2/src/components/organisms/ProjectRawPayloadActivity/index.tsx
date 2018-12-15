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
import { Form } from 'semantic-ui-react';
import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/projects';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectId: ConcordId;
    acceptsRawPayload: boolean;
}

interface StateProps {
    updating: boolean;
    error: RequestError;
}

interface DispatchProps {
    update: (orgName: ConcordKey, projectId: ConcordId, acceptsRawPayload: boolean) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectRenameActivity extends React.PureComponent<Props> {
    render() {
        const { error, updating, acceptsRawPayload, update, orgName, projectId } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <Form loading={updating}>
                    <Form.Group>
                        <Form.Checkbox
                            toggle={true}
                            defaultChecked={acceptsRawPayload}
                            onChange={(ev, data) => update(orgName, projectId, !!data.checked)}
                        />

                        <p>
                            Allows users to start new processes using payload archives. When
                            disabled, only the configured repositories can be used to start a new
                            process.
                        </p>
                    </Form.Group>
                </Form>
            </>
        );
    }
}

const mapStateToProps = ({ projects }: { projects: State }): StateProps => ({
    updating: projects.acceptRawPayload.running,
    error: projects.acceptRawPayload.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    update: (orgName, projectId, acceptsRawPayload) =>
        dispatch(actions.setAcceptsRawPayload(orgName, projectId, acceptsRawPayload))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProjectRenameActivity);
