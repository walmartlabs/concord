
import React from 'react'
import Checkbox, { StrictCheckboxProps } from '../../modules/Checkbox'
import { getElementType, getUnhandledProps } from '../../lib'
import FormField, { StrictFormFieldProps } from './FormField'

export interface FormCheckboxProps extends StrictFormCheckboxProps {}

export interface StrictFormCheckboxProps
  extends Omit<StrictFormFieldProps, keyof StrictCheckboxProps>,
    StrictCheckboxProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A FormField control prop. */
  control?: React.ElementType

  /** HTML input type, either checkbox or radio. */
  type?: 'checkbox' | 'radio'
}

/**
 * Sugar for <Form.Field control={Checkbox} />.
 * @see Checkbox
 * @see Form
 */
function FormCheckbox(props: FormCheckboxProps): React.ReactElement {
  const { control } = props
  const rest = getUnhandledProps(FormCheckbox, props)
  const ElementType = getElementType(FormCheckbox, props)

  return React.createElement(ElementType, { ...rest, control })
}

FormCheckbox.handledProps = ['as', 'control']



FormCheckbox.defaultProps = { as: FormField, control: Checkbox } satisfies Partial<FormCheckboxProps>

export default FormCheckbox
