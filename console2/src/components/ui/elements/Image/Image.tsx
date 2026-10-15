import cx from 'clsx'

import React from 'react'
import { SemanticFLOATS, SemanticSIZES } from '../../generic'
import {
  createShorthandFactory,
  getUnhandledProps,
  htmlImageProps,
  partitionHTMLProps,
  useKeyOnly,
  useValueAndKey,
} from '../../lib'

export interface ImageProps extends React.ImgHTMLAttributes<HTMLImageElement> {
  /** An image can appear on left or right side of a content. */
  floated?: SemanticFLOATS

  /** An image can be formatted to appear in different sizes. */
  size?: SemanticSIZES

  /** An image can be formatted to appear as a ui image. */
  ui?: boolean
}

/**
 * An image is a graphic representation of something.
 * @see Icon
 */
function Image(props: ImageProps): React.ReactElement {
  const { className, floated, size, ui } = props

  const classes = cx(useKeyOnly(ui, 'ui'), size, useValueAndKey(floated, 'floated'), 'image', className)
  const rest = getUnhandledProps(Image, props)
  const [imgTagProps, rootProps] = partitionHTMLProps(rest, { htmlProps: htmlImageProps })

  return React.createElement('img', { ...rootProps, ...imgTagProps, className: classes })
}

Image.handledProps = ['className', 'floated', 'size', 'ui']



Image.defaultProps = { ui: true } satisfies Partial<ImageProps>

Image.create = createShorthandFactory(Image, (value: string) => ({ src: value }))

export default Image
