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
import { Divider, Form } from 'semantic-ui-react';
import { ProjectVisibility, UpdateProjectEntry } from '../../../api/org/project';
import { FormikDropdown, FormikInput } from '../../atoms';

export interface FormValues {
    data: UpdateProjectEntry;
}

export type EditProjectFormValues = FormValues;

interface Props {
    submitting: boolean;
    data: UpdateProjectEntry;
    onSubmit: (values: FormValues) => void;
}

const visibilityOptions = [
    {
        text: 'Public',
        icon: 'unlock',
        value: ProjectVisibility.PUBLIC
    },
    {
        text: 'Private',
        icon: 'lock',
        value: ProjectVisibility.PRIVATE
    }
];

class EditProjectForm extends React.Component<InjectedFormikProps<Props, FormValues>> {
    updateProject(ev: React.SyntheticEvent<{}>) {
        ev.preventDefault();
        this.props.submitForm();
    }

    render() {
        const { dirty, handleSubmit, submitting } = this.props;

        return (
            <Form onSubmit={handleSubmit} loading={submitting}>
                <FormikDropdown
                    selection={true}
                    name="data.visibility"
                    label="Visibility"
                    options={visibilityOptions}
                />

                <FormikInput fluid={true} label="Description" name="data.description" />
                <Divider />

                <Form.Button
                    primary={true}
                    content="Save"
                    disabled={!dirty}
                    onClick={(ev) => this.updateProject(ev)}
                />
            </Form>
        );
    }
}

export default withFormik<Props, FormValues>({
    handleSubmit: ({ data }, bag) => {
        // update only the specific fields
        bag.props.onSubmit({
            data: {
                id: data.id,
                name: bag.props.data.name,
                visibility: data.visibility,
                description: data.description
            }
        });
    }
})(EditProjectForm);
