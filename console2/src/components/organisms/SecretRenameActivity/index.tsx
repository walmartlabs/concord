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
import { useHistory } from '@/router';

import { ConcordKey, RequestError } from '../../../api/common';
import { isSecretExists } from '../../../api/service/console';
import { renameSecret as apiRenameSecret } from '../../../api/org/secret';
import { secretAlreadyExistsError } from '../../../validation';
import { EntityRenameForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

const SecretRenameActivity = ({ orgName, secretName }: ExternalProps) => {
    const history = useHistory();
    const [error, setError] = React.useState<RequestError>();
    const [renaming, setRenaming] = React.useState(false);

    const rename = React.useCallback(
        async (newSecretName: ConcordKey) => {
            setRenaming(true);
            setError(undefined);

            try {
                await apiRenameSecret(orgName, secretName, newSecretName);
                history.push(`/org/${orgName}/secret`);
            } catch (e) {
                setError(e);
            } finally {
                setRenaming(false);
            }
        },
        [history, orgName, secretName]
    );

    return (
        <>
            {error && <RequestErrorMessage error={error} />}
            <EntityRenameForm
                originalName={secretName}
                submitting={renaming}
                onSubmit={(values) => rename(values.name)}
                inputPlaceholder="Secret name"
                confirmationHeader="Rename the secret?"
                confirmationContent="Are you sure you want to rename the secret?"
                isExists={(name) => isSecretExists(orgName, name)}
                alreadyExistsTemplate={secretAlreadyExistsError}
            />
        </>
    );
};

export default SecretRenameActivity;
