import * as React from 'react';
import { connect, Dispatch } from 'react-redux';
import { RouteComponentProps, withRouter } from 'react-router';

import { actions, State as SessionState } from '../../../state/session';
import { GlobalNavMenu, GlobalNavTab } from '../../molecules';

const pathToTab = (s: string): GlobalNavTab => {
    if (s.startsWith('/process')) {
        return 'process';
    } else if (s.startsWith('/org')) {
        return 'org';
    }

    return null;
};

interface StateProps {
    userDisplayName?: string;
}

interface DispatchProps {
    logOut: () => void;
}

export type TopBarProps = StateProps & DispatchProps & RouteComponentProps<{}>;

class TopBar extends React.PureComponent<TopBarProps> {
    render() {
        const activeTab = pathToTab(this.props.location.pathname);
        return <GlobalNavMenu activeTab={activeTab} {...this.props} />;
    }
}

const mapStateToProps = ({ session }: { session: SessionState }): StateProps => ({
    userDisplayName: session.user.displayName
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    logOut: () => dispatch(actions.logout())
});

export default withRouter(connect(mapStateToProps, mapDispatchToProps)(TopBar));
