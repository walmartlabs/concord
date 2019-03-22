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
import { Search, SearchResultData, SearchResultProps } from 'semantic-ui-react';

import { UserSearchResult } from '../../../api/service/console';
import { actions, State } from '../../../state/data/search';

interface ExternalProps {
    defaultValue?: string;
    valueRender?: (value: UserSearchResult) => string;
    onSelect: (value: UserSearchResult) => void;
    onChange?: (value?: string) => void;
    placeholder?: string;
}

interface InternalState {
    value: string;
}

interface StateProps {
    loading: boolean;
    error: boolean;
    items: UserSearchResult[];
}

interface DispatchProps {
    onSearch: (filter: string) => void;
    reset: () => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

// TODO remove when the Search component will support custom result types
const toResults = (items: UserSearchResult[]) =>
    items.map((i) => ({
        title: i.displayName,
        description: i.username
    }));

// TODO remove when the Search component will support custom result types
const resultToItem = (result: SearchResultProps, items: UserSearchResult[]) =>
    items.find((i) => i.username === result.description)!;

class FindUserField extends React.PureComponent<Props, InternalState> {
    constructor(props: Props) {
        super(props);

        const { defaultValue } = this.props;
        this.state = { value: defaultValue || '' };
    }

    componentDidMount() {
        this.props.reset();

        const { defaultValue } = this.props;
        this.state = { value: defaultValue || '' };
    }

    handleSelect({ result }: SearchResultData) {
        if (!result) {
            return;
        }

        const { items, valueRender } = this.props;
        const i = resultToItem(result, items);
        if (!i) {
            return;
        }

        if (valueRender !== undefined) {
            this.setState({ value: valueRender(i) });
        } else {
            this.setState({ value: i.displayName });
        }

        const { onSelect } = this.props;
        onSelect(i);
    }

    render() {
        const { value } = this.state;
        const { error, loading, onSearch, onChange, items, placeholder } = this.props;

        return (
            <Search
                value={value}
                input={{
                    fluid: true,
                    placeholder: placeholder ? placeholder : 'Search for a user...',
                    error
                }}
                fluid={true}
                loading={loading}
                showNoResults={!loading}
                onSearchChange={(ev, data) => {
                    const filter = data.value;

                    this.setState({ value: filter || '' });

                    if (filter && filter.length >= 5) {
                        onSearch(filter);
                    }

                    if (onChange) {
                        onChange(data.value);
                    }
                }}
                onResultSelect={(ev, data) => this.handleSelect(data)}
                results={toResults(items)}
            />
        );
    }
}

const mapStateToProps = ({ search }: { search: State }): StateProps => ({
    loading: search.users.running,
    error: !!search.users.error,
    items: search.users.response // TODO yuck
        ? search.users.response.items
            ? search.users.response.items
            : []
        : []
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    reset: () => dispatch(actions.resetUserSearch()),
    onSearch: (filter: string) => dispatch(actions.findUsers(filter))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(FindUserField);
