/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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
import { Link } from 'react-router-dom';
import { Icon, List, Loader, Menu, Radio } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import { OrganizationEntry, OrganizationVisibility } from '../../../api/org';
import { actions, State } from '../../../state/data/orgs';
import { Organizations } from '../../../state/data/orgs/types';
import { comparators } from '../../../utils';
import { RequestErrorMessage } from '../../molecules';

interface OwnState {
    onlyCurrent: boolean;
}

interface StateProps {
    orgs: OrganizationEntry[];
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: (onlyCurrent: boolean) => void;
}

type Props = StateProps & DispatchProps;

class OrganizationList extends React.PureComponent<Props, OwnState> {
    constructor(props: Props) {
        super(props);
        this.state = { onlyCurrent: true };
    }

    componentDidMount() {
        this.update();
    }

    componentDidUpdate(prepProps: Props, prevState: OwnState) {
        if (this.state.onlyCurrent !== prevState.onlyCurrent) {
            this.update();
        }
    }

    update() {
        const { load } = this.props;
        load(this.state.onlyCurrent);
    }

    render() {
        const { loading, orgs, error } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        return (
            <>
                <Menu secondary={true}>
                    <Menu.Item position="right">
                        <Radio
                            label="Show only user's organizations"
                            toggle={true}
                            checked={this.state.onlyCurrent}
                            onChange={(ev, { checked }) => this.setState({ onlyCurrent: checked! })}
                        />
                    </Menu.Item>
                </Menu>

                {loading && <Loader active={true} />}

                <List divided={true} relaxed={true} size="large">
                    {orgs.map((org: OrganizationEntry, index: number) => (
                        <List.Item key={index}>
                            <Icon
                                name={
                                    org.visibility === OrganizationVisibility.PRIVATE
                                        ? 'lock'
                                        : 'unlock'
                                }
                                color="grey"
                            />
                            <List.Content>
                                <List.Header>
                                    <Link to={`/org/${org.name}`}>{org.name}</Link>
                                </List.Header>
                            </List.Content>
                        </List.Item>
                    ))}
                </List>
            </>
        );
    }
}

// TODO refactor as a selector?
const makeOrgList = (data: Organizations): OrganizationEntry[] => {
    if (!data) {
        return [];
    }

    return Object.keys(data)
        .map((k) => data[k])
        .sort(comparators.byName);
};

const mapStateToProps = ({ orgs }: { orgs: State }): StateProps => ({
    orgs: makeOrgList(orgs.orgsById),
    loading: orgs.loading,
    error: orgs.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (onlyCurrent: boolean) => dispatch(actions.listOrgs(onlyCurrent))
});

export default connect(mapStateToProps, mapDispatchToProps)(OrganizationList);
