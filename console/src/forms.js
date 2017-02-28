import React from "react";
import {Form} from "semantic-ui-react";

export const CustomInput = ({input, meta: {error, touched}, label, ...rest}) => (
    <Form.Input {...input} label={label} error={error && touched} {...rest}/>
);

export const CustomSelect = ({input: {name, value, onChange}, type, meta: {error, touched}, label, ...rest}) => {
    const onChangeFn = (ev, data) => {
        ev.target.value = data.value;
        return onChange(ev);
    };
    return <Form.Select name={name} value={value} onChange={onChangeFn} label={label}
                        error={error && touched} {...rest}/>;
};
