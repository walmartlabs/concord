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

import {ConcordKey} from '../../../api/common';
import { SingleOperationPopup } from '../../molecules';

import './styles.css';
import { SemanticCOLORS, SemanticICONS } from 'semantic-ui-react';
import {useCallback} from "react";
import {RepositoryValidationResponse, validateRepository as apiValidateRepo} from "../../../api/org/project/repository";
import {useApi} from "../../../hooks/useApi";

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
    repoName: ConcordKey;
    trigger: (onClick: () => void) => React.ReactNode;
}

const ValidateRepositoryPopup = (props: ExternalProps) => {
    const {orgName, projectName, repoName, trigger} = props;

    const validateRepoRequest = useCallback(() => {
        return apiValidateRepo(orgName, projectName, repoName);
    }, [orgName, projectName, repoName]);

    const { data, isLoading, error, clearState, fetch } = useApi<RepositoryValidationResponse>(
        validateRepoRequest,
        { fetchOnMount: false, requestByFetch: true }
    );

    const resetHandler = useCallback(() => {
        clearState();
    }, [clearState]);


    let title = 'Validate repository?';

    let icon: SemanticICONS | undefined;
    let iconColor: SemanticCOLORS | undefined;
    let msg;

    if (error) {
        icon = 'exclamation circle';
        iconColor = 'red';
        title = 'Validation error';
    }

    if (data?.ok) {
        title = 'Validation complete';
        icon = 'check circle';
        iconColor = 'green';
        msg = <p>Repository validated successfully.</p>;
    }

    const warnings = data?.warnings;
    let warningDetails;
    if (warnings !== undefined && warnings.length > 0) {
        icon = 'warning circle';
        iconColor = 'yellow';
        warningDetails = (
            <>
                <p>Warnings:</p>
                <ul>
                    {warnings.map((e) => (
                        <li>{e}</li>
                    ))}
                </ul>
            </>
        );
    }

    const errors = data?.errors;
    let errorDetails;
    if (errors !== undefined && errors.length > 0) {
        icon = 'exclamation circle';
        iconColor = 'red';
        errorDetails = (
            <>
                <p>Errors:</p>
                <ul>
                    {errors.map((e) => (
                        <li>{e}</li>
                    ))}
                </ul>
            </>
        );
    }

    return (
        <SingleOperationPopup
            trigger={trigger}
            title={title}
            icon={icon}
            iconColor={iconColor}
            introMsg={
                <p>
                    Run syntax validation for <b>{repoName}</b> repository?
                </p>
            }
            running={isLoading}
            success={data?.ok || false}
            successMsg={
                <>
                    {msg}
                    {warningDetails}
                    {errorDetails}
                </>
            }
            error={error}
            reset={resetHandler}
            onConfirm={fetch}
        />
    );
};

export default ValidateRepositoryPopup;