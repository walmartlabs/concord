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
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Icon, Input, List, Loader, Menu } from 'semantic-ui-react';

import { ConcordKey, EntityType, RequestError } from '../../../api/common';
import { checkResult as apiCheckResult } from '../../../../src/api/org';
import {
    list as getPaginatedProjectList,
    ProjectEntry,
    ProjectVisibility
} from '../../../api/org/project';
import { CreateNewEntityButton, PaginationToolBar, RequestErrorMessage } from '../../molecules';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';

interface Props {
    orgName: ConcordKey;
}

export default ({ orgName }: Props) => {
    const [data, setData] = useState<ProjectEntry[]>([]);
    const [loading, setLoading] = useState<boolean>(false);
    const [error, setError] = useState<RequestError>();
    const [canCreate, setCanCreate] = useState<boolean>(false);
    const {
        paginationFilter,
        handleLimitChange,
        handleNext,
        handlePrev,
        handleFirst,
        resetOffset
    } = usePagination();
    const [next, setNext] = useState<boolean>(false);
    const oldFilter = useRef<string>();

    const [filter, setFilter] = useState<string>();

    const fetchData = useCallback(async () => {
        try {
            setLoading(true);

            if (filter && oldFilter.current !== filter) {
                oldFilter.current = filter;
                resetOffset(0);
            }

            const paginatedProjectList = await getPaginatedProjectList(
                orgName,
                paginationFilter.offset,
                paginationFilter.limit,
                filter
            );

            setData(paginatedProjectList.items);
            setNext(paginatedProjectList.next);
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
        }
    }, [orgName, filter, paginationFilter.offset, paginationFilter.limit, resetOffset]);

    useEffect(() => {
        fetchData();
    }, [fetchData]);

    const fetchCanCreateStatus = useCallback(async () => {
        const response = await apiCheckResult(EntityType.PROJECT, orgName);
        setCanCreate(!!response);
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
                            disableNext={!next}
                            disableFirst={paginationFilter.offset <= 0}
                        />
                    </Menu.Item>
                </Menu.Menu>
            </Menu>

            {error && <RequestErrorMessage error={error} />}
            {loading && <Loader active={true} />}
            {data.length === 0 && <h3>No projects found</h3>}

            <List divided={true} relaxed={true} size="large">
                {data.map((project, index) => (
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
