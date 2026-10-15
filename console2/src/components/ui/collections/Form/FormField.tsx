import _get from 'lodash-es/get'
import _isNil from 'lodash-es/isNil'
import cx from 'clsx'

import React, { createElement } from 'react'
import { HtmlLabelProps, SemanticShorthandContent, SemanticShorthandItem } from '../../generic'
import {
  childrenUtils,
  createHTMLLabel,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
} from '../../lib'
import Label, { LabelProps } from '../../elements/Label'
import Checkbox from '../../modules/Checkbox'
import Radio from '../../addons/Radio'

export interface FormFieldProps
  extends StrictFormFieldProps,
    Omit<React.InputHTMLAttributes<HTMLInputElement>, keyof StrictFormFieldProps | 'onChange'> {
  onChange?: React.ChangeEventHandler<HTMLInputElement>
}

export interface StrictFormFieldProps {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /** Primary content. */
  children?: React.ReactNode

  /** Shorthand for primary content. */
  content?: SemanticShorthandContent

  /**
   * A form control component (i.e. Dropdown) or HTML tagName (i.e. 'input').
   * Extra FormField props are passed to the control component.
   * Mutually exclusive with children.
   */
  control?: React.ElementType

  /** Individual fields may be disabled. */
  disabled?: boolean

  /** Individual fields may display an error state along with a message. */
  error?: boolean | SemanticShorthandItem<LabelProps>

  /** The id of the control */
  id?: number | string

  /** A field can have its label next to instead of above it. */
  inline?: boolean

  /** Mutually exclusive with children. */
  label?: SemanticShorthandItem<HtmlLabelProps>

  /** A field can show that input is mandatory. Requires a label. */
  required?: boolean

  /** Passed to the control component (i.e. <input type='password' />) */
  type?: string
}

/**
 * A field is a form element containing a label and an input.
 * @see Form
 * @see Button
 * @see Checkbox
 * @see Dropdown
 * @see Input
 * @see Radio
 * @see Select
 */
function FormField(props: FormFieldProps): React.ReactElement {
  const {
    children,
    className,
    content,
    control,
    disabled,
    error,
    inline,
    label,
    required,
    type,
    id,
  } = props

  const classes = cx(
    useKeyOnly(disabled, 'disabled'),
    useKeyOnly(!!error, 'error'),
    useKeyOnly(inline, 'inline'),
    useKeyOnly(required, 'required'),
    'field',
    className,
  )
  const rest = getUnhandledProps(FormField, props)
  const ElementType = getElementType(FormField, props)

  const errorPointing = _get(error, 'pointing', 'above')

  // `error` is passed through as-is, it may be a boolean that createShorthand() short-circuits
  const errorLabel = Label.create(error as SemanticShorthandItem<LabelProps>, {
    autoGenerateKey: false,
    defaultProps: {
      prompt: true,
      pointing: errorPointing,
      id: id ? `${id}-error-message` : undefined,
      role: 'alert',
      'aria-atomic': true,
    },
  })
  const errorLabelBefore = (errorPointing === 'below' || errorPointing === 'right') && errorLabel
  const errorLabelAfter = (errorPointing === 'above' || errorPointing === 'left') && errorLabel

  // ----------------------------------------
  // No Control
  // ----------------------------------------

  if (_isNil(control)) {
    if (_isNil(label)) {
      return React.createElement(
        ElementType,
        { ...rest, className: classes, id },
        childrenUtils.isNil(children) ? content : children,
      )
    }

    return React.createElement(
      ElementType,
      { ...rest, className: classes, id },
      errorLabelBefore,
      createHTMLLabel(
        label as SemanticShorthandItem<React.LabelHTMLAttributes<HTMLLabelElement>>,
        { autoGenerateKey: false },
      ),
      errorLabelAfter,
    )
  }

  // ----------------------------------------
  // Checkbox/Radio Control
  // ----------------------------------------

  const ariaDescribedBy = id && error ? `${id}-error-message` : null
  const ariaAttrs = {
    'aria-describedby': ariaDescribedBy,
    'aria-invalid': error ? true : undefined,
  }

  const controlProps = { ...rest, content, children, disabled, required, type, id }

  // wrap HTML checkboxes/radios in the label
  if (control === 'input' && (type === 'checkbox' || type === 'radio')) {
    return React.createElement(
      ElementType,
      { className: classes },
      React.createElement(
        'label',
        null,
        errorLabelBefore,
        createElement(control, { ...ariaAttrs, ...controlProps }),
        ' ',
        label,
        errorLabelAfter,
      ),
    )
  }

  // pass label prop to controls that support it
  if (control === Checkbox || control === Radio) {
    return React.createElement(
      ElementType,
      { className: classes },
      errorLabelBefore,
      createElement(control, { ...ariaAttrs, ...controlProps, label }),
      errorLabelAfter,
    )
  }

  // ----------------------------------------
  // Other Control
  // ----------------------------------------

  return React.createElement(
    ElementType,
    { className: classes },
    createHTMLLabel(label as SemanticShorthandItem<React.LabelHTMLAttributes<HTMLLabelElement>>, {
      defaultProps: { htmlFor: id as string | undefined },
      autoGenerateKey: false,
    }),
    errorLabelBefore,
    createElement(control, { ...ariaAttrs, ...controlProps }),
    errorLabelAfter,
  )
}

FormField.handledProps = [
  'as',
  'children',
  'className',
  'content',
  'control',
  'disabled',
  'error',
  'id',
  'inline',
  'label',
  'required',
  'type',
]



export default FormField
