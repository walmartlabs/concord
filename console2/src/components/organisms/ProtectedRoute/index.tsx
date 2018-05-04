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
