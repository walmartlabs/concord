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
import { Form } from 'semantic-ui-react';
import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { SecretVisibility } from '../../../api/org/secret';
import { actions, State } from '../../../state/data/secrets';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';



interface ExternalProps {
    orgName: ConcordKey;
    secretId: ConcordId;
    secretName: ConcordKey;
    visibility: SecretVisibility;
    renderOverride?: React.ReactNode;
}

interface StateProps {
    updating: boolean;
    error: RequestError;
}

interface DispatchProps {
    update: (orgName: ConcordKey, secretId: ConcordId, secretName: ConcordKey, visibility: SecretVisibility) => void;
}

interface OwnState { 
    newVisibility: SecretVisibility;
}

type Props = ExternalProps & StateProps & DispatchProps;

class ProjectRenameActivity extends React.PureComponent<Props, OwnState> {
    constructor(props: Props) {
        super(props);
        this.state = { newVisibility: this.props.visibility };
    }
    
    onChange(value: string | undefined) {
        if (!value) {
            return;
        }
        
        this.setState({ newVisibility: SecretVisibility[value] });
    }
    
    changeVisibility() {
        const { update, orgName, secretName, secretId } = this.props;
        update(orgName, secretId, secretName, this.state.newVisibility);
    }
    
    render() {
        const { error, updating, visibility, renderOverride } = this.props;

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
                        <ButtonWithConfirmation
                            renderOverride={renderOverride}
                            floated={'right'}
                            disabled={visibility === this.state.newVisibility}
                            content="Change"
                            loading={updating}
                            confirmationHeader="Change the visibility?"
                            confirmationContent={`Are you sure you want to change the visibility to ${this.state.newVisibility}`}
                            onConfirm={() => this.changeVisibility()}
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

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    update: (orgName, secretId, secretName, visibility) =>
        dispatch(actions.updateSecretVisibility(orgName, secretId, secretName, visibility))
});

export default connect(mapStateToProps, mapDispatchToProps)(ProjectRenameActivity);
