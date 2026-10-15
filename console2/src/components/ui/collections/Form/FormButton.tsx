
import React from 'react'
import { SemanticShorthandItem } from '../../generic'
import Button, { StrictButtonProps } from '../../elements/Button'
import { LabelProps } from '../../elements/Label'
import { getElementType, getUnhandledProps } from '../../lib'
import FormField, { StrictFormFieldProps } from './FormField'

export interface FormButtonProps extends StrictFormButtonProps {}

export interface StrictFormButtonProps
  extends Omit<StrictFormFieldProps, keyof StrictButtonProps | 'label'>,
    StrictButtonProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** A FormField control prop. */
  control?: React.ElementType

  /** Shorthand for a Label. */
  label?: SemanticShorthandItem<LabelProps>
}

/**
 * Sugar for <Form.Field control={Button} />.
 * @see Button
 * @see Form
 */
function FormButton(props: FormButtonProps): React.ReactElement {
  const { control } = props
  const rest = getUnhandledProps(FormButton, props)
  const ElementType = getElementType(FormButton, props)

  return React.createElement(ElementType, { ...rest, control })
}

FormButton.handledProps = ['as', 'control']



FormButton.defaultProps = { as: FormField, control: Button } satisfies Partial<FormButtonProps>

export default FormButton
