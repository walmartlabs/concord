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

import { ConcordId, ConcordKey } from '../../../api/common';
import { isProjectExists } from '../../../api/service/console';
import { projectAlreadyExistsError } from '../../../validation';
import { EntityRenameForm } from '../../molecules';
import { RequestErrorActivity } from '../index';
import { memo, useCallback, useState } from 'react';
import { rename as apiRename } from '../../../api/org/project';
import { useApi } from '../../../hooks/useApi';
import { FormValues } from '../../molecules/EntityRenameForm';
import { Redirect } from 'react-router';

interface ExternalProps {
    orgName: ConcordKey;
    projectId?: ConcordId;
    projectName: ConcordKey;
    disabled: boolean;
}

const areEqual = (prev: ExternalProps, next: ExternalProps): boolean => {
    return (
        prev.orgName === next.orgName &&
        prev.projectId === next.projectId &&
        prev.projectName === next.projectName &&
        prev.disabled === next.disabled
    );
};

const ProjectRenameActivity = memo(
    ({ orgName, projectId, projectName, disabled }: ExternalProps) => {
        const [value, setValue] = useState(projectName);

        const postData = useCallback(async () => {
            await apiRename(orgName, projectId!, value!);
            return value;
        }, [orgName, projectId, value]);

        const { data, error, isLoading, fetch, clearState } = useApi<string>(postData, {
            fetchOnMount: false,
            requestByFetch: true
        });

        const renameHandler = useCallback(
            (values: FormValues) => {
                setValue(values.name);
                clearState();
                fetch();
            },
            [clearState, fetch]
        );

        if (data && data !== projectName) {
            return <Redirect to={`/org/${orgName}/project/${value}/settings`} />;
        }

        return (
            <>
                {error && <RequestErrorActivity error={error} />}
                <EntityRenameForm
                    originalName={projectName}
                    submitting={isLoading}
                    onSubmit={renameHandler}
                    inputPlaceholder="Project name"
                    confirmationHeader="Rename the project?"
                    confirmationContent="Are you sure you want to rename the project?"
                    isExists={(name) => isProjectExists(orgName, name)}
                    alreadyExistsTemplate={projectAlreadyExistsError}
                    disabled={disabled}
                />
            </>
        );
    },
    areEqual
);

export default ProjectRenameActivity;
