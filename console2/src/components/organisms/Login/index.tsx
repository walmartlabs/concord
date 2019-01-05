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
import { RouteComponentProps, withRouter } from 'react-router';
import {
    Card,
    CardContent,
    Dimmer,
    Divider,
    Form,
    Image,
    InputOnChangeData,
    Loader,
    Message
} from 'semantic-ui-react';

import { State as AppState } from '../../../reducers';
import { actions, selectors } from './effects';

import './styles.css';

// https://github.com/facebook/create-react-app/issues/6054
export * from './effects';

interface LoginData {
    username: string;
    password: string;
    apiKey: string;
}

interface StateProps {
    loggingIn: boolean;
    apiError: string | null;
}

interface DispatchProps {
    onSubmit: (data: LoginData) => void;
}

type Props = StateProps & DispatchProps & RouteComponentProps<{}>;

class Login extends React.Component<Props, LoginData> {
    constructor(props: Props) {
        super(props);
        this.state = { username: '', password: '', apiKey: '' };
    }

    handleUsernameChange(e: {}, { value }: InputOnChangeData) {
        this.setState({ username: value });
    }

    handlePasswordChange(e: {}, { value }: InputOnChangeData) {
        this.setState({ password: value });
    }

    handleApiKeyChange(e: {}, { value }: InputOnChangeData) {
        this.setState({ apiKey: value });
    }

    handleSubmit() {
        this.props.onSubmit(this.state);
    }

    render() {
        const { username, password, apiKey } = this.state;

        const useApiKey = this.props.location.search.search('useApiKey=true') >= 0;

        return (
            <Card centered={true}>
                <CardContent>
                    <Image id="concord-logo" src="/images/concord.svg" size="medium" />

                    <Dimmer active={this.props.loggingIn} inverted={true}>
                        <Loader />
                    </Dimmer>

                    <Form error={!!this.props.apiError} onSubmit={() => this.handleSubmit()}>
                        {!useApiKey && (
                            <Form.Input
                                name="username"
                                label="Username"
                                icon="user"
                                required={true}
                                value={username}
                                onChange={(e, data) => this.handleUsernameChange(e, data)}
                            />
                        )}
                        {!useApiKey && (
                            <Form.Input
                                name="password"
                                label="Password"
                                type="password"
                                icon="lock"
                                required={true}
                                value={password}
                                autoComplete="current-password"
                                onChange={(e, data) => this.handlePasswordChange(e, data)}
                            />
                        )}

                        {useApiKey && (
                            <Form.Input
                                name="apiKey"
                                label="API Key"
                                type="password"
                                icon="lock"
                                required={true}
                                value={apiKey}
                                autoComplete="current-password"
                                onChange={(e, data) => this.handleApiKeyChange(e, data)}
                            />
                        )}

                        <Divider />

                        <Message error={true} content={this.props.apiError} />
                        <Form.Button id="loginButton" primary={true} fluid={true}>
                            Login
                        </Form.Button>
                    </Form>
                </CardContent>
            </Card>
        );
    }
}

const mapStateToProps = ({ login }: AppState): StateProps => ({
    loggingIn: selectors.isLoggingIn(login),
    apiError: selectors.getError(login)
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    onSubmit: ({ username, password, apiKey }: LoginData) =>
        dispatch(actions.doLogin(username, password, apiKey))
});

export default withRouter(
    connect(
        mapStateToProps,
        mapDispatchToProps
    )(Login)
);
