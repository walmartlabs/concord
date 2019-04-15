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
import { Redirect, Route, RouteComponentProps, RouteProps, withRouter } from 'react-router';
import { Dimmer, Loader } from 'semantic-ui-react';

import { selectors as sessionSelectors, State as SessionState } from '../../../state/session';

import { selectors as loginSelectors, State as LoginState } from '../Login';

interface ProtectedRouteStateProps {
    loggingIn: boolean;
    loggedIn: boolean;
    component?: React.ComponentType<RouteComponentProps<{}>>;
}

type ProtectedRouteProps = ProtectedRouteStateProps;

class ProtectedRoute extends React.PureComponent<ProtectedRouteProps> {
    render() {
        const { loggingIn, loggedIn, component, children, ...rest } = this.props;
        return (
            <Route
                {...rest}
                render={(props) => {
                    if (loggingIn) {
                        return (
                            <Dimmer active={true} inverted={true} page={true}>
                                <Loader active={true} size="massive" content={'Logging in'} />
                            </Dimmer>
                        );
                    }

                    if (!loggedIn) {
                        const { loginUrl } = window.concord;
                        if (loginUrl) {
                            // delay the redirect to avoid layout issues
                            setInterval(() => {
                                window.location.href = loginUrl + props.location.pathname;
                            }, 1000);

                            return (
                                <Dimmer active={true} inverted={true} page={true}>
                                    <Loader active={true} size="massive" content={'Logging in'} />
                                </Dimmer>
                            );
                        } else {
                            return (
                                <Redirect
                                    to={{
                                        pathname: '/login',
                                        state: {
                                            from: props.location
                                        }
                                    }}
                                />
                            );
                        }
                    }

                    if (component) {
                        const Component = component;
                        return <Component {...props} />;
                    }

                    return children;
                }}
            />
        );
    }
}

const mapStateToProps = ({
    login,
    session
}: {
    login: LoginState;
    session: SessionState;
}): ProtectedRouteStateProps => ({
    loggingIn: loginSelectors.isLoggingIn(login),
    loggedIn: sessionSelectors.isLoggedIn(session)
});

const connectedProtectedRoute = connect(mapStateToProps)(ProtectedRoute);

// TODO better types
/* tslint:disable */
export default withRouter(connectedProtectedRoute as any) as React.ComponentType<RouteProps>;
