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

import { format as formatDate, parseISO as parseDate } from 'date-fns';
import * as React from 'react';

import { DateInput, DateTimeInput } from 'semantic-ui-calendar-react';
import {
    Button,
    Checkbox,
    CheckboxProps,
    DropdownProps,
    Form,
    Header,
    Input,
    Label
} from 'semantic-ui-react';

import { RequestError } from '../../../api/common';
import {
    Cardinality,
    FormField,
    FormFieldType,
    FormInstanceEntry
} from '../../../api/process/form';
import { DropdownWithAddition } from '../../molecules';
import { RequestErrorMessage } from '../index';

// date-fns format patterns
const DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

// moment.js format patterns
const MOMENT_DATE_FORMAT = 'YYYY-MM-DD';
const MOMENT_DATE_TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss.SSSZ';

interface State {
    [name: string]: any;
}

interface Props {
    form: FormInstanceEntry;
    errors?: {
        [name: string]: string;
    };
    submitting?: boolean;
    submitError?: RequestError;
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

            if (
                (t === FormFieldType.DATE || t === FormFieldType.DATE_TIME) &&
                values[k] !== undefined
            ) {
                // Append the client zone information for date format consistency
                const d = parseDate(values[k]);
                values[k] = formatDate(d, DATE_TIME_FORMAT);
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

    handleDateInput(name: string): (e: React.SyntheticEvent<HTMLElement>, data: any) => void {
        return (ev, { value }) => {
            this.setState({ [name]: value });
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
        allowedValue: DropdownAllowedValue,
        multiple: boolean,
        opts?: {}
    ) {
        const { submitting, completed } = this.props;

        const options = allowedValue ? allowedValue.map((v) => ({ text: v, value: v })) : [];
        const required =
            cardinality === Cardinality.AT_LEAST_ONE ||
            cardinality === Cardinality.ONE_AND_ONLY_ONE;
        const allowAdditions = options.length === 0;

        if (value === null) {
            value = undefined;
        }

        if (multiple && value === undefined) {
            value = [];
        }

        return (
            <DropdownWithAddition
                name={name}
                options={options}
                value={value}
                required={required}
                multiple={multiple}
                completed={completed}
                submitting={submitting}
                allowAdditions={allowAdditions}
                onChange={this.handleDropdown(name)}
                {...opts}
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

        const singleSelect =
            cardinality === Cardinality.ONE_AND_ONLY_ONE || Cardinality.ONE_OR_NONE;
        const multiSelect =
            cardinality === Cardinality.AT_LEAST_ONE || cardinality === Cardinality.ANY;

        const dropdown = (allowedValue instanceof Array && singleSelect) || multiSelect;
        const required =
            cardinality === Cardinality.AT_LEAST_ONE ||
            cardinality === Cardinality.ONE_AND_ONLY_ONE;

        return (
            <Form.Field key={name} error={!!error} required={required}>
                <label>{label}</label>

                {dropdown
                    ? this.renderDropdown(
                          name,
                          cardinality,
                          value,
                          allowedValue,
                          multiSelect,
                          options
                      )
                    : this.renderInput(name, type, value, inputType, options)}

                {error && (
                    <Label basic={true} color="red" pointing={true}>
                        {error}
                    </Label>
                )}
            </Form.Field>
        );
    }

    renderNumberField({ name, label, type, options }: FormField, value?: number) {
        const { errors } = this.props;
        const error = errors ? errors[name] : undefined;

        if (value !== undefined && isNaN(value)) {
            value = undefined;
        }

        return (
            <Form.Field key={name} error={!!error}>
                <label>{label}</label>

                {this.renderInput(name, type, value, 'number', {
                    step: type === 'decimal' ? 'any' : '1',
                    ...options
                })}

                {error && (
                    <Label basic={true} color="red" pointing={true}>
                        {error}
                    </Label>
                )}
            </Form.Field>
        );
    }

    renderBooleanField({ name, label, type, options }: FormField, value: boolean) {
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
                    {...options}
                />

                {error && (
                    <Label basic={true} color="red" pointing={true}>
                        {error}
                    </Label>
                )}
            </Form.Field>
        );
    }

    renderFileField({ name, label, type, cardinality }: FormField) {
        const { errors, submitting, completed } = this.props;
        const error = errors ? errors[name] : undefined;

        const required = cardinality === Cardinality.ONE_AND_ONLY_ONE;

        return (
            <Form.Field key={name} error={!!error} required={required}>
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

    renderDateField({ name, label, cardinality, type, options }: FormField, value: any) {
        const { errors } = this.props;
        const error = errors ? errors[name] : undefined;
        const popupPosition = options ? options.popupPosition : undefined;

        if (value === undefined) {
            value = '';
        }

        if (!cardinality) {
            cardinality = Cardinality.ONE_OR_NONE;
        }

        const required =
            cardinality === Cardinality.AT_LEAST_ONE ||
            cardinality === Cardinality.ONE_AND_ONLY_ONE;

        return (
            <Form.Field key={name} error={!!error} required={required}>
                <label>{label}</label>

                <DateInput
                    name={name}
                    placeholder={`Date (${MOMENT_DATE_FORMAT})`}
                    value={value}
                    iconPosition="left"
                    closable={true}
                    popupPosition={popupPosition}
                    dateFormat={MOMENT_DATE_FORMAT}
                    autoComplete={'off'}
                    clearable={!required}
                    onChange={this.handleDateInput(name)}
                />

                {error && (
                    <Label basic={true} color="red" pointing={true}>
                        {error}
                    </Label>
                )}
            </Form.Field>
        );
    }

    renderDateTimeField({ name, label, cardinality, type, options }: FormField, value: any) {
        const { errors } = this.props;
        const error = errors ? errors[name] : undefined;
        const popupPosition = options ? options.popupPosition : undefined;

        if (value === undefined) {
            value = '';
        }

        if (!cardinality) {
            cardinality = Cardinality.ONE_OR_NONE;
        }

        const required =
            cardinality === Cardinality.AT_LEAST_ONE ||
            cardinality === Cardinality.ONE_AND_ONLY_ONE;

        return (
            <Form.Field key={name} error={!!error} required={required}>
                <label>{label}</label>

                <DateTimeInput
                    name={name}
                    placeholder={`Date/Time (${MOMENT_DATE_TIME_FORMAT})`}
                    value={value}
                    iconPosition="left"
                    closable={true}
                    popupPosition={popupPosition}
                    dateFormat={MOMENT_DATE_FORMAT}
                    dateTimeFormat={MOMENT_DATE_TIME_FORMAT}
                    autoComplete={'off'}
                    clearable={!required}
                    onChange={this.handleDateInput(name)}
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
            case FormFieldType.DATE:
                return this.renderDateField(f, value);
            case FormFieldType.DATE_TIME:
                return this.renderDateTimeField(f, value);
            default:
                return <p key={f.name}>Unknown field type: {f.type}</p>;
        }
    }

    render() {
        const { form, submitting, submitError, completed } = this.props;

        return (
            <>
                <Header as="h2">{form.name}</Header>
                {submitError && <RequestErrorMessage error={submitError} />}
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
                            id="formSubmitButton"
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
