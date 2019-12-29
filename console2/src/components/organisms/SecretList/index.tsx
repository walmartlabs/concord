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
import { actions, State, Pagination } from '../../../state/data/secrets';
import { comparators } from '../../../utils';
import { RequestErrorMessage, PaginationToolBar } from '../../molecules';

interface ExternalProps {
    orgName: string;
    filter?: string;
}

interface StateProps {
    secrets: SecretEntry[];
    loading: boolean;
    error: RequestError;
    next?: boolean;
}

interface PaginationState {
    next: boolean;
    paginationFilter: Pagination;
}
const toState = (paginationFilter: Pagination): PaginationState => {
    return {
        next: true,
        paginationFilter: paginationFilter || {}
    };
};

interface DispatchProps {
    load: (paginationFilter: Pagination, filter?: string) => void;
}

const SecretVisibilityIcon = ({ secret }: { secret: SecretEntry }) => {
    if (secret.visibility === SecretVisibility.PUBLIC) {
        return <Icon name="unlock" />;
    } else {
        return <Icon name="lock" color="red" />;
    }
};

type Props = StateProps & DispatchProps & ExternalProps;

class SecretList extends React.Component<Props, PaginationState> {
    constructor(props: Props) {
        super(props);
        this.state = toState({ limit: 50, offset: 0 });
        this.handleNext = this.handleNext.bind(this);
        this.handlePrev = this.handlePrev.bind(this);
        this.handleFirst = this.handleFirst.bind(this);
    }
    componentDidMount() {
        const { paginationFilter } = this.state;
        const { filter } = this.props;
        this.props.load(paginationFilter, filter);
    }

    componentDidUpdate(prevProps: Props) {
        const { orgName: newOrgName, filter } = this.props;
        const { orgName: oldOrgName, filter: oldFilter } = prevProps;
        const { paginationFilter } = this.state;

        if (oldOrgName !== newOrgName) {
            this.props.load(this.state.paginationFilter, filter);
        }

        if (filter !== oldFilter) {
            this.handleNavigation(0, paginationFilter.limit);
        }
    }

    handleLimitChange(limit: any) {
        this.handleNavigation(0, limit);
    }

    handleNext() {
        const { paginationFilter } = this.state;
        let nextOffSet = this.state.paginationFilter.offset + 1;

        this.handleNavigation(nextOffSet, paginationFilter.limit);
    }

    handlePrev() {
        const { paginationFilter } = this.state;
        let nextOffSet = this.state.paginationFilter.offset - 1;

        this.handleNavigation(nextOffSet, paginationFilter.limit);
    }

    handleFirst() {
        const { paginationFilter } = this.state;
        this.handleNavigation(0, paginationFilter.limit);
    }

    handleNavigation(offset: number, limit: number) {
        const { load, filter } = this.props;

        this.setState({ paginationFilter: { offset, limit } }, () => {
            load(this.state.paginationFilter, filter);
        });
    }

    render() {
        const { error, loading, secrets, orgName } = this.props;
        const { paginationFilter } = this.state;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading) {
            return <Loader active={true} />;
        }

        return (
            <>
                <Table attached="top" basic={true} style={{ borderBottom: 0 }}>
                    <Table.Header>
                        <Table.Row>
                            <Table.HeaderCell
                                textAlign="right"
                                collapsing={true}
                                style={{ fontWeight: 'normal' }}>
                                <PaginationToolBar
                                    filterProps={paginationFilter}
                                    handleLimitChange={(limit) => this.handleLimitChange(limit)}
                                    handleNext={this.handleNext}
                                    handlePrev={this.handlePrev}
                                    handleFirst={this.handleFirst}
                                    disablePrevious={this.state.paginationFilter.offset <= 0}
                                    disableNext={!this.props.next}
                                    disableFirst={this.state.paginationFilter.offset <= 0}
                                />
                            </Table.HeaderCell>
                        </Table.Row>
                    </Table.Header>
                </Table>

                <Table celled={true} compact={true} attached="bottom">
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
                {secrets.length <= 0 && <h3>No secrets found. </h3>}
            </>
        );
    }
}

// TODO refactor as a selector?
const makeSecretList = (data?: { [id: string]: SecretEntry }): SecretEntry[] => {
    if (!data) {
        return [];
    }
    return Object.keys(data)
        .map((k) => data[k])
        .sort(comparators.byName);
};

const mapStateToProps = (
    { secrets }: { secrets: State },
    { filter }: ExternalProps
): StateProps => ({
    secrets: makeSecretList(secrets.secretById.items),
    next: secrets.secretById.next,
    loading: secrets.listSecrets.running,
    error: secrets.listSecrets.error
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { orgName }: ExternalProps
): DispatchProps => ({
    load: (paginationFilter, filter) =>
        dispatch(actions.listSecrets(orgName, paginationFilter, filter))
});

export default connect(mapStateToProps, mapDispatchToProps)(SecretList);
