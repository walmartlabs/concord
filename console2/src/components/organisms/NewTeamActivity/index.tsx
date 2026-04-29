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
import { NewTeamEntry, createOrUpdate as apiCreateOrUpdate } from '../../../api/org/team';
import { NewTeamForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
}

const NewTeamActivity = ({ orgName }: ExternalProps) => {
    const history = useHistory();
    const [error, setError] = React.useState<RequestError>();
    const [submitting, setSubmitting] = React.useState(false);

    const submit = React.useCallback(
        async (entry: NewTeamEntry) => {
            setSubmitting(true);
            setError(undefined);

            try {
                await apiCreateOrUpdate(orgName, entry);
                history.push(`/org/${orgName}/team/${entry.name}`);
            } catch (e) {
                setError(e);
            } finally {
                setSubmitting(false);
            }
        },
        [history, orgName]
    );

    return (
        <>
            {error && <RequestErrorMessage error={error} />}
            <NewTeamForm
                orgName={orgName}
                submitting={submitting}
                onSubmit={(values) => submit(values)}
            />
        </>
    );
};

export default NewTeamActivity;
