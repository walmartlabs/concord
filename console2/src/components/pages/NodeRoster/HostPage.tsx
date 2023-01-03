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
import { Redirect, Route, RouteComponentProps, Switch } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Icon, Menu } from 'semantic-ui-react';

import { ConcordId } from '../../../api/common';
import { MainToolbar } from '../../molecules';
import { useRef } from 'react';
import { useCallback } from 'react';
import { useState } from 'react';
import { NotFoundPage } from '../index';
import { LoadingDispatch, LoadingState } from '../../../App';
import { getHost as apiGetHost, HostEntry } from '../../../api/noderoster';
import { useApi } from '../../../hooks/useApi';
import RequestErrorActivity from '../../organisms/RequestErrorActivity';
import HostFacts from './HostFacts';
import HostArtifacts from './HostArtifacts';
import HostProcesses from './HostProcesses';

interface RouteProps {
    id: ConcordId;
}

type TabLink = 'facts' | 'artifacts' | 'processes' | null;

const pathToTab = (s: string): TabLink => {
    if (s.endsWith('/facts')) {
        return 'facts';
    } else if (s.endsWith('/artifacts')) {
        return 'artifacts';
    } else if (s.endsWith('/processes')) {
        return 'processes';
    }

    return null;
};

const HostPage = (props: RouteComponentProps<RouteProps>) => {
    const stickyRef = useRef(null);

    const loading = React.useContext(LoadingState);
    const dispatch = React.useContext(LoadingDispatch);
    const [refresh, toggleRefresh] = useState<boolean>(false);

    const { id } = props.match.params;
    const activeTab = pathToTab(props.location.pathname);

    const fetchData = useCallback(() => {
        return apiGetHost(id);
    }, [id]);

    const { data, error } = useApi<HostEntry>(fetchData, {
        fetchOnMount: true,
        forceRequest: refresh,
        dispatch: dispatch
    });

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    const baseUrl = `/noderoster/host/${id}`;

    return (
        <div ref={stickyRef}>
            <MainToolbar
                loading={loading}
                refresh={refreshHandler}
                stickyRef={stickyRef}
                breadcrumbs={renderBreadcrumbs(data?.name)}
            />

            {error && <RequestErrorActivity error={error} />}

            {!error && (
                <Menu tabular={true} style={{ marginTop: 0 }}>
                    <Menu.Item active={activeTab === 'facts'}>
                        <Icon name="th" />
                        <Link to={`${baseUrl}/facts`}>Facts</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'artifacts'}>
                        <Icon name="cubes" />
                        <Link to={`${baseUrl}/artifacts`}>Artifacts</Link>
                    </Menu.Item>
                    <Menu.Item active={activeTab === 'processes'}>
                        <Icon name="tasks" />
                        <Link to={`${baseUrl}/processes`}>Processes</Link>
                    </Menu.Item>
                </Menu>
            )}

            {!error && (
                <Switch>
                    <Route path={baseUrl} exact={true}>
                        <Redirect to={`${baseUrl}/facts`} />
                    </Route>

                    <Route path={`${baseUrl}/facts`} exact={true}>
                        <HostFacts hostId={id} forceRefresh={refresh} />
                    </Route>
                    <Route path={`${baseUrl}/artifacts`} exact={true}>
                        <HostArtifacts hostId={id} forceRefresh={refresh} />
                    </Route>
                    <Route path={`${baseUrl}/processes`} exact={true}>
                        <HostProcesses hostId={id} forceRefresh={refresh} />
                    </Route>

                    <Route component={NotFoundPage} />
                </Switch>
            )}
        </div>
    );
};

const renderBreadcrumbs = (hostName?: string) => {
    return (
        <Breadcrumb size="big">
            <Breadcrumb.Section>
                <Link to={`/noderoster`}>Node Roster</Link>
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
            <Breadcrumb.Section active={true}>
                {hostName === undefined ? '...' : hostName}
            </Breadcrumb.Section>
        </Breadcrumb>
    );
};

export default HostPage;
