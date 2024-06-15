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
import {useCallback, useEffect, useState} from 'react';
import {testRepository} from '../../../api/service/console';
import {RepositoryForm, RepositoryFormValues, RepositorySourceType} from '../../molecules';
import {RequestErrorActivity} from '../index';
import {
    createOrUpdate as apiCreateOrUpdate,
    EditRepositoryEntry,
    get as apiGetRepo,
    RepositoryEntry
} from "../../../api/org/project/repository";
import {useApi} from "../../../hooks/useApi";
import {ConcordKey, RequestError} from "../../../api/common";
import {Redirect} from "react-router";

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;

    /** defined for edit, undefined for new repos */
    repoName?: ConcordKey;

    forceRefresh: any;
}

const INITIAL_VALUES:RepositoryFormValues = {
    name: '',
    url: '',
    enabled: true,
    sourceType: RepositorySourceType.BRANCH_OR_TAG,
    triggersEnabled: true
};

const EditRepositoryActivity = (props: ExternalProps) => {
    const {orgName, projectName, repoName, forceRefresh} = props;

    const [success, setSuccess] = useState<boolean>(false);
    const [error, setError] = useState<RequestError>();
    const [isLoading, setLoading] = useState<boolean>(false);

    const loadRepo = useCallback(() => {
        return apiGetRepo(orgName, projectName, repoName!);
    }, [orgName, projectName, repoName]);

    const { fetch: loadRepoFetch, clearState: loadRepoClearState, data: loadRepoData, error: loadError } =
        useApi<RepositoryEntry>(loadRepo, { fetchOnMount: false });

    useEffect(() => {
        if (repoName === undefined) {
            return
        }

        loadRepoClearState();
        loadRepoFetch();
    }, [loadRepoFetch, loadRepoClearState, forceRefresh, repoName]);

    const handleSubmit = useCallback(
        async (values: RepositoryFormValues, setSubmitting: (isSubmitting: boolean) => void) => {
            setLoading(true);

            try {
                const result= await apiCreateOrUpdate(orgName, projectName, toEditRepositoryEntry(values));
                setSuccess(result.ok);
            } catch (e) {
                setError(e);
            } finally {
                setLoading(false);
                setSubmitting(false);
            }
        },
        [orgName, projectName]
    );

    if (success) {
        return <Redirect to={`/org/${orgName}/project/${projectName}/repository`} />;
    }

    if (loadError) {
        return <RequestErrorActivity error={loadError} />
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}

            <RepositoryForm
                orgName={orgName}
                projectName={projectName}
                onSubmit={handleSubmit}
                submitting={isLoading}
                editMode={true}
                initial={
                    toFormValues(loadRepoData) || INITIAL_VALUES
                }
                testRepository={({ name, sourceType, id, ...rest }) =>
                    testRepository({ orgName, projectName, ...rest })
                }
            />
        </>
    );
};

const toFormValues = (
    r?: RepositoryEntry
): RepositoryFormValues | undefined => {
    if (!r) {
        return;
    }

    const sourceType = r.commitId
        ? RepositorySourceType.COMMIT_ID
        : RepositorySourceType.BRANCH_OR_TAG;

    return {
        id: r.id,
        name: r.name,
        url: r.url,
        sourceType,
        branch: r.branch,
        commitId: r.commitId,
        path: r.path,
        secretId: r.secretId,
        secretName: r.secretName,
        enabled: !r.disabled,
        triggersEnabled: !r.triggersDisabled
    };
};

const notEmpty = (s: string | undefined): string | undefined => {
    if (!s) {
        return;
    }

    if (s === '') {
        return;
    }

    return s;
};

const toEditRepositoryEntry = (
    repo: RepositoryFormValues
): EditRepositoryEntry => {
    let branch = notEmpty(repo.branch);
    if (repo.sourceType !== RepositorySourceType.BRANCH_OR_TAG) {
        branch = undefined;
    }

    let commitId = notEmpty(repo.commitId);
    if (repo.sourceType !== RepositorySourceType.COMMIT_ID) {
        commitId = undefined;
    }

    return {
        id: repo.id,
        name: repo.name,
        url: repo.url,
        branch,
        commitId,
        path: repo.path,
        secretId: repo.secretId!,
        disabled: !repo.enabled,
        triggersDisabled: !repo.triggersEnabled
    };
}

export default EditRepositoryActivity;