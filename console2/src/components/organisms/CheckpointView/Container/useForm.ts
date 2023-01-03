/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import { useState } from 'react';

export interface FormValues {
    // name of field and it's value
    [name: string]: string;
}

/**
 * React hook to handle general form values
 * @param initialFormValues
 *
 * @return {FormValues} form - The current form values
 * @return setField - Function to set a new form value
 * @return clear - Function to clear all form values
 * @return reset - Function to reset values to the initial form values
 */
export const useForm = (initialFormValues: FormValues) => {
    const [initialForm] = useState<FormValues>(initialFormValues);
    const [form, setForm] = useState<FormValues>(initialForm);

    /**
     * Deletes a field from form state
     *
     * @param name - name of the field to delete
     */
    const deleteField = (name: string) => {
        const newForm = form;
        delete newForm[name];
        setForm(newForm);
    };

    /**
     * Sets a form field value in state
     *
     * @param name - name of the field to modify in state
     * @param value - value to set on the field property
     */
    const setField = (name: string, value: string) => {
        // If name given but value is empty, delete the field
        if (name && value === '') {
            deleteField(name);
        }

        setForm({
            ...form,
            [name]: value
        });
    };

    /**
     * Form will be empty
     */
    const clear = () => {
        setForm({});
    };

    /**
     * Form will be set to it's initial values
     */
    const reset = () => {
        setForm(initialForm);
    };

    return { form, setField, clear, reset };
};

export default useForm;
