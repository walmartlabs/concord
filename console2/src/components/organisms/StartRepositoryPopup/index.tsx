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
import { Button, Dropdown, DropdownItemProps, Table } from 'semantic-ui-react';

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { StartProcessResponse } from '../../../api/process';
import { actions, State as ProcessState } from '../../../state/data/processes';
import { GitHubLink, SingleOperationPopup } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    repoURL: string;
    repoBranchOrCommitId: string;
    repoPath: string;
    repoProfiles: string[];
    repoEntryPoints: string[];
    title?: string;
    allowEntryPoint?: boolean;
    entryPoint?: string;
    allowProfile?: boolean;
    profile?: string;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: (entryPoint?: string, profile?: string) => void;
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
    selectedProfile?: string;
}

const makeOptions = (data?: string[]): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    return data.map((name) => ({ text: name, value: name }));
};

interface SimpleDropdownProps {
    data: string[];
    defaultValue?: string;
    disabled: boolean;
    onAdd: (value: string) => void;
    onChange: (value: string) => void;
}

const SimpleDropdown = ({ data, defaultValue, disabled, onAdd, onChange }: SimpleDropdownProps) => (
    <Dropdown
        clearable={true}
        selection={true}
        allowAdditions={true}
        search={true}
        defaultValue={defaultValue}
        disabled={disabled}
        options={makeOptions(data)}
        onAddItem={(e, data) => onAdd(data.value as string)}
        onChange={(e, data) => onChange(data.value as string)}
    />
);

class StartRepositoryPopup extends React.Component<Props, OwnState> {
    constructor(props: Props) {
        super(props);
        this.state = {
            entryPoints: [...(props.repoEntryPoints || [])],
            profiles: [...(props.repoProfiles || [])]
        };
    }

    render() {
        const {
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
            profile
        } = this.props;

        const successMsg = response ? (
            <div>
                {JSON.stringify(
                    {
                        ok: response.ok,
                        instanceId: response.instanceId
                    },
                    null,
                    2
                )}
            </div>
        ) : null;

        const instanceId = response ? response.instanceId : undefined;

        return (
            <SingleOperationPopup
                customStyle={{ maxWidth: '800px' }}
                trigger={trigger}
                title={title || `Start repository: ${repoName}`}
                icon="triangle right"
                iconColor="blue"
                introMsg={
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
                                <Table.Cell textAlign={'right'}>Flow</Table.Cell>
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
                                        />
                                    ) : (
                                        entryPoint
                                    )}
                                </Table.Cell>
                            </Table.Row>
                            <Table.Row>
                                <Table.Cell textAlign={'right'}>Profile</Table.Cell>
                                <Table.Cell>
                                    {allowProfile ? (
                                        <SimpleDropdown
                                            data={this.state.profiles}
                                            defaultValue={profile}
                                            disabled={profile !== undefined}
                                            onAdd={(v) =>
                                                this.setState({
                                                    selectedProfile: v,
                                                    profiles: [v, ...this.state.profiles]
                                                })
                                            }
                                            onChange={(v) => this.setState({ selectedProfile: v })}
                                        />
                                    ) : (
                                        profile
                                    )}
                                </Table.Cell>
                            </Table.Row>
                        </Table.Body>
                    </Table>
                }
                running={starting}
                runningMsg={<p>Starting the process...</p>}
                success={success}
                successMsg={successMsg}
                error={error}
                reset={reset}
                onConfirm={() =>
                    onConfirm(this.state.selectedEntryPoint, this.state.selectedProfile)
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
    { orgName, projectName, repoName }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    onConfirm: (entryPoint?: string, profile?: string) =>
        dispatch(actions.startProcess(orgName, projectName, repoName, entryPoint, profile)),
    openProcessPage: (instanceId: ConcordId) => dispatch(pushHistory(`/process/${instanceId}`))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(StartRepositoryPopup);
