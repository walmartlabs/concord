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
import { connect } from 'react-redux';
import { AnyAction, Dispatch } from 'redux';

import { ConcordId, ConcordKey, RequestError } from '../../../api/common';
import { isSecretExists } from '../../../api/service/console';
import { actions, State } from '../../../state/data/secrets';
import { secretAlreadyExistsError } from '../../../validation';
import { EntityRenameForm, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretId: ConcordId;
    secretName: ConcordKey;
}

interface StateProps {
    renaming: boolean;
    error: RequestError;
}

interface DispatchProps {
    rename: (orgName: ConcordKey, secretName: ConcordKey, newSecretName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class SecretRenameActivity extends React.PureComponent<Props> {
    render() {
        const { error, renaming, orgName, secretName, rename } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <EntityRenameForm
                    originalName={secretName}
                    submitting={renaming}
                    onSubmit={(values) => rename(orgName, secretName, values.name)}
                    inputPlaceholder="Secret name"
                    confirmationHeader="Rename the secret?"
                    confirmationContent="Are you sure you want to rename the secret?"
                    isExists={(name) => isSecretExists(orgName, name)}
                    alreadyExistsTemplate={secretAlreadyExistsError}
                />
            </>
        );
    }
}

const mapStateToProps = ({ secrets }: { secrets: State }): StateProps => ({
    renaming: secrets.renameSecret.running,
    error: secrets.renameSecret.error
});

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    rename: (orgName, secretName, newSecretName) =>
        dispatch(actions.renameSecret(orgName, secretName, newSecretName))
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretRenameActivity);
