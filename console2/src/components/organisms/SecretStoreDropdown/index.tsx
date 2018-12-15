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
import { DropdownItemProps } from 'semantic-ui-react';
import { RequestError } from '../../../api/common';

import { listActiveStores as apiList, SecretStoreEntry } from '../../../api/secret/store';
import { FormikDropdown } from '../../atoms';
import { RequestErrorMessage } from '../../molecules';

interface State {
    loading: boolean;
    options: DropdownItemProps[];
    error: RequestError;
}

interface Props {
    name: string;
    label?: string;
    required?: boolean;
    fluid?: boolean;
}

const makeOptions = (data: SecretStoreEntry[]): DropdownItemProps[] => {
    if (!data) {
        return [];
    }

    return Object.keys(data)
        .map((k) => data[k])
        .sort((a, b) => (a.storeType > b.storeType ? 1 : a.storeType < b.storeType ? -1 : 0))
        .map(({ storeType, description }) => ({
            value: storeType,
            text: description
        }));
};

class SecretStoreDropdown extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { loading: false, options: [], error: null };
    }

    componentDidMount() {
        this.setState({ loading: true, error: null });

        apiList()
            .then((items) => this.setState({ loading: false, options: makeOptions(items) }))
            .catch((e) => {
                this.setState({
                    loading: false,
                    error: e,
                    options: []
                });
            });
    }

    render() {
        const { loading, error, options } = this.state;
        return (
            <>
                {error && <RequestErrorMessage error={error} />}
                <FormikDropdown
                    disabled={options.length <= 1}
                    selection={true}
                    {...this.props}
                    options={options}
                    loading={loading}
                    error={!!error}
                />
            </>
        );
    }
}

export default SecretStoreDropdown;
