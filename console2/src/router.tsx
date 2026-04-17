/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import {
    To,
    useLocation,
    useNavigate,
    useParams,
    type Location,
    type NavigateFunction,
} from 'react-router';

interface HistoryCompat {
    location: Location;
    push: NavigateFunction;
    replace: (to: To) => void;
}

export interface RouteComponentProps<P = {}> {
    history: HistoryCompat;
    location: Location;
    match: {
        isExact: boolean;
        params: P;
        path: string;
        url: string;
    };
}

export const useHistory = (): HistoryCompat => {
    const location = useLocation();
    const navigate = useNavigate();

    return React.useMemo(
        () => ({
            location,
            push: navigate,
            replace: (to: To) => navigate(to, { replace: true }),
        }),
        [location, navigate]
    );
};

export const withRouter = <P extends RouteComponentProps<any>>(
    Component: React.ComponentType<P>
) => {
    const WithRouter = (props: Omit<P, keyof RouteComponentProps<any>>) => {
        const location = useLocation();
        const params = useParams();
        const history = useHistory();

        return (
            <Component
                {...(props as P)}
                history={history}
                location={location}
                match={{
                    isExact: false,
                    params: params as any,
                    path: '',
                    url: location.pathname,
                }}
            />
        );
    };

    WithRouter.displayName = `withRouter(${Component.displayName || Component.name || 'Component'})`;

    return WithRouter;
};
