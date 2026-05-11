/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import { useCallback, useEffect, useState } from 'react';
import { Loader } from 'semantic-ui-react';

import {
    AnsibleEvent,
    AnsibleStatus,
    listAnsibleEvents as apiListAnsibleEvents
} from '../../../../api/process/ansible';
import { ProcessEventEntry } from '../../../../api/process/event';
import { ConcordId, RequestError } from '../../../../api/common';
import { AnsibleTaskList, RequestErrorMessage } from '../../../molecules';
import { combinePrePostEvents } from '../../ProcessEventsActivity';

interface Props {
    instanceId: ConcordId;
    playbookId?: ConcordId;
    host?: string;
    hostGroup?: string;
    status?: AnsibleStatus;
    showHosts?: boolean;
}

const sortAnsibleEvents = (events: Array<ProcessEventEntry<AnsibleEvent>>) =>
    events
        .filter((value) => value.data.status !== undefined)
        .sort((a, b) => (a.eventDate > b.eventDate ? 1 : a.eventDate < b.eventDate ? -1 : 0));

const AnsibleTaskActivity = ({
    instanceId,
    playbookId,
    host,
    hostGroup,
    status,
    showHosts
}: Props) => {
    const [loading, setLoading] = useState(false);
    const [events, setEvents] = useState<Array<ProcessEventEntry<AnsibleEvent>>>([]);
    const [error, setError] = useState<RequestError>();

    const loadEvents = useCallback(async () => {
        setLoading(true);
        setError(undefined);

        try {
            const response = await apiListAnsibleEvents(
                instanceId,
                host,
                hostGroup,
                status,
                playbookId
            );

            setEvents(combinePrePostEvents(sortAnsibleEvents(response)));
        } catch (e) {
            setError(e);
        } finally {
            setLoading(false);
        }
    }, [host, hostGroup, instanceId, playbookId, status]);

    useEffect(() => {
        loadEvents();
    }, [loadEvents]);

    if (error) {
        return <RequestErrorMessage error={error} />;
    }

    if (loading) {
        return <Loader active={true} />;
    }

    if (events.length === 0) {
        return <h4>No failures detected.</h4>;
    }

    return <AnsibleTaskList title={host} showHosts={showHosts} tasks={events} />;
};

export default AnsibleTaskActivity;
