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

import {ConcordKey, GenericOperationResult} from '../../../api/common';
import {EditProjectForm, FormValues} from '../../molecules';
import { UpdateProjectEntry, ProjectEntry } from '../../../api/org/project';
import { RequestErrorActivity } from '../index';
import {useCallback, useState} from "react";
import {createOrUpdate as apiUpdate} from "../../../api/org/project";
import {useApi} from "../../../hooks/useApi";
import {LoadingDispatch} from "../../../App";

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    initial?: ProjectEntry;
}

const toUpdateProjectEntry = (p?: ProjectEntry): UpdateProjectEntry => {
    return {
        id: p?.id,
        name: p?.name,
        visibility: p?.visibility,
        description: p?.description
    };
};

const EditProjectActivity = (props: ExternalProps) => {
    const {orgName, initial} = props;

    const dispatch = React.useContext(LoadingDispatch);
    const [updateEntry, setUpdateEntry] = useState(toUpdateProjectEntry(initial));

    const postData = useCallback(() => {
        return apiUpdate(orgName, updateEntry);
    }, [orgName, updateEntry]);

    const { error, isLoading, fetch } = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false,
        requestByFetch: true,
        dispatch
    });

    const handleSubmit = useCallback(
        (values: FormValues) => {
            setUpdateEntry(values.data);
            fetch();
        },
        [fetch]
    );

    if (!initial) {
        return <></>;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}

            <EditProjectForm
                submitting={isLoading}
                data={toUpdateProjectEntry(initial)}
                onSubmit={handleSubmit}
            />
        </>
    );
};

export default EditProjectActivity;