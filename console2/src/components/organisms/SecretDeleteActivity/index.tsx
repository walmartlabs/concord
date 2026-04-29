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
import { deleteSecret as apiDeleteSecret } from '../../../api/org/secret';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

const SecretDeleteActivity = ({ orgName, secretName }: ExternalProps) => {
    const history = useHistory();
    const [error, setError] = React.useState<RequestError>();
    const [deleting, setDeleting] = React.useState(false);

    const deleteSecret = React.useCallback(async () => {
        setDeleting(true);
        setError(undefined);

        try {
            await apiDeleteSecret(orgName, secretName);
            history.push(`/org/${orgName}/secret`);
        } catch (e) {
            setError(e);
        } finally {
            setDeleting(false);
        }
    }, [history, orgName, secretName]);

    return (
        <>
            {error && <RequestErrorMessage error={error} />}
            <ButtonWithConfirmation
                primary={true}
                negative={true}
                content="Delete"
                loading={deleting}
                confirmationHeader="Delete the secret?"
                confirmationContent="Are you sure you want to delete the secret?"
                onConfirm={deleteSecret}
            />
        </>
    );
};

export default SecretDeleteActivity;
