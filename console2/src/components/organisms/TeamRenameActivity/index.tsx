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

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { isTeamExists } from '../../../api/service/console';
import { rename as apiRename } from '../../../api/org/team';
import { teamAlreadyExistsError } from '../../../validation';
import { EntityRenameForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    teamId: ConcordId;
    teamName: ConcordKey;
}

const TeamRenameActivity = ({ orgName, teamId, teamName }: ExternalProps) => {
    const history = useHistory();
    const [error, setError] = React.useState<RequestError>();
    const [renaming, setRenaming] = React.useState(false);

    const rename = React.useCallback(
        async (newTeamName: ConcordKey) => {
            setRenaming(true);
            setError(undefined);

            try {
                await apiRename(orgName, teamId, newTeamName);
                history.push(`/org/${orgName}/team/${newTeamName}`);
            } catch (e) {
                setError(e);
            } finally {
                setRenaming(false);
            }
        },
        [history, orgName, teamId]
    );

    return (
        <>
            {error && <RequestErrorMessage error={error} />}
            <EntityRenameForm
                originalName={teamName}
                submitting={renaming}
                onSubmit={(values) => rename(values.name)}
                inputPlaceholder="Team name"
                confirmationHeader="Rename the team?"
                confirmationContent="Are you sure you want to rename the team?"
                isExists={(name) => isTeamExists(orgName, name)}
                alreadyExistsTemplate={teamAlreadyExistsError}
                inputTestId="team-rename-input"
                buttonTestId="team-rename-button"
            />
        </>
    );
};

export default TeamRenameActivity;
