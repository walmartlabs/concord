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
import { SecretVisibility } from '../../../api/org/secret';
import { actions, State } from '../../../state/data/secrets';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretId: ConcordId;
    visibility: SecretVisibility;
}

interface StateProps {
    updating: boolean;
    error: RequestError;
}

interface DispatchProps {
    update: (orgName: ConcordKey, secretId: ConcordId, visibility: SecretVisibility) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectRenameActivity extends React.PureComponent<Props> {
    onChange(value: string | undefined) {
        if (!value) {
            return;
        }

        const { update, orgName, secretId } = this.props;
        update(orgName, secretId, SecretVisibility[value]);
    }

    render() {
        const { error, updating, visibility } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <Form loading={updating}>
                    <Form.Group>
                        <Form.Dropdown
                            selection={true}
                            options={[
                                {
                                    text: 'Public',
                                    icon: 'unlock',
                                    value: SecretVisibility.PUBLIC
                                },
                                {
                                    text: 'Private',
                                    icon: 'lock',
                                    value: SecretVisibility.PRIVATE
                                }
                            ]}
                            defaultValue={visibility}
                            onChange={(ev, data) => this.onChange(data.value as string)}
                        />
                    </Form.Group>
                </Form>
            </>
        );
    }
}

const mapStateToProps = ({ secrets }: { secrets: State }): StateProps => ({
    updating: secrets.updateSecretVisibility.running,
    error: secrets.updateSecretVisibility.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    update: (orgName, secretId, visibility) =>
        dispatch(actions.updateSecretVisibility(orgName, secretId, visibility))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProjectRenameActivity);
