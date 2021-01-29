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
import { useCallback, useState } from 'react';

import { ConcordKey, RequestError } from '../../../api/common';
import { updateSecretProject as apiUpdateSecretProject } from '../../../api/org/secret';
import { ProjectSearch, RequestErrorActivity } from '../index';
import { Confirm, Form } from 'semantic-ui-react';

interface ExternalProps {
    orgName: ConcordKey;
    projectName?: ConcordKey;
    secretName: ConcordKey;
}

type Props = ExternalProps;

export default ({ orgName, projectName, secretName }: Props) => {
    const [dirty, setDirty] = useState<boolean>(false);
    const [showConfirm, setShowConfirm] = useState<boolean>(false);
    const [updating, setUpdating] = useState(false);
    const [error, setError] = useState<RequestError>();

    const [submittedProjectName, setSubmittedProjectName] = useState<string | undefined>(
        projectName
    );
    const [projectNameValue, setProjectNameValue] = useState<string | undefined>();

    const update = useCallback(async () => {
        try {
            setUpdating(true);
            await apiUpdateSecretProject(orgName, secretName, projectNameValue || '');
            setSubmittedProjectName(projectNameValue);
        } catch (e) {
            setError(e);
        } finally {
            setUpdating(false);
        }
    }, [orgName, secretName, projectNameValue]);

    const onConfirmHandler = useCallback(async () => {
        setShowConfirm(false);

        await update();

        setDirty(false);
    }, [update]);

    const onCancelHandler = useCallback(() => {
        setShowConfirm(false);
    }, []);

    return (
        <>
            {error && <RequestErrorActivity error={error} />}

            <Form loading={updating}>
                <Form.Group widths={3}>
                    <Form.Field>
                        <ProjectSearch
                            orgName={orgName}
                            placeholder="any"
                            fluid={true}
                            defaultProjectName={submittedProjectName}
                            onReset={(value) => {
                                setDirty(false);
                                setProjectNameValue(value?.name);
                            }}
                            onClear={() => {
                                setDirty(true);
                                setProjectNameValue(undefined);
                            }}
                            onSelect={(value) => {
                                setDirty(true);
                                setProjectNameValue(value.name);
                            }}
                        />
                    </Form.Field>

                    <Form.Button
                        primary={true}
                        negative={true}
                        content="Update"
                        disabled={!dirty}
                        onClick={(ev) => setShowConfirm(true)}
                    />
                </Form.Group>

                <Confirm
                    open={showConfirm}
                    header="Update the project?"
                    content="Are you sure you want to update the project?"
                    onConfirm={onConfirmHandler}
                    onCancel={onCancelHandler}
                />
            </Form>
        </>
    );
};
