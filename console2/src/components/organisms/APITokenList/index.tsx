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
import { Icon, List, Loader } from 'semantic-ui-react';

import { APITokenDeleteActivity } from '../index';
import { RequestError } from '../../../api/common';
import { TokenEntry } from '../../../api/profile/api_token';
import { actions, State } from '../../../state/data/apiTokens';
import { comparators } from '../../../utils';
import { LocalTimestamp } from '../../molecules/index';

import { RequestErrorMessage } from '../../molecules';

interface StateProps {
    tokens: TokenEntry[];
    loading: boolean;
    error: RequestError;
}

interface DispatchProps {
    load: () => void;
}

type Props = StateProps & DispatchProps;

class APITokenList extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.load();
    }

    render() {
        const { loading, tokens, error } = this.props;

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        if (loading) {
            return <Loader active={true} />;
        }

        if (tokens.length === 0) {
            return <p>There are no API tokens associated with your account.</p>;
        }

        return (
            <List divided={true} relaxed={true} size="large">
                {tokens.map((token, index) => (
                    <List.Item key={index}>
                        <List.Content floated={'right'}>
                            <APITokenDeleteActivity id={token.id} name={token.name} />
                        </List.Content>
                        <Icon name="key" size="large" />
                        <List.Content>
                            <List.Header>{token.name}</List.Header>
                            {token.expiredAt && (
                                <List.Description>
                                    expired at: <LocalTimestamp value={token.expiredAt} />
                                </List.Description>
                            )}
                        </List.Content>
                    </List.Item>
                ))}
            </List>
        );
    }
}

const makeAPITokenList = (data: { [id: string]: TokenEntry }): TokenEntry[] =>
    Object.keys(data)
        .map((k) => data[k])
        .sort(comparators.byName);

const mapStateToProps = ({ tokens }: { tokens: State }): StateProps => ({
    tokens: makeAPITokenList(tokens.tokenById),
    loading: tokens.loading,
    error: tokens.error
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    load: () => dispatch(actions.listTokens())
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(APITokenList);
