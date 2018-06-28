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
import { Button, Loader, Table } from 'semantic-ui-react';

import { ConcordKey, RequestError } from '../../../api/common';
import {
    SecretEntry,
    SecretEncryptedByType,
    SecretType,
    SecretVisibility,
    typeToText
} from '../../../api/org/secret';
import { actions, selectors, State } from '../../../state/data/secrets';
import { RequestErrorMessage } from '../../molecules';
import { DeleteSecretPopup, PublicKeyPopup } from '../../organisms';

interface ExternalProps {
    orgName: ConcordKey;
    secretName: ConcordKey;
}

interface StateProps {
    secret?: SecretEntry;
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: () => void;
}

type Props = StateProps & DispatchProps & ExternalProps;

const visibilityToText = (v: SecretVisibility) =>
    v === SecretVisibility.PUBLIC ? 'Public' : 'Private';

const encryptedByToText = (t: SecretEncryptedByType) =>
    t === SecretEncryptedByType.SERVER_KEY ? 'Server key' : 'Password';

class SecretInfo extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.load();
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName, secretName: newSecretName } = this.props;
        const { orgName: oldOrgName, secretName: oldSecretName } = prevProps;

        if (oldOrgName !== newOrgName || oldSecretName !== newSecretName) {
            this.props.load();
        }
    }

    render() {
        const { error, loading, secret } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (!secret || loading) {
            return <Loader active={true} />;
        }

        return (
            <>
                <Table collapsing={true} definition={true}>
                    <Table.Body>
                        <Table.Row>
                            <Table.Cell>Name</Table.Cell>
                            <Table.Cell>{secret.name}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Visibility</Table.Cell>
                            <Table.Cell>{visibilityToText(secret.visibility)}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Type</Table.Cell>
                            <Table.Cell>{typeToText(secret.type)}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Protected by</Table.Cell>
                            <Table.Cell>{encryptedByToText(secret.encryptedBy)}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Project</Table.Cell>
                            <Table.Cell>{secret.projectName}</Table.Cell>
                        </Table.Row>
                        <Table.Row>
                            <Table.Cell>Owner</Table.Cell>
                            <Table.Cell>{secret.owner ? secret.owner.username : '-'}</Table.Cell>
                        </Table.Row>
                    </Table.Body>
                </Table>

                {secret.type === SecretType.KEY_PAIR &&
                    secret.encryptedBy === SecretEncryptedByType.SERVER_KEY &&
                    this.renderPublicKey()}

                <DeleteSecretPopup
                    orgName={secret.orgName}
                    secretName={secret.name}
                    trigger={(onClick) => (
                        <Button
                            floated="right"
                            color="red"
                            icon="delete"
                            content="Delete"
                            onClick={onClick}
                        />
                    )}
                />
            </>
        );
    }

    renderPublicKey() {
        const { secret } = this.props;

        if (!secret) {
            return;
        }

        return <PublicKeyPopup orgName={secret.orgName} secretName={secret.name} />;
    }
}

const mapStateToProps = (
    { secrets }: { secrets: State },
    { orgName, secretName }: ExternalProps
): StateProps => ({
    secret: selectors.secretByName(secrets, orgName, secretName),
    loading: secrets.listSecrets.running,
    error: secrets.listSecrets.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<{}>,
    { orgName, secretName }: ExternalProps
): DispatchProps => ({
    load: () => dispatch(actions.getSecret(orgName, secretName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(SecretInfo);
