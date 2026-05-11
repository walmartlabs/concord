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
import { useContext } from 'react';
import { useLocation, useNavigate } from 'react-router';

import { CustomResources, LinkMeta } from '../../../../cfg';
import { GlobalNavMenu, GlobalNavTab } from '../../molecules';
import { logout, UserSessionContext } from '../../../session';

const pathToTab = (s: string): GlobalNavTab => {
    if (s.startsWith('/activity')) {
        return 'activity';
    } else if (s.startsWith('/process')) {
        return 'process';
    } else if (s.startsWith('/org')) {
        return 'org';
    } else if (s.startsWith('/noderoster')) {
        return 'noderoster';
    }

    return null;
};

const getExtraSystemLinks = (): LinkMeta[] => {
    const topBar = window.concord?.topBar;

    if (topBar && topBar.systemLinks) {
        return topBar.systemLinks;
    }

    return [];
};

const getCustomResources = (): CustomResources => {
    const customResources = window.concord?.customResources;
    return customResources || {};
};

interface NavigationProps {
    openUrl: (url: string) => void;
    openAbout: () => void;
    openProfile: () => void;
    openCustomResource: (name: string) => void;
}

const TopBar = () => {
    const userSession = useContext(UserSessionContext);
    const location = useLocation();
    const navigate = useNavigate();

    const navigationProps: NavigationProps = {
        openUrl: (url: string) => window.open(url, '_blank'),
        openAbout: () => navigate('/about'),
        openProfile: () => navigate('/profile'),
        openCustomResource: (name: string) => navigate(`/custom/${name}`),
    };

    const logOut = async () => {
        const logoutUrl = window.concord?.logoutUrl;
        if (logoutUrl) {
            window.location.href = logoutUrl;
            return;
        }

        if (await logout(userSession)) {
            navigate('/logout/done');
        }
    };

    const activeTab = pathToTab(location.pathname);
    return (
        <GlobalNavMenu
            activeTab={activeTab}
            extraSystemLinks={getExtraSystemLinks()}
            customResources={getCustomResources()}
            userDisplayName={userSession.userInfo?.displayName}
            {...navigationProps}
            logOut={logOut}
        />
    );
};

export default TopBar;
