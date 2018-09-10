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
import { Link } from 'react-router-dom';
import { Icon, List, Loader } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import { OrganizationEntry, OrganizationVisibility } from '../../../api/org';
import { actions, State } from '../../../state/data/orgs';
import { Organizations } from '../../../state/data/orgs/types';
import { comparators } from '../../../utils';
import { RequestErrorMessage } from '../../molecules';

interface ExternalProps {
    filter?: string;
    onlyCurrent: boolean;
}

interface StateProps {
    data: OrganizationEntry[];
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: (onlyCurrent: boolean) => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

class OrganizationList extends React.PureComponent<Props> {
    componentDidMount() {
        this.update();
    }

    componentDidUpdate(prepProps: Props) {
        if (this.props.onlyCurrent !== prepProps.onlyCurrent) {
            this.update();
        }
    }

    update() {
        const { load, onlyCurrent } = this.props;
        load(onlyCurrent);
    }

    render() {
        const { loading, data, error } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading) {
            return <Loader active={true} />;
        }

        if (data.length === 0) {
            return <h3>No organizations found</h3>;
        }

        return (
            <List divided={true} relaxed={true} size="large">
                {data.map((org: OrganizationEntry, index: number) => (
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
        );
    }
}

// TODO refactor as a selector?
const makeOrgList = (data: Organizations, filter?: string): OrganizationEntry[] =>
    Object.keys(data)
        .map((k) => data[k])
        .filter((e) => (filter ? e.name.toLowerCase().indexOf(filter.toLowerCase()) >= 0 : true))
        .sort(comparators.byName);

const mapStateToProps = ({ orgs }: { orgs: State }, { filter }: ExternalProps): StateProps => ({
    data: makeOrgList(orgs.orgById, filter),
    loading: orgs.loading,
    error: orgs.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: (onlyCurrent: boolean) => dispatch(actions.listOrgs(onlyCurrent))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(OrganizationList);
