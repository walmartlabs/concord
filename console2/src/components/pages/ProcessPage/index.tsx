import * as React from 'react';
import { Redirect, Route, RouteComponentProps, Switch, withRouter } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Icon, Menu, Segment } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { ProcessLogActivity, ProcessStatusActivity } from '../../organisms';
import { NotFoundPage } from '../index';

interface Props {
    instanceId: ConcordId;
}

type TabLink = 'status' | 'log' | 'events' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/status')) {
        return 'status';
    } else if (s.endsWith('/log')) {
        return 'log';
    }

    return null;
};

class ProcessPage extends React.PureComponent<RouteComponentProps<Props>> {
    render() {
        const { instanceId } = this.props.match.params;
        const { url } = this.props.match;

        const activeTab = pathToTab(this.props.location.pathname);

        return (
            <>
                <Segment basic={true}>
                    <Breadcrumb size="big">
                        <Breadcrumb.Section>
                            <Link to={`/process`}>Processes</Link>
                        </Breadcrumb.Section>
                        <Breadcrumb.Divider />
                        <Breadcrumb.Section active={true}>{instanceId}</Breadcrumb.Section>
                    </Breadcrumb>
                </Segment>

                <Menu tabular={true}>
                    <Menu.Item active={activeTab === 'status'}>
                        <Icon name="hourglass half" />
                        <Link to={`${url}/status`}>Status</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'log'}>
                        <Icon name="book" />
                        <Link to={`${url}/log`}>Logs</Link>
                    </Menu.Item>
                </Menu>

                <Switch>
                    <Route path={url} exact={true}>
                        <Redirect to={`${url}/status`} />
                    </Route>
                    <Route path={`${url}/status`}>
                        <ProcessStatusActivity instanceId={instanceId} />
                    </Route>
                    <Route path={`${url}/log`} exact={true}>
                        <ProcessLogActivity instanceId={instanceId} />
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            </>
        );
    }
}

export default withRouter(ProcessPage);
