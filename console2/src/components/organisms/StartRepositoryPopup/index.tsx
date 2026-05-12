/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import { ReactNode, useCallback, useMemo, useState } from 'react';
import { useHistory } from '@/router';
import { Dropdown, DropdownItemProps, Message, Table } from 'semantic-ui-react';

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { start as apiStart, StartProcessResponse } from '../../../api/process';
import { get as getRepo, RepositoryMeta } from '../../../api/org/project/repository';
import { ReactJson } from '../../atoms';
import { GitHubLink, RequestErrorMessage, SingleOperationPopup } from '../../molecules';
import { RefreshRepositoryPopup } from '../../organisms';

import './styles.css';

interface Props {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    repoURL: string;
    repoBranchOrCommitId: string;
    repoPath: string;
    title?: string;
    allowEntryPoint?: boolean;
    entryPoint?: string;
    allowProfile?: boolean;
    profiles?: string[];
    showArgs?: boolean;
    args?: object;
    trigger: (onClick: () => void) => ReactNode;
}

interface DropdownProps {
    data: string[];
    defaultValue?: string;
    disabled: boolean;
    onAdd: (value: string) => void;
    onChange: (value: string) => void;
    loading: boolean;
    testId?: string;
}

interface MultiSelectDropdownProps {
    data: string[];
    defaultValue?: string;
    disabled: boolean;
    onAdd: (value: string) => void;
    onChange: (value: string[]) => void;
    loading: boolean;
}

const makeOptions = (data?: string[]): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    return data.map((name) => ({ text: name, value: name }));
};

const getProfiles = (meta?: RepositoryMeta): string[] => {
    if (!meta) {
        return [];
    }

    return meta.profiles || [];
};

const getEntryPoints = (meta?: RepositoryMeta): string[] => {
    if (!meta) {
        return [];
    }

    return meta.entryPoints?.sort() || [];
};

const SimpleDropdown = ({
    data,
    defaultValue,
    disabled,
    onAdd,
    onChange,
    loading,
    testId,
}: DropdownProps) => (
    <Dropdown
        clearable={true}
        selection={true}
        allowAdditions={false}
        search={true}
        defaultValue={defaultValue}
        disabled={disabled}
        options={makeOptions(data)}
        onAddItem={(event, item) => onAdd(item.value as string)}
        onChange={(event, item) => onChange(item.value as string)}
        loading={loading}
        data-testid={testId}
    />
);

const MultiSelectDropdown = ({
    data,
    defaultValue,
    disabled,
    onAdd,
    onChange,
    loading,
}: MultiSelectDropdownProps) => (
    <Dropdown
        clearable={true}
        selection={true}
        multiple={true}
        allowAdditions={false}
        search={true}
        defaultValue={defaultValue}
        disabled={disabled}
        options={makeOptions(data)}
        onAddItem={(event, item) => onAdd(item.value as string)}
        onChange={(event, item) => onChange(item.value as string[])}
        loading={loading}
    />
);

const StartRepositoryPopup = ({
    orgName,
    projectName,
    repoName,
    repoURL,
    repoBranchOrCommitId,
    repoPath,
    trigger,
    title,
    allowEntryPoint,
    entryPoint,
    allowProfile,
    profiles,
    showArgs,
    args,
}: Props) => {
    const history = useHistory();

    const [entryPoints, setEntryPoints] = useState<string[]>([]);
    const [selectedEntryPoint, setSelectedEntryPoint] = useState<string | undefined>(entryPoint);
    const [availableProfiles, setAvailableProfiles] = useState<string[]>([]);
    const [selectedProfiles, setSelectedProfiles] = useState<string[] | undefined>(profiles);
    const [loading, setLoading] = useState(false);
    const [disableStart, setDisableStart] = useState(false);
    const [apiError, setApiError] = useState<RequestError>();

    const [starting, setStarting] = useState(false);
    const [success, setSuccess] = useState(false);
    const [response, setResponse] = useState<StartProcessResponse | null>(null);
    const [error, setError] = useState<RequestError>();

    const reset = useCallback(() => {
        setStarting(false);
        setSuccess(false);
        setResponse(null);
        setError(undefined);
    }, []);

    const loadRepo = useCallback(async () => {
        if (!allowEntryPoint) {
            return;
        }

        try {
            setLoading(true);
            setApiError(undefined);
            setDisableStart(false);

            const repo = await getRepo(orgName, projectName, repoName);
            setEntryPoints(getEntryPoints(repo.meta));
            setAvailableProfiles(getProfiles(repo.meta));
        } catch (e) {
            setDisableStart(true);
            setApiError(e);
        } finally {
            setLoading(false);
        }
    }, [allowEntryPoint, orgName, projectName, repoName]);

    const onConfirm = useCallback(async () => {
        setStarting(true);
        setSuccess(false);
        setError(undefined);

        try {
            const result = await apiStart(
                orgName,
                projectName,
                repoName,
                selectedEntryPoint,
                selectedProfiles,
                args
            );

            setResponse(result);
            setSuccess(result.ok);
        } catch (e) {
            setError(e);
        } finally {
            setStarting(false);
        }
    }, [args, orgName, projectName, repoName, selectedEntryPoint, selectedProfiles]);

    const openProcessPage = useCallback(
        (instanceId: ConcordId) => {
            history.push(`/process/${instanceId}`);
        },
        [history]
    );

    const renderRefreshWarning = useCallback(() => {
        return (
            <Message warning={true} size={'small'}>
                If your flows or profiles are not visible, please{' '}
                <RefreshRepositoryPopup
                    orgName={orgName}
                    projectName={projectName}
                    repoName={repoName}
                    trigger={(onClick: any) => (
                        <span className="asLink" onClick={onClick}>
                            refresh
                        </span>
                    )}
                    onDone={loadRepo}
                />{' '}
                the repository before starting this process.
            </Message>
        );
    }, [loadRepo, orgName, projectName, repoName]);

    const successMsg = useMemo(() => {
        if (!response) {
            return null;
        }

        return (
            <ReactJson
                name={null}
                displayDataTypes={false}
                displayObjectSize={false}
                src={{
                    ok: response.ok,
                    instanceId: response.instanceId,
                }}
            />
        );
    }, [response]);

    if (apiError) {
        return <RequestErrorMessage error={apiError} />;
    }

    return (
        <SingleOperationPopup
            customStyle={{ maxWidth: '800px' }}
            trigger={trigger}
            onOpen={loadRepo}
            title={title || `Start repository: ${repoName}`}
            icon="triangle right"
            iconColor="blue"
            introMsg={
                <div>
                    <Table definition={true}>
                        <Table.Body>
                            <Table.Row>
                                <Table.Cell textAlign={'right'}>Repository URL</Table.Cell>
                                <Table.Cell>
                                    <GitHubLink url={repoURL} text={repoURL} />
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell textAlign={'right'}>Branch</Table.Cell>
                                <Table.Cell>{repoBranchOrCommitId}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell textAlign={'right'}>Path</Table.Cell>
                                <Table.Cell>{repoPath}</Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell textAlign={'right'}>Flow*</Table.Cell>
                                <Table.Cell>
                                    {allowEntryPoint ? (
                                        <SimpleDropdown
                                            data={entryPoints}
                                            defaultValue={entryPoint}
                                            disabled={entryPoint !== undefined}
                                            onAdd={(value) => {
                                                setSelectedEntryPoint(value);
                                                setEntryPoints((prev) => [value, ...prev]);
                                            }}
                                            onChange={setSelectedEntryPoint}
                                            loading={loading}
                                            testId="repository-run-flow-dropdown"
                                        />
                                    ) : (
                                        entryPoint
                                    )}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell textAlign={'right'}>Profile(s)*</Table.Cell>
                                <Table.Cell>
                                    {allowProfile ? (
                                        <MultiSelectDropdown
                                            data={availableProfiles}
                                            defaultValue={profiles ? profiles[0] : undefined}
                                            disabled={profiles !== undefined}
                                            onAdd={(value) => {
                                                setSelectedProfiles([value]);
                                                setAvailableProfiles((prev) => [value, ...prev]);
                                            }}
                                            onChange={setSelectedProfiles}
                                            loading={loading}
                                        />
                                    ) : profiles !== undefined ? (
                                        profiles.join(',')
                                    ) : (
                                        ''
                                    )}
                                </Table.Cell>
                            </Table.Row>
                            {showArgs ? (
                                <Table.Row>
                                    <Table.Cell textAlign={'right'}>Arguments</Table.Cell>
                                    <Table.Cell>
                                        <ReactJson
                                            src={args != null ? args : {}}
                                            collapsed={false}
                                            name={null}
                                        />
                                    </Table.Cell>
                                </Table.Row>
                            ) : (
                                ''
                            )}
                        </Table.Body>
                    </Table>
                    {renderRefreshWarning()}
                </div>
            }
            running={starting}
            runningMsg={<p>Starting the process...</p>}
            success={success}
            successMsg={successMsg}
            error={error}
            reset={reset}
            onConfirm={onConfirm}
            onDoneElements={() =>
                response?.instanceId ? (
                    <button
                        type="button"
                        className="ui basic button"
                        data-testid="repository-open-process-page-button"
                        onClick={() => openProcessPage(response.instanceId)}
                    >
                        Open the process page
                    </button>
                ) : null
            }
            customYes="Start"
            customNo="Cancel"
            disableYes={loading || disableStart}
        />
    );
};

export default StartRepositoryPopup;
