/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { AnyAction, Dispatch } from 'redux';
import { RouteComponentProps, withRouter } from 'react-router';
import { push as pushHistory } from 'connected-react-router';

import { CustomResources, LinkMeta } from '../../../../cfg';
import { GlobalNavMenu, GlobalNavTab } from '../../molecules';
import { logout, UserSession, UserSessionContext } from '../../../session';

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

interface DispatchProps {
    openUrl: (url: string) => void;
    openAbout: () => void;
    openProfile: () => void;
    openCustomResource: (name: string) => void;
}

export type TopBarProps = DispatchProps & RouteComponentProps<{}>;

class TopBar extends React.PureComponent<TopBarProps> {
    getLogout(userSession: UserSession) {
        const logoutUrl = window.concord?.logoutUrl;
        if (logoutUrl) {
            return () => (window.location.href = logoutUrl);
        }

        return () => logout(userSession);
    }

    render() {
        const activeTab = pathToTab(this.props.location.pathname);
        return (
            <UserSessionContext.Consumer>
                {(value) => (
                    <GlobalNavMenu
                        activeTab={activeTab}
                        extraSystemLinks={getExtraSystemLinks()}
                        customResources={getCustomResources()}
                        userDisplayName={value.userInfo?.displayName}
                        {...this.props}
                        logOut={this.getLogout(value)}
                    />
                )}
            </UserSessionContext.Consumer>
        );
    }
}

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    openUrl: (url: string) => window.open(url, '_blank'),
    openAbout: () => dispatch(pushHistory('/about')),
    openProfile: () => dispatch(pushHistory('/profile')),
    openCustomResource: (name: string) => dispatch(pushHistory(`/custom/${name}`))
});

export default withRouter(connect(null, mapDispatchToProps)(TopBar));
