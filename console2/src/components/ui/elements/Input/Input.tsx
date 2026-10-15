import _get from 'lodash-es/get'
import _invoke from 'lodash-es/invoke'
import _isNil from 'lodash-es/isNil'
import { handleRef } from '@fluentui/react-component-ref'
import cx from 'clsx'

import React, { Children, cloneElement } from 'react'
import { SemanticShorthandItem } from '../../generic'
import {
  childrenUtils,
  createHTMLInput,
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
  partitionHTMLProps,
  useKeyOnly,
  useValueAndKey,
} from '../../lib'
import type { ShorthandFactoryOptions } from '../../lib'
import Icon, { IconProps } from '../Icon/Icon'

export interface InputProps
  extends Omit<
    React.InputHTMLAttributes<HTMLInputElement>,
    'onChange' | 'id' | 'size' | 'tabIndex' | 'content'
  > {
  as?: React.ElementType
  id?: string | number
  tabIndex?: string | number
  error?: boolean
  fluid?: boolean
  icon?: boolean | SemanticShorthandItem<IconProps>
  iconPosition?: 'left'
  loading?: boolean
  onChange?: (event: React.ChangeEvent<HTMLInputElement>, data: InputOnChangeData) => void
}

export type StrictInputProps = InputProps

export interface InputOnChangeData extends InputProps {
  value: string
}

/** Props of the native `<input />` rendered by this component. */
type InputNativeProps = Partial<React.InputHTMLAttributes<HTMLInputElement>> & {
  ref?: React.Ref<HTMLInputElement>
}

/** A native `<input />` element, the only kind of child that `Input` clones. */
type InputChild = React.DOMElement<InputNativeProps, HTMLInputElement>

/**
 * An Input is a field used to elicit a response from a user.
 * @see Button
 * @see Form
 * @see Icon
 * @see Label
 */
class Input extends React.Component<InputProps> {
  static create: (
    value: SemanticShorthandItem<InputProps>,
    options?: ShorthandFactoryOptions<InputProps>,
  ) => React.ReactNode
  static handledProps: string[]
  static defaultProps?: Partial<InputProps>

  private inputRef: HTMLInputElement | null = null

  focus(): void {
    this.inputRef?.focus()
  }

  select(): void {
    this.inputRef?.select()
  }

  private setInputRef = (node: HTMLInputElement | null): void => {
    this.inputRef = node
  }

  render(): React.ReactElement {
    return renderInput(this.props, this.setInputRef)
  }
}
export type InputHandle = Input

function renderInput(props: InputProps, ref: React.Ref<HTMLInputElement>): React.ReactElement {
  const computeIcon = (): SemanticShorthandItem<IconProps> | undefined => {
    const { loading, icon } = props

    if (!_isNil(icon)) return icon
    if (loading) return 'spinner'
  }

  const computeTabIndex = (): string | number | undefined => {
    const { disabled, tabIndex } = props

    if (!_isNil(tabIndex)) return tabIndex
    if (disabled) return -1
  }

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>): void => {
    const value: string = _get(e, 'target.value')

    _invoke(props, 'onChange', e, { ...props, value })
  }

  const handleChildOverrides = (
    child: InputChild,
    defaultProps: InputNativeProps,
  ): InputNativeProps => ({
    ...defaultProps,
    ...child.props,
    ref: (node: HTMLInputElement | null) => {
      handleRef(child.ref as React.Ref<HTMLInputElement> | undefined, node)
      handleRef(ref, node)
    },
  })

  const partitionProps = (): [InputNativeProps, Partial<InputProps>] => {
    const { disabled, type } = props
    const tabIndex = computeTabIndex()
    const unhandled = getUnhandledProps(Input, props)
    const [htmlInputProps, rest] = partitionHTMLProps(unhandled)

    return [
      // `id`, `tabIndex` and `onChange` are forwarded verbatim, so the props
      // partitioned as native input props keep their looser public signatures.
      {
        ...htmlInputProps,
        disabled,
        type,
        tabIndex,
        onChange: handleChange,
      } as unknown as InputNativeProps,
      rest,
    ]
  }

  const { children, className, disabled, error, fluid, icon, iconPosition, loading, type } = props

  // `Search` supplies the private native-input shorthand to add its prompt
  // class; it is intentionally absent from the public InputProps contract.
  const { input } = props as InputProps & {
    input?: SemanticShorthandItem<React.InputHTMLAttributes<HTMLInputElement>>
  }

  const classes = cx(
    'ui',
    useKeyOnly(disabled, 'disabled'),
    useKeyOnly(error, 'error'),
    useKeyOnly(fluid, 'fluid'),
    useKeyOnly(loading, 'loading'),
    useValueAndKey(iconPosition, 'icon') || useKeyOnly(!!icon || loading, 'icon'),
    'input',
    className,
  )
  const ElementType = getElementType(Input, props)

  const [htmlInputProps, rest] = partitionProps()

  // Render with children
  // ----------------------------------------
  if (!childrenUtils.isNil(children)) {
    // add htmlInputProps to the `<input />` child
    const childElements = Children.toArray(children).map((child) => {
      // Children can be anything React renders, only intrinsic `<input />`
      // elements can take the native input props.
      const inputChild = child as unknown as InputChild

      if (inputChild.type !== 'input') return child

      return cloneElement(inputChild, handleChildOverrides(inputChild, htmlInputProps))
    })

    return React.createElement(ElementType, { ...rest, className: classes }, childElements)
  }

  // Render Shorthand
  // ----------------------------------------
  return React.createElement(
    ElementType,
    { ...rest, className: classes },
    createHTMLInput(input || type, { defaultProps: { ...htmlInputProps, ref }, autoGenerateKey: false }),
    Icon.create(computeIcon(), { autoGenerateKey: false }),
  )
}

Input.create = createShorthandFactory(Input, (value: string) => ({
  type: value,
}))

// Search supplies the private native-input shorthand to add its prompt class.
Input.handledProps = [
  'as',
  'children',
  'className',
  'disabled',
  'error',
  'fluid',
  'icon',
  'iconPosition',
  'input',
  'loading',
  'onChange',
  'tabIndex',
  'type',
]



Input.defaultProps = { type: 'text' } satisfies Partial<InputProps>

export default Input
