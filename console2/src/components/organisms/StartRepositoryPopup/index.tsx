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
import { connect, Dispatch } from 'react-redux';
import { push as pushHistory } from 'react-router-redux';
import { Button } from 'semantic-ui-react';

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { StartProcessResponse } from '../../../api/org/process';
import { actions, State as ProcessState } from '../../../state/data/processes';
import { SingleOperationPopup, GitHubLink } from '../../molecules';

import { Table, Keys, Key, Values, Value, Input } from './styles';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    repoURL: string;
    trigger: (onClick: () => void) => React.ReactNode;
}

interface DispatchProps {
    reset: () => void;
    onConfirm: (entryPoint: string) => void;
    openProcessPage: (instanceId: ConcordId) => void;
}

interface StateProps {
    starting: boolean;
    success: boolean;
    response: StartProcessResponse | null;
    error: RequestError;
}

type Props = DispatchProps & ExternalProps & StateProps;

interface State {
    entryPoint: string;
}

class StartRepositoryPopup extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {
            entryPoint: 'default'
        };
    }

    updateEntryPoint(e: React.KeyboardEvent<HTMLInputElement>) {
        this.setState({ entryPoint: e.currentTarget.value });
    }

    render() {
        const {
            repoName,
            trigger,
            starting,
            success,
            response,
            repoURL,
            error,
            reset,
            onConfirm,
            openProcessPage
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
                title={`Start flow ${repoName}`}
                icon="triangle right"
                iconColor="blue"
                introMsg={
                    <Table>
                        <Keys>
                            <Key>Repository URL</Key>
                            <Key>Source</Key>
                            <Key>Path</Key>
                            <Key>Entry Point</Key>
                        </Keys>

                        <Values>
                            <Value>
                                <GitHubLink url={repoURL} text={repoURL} />
                            </Value>
                            <Value>master</Value>
                            <Value>/</Value>
                            <Value>
                                <Input
                                    type="text"
                                    placeholder="default"
                                    onKeyUp={(e: any) => this.updateEntryPoint(e)}
                                />
                            </Value>
                        </Values>
                    </Table>
                }
                running={starting}
                runningMsg={<p>Starting the process...</p>}
                success={success}
                successMsg={successMsg}
                error={error}
                reset={reset}
                onConfirm={() => onConfirm(this.state.entryPoint || 'default')}
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
    dispatch: Dispatch<{}>,
    { orgName, projectName, repoName }: ExternalProps
): DispatchProps => ({
    reset: () => dispatch(actions.reset()),
    onConfirm: (entryPoint: string) =>
        dispatch(actions.startProcess(orgName, projectName, repoName, entryPoint)),
    openProcessPage: (instanceId: ConcordId) => dispatch(pushHistory(`/process/${instanceId}`))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(StartRepositoryPopup);
