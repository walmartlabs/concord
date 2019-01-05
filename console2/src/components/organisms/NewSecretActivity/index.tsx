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

import * as copyToClipboard from 'copy-to-clipboard';
import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';
import { Button, Message, Modal, TextArea } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import {
    NewSecretEntry,
    SecretStoreType,
    SecretTypeExt,
    SecretVisibility
} from '../../../api/org/secret';
import { validatePassword } from '../../../api/service/console';
import { actions, State } from '../../../state/data/secrets';
import { CreateSecretResponse } from '../../../state/data/secrets/types';
import { passwordTooWeakError } from '../../../validation';
import { NewSecretForm, NewSecretFormValues, RequestErrorMessage } from '../../molecules';

import './styles.css';

interface OwnState {
    showPasswordConfirm: boolean;
    currentEntry?: NewSecretEntry;
}

interface ExternalProps {
    orgName: ConcordKey;
}

interface StateProps {
    submitting: boolean;
    error: RequestError;
    response?: CreateSecretResponse;
}

interface DispatchProps {
    submit: (values: NewSecretFormValues) => void;
    reset: () => void;
    done: (secretName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class NewSecretActivity extends React.Component<Props, OwnState> {
    constructor(props: Props) {
        super(props);
        this.state = { showPasswordConfirm: false };
    }

    componentDidMount() {
        this.props.reset();
    }

    async handleSubmit(entry: NewSecretEntry, confirm?: boolean) {
        if (confirm) {
            this.setState({ showPasswordConfirm: false });
        } else if (entry.type === SecretTypeExt.USERNAME_PASSWORD && entry.password) {
            const valid = await validatePassword(entry.password);
            if (!valid) {
                this.setState({ showPasswordConfirm: true, currentEntry: entry });
                return;
            }
        }

        this.props.submit(entry);
    }

    renderResponse() {
        const { response, done, error } = this.props;

        if (!response) {
            return;
        }

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        const { name: secretName, publicKey, password } = response;

        return (
            <>
                <Message success={true}>
                    <Message.Header>Secret created</Message.Header>

                    {publicKey && (
                        <div>
                            <b>Public key: </b>
                            <Button
                                icon="copy"
                                size="mini"
                                basic={true}
                                onClick={() => (copyToClipboard as any)(publicKey)}
                            />
                            <TextArea className="secretData" value={publicKey} rows={5} />
                        </div>
                    )}

                    {password && (
                        <div>
                            <b>Export password: </b>
                            <Button
                                icon="copy"
                                size="mini"
                                basic={true}
                                onClick={() => (copyToClipboard as any)(password)}
                            />
                            <TextArea className="secretData" value={password} rows={2} />
                        </div>
                    )}
                </Message>

                <Button primary={true} content={'Done'} onClick={() => done(secretName)} />
            </>
        );
    }

    renderPasswordWarning() {
        return (
            <Modal open={this.state.showPasswordConfirm} dimmer="inverted">
                <Modal.Content>{passwordTooWeakError()}</Modal.Content>
                <Modal.Actions>
                    <Button
                        color="grey"
                        onClick={() => this.setState({ showPasswordConfirm: false })}
                        content="Cancel"
                    />
                    <Button
                        color="yellow"
                        onClick={() => this.handleSubmit(this.state.currentEntry!, true)}
                        content="Ignore"
                    />
                </Modal.Actions>
            </Modal>
        );
    }

    render() {
        const { error, submitting, orgName, response } = this.props;

        if (!error && response) {
            return this.renderResponse();
        }

        return (
            <>
                {error && <RequestErrorMessage error={error} />}

                {this.renderPasswordWarning()}

                <NewSecretForm
                    orgName={orgName}
                    submitting={submitting}
                    onSubmit={(entry) => this.handleSubmit(entry)}
                    initial={{
                        name: '',
                        visibility: SecretVisibility.PUBLIC,
                        type: SecretTypeExt.NEW_KEY_PAIR,
                        storeType: SecretStoreType.CONCORD
                    }}
                />
            </>
        );
    }
}

const mapStateToProps = ({ secrets }: { secrets: State }): StateProps => ({
    submitting: secrets.createSecret.running,
    error: secrets.createSecret.error,
    response: secrets.createSecret.response as CreateSecretResponse
});

const mapDispatchToProps = (dispatch: Dispatch<{}>, { orgName }: ExternalProps): DispatchProps => ({
    submit: (entry: NewSecretEntry) => {
        dispatch(actions.createSecret(orgName, entry));
    },

    reset: () => {
        dispatch(actions.reset());
    },

    done: (secretName: ConcordKey) => {
        dispatch(pushHistory(`/org/${orgName}/secret/${secretName}`));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(NewSecretActivity);
