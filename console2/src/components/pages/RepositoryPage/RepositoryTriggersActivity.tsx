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

import { useCallback, useContext } from 'react';
import ReactJson from 'react-json-view';
import { Loader, Table } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { TriggerEntry, listTriggers as apiListTriggers } from '../../../api/org/project/repository';
import { useApi } from '../../../hooks/useApi';
import { LoadingDispatch } from '../../../App';
import { comparators } from '../../../utils';
import { LocalTimestamp, RequestErrorMessage } from '../../molecules';

import * as cronjsMatcher from '@datasert/cronjs-matcher';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
}

const prepareData = (data: TriggerEntry[] | undefined) => {
    if (!data) {
        return undefined;
    }

    return data
        .sort(comparators.byProperty((i) => i.cfg.entryPoint))
        .sort(comparators.byProperty((i) => i.eventSource));
};

const RepositoryTriggersActivity = ({ orgName, projectName, repoName }: ExternalProps) => {
    const dispatch = useContext(LoadingDispatch);

    const fetchData = useCallback(() => {
        return apiListTriggers(orgName, projectName, repoName);
    }, [orgName, projectName, repoName]);

    const { data, error, isLoading } = useApi<TriggerEntry[]>(fetchData, {
        fetchOnMount: true,
        dispatch
    });

    if (error) {
        return <RequestErrorMessage error={error} />;
    }

    if (isLoading || !data) {
        return <Loader active={true} />;
    }

    const triggers = prepareData(data);
    const cronTriggers = triggers?.filter((t) => t.eventSource === 'cron');
    const otherTriggers = triggers?.filter((t) => t.eventSource !== 'cron');

    return (
        <>
            {cronTriggers && <>
                <h3>Cron Triggers</h3>
                <Table celled={true} striped={true}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell width={3}>Conditions</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>Entry Point</Table.HeaderCell>
                            <Table.HeaderCell width={5}>Configuration</Table.HeaderCell>
                            <Table.HeaderCell width={5}>Arguments</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {cronTriggers.map((t, idx) => (
                            <Table.Row key={idx}>
                                <Table.Cell>
                                    {t.conditions?.spec !== undefined && (
                                        <pre>
                                            Expression: <b>{t.conditions?.spec}</b>
                                            <br/>
                                            Next run: <LocalTimestamp value={cronjsMatcher.getFutureMatches(t.conditions?.spec, {matchCount: 1})[0]}/>
                                        </pre>)}
                                </Table.Cell>
                                <Table.Cell>
                                    <pre>{t.cfg.entryPoint}</pre>
                                </Table.Cell>
                                <Table.Cell>
                                    <ReactJson
                                        src={t.cfg}
                                        collapsed={true}
                                        name={null}
                                        enableClipboard={false}
                                    />
                                </Table.Cell>
                                <Table.Cell>
                                    {t.arguments && (
                                        <ReactJson
                                            src={t.arguments}
                                            collapsed={true}
                                            name={null}
                                            enableClipboard={false}
                                        />
                                    )}
                                </Table.Cell>
                            </Table.Row>
                        ))}
                        {cronTriggers.length === 0 && (
                            <Table.Row>
                                <Table.Cell colSpan={5}>No cron triggers</Table.Cell>
                            </Table.Row>
                        )}
                    </Table.Body>
                </Table>
            </>}
            {otherTriggers && <>
                <h3>Other Triggers</h3>
                <Table celled={true} striped={true}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell collapsing={true}>Source</Table.HeaderCell>
                            <Table.HeaderCell>Conditions</Table.HeaderCell>
                            <Table.HeaderCell collapsing={true}>Entry Point</Table.HeaderCell>
                            <Table.HeaderCell>Configuration</Table.HeaderCell>
                            <Table.HeaderCell>Arguments</Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>

                    <Table.Body>
                        {otherTriggers.map((t, idx) => (
                            <Table.Row key={idx}>
                                <Table.Cell>{t.eventSource}</Table.Cell>
                                <Table.Cell>
                                    {t.conditions && (
                                        <ReactJson
                                            src={t.conditions}
                                            collapsed={true}
                                            name={null}
                                            enableClipboard={false}
                                        />
                                    )}
                                </Table.Cell>
                                <Table.Cell>{t.cfg.entryPoint}</Table.Cell>
                                <Table.Cell>
                                    <ReactJson
                                        src={t.cfg}
                                        collapsed={true}
                                        name={null}
                                        enableClipboard={false}
                                    />
                                </Table.Cell>
                                <Table.Cell>
                                    {t.arguments && (
                                        <ReactJson
                                            src={t.arguments}
                                            collapsed={true}
                                            name={null}
                                            enableClipboard={false}
                                        />
                                    )}
                                </Table.Cell>
                            </Table.Row>
                        ))}
                        {otherTriggers.length === 0 && (
                            <Table.Row>
                                <Table.Cell colSpan={5}>No other triggers</Table.Cell>
                            </Table.Row>
                        )}
                    </Table.Body>
                </Table>
            </>}
        </>
    );
};

export default RepositoryTriggersActivity;
