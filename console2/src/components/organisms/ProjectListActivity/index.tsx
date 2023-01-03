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
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Icon, Input, List, Menu } from 'semantic-ui-react';

import { ConcordKey, EntityType } from '../../../api/common';
import { checkResult as apiCheckResult } from '../../../../src/api/org';
import {
    list as getPaginatedProjectList,
    PaginatedProjectEntries,
    ProjectEntry,
    ProjectVisibility
} from '../../../api/org/project';
import { CreateNewEntityButton, PaginationToolBar } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import { RequestErrorActivity } from '../index';
import { useApi } from '../../../hooks/useApi';
import { LoadingDispatch } from '../../../App';
import './styles.css';

interface ExternalProps {
    orgName: ConcordKey;
    forceRefresh: any;
}

export default ({ orgName, forceRefresh }: ExternalProps) => {
    const dispatch = React.useContext(LoadingDispatch);

    const [canCreate, setCanCreate] = useState<boolean>(false);
    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst,
        resetOffset
    } = usePagination();
    const oldFilter = useRef<string>();

    const [filter, setFilter] = useState<string>();

    const fetchData = useCallback(() => {
        if (filter && oldFilter.current !== filter) {
            oldFilter.current = filter;
            resetOffset(0);
        }

        return getPaginatedProjectList(
            orgName,
            paginationFilter.offset,
            paginationFilter.limit,
            filter
        );
    }, [orgName, filter, paginationFilter.offset, paginationFilter.limit, resetOffset]);

    const { data, error } = useApi<PaginatedProjectEntries>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    const fetchCanCreateStatus = useCallback(async () => {
        try {
            const response = await apiCheckResult(EntityType.PROJECT, orgName);
            setCanCreate(!!response);
        } catch (e) {
            // ignore
        }
    }, [orgName]);

    useEffect(() => {
        fetchCanCreateStatus();
    }, [fetchCanCreateStatus]);

    const ProjectVisibilityIcon = ({ project }: { project: ProjectEntry }) => {
        if (project.visibility === ProjectVisibility.PUBLIC) {
            return <Icon name="unlock" size="large" />;
        } else {
            return <Icon name="lock" color="red" size="large" />;
        }
    };

    return (
        <>
            <Menu secondary={true}>
                <Menu.Item>
                    <Input
                        icon="search"
                        placeholder="Filter..."
                        onChange={(ev, data) => setFilter(data.value)}
                    />
                </Menu.Item>

                <Menu.Menu position={'right'}>
                    <Menu.Item>
                        <CreateNewEntityButton
                            entity="project"
                            orgName={orgName}
                            userInOrg={true}
                            enabledInPolicy={canCreate}
                        />
                    </Menu.Item>
                    <Menu.Item style={{ padding: 0 }}>
                        <PaginationToolBar
                            limit={paginationFilter.limit}
                            handleLimitChange={(limit) => handleLimitChange(limit)}
                            handleNext={handleNext}
                            handlePrev={handlePrev}
                            handleFirst={handleFirst}
                            disablePrevious={paginationFilter.offset <= 0}
                            disableNext={!data?.next}
                            disableFirst={paginationFilter.offset <= 0}
                        />
                    </Menu.Item>
                </Menu.Menu>
            </Menu>

            {error && <RequestErrorActivity error={error} />}
            {data?.items.length === 0 && <h3>No projects found</h3>}

            <List divided={true} relaxed={true} size="large">
                {data?.items.map((project, index) => (
                    <List.Item key={index}>
                        <ProjectVisibilityIcon project={project} />
                        <List.Content>
                            <List.Header>
                                <Link to={`/org/${orgName}/project/${project.name}`}>
                                    {project.name}
                                </Link>
                            </List.Header>
                            <List.Description>
                                {project.description ? project.description : 'No description'}
                            </List.Description>
                        </List.Content>
                    </List.Item>
                ))}
            </List>
        </>
    );
};
