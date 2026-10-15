
import React from 'react'
import { SemanticShorthandItem } from '../../generic'
import Input, { StrictInputProps } from '../../elements/Input'
import { LabelProps } from '../../elements/Label'
import { getElementType, getUnhandledProps } from '../../lib'
import FormField, { StrictFormFieldProps } from './FormField'

export interface FormInputProps extends StrictFormInputProps {}

export interface StrictFormInputProps
  extends Omit<StrictFormFieldProps, keyof StrictInputProps | 'label'>,
    StrictInputProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A FormField control prop. */
  control?: React.ElementType

  /** Shorthand for a Label. */
  label?: SemanticShorthandItem<LabelProps>
}

/**
 * Sugar for <Form.Field control={Input} />.
 * @see Form
 * @see Input
 */
function FormInput(props: FormInputProps): React.ReactElement {
  const { control } = props
  const rest = getUnhandledProps(FormInput, props)
  const ElementType = getElementType(FormInput, props)

  return React.createElement(ElementType, { ...rest, control })
}

FormInput.handledProps = ['as', 'control']



FormInput.defaultProps = { as: FormField, control: Input } satisfies Partial<FormInputProps>

export default FormInput
