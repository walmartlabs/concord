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

import { Confirm, Form } from 'semantic-ui-react';
import { UserSearchResult } from '../../../api/service/console';
import { FindUserField } from '../../organisms';

interface Owner {
    username: string;
    userDomain?: string;
    displayName?: string;
}

interface Props {
    originalOwner: Owner;
    confirmationHeader: string;
    confirmationContent: string;
    onSubmit: (owner: Owner) => void;
    submitting: boolean;
}

interface State {
    dirty: boolean;
    showConfirm: boolean;
    value: Owner;
}

class EntityOwnerChangeForm extends React.PureComponent<Props, State> {
    constructor(props: Props) {
        super(props);

        this.state = { dirty: false, showConfirm: false, value: props.originalOwner };
    }

    onSelect(i: UserSearchResult) {
        const { originalOwner } = this.props;
        const dirty = originalOwner.username !== i.username;
        this.setState({ dirty, value: i });
    }

    handleShowConfirm(ev: React.SyntheticEvent<{}>) {
        ev.preventDefault();
        this.setState({ showConfirm: true });
    }

    handleCancel() {
        this.setState({ showConfirm: false });
    }

    handleConfirm() {
        this.setState({ showConfirm: false });
        this.handleSubmit();
    }

    handleSubmit() {
        const { value } = this.state;
        this.props.onSubmit(value);
    }

    render() {
        const { dirty } = this.state;
        const { submitting, originalOwner, confirmationHeader, confirmationContent } = this.props;

        return (
            <>
                <Form loading={submitting}>
                    <Form.Group widths={3}>
                        <Form.Field>
                            <FindUserField
                                placeholder="Search for a user..."
                                defaultUsername={
                                    originalOwner !== undefined ? originalOwner.username : ''
                                }
                                defaultUserDomain={
                                    originalOwner !== undefined ? originalOwner.userDomain : undefined
                                }
                                defaultDisplayName={
                                    originalOwner !== undefined ? originalOwner.displayName : ''
                                }
                                onSelect={(u: any) => this.onSelect(u)}
                            />
                        </Form.Field>

                        <Form.Button
                            primary={true}
                            negative={true}
                            content="Change"
                            disabled={!dirty}
                            onClick={(ev) => this.handleShowConfirm(ev)}
                        />
                    </Form.Group>

                    <Confirm
                        open={this.state.showConfirm}
                        header={confirmationHeader}
                        content={confirmationContent}
                        onConfirm={() => this.handleConfirm()}
                        onCancel={() => this.handleCancel()}
                    />
                </Form>
            </>
        );
    }
}

export default EntityOwnerChangeForm;
