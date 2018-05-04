import { Field, getIn } from 'formik';
import * as React from 'react';
import { Checkbox, Form, FormCheckboxProps, Label } from 'semantic-ui-react';

interface Props {
    name: string;
}

export default class extends React.PureComponent<FormCheckboxProps & Props> {
    render() {
        const { name: fieldName, label, required, ...rest } = this.props;

        return (
            <Field
                name={fieldName}
                render={({ field, form }) => {
                    const touched = getIn(form.touched, fieldName);
                    const error = getIn(form.errors, fieldName);
                    const invalid = !!(touched && error);

                    const handleChanges = (ev: {}, { checked }: { checked: boolean }) =>
                        form.setFieldValue(fieldName, checked);

                    return (
                        <Form.Field error={invalid} required={required}>
                            <label>{label}</label>
                            <Checkbox {...rest} onChange={handleChanges} checked={field.value} />
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
