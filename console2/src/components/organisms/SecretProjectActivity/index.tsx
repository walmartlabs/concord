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
import { useState } from 'react';

import { ConcordKey, RequestError } from '../../../api/common';
import { updateSecretProject as apiUpdateSecretProject } from '../../../api/org/secret';
import { RequestErrorMessage, SecretProjectForm } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
    projectName: ConcordKey;
}

type Props = ExternalProps;

export default (props: Props) => {
    const [updating, setUpdating] = useState(false);
    const [error, setError] = useState<RequestError>();

    const { orgName, secretName, projectName } = props;

    const update = async (projectName?: string) => {
        try {
            setUpdating(true);
            await apiUpdateSecretProject(orgName, secretName, projectName || '');
        } catch (e) {
            setError(e);
        } finally {
            setUpdating(false);
        }
    };

    return (
        <>
            {error && <RequestErrorMessage error={error} />}
            <SecretProjectForm
                orgName={orgName}
                projectName={projectName}
                submitting={updating}
                onSubmit={(values) => update(values.projectName)}
                confirmationHeader="Update the project?"
                confirmationContent="Are you sure you want to update the project?"
            />
        </>
    );
};
