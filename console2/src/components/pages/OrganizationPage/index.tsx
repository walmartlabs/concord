import * as React from 'react';
import { Redirect, Route, RouteComponentProps, Switch, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { BreadcrumbSegment } from '../../molecules';
import { ProcessList, ProjectList, RedirectButton, SecretList } from '../../organisms';
import { NotFoundPage } from '../index';

interface RouteProps {
    orgName: string;
}

type TabLink = 'process' | 'project' | 'secret' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/process')) {
        return 'process';
    } else if (s.endsWith('/project')) {
        return 'project';
    } else if (s.endsWith('/secret')) {
        return 'secret';
    }

    return null;
};

class OrganizationPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName } = this.props.match.params;
        const { url } = this.props.match;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to="/org">Organizations</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{orgName}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Menu tabular={true}>
                    <Menu.Item active={activeTab === 'project'}>
                        <Icon name="sitemap" />
                        <Link to={`${url}/project`}>Projects</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'process'}>
                        <Icon name="tasks" />
                        <Link to={`${url}/process`}>Processes</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'secret'}>
                        <Icon name="lock" />
                        <Link to={`${url}/secret`}>Secrets</Link>
                    </Menu.Item>
                </Menu>

                <Switch>
                    <Route path={url} exact={true}>
                        <Redirect to={`${url}/project`} />
                    </Route>
                    <Route path={`${url}/project`}>{this.renderProjects()}</Route>
                    <Route path={`${url}/process`}>
                        <ProcessList orgName={orgName} />
                    </Route>
                    <Route path={`${url}/secret`} exact={true}>
                        {this.renderSecrets()}
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }

    renderProjects() {
        const { orgName } = this.props.match.params;
        return (
            <>
                <Menu secondary={true}>
                    <Menu.Item position={'right'}>
                        <RedirectButton
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="New project"
                            location={`/org/${orgName}/project/_new`}
                        />
                    </Menu.Item>
                </Menu>

                <ProjectList orgName={orgName} />
            </>
        );
    }

    renderSecrets() {
        const { orgName } = this.props.match.params;
        return (
            <>
                <Menu secondary={true}>
                    <Menu.Item position={'right'}>
                        <RedirectButton
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="New secret"
                            location={`/org/${orgName}/secret/_new`}
                        />
                    </Menu.Item>
                </Menu>

                <SecretList orgName={orgName} />
            </>
        );
    }
}

export default withRouter(OrganizationPage);
