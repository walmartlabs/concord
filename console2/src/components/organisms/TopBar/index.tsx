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
import { AnyAction, Dispatch } from 'redux';
import { RouteComponentProps, withRouter } from 'react-router';
import { push as pushHistory } from 'connected-react-router';
import { LinkMeta } from '../../../../cfg';

import { actions, State as SessionState } from '../../../state/session';
import { GlobalNavMenu, GlobalNavTab } from '../../molecules';

const pathToTab = (s: string): GlobalNavTab => {
    if (s.startsWith('/activity')) {
        return 'activity';
    } else if (s.startsWith('/process')) {
        return 'process';
    } else if (s.startsWith('/org')) {
        return 'org';
    }

    return null;
};

const getExtraSystemLinks = (): LinkMeta[] => {
    const { topBar } = window.concord;
    if (topBar && topBar.systemLinks) {
        return topBar.systemLinks;
    }
    return [];
};

interface StateProps {
    userDisplayName?: string;
}

interface DispatchProps {
    openUrl: (url: string) => void;
    openAbout: () => void;
    openProfile: () => void;
    logOut: () => void;
}

export type TopBarProps = StateProps & DispatchProps & RouteComponentProps<{}>;

class TopBar extends React.PureComponent<TopBarProps> {
    getLogout() {
        const { logoutUrl } = window.concord;
        if (logoutUrl) {
            return () => (window.location.href = logoutUrl);
        }
        return () => this.props.logOut();
    }

    render() {
        const activeTab = pathToTab(this.props.location.pathname);
        return (
            <GlobalNavMenu
                activeTab={activeTab}
                extraSystemLinks={getExtraSystemLinks()}
                {...this.props}
                logOut={this.getLogout()}
            />
        );
    }
}

const mapStateToProps = ({ session }: { session: SessionState }): StateProps => ({
    userDisplayName: session.user.displayName
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    openUrl: (url: string) => window.open(url, '_blank'),
    openAbout: () => dispatch(pushHistory('/about')),
    openProfile: () => dispatch(pushHistory('/profile')),
    logOut: () => dispatch(actions.logout())
});

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(TopBar));
