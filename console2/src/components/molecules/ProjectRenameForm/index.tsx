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
import { isProjectExists } from '../../../api/service/console';
import { notEmpty } from '../../../utils';
import { project as validation, projectAlreadyExistsError } from '../../../validation';
import { FormikInput } from '../../atoms';

interface State {
    showConfirm: boolean;
}

interface FormValues {
    name: ConcordKey;
}

interface Props {
    orgName: ConcordKey;
    initial: FormValues;
    submitting: boolean;
    onSubmit: (values: FormValues) => void;
}

class ProjectRenameForm extends React.Component<InjectedFormikProps<Props, FormValues>, State> {
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
        this.props.submitForm();
    }

    render() {
        const { dirty, handleSubmit, submitting } = this.props;
        const hasErrors = notEmpty(this.props.errors);

        return (
            <Form onSubmit={handleSubmit} loading={submitting}>
                <FormikInput name="name" placeholder="Project name" />

                <Form.Button
                    primary={true}
                    negative={true}
                    content="Rename"
                    disabled={hasErrors || !dirty}
                    onClick={(ev) => this.handleShowConfirm(ev)}
                />

                <Confirm
                    open={this.state.showConfirm}
                    header="Rename the project?"
                    content="Are you sure you want to rename the project?"
                    onConfirm={() => this.handleConfirm()}
                    onCancel={() => this.handleCancel()}
                />
            </Form>
        );
    }
}

const validator = async (values: FormValues, props: Props) => {
    let e;

    e = validation.name(values.name);
    if (e) {
        throw { name: e };
    }

    if (values.name !== props.initial.name) {
        const exists = await isProjectExists(props.orgName, values.name);
        if (exists) {
            throw { name: projectAlreadyExistsError(values.name) };
        }
    }

    return {};
};

export default withFormik<Props, FormValues>({
    handleSubmit: (values, bag) => {
        bag.props.onSubmit(values);
    },
    mapPropsToValues: (props) => props.initial,
    validate: validator
})(ProjectRenameForm);
