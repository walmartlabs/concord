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

import copyToClipboard from 'copy-to-clipboard';
import * as React from 'react';
import { Form, Icon, Input, Message } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { encrypt } from '../../../api/org/project';

import './styles.css';

interface ExternalProps {
    orgName: ConcordKey;
    projectName: ConcordKey;
}

interface State {
    encrypting: boolean;
    result: any;
    success: boolean | undefined;
    data: string;
    dirty: boolean;
}

class EncryptValueActivity extends React.PureComponent<ExternalProps, State> {
    constructor(props: ExternalProps) {
        super(props);

        this.state = {
            encrypting: false,
            result: undefined,
            success: undefined,
            data: '',
            dirty: false
        };
    }

    handleEncryptValue(value: string) {
        this.setState({ data: value });

        if (value !== '') {
            this.setState({ dirty: true });
        } else {
            this.setState({ dirty: false });
        }
    }

    reset() {
        this.setState({
            data: '',
            result: undefined,
            success: undefined
        });
    }

    encryptValue(data: string) {
        this.setState({
            encrypting: true,
            result: false
        });

        encrypt(this.props.orgName, this.props.projectName, data)
            .then((responseData) => {
                this.setState({
                    encrypting: false,
                    success: true,
                    result: responseData
                });
            })
            .catch((error) => {
                this.setState({
                    encrypting: false,
                    success: false,
                    result: error.details
                });
            });
    }

    render() {
        const { result, success, encrypting, data, dirty } = this.state;

        return (
            <>
                <Form>
                    <Form.Group widths={3}>
                        <Form.Input
                            name="encrypt"
                            value={data}
                            autoComplete="off"
                            onChange={(e, { value }) => this.handleEncryptValue(value)}
                        />

                        <Form.Button
                            primary={true}
                            loading={encrypting}
                            negative={false}
                            content="Encrypt"
                            disabled={!dirty}
                            onClick={(ev) => {
                                ev.preventDefault();
                                this.encryptValue(data);
                            }}
                        />
                    </Form.Group>
                </Form>

                <p>
                    The encrypted value can be later decrypted in flows using{' '}
                    <span className="codeSnippet">{`\${crypto.decryptString("value")}`}</span>{' '}
                    expression.
                </p>
                <p>The value is valid for the current project only.</p>

                {result && (
                    <Message success={success} error={!success} onDismiss={() => this.reset()}>
                        <Message.Header>{success ? 'Success' : 'Failure'}</Message.Header>
                        <br />
                        <Message.Content>
                            {success ? (
                                <div>
                                    <Input
                                        icon={
                                            <Icon
                                                name="copy"
                                                link={true}
                                                onClick={() =>
                                                    (copyToClipboard as any)(result.data)
                                                }
                                            />
                                        }
                                        fluid={true}
                                        value={result.data}
                                        className="encryptedValue"
                                    />
                                </div>
                            ) : (
                                <div>{result}</div>
                            )}
                        </Message.Content>
                    </Message>
                )}
            </>
        );
    }
}

export default EncryptValueActivity;
