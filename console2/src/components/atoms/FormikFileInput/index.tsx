import { Field, getIn } from 'formik';
import * as React from 'react';
import { Form, FormInputProps, Input, Label } from 'semantic-ui-react';

export default class extends React.Component<FormInputProps> {
    render() {
        const { name: fieldName, label, required, ...rest } = this.props;

        return (
            <Field
                name={fieldName}
                render={({ field, form }) => {
                    const touched = getIn(form.touched, fieldName);
                    const error = getIn(form.errors, fieldName);
                    const invalid = !!(touched && error);

                    const handleChange = (ev: any) =>
                        form.setFieldValue(fieldName, ev.target.files[0]);

                    return (
                        <Form.Field error={invalid} required={required}>
                            <label>{label}</label>
                            <Input {...rest} type="file" onChange={handleChange} />
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
