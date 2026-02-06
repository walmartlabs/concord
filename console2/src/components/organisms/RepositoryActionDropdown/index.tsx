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
import { useEffect, useState } from 'react';
import { Button, Dropdown, Icon, Table } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import {
    listTriggersV2 as apiListTriggers,
    RepositoryEntry,
    TriggerEntry
} from '../../../api/org/project/repository';
import {
    DeleteRepositoryPopup,
    RefreshRepositoryPopup,
    StartRepositoryPopup,
    ValidateRepositoryPopup
} from '../../organisms';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repo: RepositoryEntry;
    triggerData: TriggerEntry[];
    refresh: () => void;
}

const renderManualTrigger = ({
    trigger,
    orgName,
    projectName,
    repoName,
    repoURL,
    repoBranchOrCommitId,
    repoPathOrDefault,
    repoDisabled
}: {
    trigger: TriggerEntry;
    orgName: string;
    projectName: string;
    repoName: string;
    repoURL: string;
    repoBranchOrCommitId: string;
    repoPathOrDefault: string;
    repoDisabled: boolean;
}) => {
    return (
        <StartRepositoryPopup
            orgName={orgName}
            projectName={projectName}
            repoName={repoName}
            repoURL={repoURL}
            repoBranchOrCommitId={repoBranchOrCommitId!}
            repoPath={repoPathOrDefault}
            allowEntryPoint={false}
            entryPoint={trigger.cfg.entryPoint}
            allowProfile={false}
            profiles={trigger.activeProfiles}
            showArgs={trigger.arguments !== null}
            args={trigger.arguments}
            title={`Start '${trigger.cfg.name}' from repository '${repoName}'`}
            trigger={(onClick: any) => (
                <Dropdown.Item onClick={onClick} disabled={repoDisabled} key={trigger.id}>
                    <Icon name="play" color="green" />
                    <span className="text">{trigger.cfg.name}</span>
                </Dropdown.Item>
            )}
        />
    );
};

const RepositoryActionDropdown = (props: ExternalProps) => {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<RequestError>();

    const { orgName, projectName, repo, refresh } = props;

    const {
        name: repoName,
        url: repoURL,
        commitId: repoCommitId,
        branch: repoBranch,
        path: repoPath,
        disabled: repoDisabled
    } = repo;

    // show the commit ID if defined, otherwise show the branch name
    const repoBranchOrCommitId = repoCommitId ? repoCommitId : repoBranch;
    const repoPathOrDefault = repoPath ? repoPath : '/';

    return (
        <>
            <Table.Cell style={{ paddingRight: '0px' }}>
                {props.triggerData?.length > 0 ? (
                    <Dropdown
                        icon="play green"
                        pointing={'top right'}
                        style={{ paddingLeft: '25%' }}
                        loading={loading}
                        error={error != null}>
                        <Dropdown.Menu>
                            <StartRepositoryPopup
                                orgName={orgName}
                                projectName={projectName}
                                repoName={repoName}
                                repoURL={repoURL}
                                repoBranchOrCommitId={repoBranchOrCommitId!}
                                repoPath={repoPathOrDefault}
                                allowEntryPoint={true}
                                allowProfile={true}
                                trigger={(onClick: any) => (
                                    <Dropdown.Item
                                        onClick={onClick}
                                        disabled={repoDisabled}
                                        key={'run'}>
                                        <Icon name="play" color="blue" />
                                        <span className="text">Run</span>
                                    </Dropdown.Item>
                                )}
                            />
                            <Dropdown.Divider />
                            {props.triggerData.map((t) =>
                                renderManualTrigger({
                                    trigger: t,
                                    orgName,
                                    projectName,
                                    repoName,
                                    repoURL,
                                    repoBranchOrCommitId: repoBranchOrCommitId!,
                                    repoPathOrDefault,
                                    repoDisabled: repo.disabled
                                })
                            )}
                        </Dropdown.Menu>
                    </Dropdown>
                ) : (
                    <StartRepositoryPopup
                        orgName={orgName}
                        projectName={projectName}
                        repoName={repoName}
                        repoURL={repoURL}
                        repoBranchOrCommitId={repoBranchOrCommitId!}
                        repoPath={repoPathOrDefault}
                        allowEntryPoint={true}
                        allowProfile={true}
                        trigger={(onClick: any) => (
                            <Button
                                onClick={onClick}
                                style={{
                                    backgroundColor: 'rgba(255, 255, 255, 0)',
                                    paddingLeft: '25%'
                                }}
                                size="medium"
                                icon={true}
                                disabled={repoDisabled}
                                compact={true}>
                                <div data-toggle="tooltip" data-placement="bottom" title="Run">
                                    <Icon name="play" color="blue" />
                                </div>
                            </Button>
                        )}
                    />
                )}
            </Table.Cell>
            <Table.Cell style={{ paddingLeft: '0px' }}>
                <Dropdown
                    icon="bars"
                    pointing={'top right'}
                    style={{ paddingTop: '25%', paddingBottom: '25%' }}
                    loading={loading}
                    error={error != null}>
                    <Dropdown.Menu>
                        <ValidateRepositoryPopup
                            orgName={orgName}
                            projectName={projectName}
                            repoName={repoName}
                            trigger={(onClick: any) => (
                                <Dropdown.Item onClick={onClick}>
                                    <Icon name="check" />
                                    <span className="text">Validate</span>
                                </Dropdown.Item>
                            )}
                        />

                        <RefreshRepositoryPopup
                            orgName={orgName}
                            projectName={projectName}
                            repoName={repoName}
                            trigger={(onClick: any) => (
                                <Dropdown.Item onClick={onClick}>
                                    <Icon name="refresh" />
                                    <span className="text">Refresh</span>
                                </Dropdown.Item>
                            )}
                        />

                        <DeleteRepositoryPopup
                            orgName={orgName}
                            projectName={projectName}
                            repoName={repoName}
                            trigger={(onClick: any) => (
                                <Dropdown.Item onClick={onClick}>
                                    <Icon name="delete" color="red" />
                                    <span className="text">Delete</span>
                                </Dropdown.Item>
                            )}
                            onDone={refresh}
                        />
                    </Dropdown.Menu>
                </Dropdown>
            </Table.Cell>
        </>
    );
};

export default RepositoryActionDropdown;
