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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';
import { push as pushHistory } from 'connected-react-router';
import ReactJson from 'react-json-view';
import { Button, Dropdown, DropdownItemProps, Message, Table } from 'semantic-ui-react';

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { StartProcessResponse } from '../../../api/process';
import { actions, State as ProcessState } from '../../../state/data/processes';
import { GitHubLink, RequestErrorMessage, SingleOperationPopup } from '../../molecules';
import { get as getRepo, RepositoryMeta } from '../../../api/org/project/repository';
import { RefreshRepositoryPopup } from '../../organisms';

import './styles.css';

interface ExternalProps {
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
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: (entryPoint?: string, profiles?: string[], args?: object) => void;
    openProcessPage: (instanceId: ConcordId) => void;
}

interface StateProps {
    starting: boolean;
    success: boolean;
    response: StartProcessResponse | null;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

interface OwnState {
    entryPoints: string[];
    selectedEntryPoint?: string;
    profiles: string[];
    selectedProfiles?: string[];
    arguments?: object;
    loading: boolean;
    disableStart: boolean;
    apiError: RequestError;
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

interface SimpleDropdownProps {
    data: string[];
    defaultValue?: string;
    disabled: boolean;
    onAdd: (value: string) => void;
    onChange: (value: string) => void;
    loading: boolean;
}

interface MultiSelectDropdownProps {
    data: string[];
    defaultValue?: string;
    disabled: boolean;
    onAdd: (value: string) => void;
    onChange: (value: string[]) => void;
    loading: boolean;
}

const SimpleDropdown = ({
    data,
    defaultValue,
    disabled,
    onAdd,
    onChange,
    loading
}: SimpleDropdownProps) => (
    <Dropdown
        clearable={true}
        selection={true}
        allowAdditions={false}
        search={true}
        defaultValue={defaultValue}
        disabled={disabled}
        options={makeOptions(data)}
        onAddItem={(e, data) => onAdd(data.value as string)}
        onChange={(e, data) => onChange(data.value as string)}
        loading={loading}
    />
);

const MultiSelectDropdown = ({
    data,
    defaultValue,
    disabled,
    onAdd,
    onChange,
    loading
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
        onAddItem={(e, data) => onAdd(data.value as string)}
        onChange={(e, data) => onChange(data.value as string[])}
        loading={loading}
    />
);

class StartRepositoryPopup extends React.Component<Props, OwnState> {
    constructor(props: Props) {
        super(props);

        this.state = {
            entryPoints: [],
            selectedEntryPoint: props.entryPoint,
            selectedProfiles: props.profiles,
            profiles: [],
            arguments: props.args,
            loading: false,
            disableStart: false,
            apiError: null
        };
    }

    async loadRepo() {
        const { orgName, projectName, repoName, allowEntryPoint } = this.props;

        if (allowEntryPoint) {
            try {
                this.setState({ loading: true });

                const repo = await getRepo(orgName, projectName, repoName);

                this.setState({
                    entryPoints: getEntryPoints(repo.meta),
                    profiles: getProfiles(repo.meta)
                });
            } catch (e) {
                this.setState({ disableStart: true, apiError: e });
            } finally {
                this.setState({ loading: false });
            }
        }
    }

    renderRefreshWarning(orgName: ConcordKey, projectName: ConcordKey, repoName: ConcordKey) {
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
                    onDone={() => this.loadRepo()}
                />{' '}
                the repository before starting this process.
            </Message>
        );
    }

    render() {
        const {
            orgName,
            projectName,
            repoName,
            repoURL,
            repoBranchOrCommitId,
            repoPath,
            trigger,
            starting,
            success,
            response,
            error,
            reset,
            onConfirm,
            openProcessPage,
            title,
            allowEntryPoint,
            entryPoint,
            allowProfile,
            profiles,
            showArgs,
            args
        } = this.props;

        const successMsg = response ? (
            <ReactJson
                name={null}
                displayDataTypes={false}
                displayObjectSize={false}
                src={{
                    ok: response.ok,
                    instanceId: response.instanceId
                }}
            />
        ) : null;

        if (this.state.apiError) {
            return <RequestErrorMessage error={this.state.apiError} />;
        }

        const instanceId = response ? response.instanceId : undefined;

        return (
            <SingleOperationPopup
                customStyle={{ maxWidth: '800px' }}
                trigger={trigger}
                onOpen={() => this.loadRepo()}
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
                                                data={this.state.entryPoints}
                                                defaultValue={entryPoint}
                                                disabled={entryPoint !== undefined}
                                                onAdd={(v) =>
                                                    this.setState({
                                                        selectedEntryPoint: v,
                                                        entryPoints: [v, ...this.state.entryPoints]
                                                    })
                                                }
                                                onChange={(v) =>
                                                    this.setState({ selectedEntryPoint: v })
                                                }
                                                loading={this.state.loading}
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
                                                data={this.state.profiles}
                                                defaultValue={profiles ? profiles[0] : undefined}
                                                disabled={profiles !== undefined}
                                                onAdd={(v) =>
                                                    this.setState({
                                                        selectedProfiles: [v],
                                                        profiles: [v, ...this.state.profiles]
                                                    })
                                                }
                                                onChange={(v) =>
                                                    this.setState({ selectedProfiles: v })
                                                }
                                                loading={this.state.loading}
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
                        {this.renderRefreshWarning(orgName, projectName, repoName)}
                    </div>
                }
                running={starting}
                runningMsg={<p>Starting the process...</p>}
                success={success}
                successMsg={successMsg}
                error={error}
                reset={reset}
                onConfirm={() =>
                    onConfirm(this.state.selectedEntryPoint, this.state.selectedProfiles)
                }
                onDoneElements={() => (
                    <Button
                        basic={true}
                        content="Open the process page"
                        onClick={() => instanceId && openProcessPage(instanceId)}
                    />
                )}
                customYes="Start"
                customNo="Cancel"
                disableYes={this.state.loading || this.state.disableStart}
            />
        );
    }
}

const mapStateToProps = ({ processes }: { processes: ProcessState }): StateProps => ({
    starting: processes.startProcess.running,
    success: !!processes.startProcess.response && processes.startProcess.response.ok,
    response: processes.startProcess.response,
    error: processes.startProcess.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { orgName, projectName, repoName, args }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    onConfirm: (entryPoint?: string, profiles?: string[]) =>
        dispatch(actions.startProcess(orgName, projectName, repoName, entryPoint, profiles, args)),
    openProcessPage: (instanceId: ConcordId) => dispatch(pushHistory(`/process/${instanceId}`))
});

export default connect(mapStateToProps, mapDispatchToProps)(StartRepositoryPopup);
