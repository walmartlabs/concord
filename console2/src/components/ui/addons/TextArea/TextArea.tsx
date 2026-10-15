import _get from 'lodash-es/get'
import _invoke from 'lodash-es/invoke'

import React from 'react'
import { getElementType, getUnhandledProps } from '../../lib'

export interface TextAreaProps
  extends Omit<
    React.TextareaHTMLAttributes<HTMLTextAreaElement>,
    'onChange' | 'onInput' | 'rows' | 'value'
  > {
  /** An element type to render as (string or function). */
  as?: React.ElementType

  /**
   * Called on change.
   * @param {SyntheticEvent} event - The React SyntheticEvent object
   * @param {object} data - All props and the event value.
   */
  onChange?: (event: React.ChangeEvent<HTMLTextAreaElement>, data: TextAreaProps) => void

  /**
   * Called on input.
   * @param {SyntheticEvent} event - The React SyntheticEvent object
   * @param {object} data - All props and the event value.
   */
  onInput?: (event: React.FormEvent<HTMLTextAreaElement>, data: TextAreaProps) => void

  /** Indicates row count for a TextArea. */
  rows?: number | string

  /** The value of the textarea. */
  value?: number | string
}

export type StrictTextAreaProps = TextAreaProps

/**
 * A TextArea can be used to allow for extended user input.
 * @see Form
 */
class TextArea extends React.Component<TextAreaProps> {
  static handledProps: string[]
  static defaultProps?: Partial<TextAreaProps>

  private textareaRef: HTMLTextAreaElement | null = null

  focus(): void {
    this.textareaRef?.focus()
  }

  render(): React.ReactElement {
    return renderTextArea(this.props, (node) => {
      this.textareaRef = node
    })
  }
}
export type TextAreaHandle = TextArea

function renderTextArea(
  props: TextAreaProps,
  setTextareaRef: (node: HTMLTextAreaElement | null) => void,
): React.ReactElement {
  const handleChange = (e: React.ChangeEvent<HTMLTextAreaElement>): void => {
    const value: string = _get(e, 'target.value')

    _invoke(props, 'onChange', e, { ...props, value })
  }

  const handleInput = (e: React.FormEvent<HTMLTextAreaElement>): void => {
    const value: string = _get(e, 'target.value')

    _invoke(props, 'onInput', e, { ...props, value })
  }

  const { rows, value } = props

  const rest = getUnhandledProps(TextArea, props)
  const ElementType = getElementType(TextArea, props)

  return React.createElement(ElementType, {
    ...rest,
    ref: setTextareaRef,
    onChange: handleChange,
    onInput: handleInput,
    rows,
    value,
  })
}

TextArea.handledProps = ['as', 'onChange', 'onInput', 'rows', 'value']



TextArea.defaultProps = { as: 'textarea', rows: 3 } satisfies Partial<TextAreaProps>

export default TextArea
