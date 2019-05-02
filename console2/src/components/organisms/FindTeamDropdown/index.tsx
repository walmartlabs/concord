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
import { DropdownItemProps, Dropdown, DropdownProps } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { State, actions } from '../../../state/data/teams';
import { Teams } from '../../../state/data/teams';
import { comparators } from '../../../utils';
import { TeamEntry } from '../../../api/org/team';

interface ExternalProps {
    onSelect: (value: TeamEntry) => void;
    orgName: ConcordKey;
    name: string;
}

interface StateProps {
    loading: boolean;
    options: DropdownItemProps[];
}

interface DispatchProps {
    load: () => void;
}

class FindTeamDropdown extends React.PureComponent<ExternalProps & StateProps & DispatchProps> {
    componentDidMount() {
        this.props.load();
    }

    handleChange(ev: DropdownItemProps, { value }: DropdownProps) {
        const i = {
            id: value as string,
            orgId: '',
            orgName: '',
            name: ev.target.textContent
        };

        const { onSelect } = this.props;
        onSelect(i);
    }

    render() {
        const { ...rest } = this.props;

        return (
            <Dropdown
                placeholder="Select team"
                selection={true}
                search={true}
                {...rest}
                onChange={(ev, data) => this.handleChange(ev, data)}
            />
        );
    }
}

const makeOptions = (data: Teams): DropdownItemProps[] => {
    if (!data) {
        return [];
    }
    return Object.keys(data)
        .map((k) => data[k])
        .sort(comparators.byName)
        .map(({ name, id }) => ({
            value: id,
            text: name
        }));
};

const mapStateToProps = ({ teams }: { teams: State }): StateProps => ({
    loading: teams.list.running,
    options: makeOptions(teams.teamById)
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { orgName }: ExternalProps
): DispatchProps => ({
    load: () => dispatch(actions.listTeams(orgName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(FindTeamDropdown);
