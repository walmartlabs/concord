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
import { DropdownItemProps } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { actions, selectors, State } from '../../../state/data/projects';
import { FormikDropdown } from '../../atoms';

interface ExternalProps {
    orgName: ConcordKey;
    name: string;
    label?: string;
    required?: boolean;
    fluid?: boolean;
    placeholder?: string;
}

interface StateProps {
    loading: boolean;
    options: DropdownItemProps[];
}

interface DispatchProps {
    load: () => void;
}

class ProjectDropdown extends React.PureComponent<ExternalProps & StateProps & DispatchProps> {
    componentDidMount() {
        this.props.load();
    }

    render() {
        const { orgName, load, ...rest } = this.props;

        return <FormikDropdown selection={true} search={true} clearable={true} {...rest} />;
    }
}

const makeOptions = (data: ConcordKey[]): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    return data.map((s) => ({ key: s, value: s, text: s }));
};

const mapStateToProps = (
    { projects }: { projects: State },
    { orgName }: ExternalProps
): StateProps => ({
    loading: projects.loading,
    options: makeOptions(selectors.projectNames(projects, orgName))
});

const mapDispatchToProps = (
    dispatch: Dispatch<AnyAction>,
    { orgName }: ExternalProps
): DispatchProps => ({
    load: () => dispatch(actions.listProjects(orgName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(ProjectDropdown);
