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
import { RouteComponentProps } from 'react-router';
import { Link } from 'react-router-dom';
import { Breadcrumb, Container, Header, Segment } from 'semantic-ui-react';

import { MainToolbar } from '../../molecules';
import { useRef } from 'react';
import { useState } from 'react';
import { useCallback } from 'react';
import EditStoreQueryActivity from './EditStoreQueryActivity';
import { useContext } from 'react';
import { LoadingState } from '../../../App';

interface RouteProps {
    orgName: string;
    storeName: string;
    queryName: string;
}

const EditStoreQueryPage = (props: RouteComponentProps<RouteProps>) => {
    const loading = useContext(LoadingState);
    const stickyRef = useRef(null);
    const [refresh, toggleRefresh] = useState<boolean>(false);

    const { orgName, storeName, queryName } = props.match.params;

    const refreshHandler = useCallback(() => {
        toggleRefresh((prevState) => !prevState);
    }, []);

    return (
        <div ref={stickyRef}>
            <MainToolbar
                loading={loading}
                refresh={refreshHandler}
                stickyRef={stickyRef}
                breadcrumbs={renderBreadcrumbs(orgName, storeName, queryName)}
            />

            <Segment basic={true}>
                <Container text={true}>
                    <Header>Edit query: {queryName}</Header>
                    <EditStoreQueryActivity
                        orgName={orgName}
                        storeName={storeName}
                        queryName={queryName}
                        forceRefresh={refresh}
                    />
                </Container>
            </Segment>
        </div>
    );
};

const renderBreadcrumbs = (orgName: string, storeName: string, queryName: string) => {
    return (
        <Breadcrumb size="big">
            <Breadcrumb.Section>
                <Link to={`/org/${orgName}/jsonstore`}>{orgName}</Link>
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
            <Breadcrumb.Section>
                <Link to={`/org/${orgName}/jsonstore/${storeName}/query`}>{storeName}</Link>
            </Breadcrumb.Section>
            <Breadcrumb.Divider />
            <Breadcrumb.Section active={true}>{queryName}</Breadcrumb.Section>
        </Breadcrumb>
    );
};

export default EditStoreQueryPage;
