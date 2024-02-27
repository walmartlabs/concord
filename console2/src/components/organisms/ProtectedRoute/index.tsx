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
import { ReactNode } from 'react';
import { Redirect, Route, RouteComponentProps, RouteProps, withRouter } from 'react-router';
import { Dimmer, Loader } from 'semantic-ui-react';

import { UserSession, UserSessionContext } from '../../../session';
import {setQueryParam} from "../../../utils";

interface ProtectedRouteStateProps {
    component?: React.ComponentType<RouteComponentProps<{}>>;
}

type ProtectedRouteProps = ProtectedRouteStateProps;

const renderRoute = (
    props: any,
    { loggingIn, userInfo }: UserSession,
    component?: React.ComponentType<RouteComponentProps<{}>>,
    children?: ReactNode
) => {
    if (loggingIn) {
        return (
            <Dimmer active={true} inverted={true} page={true}>
                <Loader active={true} size="massive" content={'Logging in'} />
            </Dimmer>
        );
    }

    const loggedIn = !!userInfo?.username;

    if (!loggedIn) {
        const loginUrl = window.concord?.loginUrl;
        if (loginUrl) {
            const requested = new URL(window.location.href).hash;
            // delay the redirect to avoid layout issues
            setTimeout(() => {
                window.location.href = setQueryParam(loginUrl, 'from', '/' + requested)
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
};

class ProtectedRoute extends React.PureComponent<ProtectedRouteProps> {
    render() {
        const { component, children, ...rest } = this.props;

        return (
            <UserSessionContext.Consumer>
                {(userSession) => (
                    <Route
                        {...rest}
                        render={(props) => renderRoute(props, userSession, component, children)}
                    />
                )}
            </UserSessionContext.Consumer>
        );
    }
}

// TODO better types
/* tslint:disable */
export default withRouter(ProtectedRoute as any) as React.ComponentType<RouteProps>;
