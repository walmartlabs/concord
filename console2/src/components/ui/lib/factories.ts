import _uniq from 'lodash-es/uniq'
import _isArray from 'lodash-es/isArray'
import _isPlainObject from 'lodash-es/isPlainObject'
import _isFunction from 'lodash-es/isFunction'
import _isNumber from 'lodash-es/isNumber'
import _isString from 'lodash-es/isString'
import _isBoolean from 'lodash-es/isBoolean'
import _isNil from 'lodash-es/isNil'
import cx from 'clsx'
import * as React from 'react'
import {
  SemanticShorthandItem,
  SemanticShorthandItemFunc,
  ShorthandRenderFunction,
} from '../generic'

type ShorthandProps<P> = Partial<P> &
  React.Attributes & {
    ref?: React.Ref<unknown>
    childKey?: React.Key | ((props: Partial<P>) => React.Key)
  }

export interface ShorthandFactoryOptions<P> {
  defaultProps?: ShorthandProps<P>
  overrideProps?:
    | ShorthandProps<P>
    | ((props: ShorthandProps<P>) => ShorthandProps<P>)
  autoGenerateKey?: boolean
}

export type ShorthandFactory<P extends object> = (
  value: SemanticShorthandItem<P>,
  options?: ShorthandFactoryOptions<P>,
) => React.ReactNode

/**
 * The dynamic merged-shorthand shape used inside `createShorthand`. Public
 * options keep the narrow `ShorthandProps<P>` contract; the helper narrows the
 * dynamic values here instead of in every consumer.
 */
type MergedProps = Record<string, unknown> & {
  key?: React.Key | null
  childKey?: React.Key | ((props: MergedProps) => React.Key)
  className?: string
  style?: React.CSSProperties
  children?: unknown
}

export function createShorthand<P extends object, V>(
  Component: React.ElementType<P>,
  mapValueToProps: ((value: V) => Partial<P>) | null,
  value: SemanticShorthandItem<P>,
  options: ShorthandFactoryOptions<P> = {},
): React.ReactNode {
  if (typeof Component !== 'function' && typeof Component !== 'string') {
    throw new Error('createShorthand() Component must be a string or function.')
  }

  // short circuit noop values
  if (_isNil(value) || _isBoolean(value)) return null

  const valueIsString = _isString(value)
  const valueIsNumber = _isNumber(value)
  const valueIsFunction = _isFunction(value)
  const valueIsReactElement = React.isValidElement(value)
  const valueIsPropsObject = _isPlainObject(value)
  const valueIsPrimitiveValue = valueIsString || valueIsNumber || _isArray(value)

  // unhandled type return null
  if (
    !valueIsFunction &&
    !valueIsReactElement &&
    !valueIsPropsObject &&
    !valueIsPrimitiveValue
  ) {
    if (process.env.NODE_ENV !== 'production') {
      console.error(
        [
          'Shorthand value must be a string|number|array|object|ReactElement|function.',
          ' Use null|undefined|boolean for none',
          ` Received ${typeof value}.`,
        ].join(''),
      )
    }

    return null
  }

  // ----------------------------------------
  // Build up props
  // ----------------------------------------

  const defaultProps = (options.defaultProps ?? {}) as MergedProps

  // User's props
  const usersProps = (
    valueIsReactElement
      ? (value as React.ReactElement<P>).props
      : valueIsPropsObject
        ? value
        : valueIsPrimitiveValue && mapValueToProps
          ? mapValueToProps(value as V)
          : null
  ) as MergedProps | null

  // Override props
  const rawOverrideProps = options.overrideProps ?? {}
  const overrideProps = (
    _isFunction(rawOverrideProps)
      ? rawOverrideProps({ ...defaultProps, ...(usersProps ?? {}) })
      : rawOverrideProps
  ) as MergedProps

  // Merge props
  /* eslint-disable react/prop-types */
  const props: MergedProps = { ...defaultProps, ...(usersProps ?? {}), ...overrideProps }

  // Merge className
  if (defaultProps.className || overrideProps.className || usersProps?.className) {
    const mergedClassesNames = cx(
      defaultProps.className,
      overrideProps.className,
      usersProps?.className,
    )
    props.className = _uniq(mergedClassesNames.split(' ')).join(' ')
  }

  // Merge style
  if (defaultProps.style || overrideProps.style || usersProps?.style) {
    props.style = {
      ...defaultProps.style,
      ...usersProps?.style,
      ...overrideProps.style,
    }
  }

  // ----------------------------------------
  // Get key
  // ----------------------------------------
  // Use key, childKey, or generate key
  if (_isNil(props.key)) {
    const { childKey } = props
    const { autoGenerateKey = true } = options

    if (!_isNil(childKey)) {
      // apply and consume the childKey
      props.key = typeof childKey === 'function' ? childKey(props) : childKey
      delete props.childKey
    } else if (autoGenerateKey && (valueIsString || valueIsNumber)) {
      // use string/number shorthand values as the key
      props.key = value as React.Key
    }
  }

  // ----------------------------------------
  // Create Element
  // ----------------------------------------
  // Clone ReactElements
  if (valueIsReactElement) {
    return React.cloneElement(
      value as React.ReactElement<P>,
      props as Partial<P> & React.Attributes,
    )
  }

  if (typeof props.children === 'function') {
    return (props.children as ShorthandRenderFunction<React.ElementType<P>, P>)(
      Component,
      { ...props, children: undefined } as P,
    )
  }

  // Create ReactElements from built up props
  if (valueIsPrimitiveValue || valueIsPropsObject) {
    return React.createElement(Component, props as React.Attributes & P)
  }

  // Call functions with args similar to createElement()
  if (valueIsFunction) {
    return (value as SemanticShorthandItemFunc<P>)(
      Component,
      props as P,
      props.children as React.ReactNode,
    )
  }

  return null
}

export function createShorthandFactory<P extends object, V>(
  Component: React.ElementType<P>,
  mapValueToProps: ((value: V) => Partial<P>) | null,
): ShorthandFactory<P> {
  if (typeof Component !== 'function' && typeof Component !== 'string') {
    throw new Error('createShorthandFactory() Component must be a string or function.')
  }

  return (value, options) => createShorthand(Component, mapValueToProps, value, options)
}

// ============================================================
// HTML Factories
// ============================================================

export const createHTMLDivision = createShorthandFactory<
  React.HTMLAttributes<HTMLDivElement>,
  React.ReactNode
>('div', (val) => ({ children: val }))
export const createHTMLInput = createShorthandFactory<
  React.InputHTMLAttributes<HTMLInputElement>,
  string
>('input', (val) => ({ type: val }))
export const createHTMLLabel = createShorthandFactory<
  React.LabelHTMLAttributes<HTMLLabelElement>,
  React.ReactNode
>('label', (val) => ({ children: val }))
export const createHTMLParagraph = createShorthandFactory<
  React.HTMLAttributes<HTMLParagraphElement>,
  React.ReactNode
>('p', (val) => ({ children: val }))
