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
import { ButtonWithConfirmation } from '../../molecules';
import { RequestErrorActivity } from '../index';
import {useCallback} from "react";
import {deleteProject as apiDelete} from "../../../api/org/project";
import {useApi} from "../../../hooks/useApi";
import {Redirect} from "react-router";

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    disabled?: boolean;
}

const ProjectDeleteActivity = (props: ExternalProps) => {
    const {orgName, projectName, disabled} = props;

    const deleteData = useCallback(async () => {
        return await apiDelete(orgName, projectName);
    }, [orgName, projectName]);

    const { data, error, isLoading, fetch, clearState } = useApi<GenericOperationResult>(deleteData, {
        fetchOnMount: false,
        requestByFetch: true
    });

    const confirmHandler = useCallback(() => {
            clearState();
            fetch();
        },
        [clearState, fetch]
    );

    if (data) {
        return <Redirect to={`/org/${orgName}/project`} />;
    }

    return (
        <>
            {error && <RequestErrorActivity error={error} />}
            <ButtonWithConfirmation
                primary={true}
                negative={true}
                content="Delete"
                loading={isLoading}
                disabled={disabled}
                confirmationHeader="Delete the project?"
                confirmationContent="Are you sure you want to delete the project?"
                onConfirm={confirmHandler}
            />
        </>
    );
};

export default ProjectDeleteActivity;