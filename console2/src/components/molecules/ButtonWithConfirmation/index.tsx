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
import { Button, Confirm, ButtonProps } from 'semantic-ui-react';

interface State {
    showConfirm: boolean;
}

interface Props extends ButtonProps {
    renderOverride?: React.ReactNode;
    confirmationHeader: string;
    confirmationContent: string;
    onConfirm: () => void;
}

class ButtonWithConfirmation extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { showConfirm: false };
        this.handleShowConfirm = this.handleShowConfirm.bind(this);
        this.handleCancel = this.handleCancel.bind(this);
        this.handleConfirm = this.handleConfirm.bind(this);
    }

    handleShowConfirm(ev: React.SyntheticEvent<{}>) {
        ev.preventDefault();
        ev.stopPropagation();
        this.setState({ showConfirm: true });
    }

    handleCancel(ev: React.SyntheticEvent<{}>) {
        ev.stopPropagation();
        this.setState({ showConfirm: false });
    }

    handleConfirm(ev: React.SyntheticEvent<{}>) {
        ev.stopPropagation();
        this.setState({ showConfirm: false });
        this.props.onConfirm();
    }

    render() {
        const {
            confirmationHeader,
            confirmationContent,
            onConfirm,
            renderOverride,
            ...rest
        } = this.props;

        return (
            <>
                {renderOverride ? (
                    <div onClick={(ev) => this.handleShowConfirm(ev)}>{renderOverride}</div>
                ) : (
                    <Button {...rest} onClick={(ev) => this.handleShowConfirm(ev)} />
                )}

                <Confirm
                    open={this.state.showConfirm}
                    header={confirmationHeader}
                    content={confirmationContent}
                    onConfirm={this.handleConfirm}
                    onCancel={this.handleCancel}
                />
            </>
        );
    }
}

export default ButtonWithConfirmation;
