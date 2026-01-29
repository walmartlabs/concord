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

import { InjectedFormikProps, withFormik } from 'formik';
import * as React from 'react';
import { Confirm, Form } from 'semantic-ui-react';

import { ConcordKey } from '../../../api/common';
import { notEmpty } from '../../../utils';
import { project as validation } from '../../../validation';
import { FormikInput } from '../../atoms';

interface State {
    showConfirm: boolean;
}

export interface FormValues {
    name: ConcordKey;
}

interface Props {
    originalName: ConcordKey;
    submitting: boolean;
    inputPlaceholder?: string;
    confirmationHeader: string;
    confirmationContent: string;
    onSubmit: (values: FormValues) => void;
    isExists: (name: string) => Promise<boolean> | undefined;
    alreadyExistsTemplate: (name: string) => string;
    disabled?: boolean;
    inputTestId?: string;
    buttonTestId?: string;
}

class EntityRenameForm extends React.Component<InjectedFormikProps<Props, FormValues>, State> {
    constructor(props: InjectedFormikProps<Props, FormValues>) {
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
        this.setState({ showConfirm: false });
        this.props.submitForm();
    }

    render() {
        const {
            inputPlaceholder,
            dirty,
            handleSubmit,
            submitting,
            confirmationHeader,
            confirmationContent,
            disabled,
            inputTestId,
            buttonTestId
        } = this.props;
        const hasErrors = notEmpty(this.props.errors);

        return (
            <Form onSubmit={handleSubmit} loading={submitting}>
                <Form.Group widths={3}>
                    <div data-testid={inputTestId}>
                        <FormikInput
                            fluid={true}
                            name="name"
                            placeholder={inputPlaceholder}
                            disabled={disabled}
                        />
                    </div>

                    <Form.Button
                        primary={true}
                        negative={true}
                        content="Rename"
                        disabled={hasErrors || !dirty || disabled}
                        onClick={(ev) => this.handleShowConfirm(ev)}
                        data-testid={buttonTestId}
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
        );
    }
}

const validator = async (
    values: FormValues,
    { originalName, isExists, alreadyExistsTemplate }: Props
) => {
    let e;

    e = validation.name(values.name);
    if (e) {
        return Promise.resolve({ name: e });
    }

    if (values.name !== originalName) {
        const exists = await isExists(values.name);
        if (exists) {
            return Promise.resolve({ name: alreadyExistsTemplate(values.name) });
        }
    }

    return {};
};

export default withFormik<Props, FormValues>({
    handleSubmit: (values, bag) => {
        bag.props.onSubmit(values);
    },
    mapPropsToValues: (props) => ({
        name: props.originalName
    }),
    validate: validator
})(EntityRenameForm);
