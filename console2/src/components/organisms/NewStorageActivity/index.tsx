/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import { ConcordKey, GenericOperationResult } from '../../../api/common';
import { NewStorageForm } from '../../molecules';
import RequestErrorActivity from '../RequestErrorActivity';
import { useCallback, useState } from 'react';
import { StorageVisibility, createOrUpdate as apiCreate } from '../../../api/org/jsonstore';
import { useApi } from '../../../hooks/useApi';
import { NewStorageFormValues } from '../../molecules/NewStorageForm';
import { Redirect } from 'react-router';

interface ExternalProps {
    orgName: ConcordKey;
}

const INIT_VALUES = {
    name: '',
    visibility: StorageVisibility.PRIVATE
};

const NewStoreActivity = ({ orgName }: ExternalProps) => {
    const [values, setValues] = useState<NewStorageFormValues>(INIT_VALUES);

    const postData = useCallback(() => {
        return apiCreate(orgName, values.name, values.visibility);
    }, [orgName, values]);

    const { error, isLoading, data } = useApi<GenericOperationResult>(postData, {
        fetchOnMount: false
    });

    const handleSubmit = useCallback((values: NewStorageFormValues) => {
        setValues({ ...values });
    }, []);

    if (data) {
        return <Redirect to={`/org/${orgName}/jsonstore/${values.name}`} />;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <NewStorageForm
                orgName={orgName}
                submitting={isLoading}
                onSubmit={handleSubmit}
                initial={INIT_VALUES}
            />
        </>
    );
};

export default NewStoreActivity;
