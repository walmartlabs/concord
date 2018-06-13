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
import { connect } from 'react-redux';

import { State as SessionState } from '../../../state/session';
import { Button, Message, Icon } from 'semantic-ui-react';
import { create } from '../../../api/profile/api_token';

interface StateProps {
    username: string;
}

interface State {
    generating: boolean;
    result: any;
    success: boolean | undefined;
}

type Props = StateProps;

export class NewAPIToken extends React.PureComponent<Props, State> {
    constructor(props: Props) {
        super(props);

        this.handleGenerate = this.handleGenerate.bind(this);

        this.state = {
            generating: false,
            result: undefined,
            success: undefined
        };
    }

    handleGenerate = () => {
        this.setState({
            generating: true,
            result: false
        });

        create(this.props.username)
            .then((responseData) => {
                this.setState({
                    generating: false,
                    success: true,
                    result: responseData
                });
            })
            .catch((error) => {
                this.setState({
                    generating: false,
                    success: false
                });
            });
    };

    render() {
        const { generating, result, success } = this.state;
        const { username } = this.props;
        return (
            <>
                <h4>New API Token</h4>
                <p>
                    Tokens are not stored and cannot be restored - only recreated.
                    <strong>
                        {' '}
                        Generating a new token will overwrite any existing tokens you have.
                    </strong>
                </p>

                <Button primary={true} onClick={() => this.handleGenerate()} loading={generating}>
                    Generate
                </Button>

                {result && (
                    <Message success={success} error={!success}>
                        <Message.Header>{success ? 'Success' : 'Failure'}</Message.Header>
                        <Message.Content>
                            {success ? (
                                // TODO: research why padding is needed here
                                <div style={{ paddingTop: 5 }}>
                                    <b>Token: </b>
                                    {result.key}
                                    <p>
                                        <Icon color="black" name="info circle" />
                                        <strong>Store this token for future use.</strong>
                                    </p>
                                </div>
                            ) : (
                                'Token generation failed for user: ' + username ||
                                'Unknown User' + '. Please contact Admin.'
                            )}
                        </Message.Content>
                    </Message>
                )}
            </>
        );
    }
}

const mapStateToProps = ({ session }: { session: SessionState }): StateProps => {
    return {
        username: session.user.username!
    };
};

export default connect(mapStateToProps)(NewAPIToken);
