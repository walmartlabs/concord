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
import { Icon, Input, Menu, Table } from 'semantic-ui-react';

import { ConcordKey, EntityType } from '../../../api/common';
import { checkResult as apiCheckResult } from '../../../api/org';
import { CreateNewEntityButton, PaginationToolBar } from '../../molecules';
import { Link } from 'react-router-dom';
import {
    PaginatedSecretEntries,
    SecretEntry,
    SecretVisibility,
    typeToText
} from '../../../api/org/secret';
import { usePagination } from '../../molecules/PaginationToolBar/usePagination';
import { LoadingDispatch } from '../../../App';
import { useApi } from '../../../hooks/useApi';
import { list as getPaginatedSecretList } from '../../../api/org/secret';
import { RequestErrorActivity } from '../index';

interface Props {
    orgName: ConcordKey;
    forceRefresh: any;
}

const SecretListActivity = ({ orgName, forceRefresh }: Props) => {
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

        return getPaginatedSecretList(
            orgName,
            paginationFilter.offset,
            paginationFilter.limit,
            filter
        );
    }, [orgName, filter, paginationFilter.offset, paginationFilter.limit, resetOffset]);

    const { data, error } = useApi<PaginatedSecretEntries>(fetchData, {
        fetchOnMount: true,
        forceRequest: forceRefresh,
        dispatch: dispatch
    });

    const fetchCanCreateStatus = useCallback(async () => {
        try {
            const response = await apiCheckResult(EntityType.SECRET, orgName);
            setCanCreate(!!response);
        } catch (e) {
            // ignore
        }
    }, [orgName]);

    useEffect(() => {
        fetchCanCreateStatus();
    }, [fetchCanCreateStatus]);

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

                <Menu.Item position={'right'}>
                    <CreateNewEntityButton
                        entity="secret"
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
            </Menu>

            {error && <RequestErrorActivity error={error} />}

            <Table celled={true} compact={true}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell collapsing={true} />
                        <Table.HeaderCell>Name</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Type</Table.HeaderCell>
                        <Table.HeaderCell>Projects</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>
                <Table.Body>
                    {data?.items.length === 0 && (
                        <tr style={{ fontWeight: 'bold' }}>
                            <Table.Cell> </Table.Cell>
                            <Table.Cell colSpan={4}>No data available</Table.Cell>
                        </tr>
                    )}
                    {data?.items.map((secret, index) => (
                        <Table.Row key={index}>
                            <Table.Cell singleLine={true}>
                                <SecretVisibilityIcon secret={secret} />
                            </Table.Cell>
                            <Table.Cell singleLine={true}>
                                <Link to={`/org/${orgName}/secret/${secret.name}`}>
                                    {secret.name}
                                </Link>
                            </Table.Cell>
                            <Table.Cell singleLine={true}>{typeToText(secret.type)}</Table.Cell>
                            <Table.Cell singleLine={true}>
                                {secret.projects && secret.projects.length > 0
                                    ? secret.projects.map((project, index) => (
                                          <span key={index}>
                                              <Link
                                                  to={`/org/${secret.orgName}/project/${project.name}`}
                                              >
                                                  {project.name}
                                              </Link>
                                              <span>
                                                  {index !== secret.projects.length - 1 ? ', ' : ''}
                                              </span>
                                          </span>
                                      ))
                                    : ' - '}
                            </Table.Cell>
                        </Table.Row>
                    ))}
                </Table.Body>
            </Table>
        </>
    );
};

const SecretVisibilityIcon = ({ secret }: { secret: SecretEntry }) => {
    if (secret.visibility === SecretVisibility.PUBLIC) {
        return <Icon name="unlock" />;
    } else {
        return <Icon name="lock" color="red" />;
    }
};

export default SecretListActivity;
