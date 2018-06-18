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
import {
    Card,
    CardContent,
    Dimmer,
    Form,
    Image,
    InputOnChangeData,
    Loader,
    Message
} from 'semantic-ui-react';
import { State as AppState } from '../../../reducers';

import { actions, selectors } from './effects';

export { actions, selectors, sagas, reducers, State } from './effects';

interface LoginData {
    username: string;
    password: string;
}

interface LoginStateProps {
    loggingIn: boolean;
    apiError: string | null;
}

interface LoginDispatchProps {
    onSubmit: (data: LoginData) => void;
}

class Login extends React.Component<LoginStateProps & LoginDispatchProps, LoginData> {
    constructor(props: LoginStateProps & LoginDispatchProps) {
        super(props);
        this.state = { username: '', password: '' };
    }

    handleUsernameChange(e: {}, { value }: InputOnChangeData) {
        this.setState({ username: value });
    }

    handlePasswordChange(e: {}, { value }: InputOnChangeData) {
        this.setState({ password: value });
    }

    handleSubmit() {
        this.props.onSubmit(this.state);
    }

    render() {
        const { username, password } = this.state;

        return (
            <Card centered={true}>
                <CardContent>
                    <Image id="concord-logo" src="/images/concord.svg" size="medium" />

                    <Dimmer active={this.props.loggingIn} inverted={true}>
                        <Loader />
                    </Dimmer>

                    <Form error={!!this.props.apiError} onSubmit={() => this.handleSubmit()}>
                        <Form.Input
                            name="username"
                            label="Username"
                            icon="user"
                            required={true}
                            value={username}
                            onChange={(e, data) => this.handleUsernameChange(e, data)}
                        />
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

                        <Message error={true} content={this.props.apiError} />
                        <Form.Button primary={true} fluid={true}>
                            Login
                        </Form.Button>
                    </Form>
                </CardContent>
            </Card>
        );
    }
}

const mapStateToProps = ({ login }: AppState): LoginStateProps => ({
    loggingIn: selectors.isLoggingIn(login),
    apiError: selectors.getError(login)
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): LoginDispatchProps => ({
    onSubmit: ({ username, password }: LoginData) => dispatch(actions.doLogin(username, password))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(Login);
