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
import { DropdownItemProps } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { actions, State } from '../../../state/data/secrets';
import { Secrets } from '../../../state/data/secrets/types';
import { comparators } from '../../../utils';
import { FormikDropdown } from '../../atoms';

interface ExternalProps {
    orgName: ConcordKey;
    name: string;
    label?: string;
    required?: boolean;
    fluid?: boolean;
    disabled?: boolean;
}

interface StateProps {
    loading: boolean;
    options: DropdownItemProps[];
}

interface DispatchProps {
    load: () => void;
}

class SecretDropdown extends React.PureComponent<ExternalProps & StateProps & DispatchProps> {
    componentDidMount() {
        this.props.load();
    }

    render() {
        const { orgName, load, ...rest } = this.props;

        return <FormikDropdown selection={true} search={true} {...rest} />;
    }
}

const makeOptions = (data: Secrets, required?: boolean): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    const options = Object.keys(data)
        .map((k) => data[k])
        .sort(comparators.byName)
        .map(({ name, id }) => ({
            value: id,
            text: name
        }));

    if (!required) {
        options.unshift({
            value: '',
            text: ''
        });
    }

    return options;
};

const mapStateToProps = (
    { secrets }: { secrets: State },
    { required }: ExternalProps
): StateProps => ({
    loading: secrets.listSecrets.running,
    options: makeOptions(secrets.secretById, required)
});

const mapDispatchToProps = (dispatch: Dispatch<{}>, { orgName }: ExternalProps): DispatchProps => ({
    load: () => dispatch(actions.listSecrets(orgName))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(SecretDropdown);
