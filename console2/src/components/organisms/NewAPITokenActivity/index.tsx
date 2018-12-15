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
import { Button, Message, Icon } from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import { CreateTokenResponse } from '../../../state/data/apiTokens/types';
import { push as pushHistory } from 'react-router-redux';
import { actions, State } from '../../../state/data/apiTokens';
import { NewAPITokenForm, NewAPITokenFormValues, RequestErrorMessage } from '../../molecules';
import { NewTokenEntry } from '../../../api/profile/api_token';

interface StateProps {
    submitting: boolean;
    error: RequestError;
    response?: CreateTokenResponse;
}

interface DispatchProps {
    submit: (values: NewAPITokenFormValues) => void;
    reset: () => void;
    done: () => void;
}

type Props = StateProps & DispatchProps;

export class NewAPITokenActivity extends React.PureComponent<Props> {
    componentDidMount() {
        this.props.reset();
    }

    renderResponse() {
        const { response, done, error } = this.props;

        if (!response) {
            return;
        }

        if (error) {
            return <RequestErrorMessage error={error} />;
        }

        const { key } = response;

        return (
            <>
                <Message success={true}>
                    <Message.Header>API Token created</Message.Header>
                    <Message.Content>
                        <div>
                            <b>Token: </b>
                            {key}
                            <p>
                                <Icon color="black" name="info circle" />
                                <strong>Store this token for future use.</strong>
                            </p>
                        </div>
                    </Message.Content>
                </Message>

                <Button primary={true} content={'Done'} onClick={() => done()} />
            </>
        );
    }

    render() {
        const { error, submitting, submit, response } = this.props;

        if (!error && response) {
            return this.renderResponse();
        }

        return (
            <>
                {error && <RequestErrorMessage error={error} />}

                <NewAPITokenForm
                    submitting={submitting}
                    onSubmit={submit}
                    initial={{
                        name: ''
                    }}
                />
            </>
        );
    }
}

const mapStateToProps = ({ tokens }: { tokens: State }): StateProps => ({
    submitting: tokens.createToken.running,
    error: tokens.createToken.error,
    response: tokens.createToken.response as CreateTokenResponse
});

const mapDispatchToProps = (dispatch: Dispatch<{}>): DispatchProps => ({
    submit: (entry: NewTokenEntry) => {
        dispatch(actions.createToken(entry));
    },

    reset: () => {
        dispatch(actions.reset());
    },

    done: () => {
        dispatch(pushHistory(`/profile/api-token`));
    }
});

export default connect(
    mapStateToProps,
    mapDispatchToProps
)(NewAPITokenActivity);
