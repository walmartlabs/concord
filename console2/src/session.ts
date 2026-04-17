/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import { Organizations } from './state/data/orgs/types';
import { logout as apiLogout, whoami as apiWhoami } from './api/service/console';

export interface UserInfo {
    username: string;
    displayName: string;
    orgs: Organizations;
}

export interface UserSession {
    userInfo?: UserInfo;
    setUserInfo: (userInfo?: UserInfo) => void;

    loggingIn: boolean;
    setLoggingIn: (loggingIn: boolean) => void;
}

export const UserSessionContext = React.createContext<UserSession>({
    loggingIn: true,
    setUserInfo: () => {},
    setLoggingIn: () => {},
});

export const checkSession = async (session: UserSession) => {
    const { userInfo } = session;

    const loggedIn = !!userInfo?.username;
    if (loggedIn) {
        return;
    }

    await refreshSession(session);
};

const refreshSession = async ({ setUserInfo, setLoggingIn }: UserSession) => {
    setLoggingIn(true);
    try {
        const response = await apiWhoami();
        setUserInfo({ ...response });
    } catch (e) {
        console.warn(e);
    } finally {
        setLoggingIn(false);
    }
};

export const logout = async ({ setUserInfo }: UserSession): Promise<boolean> => {
    try {
        await apiLogout();
        setUserInfo(undefined);
        return true;
    } catch (e) {
        console.error('logout:', e);
        return false;
    }
};
