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
import { Dropdown, Icon } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import {
    listTriggersV2 as apiListTriggers,
    RepositoryEntry,
    RepositoryMeta,
    TriggerEntry
} from '../../../api/org/project/repository';
import {
    DeleteRepositoryPopup,
    RefreshRepositoryPopup,
    RepositoryTriggersPopup,
    StartRepositoryPopup,
    ValidateRepositoryPopup
} from '../../organisms';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repo: RepositoryEntry;
}

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
    return meta.entryPoints || [];
};

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
            repoBranchOrCommitId={repoBranchOrCommitId}
            repoPath={repoPathOrDefault}
            allowEntryPoint={false}
            entryPoint={trigger.cfg.entryPoint}
            allowProfile={false}
            profiles={trigger.activeProfiles}
            showArgs={trigger.arguments !== null}
            args={trigger.arguments}
            title={`Start '${trigger.cfg.name}' from repository '${repoName}'`}
            trigger={(onClick: any) => (
                <Dropdown.Item onClick={onClick} disabled={repoDisabled}>
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
    const [manualTriggers, setManualTriggers] = useState<TriggerEntry[]>();

    useEffect(() => {
        const fetchData = async () => {
            setLoading(true);
            setError(undefined);

            try {
                const result = await apiListTriggers({
                    type: 'manual',
                    orgName: props.orgName,
                    projectName: props.projectName,
                    repoName: props.repo.name
                });

                setManualTriggers(result);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
            }
        };

        fetchData();
    }, [props.orgName, props.projectName, props.repo.name]);

    const { orgName, projectName, repo } = props;

    const {
        name: repoName,
        url: repoURL,
        branch: repoBranch,
        commitId: repoCommitId,
        path: repoPath,
        meta: repoMeta
    } = repo;

    // show the commit ID if defined, otherwise show the branch name or fallback to 'master'
    const repoBranchOrCommitId = repoCommitId ? repoCommitId : repoBranch ? repoBranch : 'master';
    const repoPathOrDefault = repoPath ? repoPath : '/';

    return (
        <Dropdown
            icon="ellipsis vertical"
            pointing={'top right'}
            loading={loading}
            error={error != null}>
            <Dropdown.Menu>
                <StartRepositoryPopup
                    orgName={orgName}
                    projectName={projectName}
                    repoName={repoName}
                    repoURL={repoURL}
                    repoBranchOrCommitId={repoBranchOrCommitId}
                    repoPath={repoPathOrDefault}
                    repoProfiles={getProfiles(repoMeta)}
                    repoEntryPoints={getEntryPoints(repoMeta)}
                    allowEntryPoint={true}
                    allowProfile={true}
                    trigger={(onClick: any) => (
                        <Dropdown.Item onClick={onClick} disabled={repo.disabled} loading={true}>
                            <Icon name="play" color="blue" />
                            <span className="text">Run</span>
                        </Dropdown.Item>
                    )}
                />

                {manualTriggers &&
                    manualTriggers.map((t) =>
                        renderManualTrigger({
                            trigger: t,
                            orgName,
                            projectName,
                            repoName,
                            repoURL,
                            repoBranchOrCommitId,
                            repoPathOrDefault,
                            repoDisabled: repo.disabled
                        })
                    )}

                <Dropdown.Divider />

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

                <RepositoryTriggersPopup
                    orgName={orgName}
                    projectName={projectName}
                    repoName={repoName}
                    trigger={(onClick: any) => (
                        <Dropdown.Item onClick={onClick}>
                            <Icon name="lightning" />
                            <span className="text">Triggers</span>
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
                />
            </Dropdown.Menu>
        </Dropdown>
    );
};

export default RepositoryActionDropdown;
