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
import { Form } from 'semantic-ui-react';

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { SecretVisibility, updateSecretVisibility as apiUpdateSecretVisibility } from '../../../api/org/secret';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretId: ConcordId;
    secretName: ConcordKey;
    visibility: SecretVisibility;
    renderOverride?: React.ReactNode;
    onUpdated?: () => void;
}

const SecretVisibilityActivity = ({
    orgName,
    secretId,
    secretName,
    visibility,
    renderOverride,
    onUpdated
}: ExternalProps) => {
    const [newVisibility, setNewVisibility] = React.useState(visibility);
    const [updating, setUpdating] = React.useState(false);
    const [error, setError] = React.useState<RequestError>();

    React.useEffect(() => {
        setNewVisibility(visibility);
    }, [visibility]);

    const changeVisibility = React.useCallback(async () => {
        setUpdating(true);
        setError(undefined);

        try {
            await apiUpdateSecretVisibility(orgName, secretName, newVisibility);
            onUpdated && onUpdated();
        } catch (e) {
            setError(e);
        } finally {
            setUpdating(false);
        }
    }, [newVisibility, onUpdated, orgName, secretName]);

    return (
        <>
            {error && <RequestErrorMessage error={error} />}
            <Form loading={updating}>
                <Form.Group>
                    <Form.Field>
                        <label>Visibility</label>
                        <select
                            className="ui dropdown"
                            data-testid="secret-visibility-select"
                            value={newVisibility}
                            onChange={(ev) => setNewVisibility(ev.target.value as SecretVisibility)}
                        >
                            <option value={SecretVisibility.PUBLIC}>Public</option>
                            <option value={SecretVisibility.PRIVATE}>Private</option>
                        </select>
                    </Form.Field>
                    <ButtonWithConfirmation
                        renderOverride={renderOverride}
                        floated={'right'}
                        disabled={visibility === newVisibility}
                        content="Change"
                        loading={updating}
                        confirmationHeader="Change the visibility?"
                        confirmationContent={`Are you sure you want to change the visibility to ${newVisibility}`}
                        onConfirm={changeVisibility}
                        data-testid="secret-visibility-change-button"
                    />
                </Form.Group>
            </Form>
        </>
    );
};

export default SecretVisibilityActivity;
