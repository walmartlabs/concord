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
import { Search, SearchResultData, SearchResultProps } from 'semantic-ui-react';

import { UserSearchResult } from '../../../api/service/console';
import { actions, State } from '../../../state/data/search';

interface ExternalProps {
    defaultUsername?: string;
    defaultUserDomain?: string;
    defaultDisplayName?: string;
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
        description: i.username + (i.userDomain ? '@' + i.userDomain : ''),
        key: i.username + (i.userDomain ? '@' + i.userDomain : '')
    }));

// TODO remove when the Search component will support custom result types
const resultToItem = (result: SearchResultProps, items: UserSearchResult[]) =>
    items.find(
        (i) => i.username + (i.userDomain ? '@' + i.userDomain : '') === result.description
    )!;

export const renderUsername = (username: string, userDomain?: string): string => {
    return userDomain !== undefined ? username + '@' + userDomain : username;
};

export const renderUser = (username: string, userDomain?: string, displayName?: string): string => {
    return displayName !== undefined
        ? displayName + ' (' + renderUsername(username, userDomain) + ' )'
        : renderUsername(username, userDomain);
};

/**
 * @deprecated see FindUserField2
 */
class FindUserField extends React.PureComponent<Props, InternalState> {
    constructor(props: Props) {
        super(props);

        const { defaultUsername, defaultUserDomain, defaultDisplayName } = this.props;
        this.state = {
            value: renderUser(defaultUsername || '', defaultUserDomain, defaultDisplayName)
        };
    }

    componentDidUpdate(
        prevProps: Readonly<Props>,
        prevState: Readonly<InternalState>,
        snapshot?: any
    ): void {
        if (
            prevProps.defaultUsername !== this.props.defaultUsername ||
            prevProps.defaultUserDomain !== this.props.defaultUserDomain ||
            prevProps.defaultDisplayName !== this.props.defaultDisplayName
        ) {
            const { defaultUsername, defaultUserDomain, defaultDisplayName } = this.props;
            this.setState({
                value: renderUser(defaultUsername || '', defaultUserDomain, defaultDisplayName)
            });
        }
    }

    componentDidMount() {
        this.props.reset();

        const { defaultUsername, defaultUserDomain, defaultDisplayName } = this.props;
        this.setState({
            value: renderUser(defaultUsername || '', defaultUserDomain, defaultDisplayName)
        });
    }

    handleSelect({ result }: SearchResultData) {
        if (!result) {
            return;
        }

        const { items } = this.props;
        const i = resultToItem(result, items);
        if (!i) {
            return;
        }

        this.setState({ value: renderUser(i.username, i.userDomain, i.displayName) });

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

const mapDispatchToProps = (dispatch: Dispatch<AnyAction>): DispatchProps => ({
    reset: () => dispatch(actions.resetUserSearch()),
    onSearch: (filter: string) => dispatch(actions.findUsers(filter))
});

export default connect(mapStateToProps, mapDispatchToProps)(FindUserField);
