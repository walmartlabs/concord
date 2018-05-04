import * as React from 'react';
import { Redirect, Route, RouteComponentProps, Switch, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { BreadcrumbSegment } from '../../molecules';
import { ProcessList, RedirectButton, RepositoryList } from '../../organisms';
import { NotFoundPage } from '../index';

interface RouteProps {
    orgName: string;
    projectName: string;
}

type TabLink = 'process' | 'repository' | 'settings' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/process')) {
        return 'process';
    } else if (s.endsWith('/repository')) {
        return 'repository';
    } else if (s.endsWith('/settings')) {
        return 'settings';
    }

    return null;
};

class ProjectPage extends React.PureComponent<RouteComponentProps<RouteProps>> {
    render() {
        const { orgName, projectName } = this.props.match.params;
        const { url } = this.props.match;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <BreadcrumbSegment>
                    <Breadcrumb.Section>
                        <Link to={`/org/${orgName}`}>{orgName}</Link>
                    </Breadcrumb.Section>
                    <Breadcrumb.Divider />
                    <Breadcrumb.Section active={true}>{projectName}</Breadcrumb.Section>
                </BreadcrumbSegment>

                <Route path={`${url}/^(repository)`}>
                    <Menu tabular={true}>
                        <Menu.Item active={activeTab === 'process'}>
                            <Icon name="tasks" />
                            <Link to={`/org/${orgName}/project/${projectName}/process`}>
                                Processes
                            </Link>
                        </Menu.Item>
                        <Menu.Item active={activeTab === 'repository'}>
                            <Icon name="code" />
                            <Link to={`/org/${orgName}/project/${projectName}/repository`}>
                                Repositories
                            </Link>
                        </Menu.Item>
                        <Menu.Item active={activeTab === 'settings'}>
                            <Icon name="setting" />
                            <Link to={`/org/${orgName}/project/${projectName}/settings`}>
                                Settings
                            </Link>
                        </Menu.Item>
                    </Menu>
                </Route>

                <Switch>
                    <Route path={url} exact={true}>
                        <Redirect to={`${url}/process`} />
                    </Route>

                    <Route path={`${url}/process`} exact={true}>
                        <ProcessList orgName={orgName} projectName={projectName} />
                    </Route>
                    <Route path={`${url}/repository`} exact={true}>
                        {this.renderRepositories()}
                    </Route>

                    <Route path={`${url}/settings`} exact={true}>
                        <h1>SETTINGS</h1>
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }

    renderRepositories() {
        const { orgName, projectName } = this.props.match.params;
        return (
            <>
                <Menu secondary={true}>
                    <Menu.Item position={'right'}>
                        <RedirectButton
                            icon="plus"
                            positive={true}
                            labelPosition="left"
                            content="Add repository"
                            location={`/org/${orgName}/project/${projectName}/repository/_new`}
                        />
                    </Menu.Item>
                </Menu>

                <RepositoryList orgName={orgName} projectName={projectName} />
            </>
        );
    }
}

export default withRouter(ProjectPage);
