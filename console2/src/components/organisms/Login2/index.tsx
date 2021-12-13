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
import { RouteComponentProps, withRouter } from 'react-router';
import { useCallback, useContext, useState } from 'react';
import {
    Card,
    CardContent,
    Dimmer,
    Divider,
    Form,
    Image,
    Loader,
    Message
} from 'semantic-ui-react';

import { whoami as apiWhoami } from '../../../api/service/console';
import { UserSessionContext } from '../../../session';

import './styles.css';
import { Link } from 'react-router-dom';
import { parse as parseQueryString } from 'query-string';

const nonEmpty = (s?: string) => {
    if (!s) {
        return;
    }

    const v = s.trim();
    if (v.length === 0) {
        return;
    }

    return v;
};

const getLastLoginType = (): string | null => {
    return localStorage.getItem('lastLoginType');
};

const saveLastLoginType = (type: string) => {
    localStorage.setItem('lastLoginType', type);
};

const clearLastLoginType = () => {
    localStorage.removeItem('lastLoginType');
};

const DEFAULT_DESTINATION = '/';

const getDestination = (props: RouteComponentProps<{}>) => {
    const location = props.location as any;

    if (location && location.state && location.state.from && location.state.from.pathname) {
        return location.state.from.pathname;
    }

    const fromUrl = parseQueryString(props.location.search);

    if (fromUrl && fromUrl.from) {
        return fromUrl.from;
    }

    return DEFAULT_DESTINATION;
};

const Login = (props: RouteComponentProps<{}>) => {
    const [apiError, setApiError] = useState<string | undefined>();
    const [validationError] = useState<string | undefined>();

    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [apiKey, setApiKey] = useState<string>('');
    const [rememberMe, setRememberMe] = useState<boolean | undefined>();
    const [destination] = useState(getDestination(props));
    const { loggingIn, setLoggingIn, setUserInfo } = useContext(UserSessionContext);

    const handleSubmit = useCallback(async () => {
        setLoggingIn(true);
        try {
            const response = await apiWhoami(
                nonEmpty(username),
                nonEmpty(password),
                rememberMe,
                nonEmpty(apiKey)
            );
            setUserInfo({ ...response });

            saveLastLoginType(nonEmpty(apiKey) ? 'apiKey' : 'username');

            props.history.push(destination);
        } catch (e) {
            let msg = e.message || 'Log in error';
            if (e.status === 401) {
                msg = 'Invalid username and/or password';
            }

            setApiError(msg);
        } finally {
            setLoggingIn(false);
        }
    }, [
        username,
        password,
        rememberMe,
        apiKey,
        setLoggingIn,
        setUserInfo,
        props.history,
        destination
    ]);

    const onChangeLoginType = useCallback(() => {
        clearLastLoginType();
        setApiKey('');
        setUsername('');
        setPassword('');
        setRememberMe(false);
    }, []);

    const lastLoginType = getLastLoginType();
    const useApiKey =
        props.location.search.search('useApiKey=true') >= 0 || lastLoginType === 'apiKey';
    const usernameHint = (window.concord?.login || {}).usernameHint || 'Username';

    return (
        <Card centered={true}>
            <CardContent>
                <Image id="concord-logo" src="/images/concord.svg" size="medium" />

                <Dimmer active={loggingIn} inverted={true}>
                    <Loader />
                </Dimmer>

                <Form
                    error={!!apiError || validationError !== undefined}
                    onSubmit={() => handleSubmit()}>
                    {!useApiKey && (
                        <>
                            <Form.Input
                                name="username"
                                label="Username"
                                icon="user"
                                required={true}
                                value={username}
                                placeholder={usernameHint}
                                onChange={(e, { value }) => setUsername(value)}
                            />
                            <Form.Input
                                name="password"
                                label="Password"
                                type="password"
                                icon="lock"
                                required={true}
                                value={password}
                                autoComplete="current-password"
                                onChange={(e, { value }) => setPassword(value)}
                            />
                        </>
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
                            onChange={(e, { value }) => setApiKey(value)}
                        />
                    )}

                    <Form.Checkbox
                        name="rememberMe"
                        label="Remember me"
                        checked={rememberMe}
                        onChange={(e, { checked }) => setRememberMe(checked)}
                    />

                    <Divider />

                    <Message error={true} content={apiError} />
                    <Message error={true} content={validationError} />
                    <Form.Button id="loginButton" primary={true} fluid={true}>
                        Login
                    </Form.Button>
                </Form>
            </CardContent>
            <CardContent extra={true} textAlign={'center'}>
                {useApiKey && (
                    <Link onClick={onChangeLoginType} to="/login">
                        Login with Username and Password
                    </Link>
                )}
                {!useApiKey && (
                    <Link onClick={onChangeLoginType} to="/login?useApiKey=true">
                        Login with API Key
                    </Link>
                )}
            </CardContent>
        </Card>
    );
};

export default withRouter(Login);
