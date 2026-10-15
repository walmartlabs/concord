import _invoke from 'lodash-es/invoke'
import cx from 'clsx'

import React from 'react'
import { SemanticCOLORS, SemanticShorthandContent } from '../../generic'
import {
  childrenUtils,
  createShorthandFactory,
  getElementType,
  getUnhandledProps,
  useKeyOnly,
} from '../../lib'
import { ShorthandFactory } from '../../lib/factories'
import Icon, { IconProps } from '../Icon/Icon'
import LabelDetail from './LabelDetail'

export interface LabelProps
  extends Omit<React.HTMLAttributes<HTMLElement>, 'onClick' | 'content'> {
  as?: React.ElementType
  active?: boolean
  basic?: boolean
  color?: SemanticCOLORS
  content?: SemanticShorthandContent
  horizontal?: boolean
  onClick?: (event: React.MouseEvent<HTMLElement>, data: LabelProps) => void
  onRemove?: (event: React.MouseEvent<HTMLElement>, data: LabelProps) => void
  pointing?: boolean | 'above'
  prompt?: boolean
  value?: number | string
}

export type StrictLabelProps = LabelProps

/**
 * A label displays content classification.
 */
function Label(props: LabelProps): React.ReactElement {
  const handleClick = (e: React.MouseEvent<HTMLElement>): void => {
    const { onClick } = props

    if (onClick) onClick(e, props)
  }

  const handleIconOverrides = (predefinedProps: Partial<IconProps>) => ({
    onClick: (e: React.MouseEvent<HTMLElement>): void => {
      _invoke(predefinedProps, 'onClick', e)

      _invoke(props, 'onRemove', e, props)
    },
  })

  const {
    active,
    basic,
    children,
    className,
    color,
    content,
    horizontal,
    onRemove,
    pointing,
    prompt,
  } = props

  const pointingClass =
    (pointing === true && 'pointing') || (pointing === 'above' && 'pointing above')

  const classes = cx(
    'ui',
    color,
    pointingClass,
    useKeyOnly(active, 'active'),
    useKeyOnly(basic, 'basic'),
    useKeyOnly(horizontal, 'horizontal'),
    useKeyOnly(prompt, 'prompt'),
    'label',
    className,
  )

  const rest = getUnhandledProps(Label, props)
  const ElementType = getElementType(Label, props)

  if (!childrenUtils.isNil(children)) {
    return React.createElement(
      ElementType,
      { ...rest, className: classes, onClick: handleClick },
      children,
    )
  }

  return React.createElement(
    ElementType,
    { className: classes, onClick: handleClick, ...rest },
    content,
    onRemove &&
      Icon.create('delete', { autoGenerateKey: false, overrideProps: handleIconOverrides }),
  )
}

Label.Detail = LabelDetail
Label.create = createShorthandFactory(Label, (value: React.ReactNode) => ({
  content: value,
})) as ShorthandFactory<LabelProps>

Label.handledProps = [
  'active',
  'as',
  'basic',
  'children',
  'className',
  'color',
  'content',
  'horizontal',
  'onClick',
  'onRemove',
  'pointing',
  'prompt',
  'value',
]



export default Label
