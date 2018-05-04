import * as React from 'react';
import {
    Button,
    Checkbox,
    Dropdown,
    Form,
    Header,
    Input,
    Label,
    CheckboxProps,
    DropdownProps
} from 'semantic-ui-react';

import {
    Cardinality,
    FormField,
    FormFieldType,
    FormInstanceEntry
} from '../../../api/process/form';

interface State {
    [name: string]: any;
}

interface Props {
    form: FormInstanceEntry;
    errors?: {
        [name: string]: string;
    };
    submitting?: boolean;
    completed?: boolean;
    wizard?: boolean;
    onSubmit: (values: State) => void;
    onReturn: () => void;
}

type DropdownAllowedValue = Array<boolean | number | string> | undefined;
type DropdownValue = boolean | number | string | DropdownAllowedValue;

class ProcessForm extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = {};
    }

    handleReturn(ev: React.FormEvent<HTMLButtonElement>) {
        ev.preventDefault();
        this.props.onReturn();
    }

    handleSubmit(ev: React.FormEvent<HTMLFormElement>) {
        ev.preventDefault();

        let values = { ...this.state };
        if (!values) {
            values = {};
        }

        const { form, onSubmit } = this.props;

        for (const f of form.fields) {
            const k = f.name;
            const v = values[k];
            const t = f.type;

            if (v === null || v === undefined) {
                values[k] = f.value;
            } else if (v === '') {
                values[k] = null;
            } else if ((t === FormFieldType.INT || t === FormFieldType.DECIMAL) && isNaN(v)) {
                values[k] = null;
            }
        }

        // remove undefined values
        const result = {};
        Object.keys(values).forEach((k) => {
            const v = values[k];
            if (v !== undefined && v !== null) {
                result[k] = v;
            }
        });

        onSubmit(result);
    }

    handleInput(
        name: string,
        type: FormFieldType
    ): (event: React.SyntheticEvent<HTMLInputElement>) => void {
        return ({ target }) => {
            const t = target as HTMLInputElement;

            let v: string | number | boolean | File = t.value;
            if (type === FormFieldType.INT || type === FormFieldType.DECIMAL) {
                v = t.valueAsNumber;
            } else if (type === FormFieldType.FILE) {
                v = t.files![0];
            }

            this.setState({ [name]: v });
        };
    }

    handleDropdown(
        name: string
    ): (event: React.SyntheticEvent<HTMLElement>, data: DropdownProps) => void {
        return (ev, { value }) => {
            this.setState({ [name]: value });
        };
    }

    handleCheckboxInput(
        name: string
    ): (event: React.FormEvent<HTMLInputElement>, data: CheckboxProps) => void {
        return (ev, { checked }) => {
            this.setState({ [name]: checked });
        };
    }

    renderInput(name: string, type: FormFieldType, value: any, inputType?: string, opts?: {}) {
        const { submitting, completed } = this.props;
        return (
            <Input
                name={name}
                disabled={submitting || completed}
                defaultValue={value}
                type={inputType}
                onChange={this.handleInput(name, type)}
                {...opts}
            />
        );
    }

    renderDropdown(
        name: string,
        cardinality: Cardinality,
        value: DropdownValue,
        allowedValue: DropdownAllowedValue
    ) {
        const { submitting, completed } = this.props;

        const options = allowedValue ? allowedValue.map((v) => ({ text: v, value: v })) : [];
        const multiple =
            cardinality === Cardinality.AT_LEAST_ONE || cardinality === Cardinality.ANY;

        if (value === null) {
            value = undefined;
        }

        if (multiple && value === undefined) {
            value = [];
        }

        return (
            <Dropdown
                selection={true}
                multiple={multiple}
                name={name}
                disabled={submitting || completed}
                value={value}
                options={options}
                onChange={this.handleDropdown(name)}
            />
        );
    }

    renderStringField(
        { name, label, type, cardinality, allowedValue, options }: FormField,
        value: any
    ) {
        const { errors } = this.props;
        const error = errors ? errors[name] : undefined;
        const inputType = options ? options.inputType : undefined;

        if (!cardinality) {
            cardinality = Cardinality.ONE_OR_NONE;
        }

        // TODO check cardinality
        const dropdown = allowedValue instanceof Array;
        const required =
            cardinality === Cardinality.AT_LEAST_ONE ||
            cardinality === Cardinality.ONE_AND_ONLY_ONE;

        return (
            <Form.Field key={name} error={!!error} required={required}>
                <label>{label}</label>

                {dropdown
                    ? this.renderDropdown(name, cardinality, value, allowedValue)
                    : this.renderInput(name, type, value, inputType)}

                {error && (
                    <Label basic={true} color="red" pointing={true}>
                        {error}
                    </Label>
                )}
            </Form.Field>
        );
    }

    renderNumberField({ name, label, type }: FormField, value?: number) {
        const { errors } = this.props;
        const error = errors ? errors[name] : undefined;

        if (value !== undefined && isNaN(value)) {
            value = undefined;
        }

        return (
            <Form.Field key={name} error={!!error}>
                <label>{label}</label>

                {this.renderInput(name, type, value, 'number', {
                    step: type === 'decimal' ? 'any' : '1'
                })}

                {error && (
                    <Label basic={true} color="red" pointing={true}>
                        {error}
                    </Label>
                )}
            </Form.Field>
        );
    }

    renderBooleanField({ name, label, type }: FormField, value: boolean) {
        const { errors, submitting, completed } = this.props;
        const error = errors ? errors[name] : undefined;

        return (
            <Form.Field key={name} error={!!error}>
                <label>{label}</label>

                <Checkbox
                    name={name}
                    disabled={submitting || completed}
                    defaultChecked={value}
                    onChange={this.handleCheckboxInput(name)}
                />

                {error && (
                    <Label basic={true} color="red" pointing={true}>
                        {error}
                    </Label>
                )}
            </Form.Field>
        );
    }

    renderFileField({ name, label, type }: FormField) {
        const { errors, submitting, completed } = this.props;
        const error = errors ? errors[name] : undefined;

        return (
            <Form.Field key={name} error={!!error}>
                <label>{label}</label>

                <Input
                    name={name}
                    type="file"
                    disabled={submitting || completed}
                    onChange={this.handleInput(name, type)}
                />

                {error && (
                    <Label basic={true} color="red" pointing={true}>
                        {error}
                    </Label>
                )}
            </Form.Field>
        );
    }

    renderField(f: FormField) {
        let value = this.state[f.name];
        if (value === undefined) {
            value = f.value;
        }

        switch (f.type) {
            case FormFieldType.STRING:
                return this.renderStringField(f, value);
            case FormFieldType.INT:
            case FormFieldType.DECIMAL:
                return this.renderNumberField(f, value);
            case FormFieldType.BOOLEAN:
                return this.renderBooleanField(f, value);
            case FormFieldType.FILE:
                return this.renderFileField(f);
            default:
                return <p key={f.name}>Unknown field type: {f.type}</p>;
        }
    }

    render() {
        const { form, submitting, completed } = this.props;

        return (
            <>
                <Header as="h2">{form.name}</Header>
                <Form loading={submitting} onSubmit={(ev) => this.handleSubmit(ev)}>
                    {form.fields.map((f) => this.renderField(f))}
                    {completed ? (
                        <Button
                            icon="check"
                            primary={true}
                            content="Return to the process page"
                            onClick={(ev) => this.handleReturn(ev)}
                        />
                    ) : (
                        <Button
                            type="submit"
                            primary={true}
                            disabled={submitting}
                            content="Submit"
                        />
                    )}
                </Form>
            </>
        );
    }
}

export default ProcessForm;
