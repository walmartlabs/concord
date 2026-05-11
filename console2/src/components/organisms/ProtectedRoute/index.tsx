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
import { ReactNode, useContext } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { Dimmer, Loader } from 'semantic-ui-react';

import { UserSessionContext } from '../../../session';
import { setQueryParam } from '../../../utils';

interface ProtectedRouteProps {
    children?: ReactNode;
}

const ProtectedRoute = ({ children }: ProtectedRouteProps) => {
    const { loggingIn, userInfo } = useContext(UserSessionContext);
    const location = useLocation();

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
                window.location.href = setQueryParam(loginUrl, 'from', '/' + requested);
            }, 1000);

            return (
                <Dimmer active={true} inverted={true} page={true}>
                    <Loader active={true} size="massive" content={'Logging in'} />
                </Dimmer>
            );
        } else {
            return (
                <Navigate
                    to="/login"
                    replace={true}
                    state={{
                        from: location,
                    }}
                />
            );
        }
    }

    return <>{children || <Outlet />}</>;
};

export default ProtectedRoute;
