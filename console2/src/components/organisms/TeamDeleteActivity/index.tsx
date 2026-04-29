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
import { deleteTeam as apiDeleteTeam } from '../../../api/org/team';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    teamName: ConcordKey;
}

const TeamDeleteActivity = ({ orgName, teamName }: ExternalProps) => {
    const history = useHistory();
    const [error, setError] = React.useState<RequestError>();
    const [deleting, setDeleting] = React.useState(false);

    const deleteTeam = React.useCallback(async () => {
        setDeleting(true);
        setError(undefined);

        try {
            await apiDeleteTeam(orgName, teamName);
            history.push(`/org/${orgName}/team/`);
        } catch (e) {
            setError(e);
        } finally {
            setDeleting(false);
        }
    }, [history, orgName, teamName]);

    return (
        <>
            {error && <RequestErrorMessage error={error} />}
            <ButtonWithConfirmation
                primary={true}
                negative={true}
                content="Delete"
                loading={deleting}
                confirmationHeader="Delete the team?"
                confirmationContent="Are you sure you want to delete the team?"
                onConfirm={deleteTeam}
                data-testid="team-delete-button"
            />
        </>
    );
};

export default TeamDeleteActivity;
