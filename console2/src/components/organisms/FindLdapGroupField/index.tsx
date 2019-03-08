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

import { LdapGroupSearchResult } from '../../../api/service/console';
import { actions, State } from '../../../state/data/search';

interface ExternalProps {
    onSelect: (value: LdapGroupSearchResult) => void;
    onChange?: (value?: string) => void;
    placeholder?: string;
}

interface StateProps {
    loading: boolean;
    error: boolean;
    items: LdapGroupSearchResult[];
}

interface DispatchProps {
    onSearch: (filter: string) => void;
    reset: () => void;
}

type Props = ExternalProps & StateProps & DispatchProps;

// TODO remove when the Search component will support custom result types
const toResults = (items: LdapGroupSearchResult[]) =>
    items.map((i) => ({
        title: i.displayName,
        description: i.groupName
    }));

// TODO remove when the Search component will support custom result types
const resultToItem = (result: SearchResultProps, items: LdapGroupSearchResult[]) =>
    items.find((i) => i.groupName === result.description)!;

class FindLdapGroupField extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.reset();
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

        const { onSelect } = this.props;
        onSelect(i);
    }

    render() {
        const { error, loading, onSearch, onChange, items, placeholder } = this.props;

        return (
            <Search
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
    loading: search.ldapGroups.running,
    error: !!search.ldapGroups.error,
    items: search.ldapGroups.response // TODO yuck
        ? search.ldapGroups.response.items
            ? search.ldapGroups.response.items
            : []
        : []
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    reset: () => dispatch(actions.resetLdapGroupSearch()),
    onSearch: (filter: string) => dispatch(actions.findLdapGroups(filter))
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(FindLdapGroupField);
