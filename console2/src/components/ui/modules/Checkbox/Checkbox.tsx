import _invoke from 'lodash-es/invoke'
import _isNil from 'lodash-es/isNil'
import cx from 'clsx'

import { Ref } from '@fluentui/react-component-ref'
import React, { useRef } from 'react'
import { SemanticShorthandItem } from '../../generic'
import { LabelProps } from '../../elements/Label/Label'
import {
  createHTMLLabel,
  getElementType,
  getUnhandledProps,
  htmlInputAttrs,
  partitionHTMLProps,
  useAutoControlledState,
  useKeyOnly,
} from '../../lib'

export interface CheckboxProps
  extends Omit<
    React.HTMLAttributes<HTMLElement>,
    'onChange' | 'onClick' | 'onMouseDown' | 'onMouseUp' | 'content' | 'id' | 'tabIndex'
  > {
  as?: React.ElementType
  checked?: boolean
  defaultChecked?: boolean
  disabled?: boolean
  id?: number | string
  label?: SemanticShorthandItem<LabelProps>
  name?: string
  onChange?: (event: React.FormEvent<HTMLInputElement>, data: CheckboxEventData) => void
  onClick?: (event: React.MouseEvent<HTMLElement>, data: CheckboxEventData) => void
  onMouseDown?: (event: React.MouseEvent<HTMLElement>, data: CheckboxEventData) => void
  onMouseUp?: (event: React.MouseEvent<HTMLElement>, data: CheckboxEventData) => void
  radio?: boolean
  readOnly?: boolean
  tabIndex?: number | string
  toggle?: boolean
  type?: 'checkbox' | 'radio'
  value?: number | string
}

export type StrictCheckboxProps = CheckboxProps

export interface CheckboxEventData extends CheckboxProps {
  checked: boolean
  indeterminate: boolean
}

interface CheckboxState {
  checked?: boolean
}

/**
 * A checkbox allows a user to select a value from a small set of options, often binary.
 * @see Form
 * @see Radio
 */
function Checkbox(props: CheckboxProps): React.ReactElement {
  const inputRef = useRef<HTMLInputElement>(null)
  const labelRef = useRef<HTMLElement>(null)
  const isClickFromMouseRef = useRef(false)

  const [state, setState] = useAutoControlledState<CheckboxProps, CheckboxState>(props, {
    autoControlledProps: ['checked'],
  })

  const { checked } = state

  const canToggle = (): boolean => {
    const { disabled, radio, readOnly } = props

    return !disabled && !readOnly && !(radio && !!checked)
  }

  const computeTabIndex = (): number | string | undefined => {
    const { disabled, tabIndex } = props

    if (!_isNil(tabIndex)) return tabIndex
    return disabled ? -1 : 0
  }

  const handleClick = (e: React.MouseEvent<HTMLElement>): void => {
    const { id } = props

    const isInputClick = _invoke(inputRef.current, 'contains', e.target)
    const isLabelClick = _invoke(labelRef.current, 'contains', e.target)
    const isRootClick = !isLabelClick && !isInputClick
    const hasId = !_isNil(id)
    const isLabelClickAndForwardedToInput = isLabelClick && hasId

    // https://github.com/Semantic-Org/Semantic-UI-React/pull/3351
    if (!isLabelClickAndForwardedToInput) {
      _invoke(props, 'onClick', e, { ...props, checked: !checked, indeterminate: false })
    }

    if (isClickFromMouseRef.current) {
      isClickFromMouseRef.current = false

      if (isLabelClick && !hasId) {
        handleChange(e)
      }

      // Changes should be triggered for the slider variation
      if (isRootClick) {
        handleChange(e)
      }

      if (isLabelClick && hasId) {
        // To prevent two clicks from being fired from the component we have to stop the propagation
        // from the "input" click: https://github.com/Semantic-Org/Semantic-UI-React/issues/3433
        e.stopPropagation()
      }
    }
  }

  const handleChange = (e: React.SyntheticEvent<HTMLElement>): void => {
    if (!canToggle()) return

    _invoke(props, 'onChange', e, { ...props, checked: !checked, indeterminate: false })
    setState({ checked: !checked })
  }

  const handleMouseDown = (e: React.MouseEvent<HTMLElement>): void => {
    _invoke(props, 'onMouseDown', e, {
      ...props,
      checked: !!checked,
      indeterminate: false,
    })

    if (!e.defaultPrevented) {
      _invoke(inputRef.current, 'focus')
    }

    // Heads Up!
    // We need to call "preventDefault" to keep element focused.
    e.preventDefault()
  }

  const handleMouseUp = (e: React.MouseEvent<HTMLElement>): void => {
    isClickFromMouseRef.current = true

    _invoke(props, 'onMouseUp', e, { ...props, checked: !!checked, indeterminate: false })
  }

  const { className, disabled, label, id, name, radio, readOnly, toggle, type, value } = props

  const classes = cx(
    'ui',
    useKeyOnly(checked, 'checked'),
    useKeyOnly(disabled, 'disabled'),
    // auto apply fitted class to compact white space when there is no label
    // https://semantic-ui.com/modules/checkbox.html#fitted
    useKeyOnly(_isNil(label), 'fitted'),
    useKeyOnly(radio, 'radio'),
    useKeyOnly(readOnly, 'read-only'),
    useKeyOnly(toggle, 'toggle'),
    'checkbox',
    className,
  )

  const unhandled = getUnhandledProps(Checkbox, props)
  const ElementType = getElementType(Checkbox, props)

  const [htmlInputProps, rest] = partitionHTMLProps(unhandled, {
    htmlProps: htmlInputAttrs,
  })

  // Heads Up!
  // Do not remove empty labels, they are required by SUI CSS
  const labelElement = (createHTMLLabel(
    label as SemanticShorthandItem<React.LabelHTMLAttributes<HTMLLabelElement>>,
    {
      defaultProps: { htmlFor: id as string | undefined },
      autoGenerateKey: false,
    },
  ) || React.createElement('label', { htmlFor: id as string | undefined })) as React.ReactElement

  return (
    <ElementType
      {...rest}
      className={classes}
      onClick={handleClick}
      onChange={handleChange}
      onMouseDown={handleMouseDown}
      onMouseUp={handleMouseUp}
    >
      <input
        {...htmlInputProps as React.InputHTMLAttributes<HTMLInputElement>}
        checked={checked}
        className="hidden"
        disabled={disabled}
        id={id as string | undefined}
        name={name}
        readOnly
        tabIndex={computeTabIndex() as number}
        type={type}
        value={value}
        ref={inputRef}
      />
      <Ref innerRef={labelRef}>{labelElement}</Ref>
    </ElementType>
  )
}

Checkbox.handledProps = [
  'as',
  'checked',
  'className',
  'defaultChecked',
  'disabled',
  'id',
  'label',
  'name',
  'onChange',
  'onClick',
  'onMouseDown',
  'onMouseUp',
  'radio',
  'readOnly',
  'tabIndex',
  'toggle',
  'type',
  'value',
]



Checkbox.defaultProps = { type: 'checkbox' } satisfies Partial<CheckboxProps>

export default Checkbox
