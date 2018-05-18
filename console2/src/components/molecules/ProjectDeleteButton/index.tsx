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
import { Button, Confirm } from 'semantic-ui-react';

interface State {
    showConfirm: boolean;
}

interface Props {
    submitting: boolean;
    onConfirm: () => void;
}

// TODO can be refactored as a generic "button-with-confirmation" component
class ProjectDeleteForm extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { showConfirm: false };
    }

    handleShowConfirm(ev: React.SyntheticEvent<{}>) {
        ev.preventDefault();
        this.setState({ showConfirm: true });
    }

    handleCancel() {
        this.setState({ showConfirm: false });
    }

    handleConfirm() {
        this.props.onConfirm();
    }

    render() {
        const { submitting } = this.props;

        return (
            <>
                <Button
                    primary={true}
                    negative={true}
                    content="Delete"
                    loading={submitting}
                    onClick={(ev) => this.handleShowConfirm(ev)}
                />

                <Confirm
                    open={this.state.showConfirm}
                    header="Delete the project?"
                    content="Are you sure you want to delete the project?"
                    onConfirm={() => this.handleConfirm()}
                    onCancel={() => this.handleCancel()}
                />
            </>
        );
    }
}

export default ProjectDeleteForm;
