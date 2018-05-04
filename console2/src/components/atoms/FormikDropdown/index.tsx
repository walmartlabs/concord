import { Field, getIn } from 'formik';
import * as React from 'react';
import { Dropdown, Form, Label, FormDropdownProps } from 'semantic-ui-react';

export default class extends React.PureComponent<FormDropdownProps> {
    render() {
        const { name: fieldName, label, required, ...rest } = this.props;

        return (
            <Field
                name={fieldName}
                render={({ field, form }) => {
                    const touched = getIn(form.touched, fieldName);
                    const error = getIn(form.errors, fieldName);
                    const invalid = !!(touched && error);

                    const handleChange = (ev: {}, { value }: { value: string }) =>
                        form.setFieldValue(fieldName, value);

                    return (
                        <Form.Field error={invalid} required={required}>
                            <label>{label}</label>
                            <Dropdown
                                {...rest}
                                onChange={handleChange}
                                value={field.value}
                                error={invalid}
                            />
                            {invalid &&
                                error && (
                                    <Label basic={true} pointing={true} color="red">
                                        {error}
                                    </Label>
                                )}
                        </Form.Field>
                    );
                }}
            />
        );
    }
}
