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
import { connect, Dispatch } from 'react-redux';
import { ConcordKey, RequestError } from '../../../api/common';
import { actions, State } from '../../../state/data/secrets';
import { ButtonWithConfirmation, RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

interface StateProps {
    deleting: boolean;
    error: RequestError;
}

interface DispatchProps {
    deleteSecret: (orgName: ConcordKey, secretName: ConcordKey) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class SecretDeleteActivity extends React.PureComponent<Props> {
    render() {
        const { error, deleting, orgName, secretName, deleteSecret } = this.props;

        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <ButtonWithConfirmation
                    primary={true}
                    negative={true}
                    content="Delete"
                    loading={deleting}
                    confirmationHeader="Delete the secret?"
                    confirmationContent="Are you sure you want to delete the secret?"
                    onConfirm={() => deleteSecret(orgName, secretName)}
                />
            </>
        );
    }
}

const mapStateToProps = ({ secrets }: { secrets: State }): StateProps => ({
    deleting: secrets.deleteSecret.running,
    error: secrets.deleteSecret.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    deleteSecret: (orgName, secretName) => dispatch(actions.deleteSecret(orgName, secretName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(SecretDeleteActivity);
