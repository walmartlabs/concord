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
