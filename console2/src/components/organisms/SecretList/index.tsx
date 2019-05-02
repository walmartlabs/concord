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
import { Link } from 'react-router-dom';
import { Icon, Loader, Table } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import { SecretEntry, SecretVisibility, typeToText } from '../../../api/org/secret';
import { actions, State } from '../../../state/data/secrets';
import { comparators } from '../../../utils';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    orgName: string;
    filter?: string;
}

interface StateProps {
    secrets: SecretEntry[];
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: () => void;
}

const SecretVisibilityIcon = ({ secret }: { secret: SecretEntry }) => {
    if (secret.visibility === SecretVisibility.PUBLIC) {
        return <Icon name="unlock" />;
    } else {
        return <Icon name="lock" color="red" />;
    }
};

type Props = StateProps & DispatchProps & ExternalProps;

class SecretList extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.load();
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName } = this.props;
        const { orgName: oldOrgName } = prevProps;

        if (oldOrgName !== newOrgName) {
            this.props.load();
        }
    }

    render() {
        const { error, loading, secrets, orgName } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading) {
            return <Loader active={true} />;
        }

        if (secrets.length === 0) {
            return <h3>No secrets found.</h3>;
        }

        return (
            <Table celled={true} compact={true}>
                <Table.Header>
                    <Table.Row>
                        <Table.HeaderCell collapsing={true} />
                        <Table.HeaderCell collapsing={true}>Name</Table.HeaderCell>
                        <Table.HeaderCell>Type</Table.HeaderCell>
                        <Table.HeaderCell collapsing={true}>Project</Table.HeaderCell>
                    </Table.Row>
                </Table.Header>
                <Table.Body>
                    {secrets.map((secret, index) => (
                        <Table.Row key={index}>
                            <Table.Cell>
                                <SecretVisibilityIcon secret={secret} />
                            </Table.Cell>
                            <Table.Cell singleLine={true}>
                                <Link to={`/org/${orgName}/secret/${secret.name}`}>
                                    {secret.name}
                                </Link>
                            </Table.Cell>
                            <Table.Cell>{typeToText(secret.type)}</Table.Cell>
                            <Table.Cell>{secret.projectName}</Table.Cell>
                        </Table.Row>
                    ))}
                </Table.Body>
            </Table>
        );
    }
}

// TODO refactor as a selector?
const makeSecretList = (data: { [id: string]: SecretEntry }, filter?: string): SecretEntry[] =>
    Object.keys(data)
        .map((k) => data[k])
        .filter((e) => (filter ? e.name.toLowerCase().indexOf(filter.toLowerCase()) >= 0 : true))
        .sort(comparators.byName);

const mapStateToProps = (
    { secrets }: { secrets: State },
    { filter }: ExternalProps
): StateProps => ({
    secrets: makeSecretList(secrets.secretById, filter),
    loading: secrets.listSecrets.running,
    error: secrets.listSecrets.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { orgName }: ExternalProps
): DispatchProps => ({
    load: () => dispatch(actions.listSecrets(orgName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(SecretList);
