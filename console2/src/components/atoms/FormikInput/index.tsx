import { Field, getIn } from 'formik';
import * as React from 'react';
import { Form, FormInputProps, Input, Label } from 'semantic-ui-react';

interface ExternalProps {
    validate?: ((value: {}) => string | Promise<void> | undefined);
}

type Props = FormInputProps & ExternalProps;

export default class extends React.Component<Props> {
    render() {
        const { name: fieldName, label, required, validate, ...rest } = this.props;

        return (
            <Field
                name={fieldName}
                validate={validate}
                render={({ field, form }) => {
                    const touched = getIn(form.touched, fieldName);
                    const error = getIn(form.errors, fieldName);
                    const invalid = !!(touched && error);

                    if (!field.value) {
                        field.value = '';
                    }

                    return (
                        <Form.Field error={invalid} required={required}>
                            <label>{label}</label>
                            <Input {...rest} {...field} />
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
