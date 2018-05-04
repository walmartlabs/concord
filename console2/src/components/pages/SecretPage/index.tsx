import * as React from 'react';
import { Redirect, Route, RouteComponentProps, Switch, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { BreadcrumbSegment } from '../../molecules';
import { SecretInfo } from '../../organisms';
import { NotFoundPage } from '../index';

interface RouteProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

type TabLink = 'info' | 'access' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/info')) {
        return 'info';
    } else if (s.endsWith('/access')) {
        return 'access';
    }

    return null;
};

class SecretPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName, secretName } = this.props.match.params;
        const { url } = this.props.match;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/org/${orgName}/secret`}>{orgName}</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{secretName}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Route path={`${url}/^(repository)`}>
                    <Menu tabular={true}>
                        <Menu.Item active={activeTab === 'info'}>
                            <Icon name="file" />
                            <Link to={`/org/${orgName}/secret/${secretName}/info`}>Info</Link>
                        </Menu.Item>
                        <Menu.Item active={activeTab === 'access'}>
                            <Icon name="lock" />
                            <Link to={`/org/${orgName}/secret/${secretName}/access`}>Access</Link>
                        </Menu.Item>
                    </Menu>
                </Route>

                <Switch>
                    <Route path={url} exact={true}>
                        <Redirect to={`${url}/info`} />
                    </Route>

                    <Route path={`${url}/info`} exact={true}>
                        <SecretInfo orgName={orgName} secretName={secretName} />
                    </Route>
                    <Route path={`${url}/access`} exact={true}>
                        <h1>Access</h1>
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

export default withRouter(SecretPage);
